package com.gmp.offline.data.repository

import com.gmp.offline.data.local.dao.CatalogProductDao
import com.gmp.offline.data.local.entities.CatalogProductEntity
import com.gmp.offline.sync.CommandQueue
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject

// Tienda de componentes sueltos. Calco de CatalogKitsRepository — ver ese
// archivo para el razonamiento general (mismo patrón optimista + outbox).
// Endpoints: POST/PATCH/DELETE /catalog-products[...].
class CatalogProductsRepository @Inject constructor(
    private val catalogProductDao: CatalogProductDao,
    private val commandQueue: CommandQueue,
) {
    fun observeProducts(): Flow<List<CatalogProductEntity>> = catalogProductDao.observeAll()

    suspend fun getProduct(uuid: String): CatalogProductEntity? = catalogProductDao.getByUuid(uuid)

    suspend fun createProduct(
        name: String,
        priceUsd: String?,
        description: String?,
        inStock: Boolean,
        active: Boolean,
    ): String {
        val uuid = UUID.randomUUID().toString()
        val nowIso = isoNowUtc()

        catalogProductDao.upsertAll(
            listOf(
                CatalogProductEntity(
                    uuid = uuid, name = name, priceUsd = priceUsd,
                    description = description, inStock = inStock, active = active,
                    sortOrder = 0, createdAt = nowIso, updatedAt = nowIso,
                ),
            ),
        )

        commandQueue.enqueue(
            endpointPath = "/catalog-products",
            httpMethod = "POST",
            payload = mapOf(
                "uuid" to uuid, "name" to name, "price_usd" to priceUsd,
                "description" to description, "in_stock" to inStock, "active" to active,
            ),
        )
        return uuid
    }

    suspend fun updateProduct(
        uuid: String,
        name: String,
        priceUsd: String?,
        description: String?,
        inStock: Boolean,
        active: Boolean,
    ) {
        val existing = catalogProductDao.getByUuid(uuid) ?: return
        val nowIso = isoNowUtc()

        catalogProductDao.upsertAll(
            listOf(
                existing.copy(
                    name = name, priceUsd = priceUsd, description = description,
                    inStock = inStock, active = active, updatedAt = nowIso,
                ),
            ),
        )

        commandQueue.enqueue(
            endpointPath = "/catalog-products/$uuid",
            httpMethod = "PATCH",
            payload = mapOf(
                "name" to name, "price_usd" to priceUsd,
                "description" to description, "in_stock" to inStock, "active" to active,
            ),
        )
    }

    suspend fun deleteProduct(uuid: String) {
        catalogProductDao.deleteByUuids(listOf(uuid))
        commandQueue.enqueue(endpointPath = "/catalog-products/$uuid", httpMethod = "DELETE", payload = emptyMap())
    }

    private fun isoNowUtc(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }
}
