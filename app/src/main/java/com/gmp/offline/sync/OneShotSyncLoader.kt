package com.gmp.offline.sync

import com.gmp.offline.data.local.dao.JobDao
import com.gmp.offline.data.local.dao.JobMaterialDao
import com.gmp.offline.data.local.dao.JobPhotoDao
import com.gmp.offline.data.local.dao.JobWorkerDao
import com.gmp.offline.data.local.dao.MaterialDao
import com.gmp.offline.data.local.dao.StaffDao
import com.gmp.offline.data.remote.ApiService
import com.gmp.offline.data.remote.dto.SyncEntitiesDto
import com.gmp.offline.data.remote.dto.toEntity
import javax.inject.Inject

// ⚠️ ALCANCE DE ESTA FASE (Fase 4): esto NO es el motor de sincronización.
// El motor real (SyncEngine + outbox + PendingOperationDao + SyncWorker con
// disparo por conectividad/periódico/manual, y guardado del cursor para
// sync incremental) es la Fase 5, todavía sin empezar.
//
// Esta clase es solo un cargador manual de un solo disparo: pide un full
// dump completo a /sync (paginando hasta agotar has_more) y hace upsert
// directo en Room, para poder probar que la capa de datos local (entidades,
// DAOs, Flow) funciona de punta a punta sin depender de que el motor de
// sync ya exista.
//
// Lo que NO hace (a propósito, queda para Fase 5):
// - No guarda el `cursor` final como `since` para la próxima sincronización.
// - No corre en background ni reacciona a conectividad.
// - No procesa el outbox de comandos pendientes.
class OneShotSyncLoader @Inject constructor(
    private val apiService: ApiService,
    private val jobDao: JobDao,
    private val jobWorkerDao: JobWorkerDao,
    private val materialDao: MaterialDao,
    private val jobMaterialDao: JobMaterialDao,
    private val jobPhotoDao: JobPhotoDao,
    private val staffDao: StaffDao,
) {
    /**
     * Trae el full dump completo (since vacío) paginando hasta que el
     * servidor devuelva has_more=false, y aplica cada página a Room a
     * medida que llega.
     */
    suspend fun loadOnce() {
        var cursorPage: String? = null
        var hasMore: Boolean

        do {
            val response = apiService.sync(since = "", cursorPage = cursorPage)
            applyEntities(response.entities)
            hasMore = response.hasMore
            cursorPage = response.nextCursorPage
        } while (hasMore)
    }

    private suspend fun applyEntities(entities: SyncEntitiesDto) {
        jobDao.upsertAll(entities.jobs.upserts.map { it.toEntity() })
        jobDao.deleteByUuids(entities.jobs.deletes)

        jobWorkerDao.upsertAll(entities.jobWorkers.upserts.map { it.toEntity() })
        jobWorkerDao.deleteByUuids(entities.jobWorkers.deletes)

        materialDao.upsertAll(entities.materials.upserts.map { it.toEntity() })
        materialDao.deleteByUuids(entities.materials.deletes)

        jobMaterialDao.upsertAll(entities.jobMaterials.upserts.map { it.toEntity() })
        jobMaterialDao.deleteByUuids(entities.jobMaterials.deletes)

        jobPhotoDao.upsertAll(entities.jobPhotos.upserts.map { it.toEntity() })
        jobPhotoDao.deleteByUuids(entities.jobPhotos.deletes)

        staffDao.upsertAll(entities.staff.upserts.map { it.toEntity() })
        staffDao.deleteByUuids(entities.staff.deletes)
    }
}
