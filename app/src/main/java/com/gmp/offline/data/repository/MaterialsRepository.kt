package com.gmp.offline.data.repository

import com.gmp.offline.data.local.dao.MaterialDao
import com.gmp.offline.data.local.entities.MaterialEntity
import com.gmp.offline.sync.CommandQueue
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject

// Catálogo de materiales de la empresa (pestaña "Gestión de Materiales" del
// admin, Fase 6 Paso 6 — réplica exacta de la sección "Materiales" de la web
// legada, ver `saveMaterial`/`deleteMaterial` en index.html). Contrato
// confirmado contra materialsController.js real:
//   POST   /materials       body: { uuid, name, unit?, default_price? }
//   PATCH  /materials/:uuid body: subconjunto de { name, unit, default_price }
//   DELETE /materials/:uuid sin body (soft delete)
// `uuid` lo genera el cliente, igual que en jobs. No existe `is_active` en
// este backend (a diferencia de la web legada, que hablaba con el sistema
// viejo) — cualquier fila visible en /materials ya está activa por
// definición, las inactivas/borradas nunca llegan (soft delete filtra
// `deleted_at IS NULL` del lado del servidor).
class MaterialsRepository @Inject constructor(
    private val materialDao: MaterialDao,
    private val commandQueue: CommandQueue,
) {
    fun observeMaterials(): Flow<List<MaterialEntity>> = materialDao.observeAll()

    suspend fun getMaterial(uuid: String): MaterialEntity? = materialDao.getByUuid(uuid)

    /**
     * Crea un material offline-first: optimista en Room YA (uuid generado
     * acá mismo, igual que JobsRepository.createJob) + encola POST
     * /materials con el mismo uuid para que el servidor cree exactamente
     * ese registro (idempotente por X-Command-Id).
     */
    suspend fun createMaterial(name: String, unit: String?, defaultPrice: String?): String {
        val uuid = UUID.randomUUID().toString()
        val nowIso = isoNowUtc()

        materialDao.upsertAll(
            listOf(
                MaterialEntity(
                    uuid = uuid,
                    name = name,
                    unit = unit,
                    defaultPrice = defaultPrice,
                    createdAt = nowIso,
                    updatedAt = nowIso,
                ),
            ),
        )

        commandQueue.enqueue(
            endpointPath = "/materials",
            httpMethod = "POST",
            payload = mapOf(
                "uuid" to uuid,
                "name" to name,
                "unit" to unit,
                "default_price" to defaultPrice,
            ),
        )

        return uuid
    }

    /** Mismo patrón que `createMaterial`, contra PATCH /materials/:uuid. */
    suspend fun updateMaterial(uuid: String, name: String, unit: String?, defaultPrice: String?) {
        val existing = materialDao.getByUuid(uuid) ?: return
        val nowIso = isoNowUtc()

        materialDao.upsertAll(
            listOf(
                existing.copy(
                    name = name,
                    unit = unit,
                    defaultPrice = defaultPrice,
                    updatedAt = nowIso,
                ),
            ),
        )

        commandQueue.enqueue(
            endpointPath = "/materials/$uuid",
            httpMethod = "PATCH",
            payload = mapOf(
                "name" to name,
                "unit" to unit,
                "default_price" to defaultPrice,
            ),
        )
    }

    /**
     * Elimina un material offline-first. El borrado local es optimista
     * (hard delete en Room, ya que igual el próximo /sync lo va a traer
     * como tombstone y `SyncEngine.applyEntities` vuelve a borrarlo — ver
     * `materialDao.deleteByUuids`, usado también para aplicar tombstones
     * de sync). El servidor hace soft delete (`deleted_at`), así que
     * cualquier otro dispositivo se entera en su próximo sync.
     */
    suspend fun deleteMaterial(uuid: String) {
        materialDao.deleteByUuids(listOf(uuid))

        commandQueue.enqueue(
            endpointPath = "/materials/$uuid",
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
