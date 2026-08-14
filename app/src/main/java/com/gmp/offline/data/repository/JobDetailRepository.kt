package com.gmp.offline.data.repository

import android.content.Context
import android.net.Uri
import com.gmp.offline.BuildConfig
import com.gmp.offline.data.local.dao.JobDao
import com.gmp.offline.data.local.dao.JobMaterialDao
import com.gmp.offline.data.local.dao.JobPhotoDao
import com.gmp.offline.data.local.dao.JobWorkerDao
import com.gmp.offline.data.local.dao.MaterialDao
import com.gmp.offline.data.local.entities.JobMaterialEntity
import com.gmp.offline.data.local.entities.JobPhotoEntity
import com.gmp.offline.data.local.entities.JobWorkerEntity
import com.gmp.offline.data.remote.dto.JobPhotoDto
import com.gmp.offline.data.remote.dto.toEntity
import com.gmp.offline.data.session.SessionManager
import com.gmp.offline.sync.CommandQueue
import com.gmp.offline.sync.CommandResult
import com.gmp.offline.util.PhotoCompressor
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject

// Resultado de intentar agregar/reintentar una foto — la UI lo usa para
// mostrar un mensaje de error puntual sin tener que interpretar excepciones.
sealed interface PhotoActionResult {
    data object Success : PhotoActionResult
    data class Error(val message: String) : PhotoActionResult
}

// Agrupa lo que necesita una pantalla de detalle de job: sus trabajadores
// asignados, materiales y fotos — todo leído de Room. Desde Fase 6 Paso 2
// también agrega/quita materiales (comercial/admin/trabajador asignado),
// con el mismo patrón optimista + outbox que JobsRepository.
//
// Fase 6, Paso 4: se agrega la foto única de comercial. A diferencia de
// materiales (JSON simple, va por el outbox normal vía CommandQueue), subir
// una foto es multipart — el CommandDispatcher del outbox solo sabe mandar
// JSON, así que acá se arma la request multipart a mano reutilizando el
// mismo OkHttpClient inyectado (mismo cliente que usa Retrofit, por lo que
// ya trae el AuthInterceptor con el header Authorization puesto solo).
// El intento de subida es inmediato (no pasa por el outbox/WorkManager); si
// falla por falta de red queda guardada localmente con uploadStatus="error"
// y la UI ofrece un botón "Reintentar" que llama a retryPhotoUpload().
class JobDetailRepository @Inject constructor(
    private val jobDao: JobDao,
    private val jobWorkerDao: JobWorkerDao,
    private val jobMaterialDao: JobMaterialDao,
    private val jobPhotoDao: JobPhotoDao,
    private val materialDao: MaterialDao,
    private val commandQueue: CommandQueue,
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val sessionManager: SessionManager,
) {
    fun observeWorkers(jobUuid: String): Flow<List<JobWorkerEntity>> =
        jobWorkerDao.observeByJob(jobUuid)

    /**
     * Asigna o quita a un trabajador/admin del montaje (`solo admin` según
     * jobsActionsController.js — la UI ya lo restringe con
     * JobDetailViewModel.isAdmin antes de mostrar esta acción).
     *
     * No valida solapes de horario a propósito: un mismo trabajador puede
     * quedar asignado a más de un montaje el mismo día (pedido explícito).
     *
     * Igual que el resto de las acciones de estado (ver JobsRepository),
     * aplica el cambio optimista en Room primero y encola el comando real
     * después. Cuando se asigna el primer trabajador y el job estaba en
     * "pending", el servidor lo pasa automáticamente a "assigned" (ver
     * avance_fase_3.md, sección 4.2) — se replica ese mismo efecto acá para
     * que la UI no tenga que esperar el próximo /sync.
     */
    suspend fun toggleWorkerAssignment(jobUuid: String, workerUuid: String, assign: Boolean) {
        val nowIso = isoNowUtc()
        if (assign) {
            if (jobWorkerDao.findByJobAndUser(jobUuid, workerUuid) != null) return
            jobWorkerDao.upsertAll(
                listOf(
                    JobWorkerEntity(
                        uuid = UUID.randomUUID().toString(),
                        jobUuid = jobUuid,
                        userUuid = workerUuid,
                        createdAt = nowIso,
                        updatedAt = nowIso,
                    ),
                ),
            )

            val job = jobDao.getByUuid(jobUuid)
            if (job != null && job.status == "pending") {
                jobDao.upsertAll(listOf(job.copy(status = "assigned", updatedAt = nowIso)))
            }

            commandQueue.enqueue(
                endpointPath = "/jobs/$jobUuid/assign",
                httpMethod = "POST",
                payload = mapOf("user_uuid" to workerUuid),
            )
        } else {
            val existing = jobWorkerDao.findByJobAndUser(jobUuid, workerUuid) ?: return
            jobWorkerDao.deleteByUuids(listOf(existing.uuid))

            commandQueue.enqueue(
                endpointPath = "/jobs/$jobUuid/unassign",
                httpMethod = "POST",
                payload = mapOf("user_uuid" to workerUuid),
            )
        }
    }

    /**
     * Fija la fecha oficial confirmada del montaje (`jobs.scheduled_at`,
     * distinta de `visit_date`/`proposed_date`). Mismo patrón optimista +
     * outbox de siempre, contra `PATCH /jobs/:uuid`.
     */
    suspend fun setScheduledDate(jobUuid: String, isoDate: String?) {
        val job = jobDao.getByUuid(jobUuid) ?: return
        val scheduledAt = isoDate?.let { "${it}T00:00:00.000Z" }
        val nowIso = isoNowUtc()

        jobDao.upsertAll(listOf(job.copy(scheduledAt = scheduledAt, updatedAt = nowIso)))

        commandQueue.enqueue(
            endpointPath = "/jobs/$jobUuid",
            httpMethod = "PATCH",
            payload = mapOf("scheduled_at" to scheduledAt),
        )
    }

    fun observeMaterials(jobUuid: String): Flow<List<JobMaterialEntity>> =
        jobMaterialDao.observeByJob(jobUuid)

    fun observePhotos(jobUuid: String): Flow<List<JobPhotoEntity>> =
        jobPhotoDao.observeByJob(jobUuid)

    /**
     * Añade un material a un job — de catálogo (`materialUuid`) o de texto
     * libre (`freeTextDescription`); son mutuamente excluyentes según el
     * esquema de `job_materials` (ver fase1-diseno-datos-sync.md, 2.6).
     *
     * Si viene de catálogo y no se especifica `unitPrice`, se sugiere el
     * `defaultPrice` del material como valor inicial (decisión pendiente
     * marcada en avance_fase_3.md, sección 6.1, resuelta acá: se hereda si
     * el campo llega vacío desde la UI).
     */
    suspend fun addMaterial(
        jobUuid: String,
        materialUuid: String?,
        freeTextDescription: String?,
        quantity: String,
        unitPrice: String?,
    ) {
        val resolvedUnitPrice = unitPrice
            ?: materialUuid?.let { materialDao.getByUuid(it)?.defaultPrice }

        val itemUuid = UUID.randomUUID().toString()
        val nowIso = isoNowUtc()

        jobMaterialDao.upsertAll(
            listOf(
                JobMaterialEntity(
                    uuid = itemUuid,
                    jobUuid = jobUuid,
                    materialUuid = materialUuid,
                    freeTextDescription = freeTextDescription,
                    quantity = quantity,
                    unitPrice = resolvedUnitPrice,
                    createdAt = nowIso,
                    updatedAt = nowIso,
                ),
            ),
        )

        commandQueue.enqueue(
            endpointPath = "/jobs/$jobUuid/materials",
            httpMethod = "POST",
            payload = mapOf(
                "uuid" to itemUuid,
                "material_uuid" to materialUuid,
                "free_text_description" to freeTextDescription,
                "quantity" to quantity,
                "unit_price" to resolvedUnitPrice,
            ),
        )
    }

    /**
     * Quita (soft delete) un material ya agregado a un job.
     *
     * Nota: `jobMaterialUuid` es el `uuid` propio de la fila `job_materials`
     * (no el `uuid` del material de catálogo) — así se identifica el
     * registro puntual a borrar, siguiendo el mismo criterio que usa
     * `DELETE /materials/:uuid` para el catálogo. No se pudo confirmar este
     * detalle contra `jobMaterialsController.js` en esta sesión porque no
     * se tuvo el archivo a la vista; si el backend en realidad espera el
     * `uuid` del material de catálogo en su lugar, ajustar acá.
     */
    suspend fun removeMaterial(jobUuid: String, jobMaterialUuid: String) {
        // Borrado optimista local: se quita de Room ya mismo. Si el borrado
        // en servidor fallara por alguna regla de negocio, el próximo pull
        // de /sync lo restauraría (last-write-wins), igual que con jobs.
        jobMaterialDao.deleteByUuids(listOf(jobMaterialUuid))

        commandQueue.enqueue(
            endpointPath = "/jobs/$jobUuid/materials/$jobMaterialUuid",
            httpMethod = "DELETE",
            payload = emptyMap(),
        )
    }

    /**
     * Agrega la foto del montaje (comercial): solo se permite **una** por
     * job. Si ya había una, se reemplaza — se borra la anterior primero
     * (local siempre; en el servidor también si ya estaba sincronizada).
     *
     * La imagen se comprime antes de guardarla/subirla (ver
     * [PhotoCompressor]) para no gastar datos móviles de más ni tardar en
     * subir.
     */
    suspend fun addPhoto(jobUuid: String, imageUri: Uri): PhotoActionResult = withContext(Dispatchers.IO) {
        jobPhotoDao.getFirstByJob(jobUuid)?.let { existing -> removePhotoInternal(jobUuid, existing) }

        val compressedBytes = try {
            PhotoCompressor.compress(context, imageUri)
        } catch (e: IOException) {
            return@withContext PhotoActionResult.Error("No se pudo procesar la imagen: ${e.message}")
        }

        val photoUuid = UUID.randomUUID().toString()
        val localFile = File(photosDir(), "$photoUuid.jpg")
        try {
            localFile.writeBytes(compressedBytes)
        } catch (e: IOException) {
            return@withContext PhotoActionResult.Error("No se pudo guardar la imagen: ${e.message}")
        }

        val userUuid = sessionManager.userUuid.orEmpty()
        val nowIso = isoNowUtc()
        jobPhotoDao.upsertAll(
            listOf(
                JobPhotoEntity(
                    uuid = photoUuid,
                    jobUuid = jobUuid,
                    uploadedByUuid = userUuid,
                    url = "",
                    createdAt = nowIso,
                    updatedAt = nowIso,
                    localPath = localFile.absolutePath,
                    uploadStatus = "uploading",
                ),
            ),
        )

        applyUploadResult(
            jobUuid = jobUuid,
            photoUuid = photoUuid,
            userUuid = userUuid,
            localPath = localFile.absolutePath,
            nowIso = nowIso,
            result = uploadToServer(jobUuid, photoUuid, localFile),
        )
    }

    /**
     * Reintenta subir la foto que quedó en estado "error" (falló por red o
     * fue rechazada por el servidor). No vuelve a comprimir — usa el mismo
     * archivo local ya comprimido la primera vez.
     */
    suspend fun retryPhotoUpload(jobUuid: String): PhotoActionResult = withContext(Dispatchers.IO) {
        val existing = jobPhotoDao.getFirstByJob(jobUuid)
            ?: return@withContext PhotoActionResult.Error("No hay ninguna foto pendiente de subir.")
        val localPath = existing.localPath
            ?: return@withContext PhotoActionResult.Error("No se encontró el archivo local de la foto.")
        val file = File(localPath)
        if (!file.exists()) {
            return@withContext PhotoActionResult.Error("El archivo local de la foto ya no existe.")
        }

        jobPhotoDao.upsertAll(listOf(existing.copy(uploadStatus = "uploading")))

        applyUploadResult(
            jobUuid = jobUuid,
            photoUuid = existing.uuid,
            userUuid = existing.uploadedByUuid,
            localPath = localPath,
            nowIso = isoNowUtc(),
            result = uploadToServer(jobUuid, existing.uuid, file),
        )
    }

    /** Quita la foto del montaje sin reemplazarla por otra. */
    suspend fun removePhoto(jobUuid: String) = withContext(Dispatchers.IO) {
        jobPhotoDao.getFirstByJob(jobUuid)?.let { existing -> removePhotoInternal(jobUuid, existing) }
        Unit
    }

    private suspend fun applyUploadResult(
        jobUuid: String,
        photoUuid: String,
        userUuid: String,
        localPath: String,
        nowIso: String,
        result: CommandResult,
    ): PhotoActionResult = when (result) {
        is CommandResult.Success -> {
            val confirmed = try {
                Gson().fromJson(result.responseBody, JobPhotoDto::class.java).toEntity()
                    .copy(localPath = localPath, uploadStatus = "synced")
            } catch (e: Exception) {
                // Si la respuesta no se pudo parsear, se marca igual como
                // subida — el próximo /sync trae los datos definitivos.
                JobPhotoEntity(
                    uuid = photoUuid,
                    jobUuid = jobUuid,
                    uploadedByUuid = userUuid,
                    url = "",
                    createdAt = nowIso,
                    updatedAt = nowIso,
                    localPath = localPath,
                    uploadStatus = "synced",
                )
            }
            jobPhotoDao.upsertAll(listOf(confirmed))
            PhotoActionResult.Success
        }
        is CommandResult.HttpError -> {
            jobPhotoDao.upsertAll(
                listOf(
                    JobPhotoEntity(
                        uuid = photoUuid,
                        jobUuid = jobUuid,
                        uploadedByUuid = userUuid,
                        url = "",
                        createdAt = nowIso,
                        updatedAt = nowIso,
                        localPath = localPath,
                        uploadStatus = "error",
                    ),
                ),
            )
            PhotoActionResult.Error("El servidor rechazó la foto (código ${result.code}).")
        }
        is CommandResult.NetworkError -> {
            jobPhotoDao.upsertAll(
                listOf(
                    JobPhotoEntity(
                        uuid = photoUuid,
                        jobUuid = jobUuid,
                        uploadedByUuid = userUuid,
                        url = "",
                        createdAt = nowIso,
                        updatedAt = nowIso,
                        localPath = localPath,
                        uploadStatus = "error",
                    ),
                ),
            )
            PhotoActionResult.Error("Sin conexión — se va a poder reintentar en un momento.")
        }
    }

    private suspend fun removePhotoInternal(jobUuid: String, photo: JobPhotoEntity) {
        jobPhotoDao.deleteByUuids(listOf(photo.uuid))
        photo.localPath?.let { path -> runCatching { File(path).delete() } }
        // Si nunca se confirmó en el servidor (quedó en "uploading"/"error"),
        // no hay nada que borrar del lado del backend.
        if (photo.uploadStatus == "synced") {
            commandQueue.enqueue(
                endpointPath = "/jobs/$jobUuid/photos/${photo.uuid}",
                httpMethod = "DELETE",
                payload = emptyMap(),
            )
        }
    }

    private suspend fun uploadToServer(jobUuid: String, photoUuid: String, file: File): CommandResult =
        withContext(Dispatchers.IO) {
            try {
                val mediaType = "image/jpeg".toMediaType()
                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("uuid", photoUuid)
                    .addFormDataPart("photo", file.name, file.asRequestBody(mediaType))
                    .build()

                val url = BuildConfig.API_BASE_URL.trimEnd('/') + "/jobs/$jobUuid/photos"
                val request = Request.Builder()
                    .url(url)
                    .header("X-Command-Id", UUID.randomUUID().toString())
                    .post(multipartBody)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        CommandResult.Success(text)
                    } else {
                        CommandResult.HttpError(response.code, text)
                    }
                }
            } catch (e: IOException) {
                CommandResult.NetworkError(e.message)
            }
        }

    private fun photosDir(): File =
        File(context.filesDir, "job_photos").apply { mkdirs() }

    private fun isoNowUtc(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }
}
