package com.gmp.offline.data.repository

import com.gmp.offline.data.local.dao.JobDao
import com.gmp.offline.data.local.dao.JobWorkerDao
import com.gmp.offline.data.local.entities.JobWorkerEntity
import com.gmp.offline.sync.CommandQueue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject

/**
 * Mantiene la regla de negocio de asignación en un solo flujo:
 * un montaje está "assigned" únicamente cuando tiene fecha oficial y
 * al menos una persona asignada. Si falta cualquiera de las dos cosas,
 * permanece/vuelve a "pending" mientras no haya avanzado a otro estado.
 */
class AssignmentRepository @Inject constructor(
    private val jobDao: JobDao,
    private val jobWorkerDao: JobWorkerDao,
    private val commandQueue: CommandQueue,
) {
    suspend fun confirmAssignment(
        jobUuid: String,
        scheduledIsoDate: String?,
        selectedWorkerUuids: Set<String>,
    ) {
        val nowIso = isoNowUtc()
        val currentWorkers = jobWorkerDao.getByJob(jobUuid)
        val currentUuids = currentWorkers.map { it.userUuid }.toSet()
        val toAdd = selectedWorkerUuids - currentUuids
        val toRemove = currentUuids - selectedWorkerUuids
        val scheduledAt = scheduledIsoDate?.let { "${it}T00:00:00.000Z" }
        val job = jobDao.getByUuid(jobUuid)
        val dateChanged = job != null && job.scheduledAt != scheduledAt

        if (toAdd.isNotEmpty()) {
            jobWorkerDao.upsertAll(
                toAdd.map { workerUuid ->
                    JobWorkerEntity(
                        uuid = UUID.randomUUID().toString(),
                        jobUuid = jobUuid,
                        userUuid = workerUuid,
                        createdAt = nowIso,
                        updatedAt = nowIso,
                    )
                },
            )
        }

        if (toRemove.isNotEmpty()) {
            val rowsToRemove = currentWorkers.filter { it.userUuid in toRemove }.map { it.uuid }
            jobWorkerDao.deleteByUuids(rowsToRemove)
        }

        if (job != null) {
            val newStatus = when {
                job.status !in setOf("pending", "assigned") -> job.status
                scheduledAt != null && selectedWorkerUuids.isNotEmpty() -> "assigned"
                else -> "pending"
            }
            jobDao.upsertAll(
                listOf(job.copy(scheduledAt = scheduledAt, status = newStatus, updatedAt = nowIso)),
            )
        }

        // La fecha se manda primero. Así, cuando /assign reconcilia el estado
        // en el servidor, ya ve la fecha definitiva del montaje.
        if (dateChanged) {
            commandQueue.enqueue(
                endpointPath = "/jobs/$jobUuid",
                httpMethod = "PATCH",
                payload = mapOf("scheduled_at" to scheduledAt),
            )
        }

        for (workerUuid in toAdd) {
            commandQueue.enqueue(
                endpointPath = "/jobs/$jobUuid/assign",
                httpMethod = "POST",
                payload = mapOf("user_uuid" to workerUuid),
            )
        }

        for (workerUuid in toRemove) {
            commandQueue.enqueue(
                endpointPath = "/jobs/$jobUuid/unassign",
                httpMethod = "POST",
                payload = mapOf("user_uuid" to workerUuid),
            )
        }

        // Si solo cambió la fecha y el personal ya estaba asignado, repetimos
        // un /assign idempotente para que el backend recalcule pending/assigned.
        if (dateChanged && toAdd.isEmpty() && toRemove.isEmpty() && selectedWorkerUuids.isNotEmpty()) {
            commandQueue.enqueue(
                endpointPath = "/jobs/$jobUuid/assign",
                httpMethod = "POST",
                payload = mapOf("user_uuid" to selectedWorkerUuids.first()),
            )
        }
    }

    private fun isoNowUtc(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }
}
