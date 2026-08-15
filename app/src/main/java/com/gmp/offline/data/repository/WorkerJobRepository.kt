package com.gmp.offline.data.repository

import com.gmp.offline.data.local.dao.JobDao
import com.gmp.offline.data.local.dao.JobMaterialDao
import com.gmp.offline.data.local.dao.MaterialDao
import com.gmp.offline.data.local.entities.JobMaterialEntity
import com.gmp.offline.data.local.entities.MaterialEntity
import com.gmp.offline.sync.CommandQueue
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.RoundingMode
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
        commandQueue.enqueue(endpointPath = "/jobs/$jobUuid/start", httpMethod = "POST", payload = emptyMap())
    }

    suspend fun finishJob(jobUuid: String) {
        val job = jobDao.getByUuid(jobUuid) ?: return
        if (job.status != "in_progress") return
        val now = isoNowUtc()
        jobDao.upsertAll(listOf(job.copy(status = "finished", finishedAt = now, updatedAt = now)))
        commandQueue.enqueue(endpointPath = "/jobs/$jobUuid/finish", httpMethod = "POST", payload = emptyMap())
    }

    suspend fun invoiceJob(jobUuid: String, totalAmount: String) {
        val amount = totalAmount.toBigDecimalOrNull() ?: return
        if (amount <= BigDecimal.ZERO) return
        val job = jobDao.getByUuid(jobUuid) ?: return
        if (job.status != "finished") return
        val now = isoNowUtc()
        val formattedAmount = amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
        jobDao.upsertAll(listOf(job.copy(status = "invoiced", invoicedAt = now, totalAmount = formattedAmount, price = formattedAmount, updatedAt = now)))
        commandQueue.enqueue(
            endpointPath = "/jobs/$jobUuid/invoice",
            httpMethod = "POST",
            payload = mapOf("total_amount" to formattedAmount),
        )
    }

    suspend fun payJob(jobUuid: String, paymentAmount: String) {
        val payment = paymentAmount.toBigDecimalOrNull() ?: return
        if (payment <= BigDecimal.ZERO) return

        val job = jobDao.getByUuid(jobUuid) ?: return
        if (job.status !in setOf("invoiced", "partially_paid")) return

        val total = job.totalAmount?.toBigDecimalOrNull() ?: return
        val alreadyPaid = job.amountPaid.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val remaining = total.subtract(alreadyPaid)
        if (remaining <= BigDecimal.ZERO || payment > remaining) return

        val newPaid = alreadyPaid.add(payment).setScale(2, RoundingMode.HALF_UP)
        val newStatus = if (newPaid.compareTo(total.setScale(2, RoundingMode.HALF_UP)) >= 0) "paid" else "partially_paid"
        val now = isoNowUtc()

        jobDao.upsertAll(
            listOf(
                job.copy(
                    status = newStatus,
                    amountPaid = newPaid.toPlainString(),
                    updatedAt = now,
                ),
            ),
        )

        commandQueue.enqueue(
            endpointPath = "/jobs/$jobUuid/pay",
            httpMethod = "POST",
            payload = mapOf("amount" to payment.setScale(2, RoundingMode.HALF_UP).toPlainString()),
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
            jobMaterialDao.upsertAll(listOf(existing.copy(quantity = formatQuantity(current + delta), updatedAt = now)))
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
            listOf(JobMaterialEntity(itemUuid, jobUuid, materialUuid, null, formatQuantity(delta), material.defaultPrice, now, now)),
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

    suspend fun addCustomMaterial(
        jobUuid: String,
        name: String,
        unit: String,
        quantityToAdd: String,
        unitPrice: String?,
    ) {
        val cleanName = name.trim()
        val cleanUnit = unit.trim()
        val delta = quantityToAdd.toDoubleOrNull() ?: return
        val price = unitPrice?.takeIf { it.isNotBlank() }?.toBigDecimalOrNull()
        if (cleanName.isBlank() || cleanUnit.isBlank() || delta <= 0.0) return
        if (unitPrice != null && unitPrice.isNotBlank() && price == null) return
        if (price != null && price < BigDecimal.ZERO) return

        val encodedDescription = encodeCustomDescription(cleanName, cleanUnit)
        val existing = jobMaterialDao.findByJobAndFreeText(jobUuid, encodedDescription)
        val now = isoNowUtc()
        val formattedPrice = price?.setScale(2, RoundingMode.HALF_UP)?.toPlainString()

        if (existing != null) {
            val current = existing.quantity.toDoubleOrNull() ?: 0.0
            val resolvedPrice = existing.unitPrice ?: formattedPrice
            jobMaterialDao.upsertAll(
                listOf(existing.copy(quantity = formatQuantity(current + delta), unitPrice = resolvedPrice, updatedAt = now)),
            )
            commandQueue.enqueue(
                endpointPath = "/jobs/$jobUuid/materials",
                httpMethod = "POST",
                payload = mapOf(
                    "uuid" to existing.uuid,
                    "material_uuid" to null,
                    "free_text_description" to encodedDescription,
                    "quantity" to formatQuantity(delta),
                    "unit_price" to resolvedPrice,
                ),
            )
            return
        }

        val itemUuid = UUID.randomUUID().toString()
        jobMaterialDao.upsertAll(
            listOf(JobMaterialEntity(itemUuid, jobUuid, null, encodedDescription, formatQuantity(delta), formattedPrice, now, now)),
        )
        commandQueue.enqueue(
            endpointPath = "/jobs/$jobUuid/materials",
            httpMethod = "POST",
            payload = mapOf(
                "uuid" to itemUuid,
                "material_uuid" to null,
                "free_text_description" to encodedDescription,
                "quantity" to formatQuantity(delta),
                "unit_price" to formattedPrice,
            ),
        )
    }

    suspend fun updateMaterial(jobUuid: String, itemUuid: String, quantity: String, unitPrice: String?) {
        val exact = quantity.toDoubleOrNull() ?: return
        if (exact <= 0.0) return
        val item = jobMaterialDao.getByUuid(itemUuid) ?: return
        if (item.jobUuid != jobUuid) return
        val price = unitPrice?.takeIf { it.isNotBlank() }?.toBigDecimalOrNull()
        if (unitPrice != null && unitPrice.isNotBlank() && price == null) return
        if (price != null && price < BigDecimal.ZERO) return
        val formatted = formatQuantity(exact)
        val formattedPrice = price?.setScale(2, RoundingMode.HALF_UP)?.toPlainString() ?: item.unitPrice
        val now = isoNowUtc()
        jobMaterialDao.upsertAll(listOf(item.copy(quantity = formatted, unitPrice = formattedPrice, updatedAt = now)))
        commandQueue.enqueue(
            endpointPath = "/jobs/$jobUuid/materials/$itemUuid",
            httpMethod = "PATCH",
            payload = mapOf("quantity" to formatted, "unit_price" to formattedPrice),
        )
    }

    suspend fun removeJobMaterial(jobUuid: String, itemUuid: String) {
        val item = jobMaterialDao.getByUuid(itemUuid) ?: return
        if (item.jobUuid != jobUuid) return
        jobMaterialDao.deleteByUuids(listOf(itemUuid))
        commandQueue.enqueue(endpointPath = "/jobs/$jobUuid/materials/$itemUuid", httpMethod = "DELETE", payload = emptyMap())
    }

    private fun encodeCustomDescription(name: String, unit: String): String = "$name|||unit:$unit"

    private fun formatQuantity(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private fun isoNowUtc(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }
}