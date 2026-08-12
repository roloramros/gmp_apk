package com.gmp.offline.data.repository

import com.gmp.offline.data.local.dao.JobMaterialDao
import com.gmp.offline.data.local.dao.JobPhotoDao
import com.gmp.offline.data.local.dao.JobWorkerDao
import com.gmp.offline.data.local.dao.MaterialDao
import com.gmp.offline.data.local.entities.JobMaterialEntity
import com.gmp.offline.data.local.entities.JobPhotoEntity
import com.gmp.offline.data.local.entities.JobWorkerEntity
import com.gmp.offline.sync.CommandQueue
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject

// Agrupa lo que necesita una pantalla de detalle de job: sus trabajadores
// asignados, materiales y fotos — todo leído de Room. Desde Fase 6 Paso 2
// también agrega/quita materiales (comercial/admin/trabajador asignado),
// con el mismo patrón optimista + outbox que JobsRepository.
class JobDetailRepository @Inject constructor(
    private val jobWorkerDao: JobWorkerDao,
    private val jobMaterialDao: JobMaterialDao,
    private val jobPhotoDao: JobPhotoDao,
    private val materialDao: MaterialDao,
    private val commandQueue: CommandQueue,
) {
    fun observeWorkers(jobUuid: String): Flow<List<JobWorkerEntity>> =
        jobWorkerDao.observeByJob(jobUuid)

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

    private fun isoNowUtc(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }
}
