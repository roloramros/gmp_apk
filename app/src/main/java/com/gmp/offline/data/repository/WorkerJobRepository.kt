package com.gmp.offline.data.repository

import com.gmp.offline.data.local.dao.JobDao
import com.gmp.offline.data.local.dao.JobMaterialDao
import com.gmp.offline.data.local.dao.MaterialDao
import com.gmp.offline.data.local.entities.JobMaterialEntity
import com.gmp.offline.data.local.entities.MaterialEntity
import com.gmp.offline.sync.CommandQueue
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject

class WorkerJobRepository @Inject constructor(
    private val jobDao: JobDao,
    private val jobMaterialDao: JobMaterialDao,
    private val materialDao: MaterialDao,
    private val commandQueue: CommandQueue,
) {
    fun observeJobMaterials(jobUuid: String): Flow<List<JobMaterialEntity>> =
        jobMaterialDao.observeByJob(jobUuid)

    fun observeCatalog(): Flow<List<MaterialEntity>> = materialDao.observeAll()

    suspend fun startJob(jobUuid: String) {
        val job = jobDao.getByUuid(jobUuid) ?: return
        if (job.status != "assigned") return
        val now = isoNowUtc()
        jobDao.upsertAll(listOf(job.copy(status = "in_progress", startedAt = now, updatedAt = now)))
        commandQueue.enqueue(
            endpointPath = "/jobs/$jobUuid/start",
            httpMethod = "POST",
            payload = emptyMap(),
        )
    }

    suspend fun finishJob(jobUuid: String) {
        val job = jobDao.getByUuid(jobUuid) ?: return
        if (job.status != "in_progress") return
        val now = isoNowUtc()
        jobDao.upsertAll(listOf(job.copy(status = "finished", finishedAt = now, updatedAt = now)))
        commandQueue.enqueue(
            endpointPath = "/jobs/$jobUuid/finish",
            httpMethod = "POST",
            payload = emptyMap(),
        )
    }

    suspend fun addCatalogMaterial(jobUuid: String, materialUuid: String, quantityToAdd: String) {
        val delta = quantityToAdd.toDoubleOrNull() ?: return
        if (delta <= 0.0) return

        val material = materialDao.getByUuid(materialUuid) ?: return
        val existing = jobMaterialDao.findByJobAndMaterial(jobUuid, materialUuid)
        val now = isoNowUtc()

        if (existing != null) {
            val current = existing.quantity.toDoubleOrNull() ?: 0.0
            jobMaterialDao.upsertAll(
                listOf(existing.copy(quantity = formatQuantity(current + delta), updatedAt = now)),
            )

            // El backend consolida por job + material_uuid y suma este delta.
            commandQueue.enqueue(
                endpointPath = "/jobs/$jobUuid/materials",
                httpMethod = "POST",
                payload = mapOf(
                    "uuid" to existing.uuid,
                    "material_uuid" to materialUuid,
                    "free_text_description" to null,
                    "quantity" to formatQuantity(delta),
                    "unit_price" to existing.unitPrice,
                ),
            )
            return
        }

        val itemUuid = UUID.randomUUID().toString()
        jobMaterialDao.upsertAll(
            listOf(
                JobMaterialEntity(
                    uuid = itemUuid,
                    jobUuid = jobUuid,
                    materialUuid = materialUuid,
                    freeTextDescription = null,
                    quantity = formatQuantity(delta),
                    unitPrice = material.defaultPrice,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
        commandQueue.enqueue(
            endpointPath = "/jobs/$jobUuid/materials",
            httpMethod = "POST",
            payload = mapOf(
                "uuid" to itemUuid,
                "material_uuid" to materialUuid,
                "free_text_description" to null,
                "quantity" to formatQuantity(delta),
                "unit_price" to material.defaultPrice,
            ),
        )
    }

    private fun formatQuantity(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private fun isoNowUtc(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }
}
