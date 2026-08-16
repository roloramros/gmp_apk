package com.gmp.offline.data.repository

import com.gmp.offline.data.local.dao.JobDao
import com.gmp.offline.data.local.entities.JobEntity
import com.gmp.offline.data.session.SessionManager
import com.gmp.offline.sync.CommandQueue
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject

class JobsRepository @Inject constructor(
    private val jobDao: JobDao,
    private val commandQueue: CommandQueue,
    private val sessionManager: SessionManager,
) {
    fun observeJobs(): Flow<List<JobEntity>> = jobDao.observeAll()

    fun observeJob(uuid: String): Flow<JobEntity?> = jobDao.observeByUuid(uuid)

    fun observeJobsByStatus(status: String): Flow<List<JobEntity>> = jobDao.observeByStatus(status)

    suspend fun findFirstJobByStatus(status: String): JobEntity? = jobDao.getFirstByStatus(status)

    suspend fun getJob(jobUuid: String): JobEntity? = jobDao.getByUuid(jobUuid)

    suspend fun startJob(jobUuid: String) {
        val job = jobDao.getByUuid(jobUuid) ?: return
        val nowIso = isoNowUtc()

        jobDao.upsertAll(
            listOf(
                job.copy(
                    status = "in_progress",
                    startedAt = nowIso,
                    updatedAt = nowIso,
                ),
            ),
        )

        commandQueue.enqueue(
            endpointPath = "/jobs/$jobUuid/start",
            httpMethod = "POST",
            payload = emptyMap(),
        )
    }

    suspend fun createJob(
        clientName: String,
        clientCi: String?,
        clientPhone: String?,
        address: String,
        latitude: Double?,
        longitude: Double?,
        reference: String?,
        siteNotes: String?,
        description: String?,
        price: String,
        paymentMethod: String,
        visitDate: String?,
        proposedDate: String?,
        clientUuid: String?,
    ): String {
        val jobUuid = UUID.randomUUID().toString()
        val nowIso = isoNowUtc()
        val createdByUuid = sessionManager.userUuid.orEmpty()

        jobDao.upsertAll(
            listOf(
                JobEntity(
                    uuid = jobUuid,
                    clientUuid = clientUuid,
                    createdByUuid = createdByUuid,
                    title = clientName,
                    description = description,
                    status = "pending",
                    address = address,
                    scheduledAt = null,
                    startedAt = null,
                    finishedAt = null,
                    invoicedAt = null,
                    totalAmount = null,
                    amountPaid = "0",
                    cancelledAt = null,
                    createdAt = nowIso,
                    updatedAt = nowIso,
                    clientName = clientName,
                    clientCi = clientCi,
                    clientPhone = clientPhone,
                    latitude = latitude,
                    longitude = longitude,
                    reference = reference,
                    siteNotes = siteNotes,
                    price = price,
                    paymentMethod = paymentMethod,
                    visitDate = visitDate,
                    proposedDate = proposedDate,
                ),
            ),
        )

        commandQueue.enqueue(
            endpointPath = "/jobs",
            httpMethod = "POST",
            payload = mapOf(
                "uuid" to jobUuid,
                "title" to clientName,
                "description" to description,
                "address" to address,
                "client_uuid" to clientUuid,
                "client_name" to clientName,
                "client_ci" to clientCi,
                "client_phone" to clientPhone,
                "latitude" to latitude,
                "longitude" to longitude,
                "reference" to reference,
                "site_notes" to siteNotes,
                "price" to price,
                "payment_method" to paymentMethod,
                "visit_date" to visitDate,
                "proposed_date" to proposedDate,
            ),
        )

        return jobUuid
    }

    suspend fun updateJob(
        jobUuid: String,
        clientName: String,
        clientCi: String?,
        clientPhone: String?,
        address: String,
        latitude: Double?,
        longitude: Double?,
        reference: String?,
        siteNotes: String?,
        description: String?,
        price: String,
        paymentMethod: String,
        visitDate: String?,
        proposedDate: String?,
        clientUuid: String?,
    ) {
        val job = jobDao.getByUuid(jobUuid) ?: return
        val nowIso = isoNowUtc()

        jobDao.upsertAll(
            listOf(
                job.copy(
                    title = clientName,
                    description = description,
                    address = address,
                    clientUuid = clientUuid,
                    clientName = clientName,
                    clientCi = clientCi,
                    clientPhone = clientPhone,
                    latitude = latitude,
                    longitude = longitude,
                    reference = reference,
                    siteNotes = siteNotes,
                    price = price,
                    paymentMethod = paymentMethod,
                    visitDate = visitDate,
                    proposedDate = proposedDate,
                    updatedAt = nowIso,
                ),
            ),
        )

        commandQueue.enqueue(
            endpointPath = "/jobs/$jobUuid",
            httpMethod = "PATCH",
            payload = mapOf(
                "title" to clientName,
                "description" to description,
                "address" to address,
                "client_uuid" to clientUuid,
                "client_name" to clientName,
                "client_ci" to clientCi,
                "client_phone" to clientPhone,
                "latitude" to latitude,
                "longitude" to longitude,
                "reference" to reference,
                "site_notes" to siteNotes,
                "price" to price,
                "payment_method" to paymentMethod,
                "visit_date" to visitDate,
                "proposed_date" to proposedDate,
            ),
        )
    }

    suspend fun cancelJob(jobUuid: String) {
        val job = jobDao.getByUuid(jobUuid) ?: return
        val nowIso = isoNowUtc()

        jobDao.upsertAll(
            listOf(
                job.copy(
                    status = "cancelled",
                    cancelledAt = nowIso,
                    updatedAt = nowIso,
                ),
            ),
        )

        commandQueue.enqueue(
            endpointPath = "/jobs/$jobUuid/cancel",
            httpMethod = "POST",
            payload = emptyMap(),
        )
    }

    /**
     * Regularización histórica para admin/comercial.
     * Solo se usa desde pending/assigned y NO reproduce el flujo normal paso a paso.
     * La fecha propuesta pasa a ser oficial; para invoiced/paid el precio inicial
     * se copia como total definitivo, y para paid también como importe pagado.
     */
    suspend fun regularizeJob(jobUuid: String, targetStatus: String) {
        val job = jobDao.getByUuid(jobUuid) ?: return
        if (job.status !in setOf("pending", "assigned")) return
        if (targetStatus !in setOf("in_progress", "finished", "invoiced", "paid", "cancelled")) return

        val nowIso = isoNowUtc()

        val updatedJob = if (targetStatus == "cancelled") {
            job.copy(
                status = "cancelled",
                cancelledAt = nowIso,
                updatedAt = nowIso,
            )
        } else {
            val officialDate = job.scheduledAt ?: job.proposedDate ?: return
            val price = job.price?.toDoubleOrNull()
            if (targetStatus in setOf("invoiced", "paid") && (price == null || price <= 0.0)) return
            val finalPrice = job.price

            when (targetStatus) {
                "in_progress" -> job.copy(
                    scheduledAt = officialDate,
                    status = "in_progress",
                    startedAt = job.startedAt ?: officialDate,
                    updatedAt = nowIso,
                )
                "finished" -> job.copy(
                    scheduledAt = officialDate,
                    status = "finished",
                    startedAt = job.startedAt ?: officialDate,
                    finishedAt = job.finishedAt ?: officialDate,
                    updatedAt = nowIso,
                )
                "invoiced" -> job.copy(
                    scheduledAt = officialDate,
                    status = "invoiced",
                    startedAt = job.startedAt ?: officialDate,
                    finishedAt = job.finishedAt ?: officialDate,
                    invoicedAt = job.invoicedAt ?: officialDate,
                    totalAmount = finalPrice,
                    amountPaid = "0",
                    updatedAt = nowIso,
                )
                "paid" -> job.copy(
                    scheduledAt = officialDate,
                    status = "paid",
                    startedAt = job.startedAt ?: officialDate,
                    finishedAt = job.finishedAt ?: officialDate,
                    invoicedAt = job.invoicedAt ?: officialDate,
                    totalAmount = finalPrice,
                    amountPaid = finalPrice ?: "0",
                    updatedAt = nowIso,
                )
                else -> return
            }
        }

        jobDao.upsertAll(listOf(updatedJob))
        commandQueue.enqueue(
            endpointPath = "/jobs/$jobUuid/regularize",
            httpMethod = "POST",
            payload = mapOf("status" to targetStatus),
        )
    }

    private fun isoNowUtc(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }
}
