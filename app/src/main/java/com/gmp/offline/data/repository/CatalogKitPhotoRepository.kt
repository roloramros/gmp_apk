package com.gmp.offline.data.repository

import android.content.Context
import android.net.Uri
import com.gmp.offline.BuildConfig
import com.gmp.offline.data.local.dao.CatalogKitPhotoDao
import com.gmp.offline.data.local.entities.CatalogKitPhotoEntity
import com.gmp.offline.data.remote.dto.CatalogKitPhotoDto
import com.gmp.offline.data.remote.dto.toEntity
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

// Fotos de un kit del catálogo (varias por kit, a diferencia de la foto
// única de comercial en JobDetailRepository). Mismo patrón exacto que
// WorkerPhotoRepository: se guarda localmente YA (comprimida) con
// uploadStatus="uploading", se intenta subir por multipart de inmediato
// (fuera del outbox JSON genérico — CommandDispatcher no maneja adjuntos),
// y si falla queda en "error" con reintento manual desde la UI. No hay
// reintento automático por reconexión acá (igual que en fotos de jobs).
//
// El borrado (removePhoto) SÍ pasa por el CommandQueue/outbox normal —
// es solo un DELETE sin body, no necesita multipart.
class CatalogKitPhotoRepository @Inject constructor(
    private val catalogKitPhotoDao: CatalogKitPhotoDao,
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val commandQueue: CommandQueue,
) {
    fun observeByKit(kitUuid: String): Flow<List<CatalogKitPhotoEntity>> = catalogKitPhotoDao.observeByKit(kitUuid)

    suspend fun addPhoto(kitUuid: String, imageUri: Uri): PhotoActionResult = withContext(Dispatchers.IO) {
        val currentCount = catalogKitPhotoDao.getByKit(kitUuid).size
        if (currentCount >= MAX_PHOTOS_PER_KIT) {
            return@withContext PhotoActionResult.Error("Ya hay $MAX_PHOTOS_PER_KIT fotos en este kit, el máximo permitido.")
        }

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

        val nowIso = isoNowUtc()
        catalogKitPhotoDao.upsertAll(
            listOf(
                CatalogKitPhotoEntity(
                    uuid = photoUuid,
                    kitUuid = kitUuid,
                    url = "",
                    sortOrder = currentCount,
                    createdAt = nowIso,
                    updatedAt = nowIso,
                    localPath = localFile.absolutePath,
                    uploadStatus = "uploading",
                ),
            ),
        )

        applyUploadResult(
            kitUuid = kitUuid,
            photoUuid = photoUuid,
            sortOrder = currentCount,
            localPath = localFile.absolutePath,
            nowIso = nowIso,
            result = uploadToServer(kitUuid, photoUuid, currentCount, localFile),
        )
    }

    suspend fun retryPhoto(kitUuid: String, photoUuid: String): PhotoActionResult = withContext(Dispatchers.IO) {
        val existing = catalogKitPhotoDao.getByUuid(photoUuid)
            ?: return@withContext PhotoActionResult.Error("No se encontró la foto para reintentar.")
        val localPath = existing.localPath
            ?: return@withContext PhotoActionResult.Error("No se encontró el archivo local de la foto.")
        val file = File(localPath)
        if (!file.exists()) {
            return@withContext PhotoActionResult.Error("El archivo local de la foto ya no existe.")
        }

        catalogKitPhotoDao.upsertAll(listOf(existing.copy(uploadStatus = "uploading")))
        applyUploadResult(
            kitUuid = kitUuid,
            photoUuid = existing.uuid,
            sortOrder = existing.sortOrder,
            localPath = localPath,
            nowIso = isoNowUtc(),
            result = uploadToServer(kitUuid, existing.uuid, existing.sortOrder, file),
        )
    }

    /**
     * Quita una foto. Igual que MaterialsRepository.deleteMaterial: hard
     * delete local optimista + DELETE encolado en el outbox normal (no
     * necesita multipart). Si la foto nunca se confirmó en el servidor
     * (seguía en "uploading"/"error"), no hay nada que borrar del lado del
     * backend — igual que removePhotoInternal en JobDetailRepository.
     */
    suspend fun removePhoto(kitUuid: String, photoUuid: String) = withContext(Dispatchers.IO) {
        val existing = catalogKitPhotoDao.getByUuid(photoUuid)
        catalogKitPhotoDao.deleteByUuids(listOf(photoUuid))
        existing?.localPath?.let { path -> runCatching { File(path).delete() } }

        if (existing?.uploadStatus == "synced") {
            commandQueue.enqueue(
                endpointPath = "/catalog-kits/$kitUuid/photos/$photoUuid",
                httpMethod = "DELETE",
                payload = emptyMap(),
            )
        }
        Unit
    }

    private suspend fun applyUploadResult(
        kitUuid: String,
        photoUuid: String,
        sortOrder: Int,
        localPath: String,
        nowIso: String,
        result: CommandResult,
    ): PhotoActionResult = when (result) {
        is CommandResult.Success -> {
            val confirmed = try {
                Gson().fromJson(result.responseBody, CatalogKitPhotoDto::class.java).toEntity()
                    .copy(localPath = localPath, uploadStatus = "synced")
            } catch (_: Exception) {
                CatalogKitPhotoEntity(
                    uuid = photoUuid,
                    kitUuid = kitUuid,
                    url = "",
                    sortOrder = sortOrder,
                    createdAt = nowIso,
                    updatedAt = nowIso,
                    localPath = localPath,
                    uploadStatus = "synced",
                )
            }
            catalogKitPhotoDao.upsertAll(listOf(confirmed))
            PhotoActionResult.Success
        }
        is CommandResult.HttpError -> {
            catalogKitPhotoDao.upsertAll(
                listOf(
                    CatalogKitPhotoEntity(
                        uuid = photoUuid,
                        kitUuid = kitUuid,
                        url = "",
                        sortOrder = sortOrder,
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
            catalogKitPhotoDao.upsertAll(
                listOf(
                    CatalogKitPhotoEntity(
                        uuid = photoUuid,
                        kitUuid = kitUuid,
                        url = "",
                        sortOrder = sortOrder,
                        createdAt = nowIso,
                        updatedAt = nowIso,
                        localPath = localPath,
                        uploadStatus = "error",
                    ),
                ),
            )
            PhotoActionResult.Error("Sin conexión — la foto quedó guardada para reintentar.")
        }
    }

    private suspend fun uploadToServer(kitUuid: String, photoUuid: String, sortOrder: Int, file: File): CommandResult =
        withContext(Dispatchers.IO) {
            try {
                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("uuid", photoUuid)
                    .addFormDataPart("sort_order", sortOrder.toString())
                    .addFormDataPart("photo", file.name, file.asRequestBody("image/jpeg".toMediaType()))
                    .build()

                val request = Request.Builder()
                    .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/catalog-kits/$kitUuid/photos")
                    .header("X-Command-Id", UUID.randomUUID().toString())
                    .post(multipartBody)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (response.isSuccessful) CommandResult.Success(text)
                    else CommandResult.HttpError(response.code, text)
                }
            } catch (e: IOException) {
                CommandResult.NetworkError(e.message)
            }
        }

    private fun photosDir(): File = File(context.filesDir, "catalog_kit_photos").apply { mkdirs() }

    private fun isoNowUtc(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }

    companion object {
        const val MAX_PHOTOS_PER_KIT = 6
    }
}
