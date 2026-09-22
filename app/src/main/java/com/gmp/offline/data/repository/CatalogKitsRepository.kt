package com.gmp.offline.data.repository

import com.gmp.offline.data.local.dao.CatalogKitDao
import com.gmp.offline.data.local.entities.CatalogKitEntity
import com.gmp.offline.sync.CommandQueue
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject

// Catálogo de kits/ofertas de la empresa (pestaña "Catálogo" del admin).
// Mismo patrón exacto que MaterialsRepository: optimista en Room primero
// (uuid generado acá) + encolar el comando JSON correspondiente contra
// catalogKitsController.js:
//   POST   /catalog-kits       body: { uuid, name, power_kw?, voltage?,
//                                       battery_kwh?, panels_count?,
//                                       price_usd?, description?, active?,
//                                       sort_order? }
//   PATCH  /catalog-kits/:uuid body: subconjunto de los mismos campos
//   DELETE /catalog-kits/:uuid sin body (soft delete)
// Las fotos de cada kit NO pasan por acá — ver CatalogKitPhotoRepository,
// que sube directo por multipart (mismo motivo que WorkerPhotoRepository:
// el outbox JSON genérico no maneja adjuntos de archivo).
class CatalogKitsRepository @Inject constructor(
    private val catalogKitDao: CatalogKitDao,
    private val commandQueue: CommandQueue,
) {
    fun observeKits(): Flow<List<CatalogKitEntity>> = catalogKitDao.observeAll()

    suspend fun getKit(uuid: String): CatalogKitEntity? = catalogKitDao.getByUuid(uuid)

    suspend fun createKit(
        name: String,
        powerKw: String?,
        voltage: String?,
        batteryKwh: String?,
        panelsCount: Int?,
        priceUsd: String?,
        description: String?,
        active: Boolean,
    ): String {
        val uuid = UUID.randomUUID().toString()
        val nowIso = isoNowUtc()

        catalogKitDao.upsertAll(
            listOf(
                CatalogKitEntity(
                    uuid = uuid,
                    name = name,
                    powerKw = powerKw,
                    voltage = voltage,
                    batteryKwh = batteryKwh,
                    panelsCount = panelsCount,
                    priceUsd = priceUsd,
                    description = description,
                    active = active,
                    sortOrder = 0,
                    createdAt = nowIso,
                    updatedAt = nowIso,
                ),
            ),
        )

        commandQueue.enqueue(
            endpointPath = "/catalog-kits",
            httpMethod = "POST",
            payload = mapOf(
                "uuid" to uuid,
                "name" to name,
                "power_kw" to powerKw,
                "voltage" to voltage,
                "battery_kwh" to batteryKwh,
                "panels_count" to panelsCount,
                "price_usd" to priceUsd,
                "description" to description,
                "active" to active,
            ),
        )

        return uuid
    }

    suspend fun updateKit(
        uuid: String,
        name: String,
        powerKw: String?,
        voltage: String?,
        batteryKwh: String?,
        panelsCount: Int?,
        priceUsd: String?,
        description: String?,
        active: Boolean,
    ) {
        val existing = catalogKitDao.getByUuid(uuid) ?: return
        val nowIso = isoNowUtc()

        catalogKitDao.upsertAll(
            listOf(
                existing.copy(
                    name = name,
                    powerKw = powerKw,
                    voltage = voltage,
                    batteryKwh = batteryKwh,
                    panelsCount = panelsCount,
                    priceUsd = priceUsd,
                    description = description,
                    active = active,
                    updatedAt = nowIso,
                ),
            ),
        )

        commandQueue.enqueue(
            endpointPath = "/catalog-kits/$uuid",
            httpMethod = "PATCH",
            payload = mapOf(
                "name" to name,
                "power_kw" to powerKw,
                "voltage" to voltage,
                "battery_kwh" to batteryKwh,
                "panels_count" to panelsCount,
                "price_usd" to priceUsd,
                "description" to description,
                "active" to active,
            ),
        )
    }

    /**
     * Borrado offline-first (mismo patrón que MaterialsRepository.deleteMaterial):
     * hard delete local optimista — si el próximo /sync trae este uuid como
     * tombstone, deleteByUuids ya lo deja consistente igual.
     */
    suspend fun deleteKit(uuid: String) {
        catalogKitDao.deleteByUuids(listOf(uuid))

        commandQueue.enqueue(
            endpointPath = "/catalog-kits/$uuid",
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
