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
import com.gmp.offline.data.session.SyncCursorStore
import javax.inject.Inject

// Motor de sincronización real de la Fase 5 (mitad "pull" del diseño).
// Sustituye al OneShotSyncLoader de la Fase 4: hace lo mismo (pedir /sync,
// paginar, aplicar a Room) pero además persiste el cursor en
// SyncCursorStore, así que las llamadas siguientes son incrementales de
// verdad y no un full dump cada vez.
class SyncEngine @Inject constructor(
    private val apiService: ApiService,
    private val syncCursorStore: SyncCursorStore,
    private val jobDao: JobDao,
    private val jobWorkerDao: JobWorkerDao,
    private val materialDao: MaterialDao,
    private val jobMaterialDao: JobMaterialDao,
    private val jobPhotoDao: JobPhotoDao,
    private val staffDao: StaffDao,
) {
    /**
     * Trae los cambios desde el último cursor guardado (o un full dump si
     * nunca se sincronizó antes, o si [forceFullResync] es true), paginando
     * hasta agotar `has_more`, y aplica cada página a Room a medida que
     * llega. El cursor nuevo se guarda recién cuando TODA la secuencia
     * terminó (has_more:false) — tal como exige el contrato del servidor
     * (fase3-jobs-materiales-sync.md, §5.1): guardarlo antes de tiempo
     * podría hacer que una página a mitad de camino se pierda en el
     * próximo `since`.
     */
    suspend fun pull(forceFullResync: Boolean = false) {
        val since = if (forceFullResync) "" else (syncCursorStore.lastCursor ?: "")
        var cursorPage: String? = null
        var hasMore: Boolean
        var latestCursor: String

        do {
            val response = apiService.sync(since = since, cursorPage = cursorPage)
            applyEntities(response.entities)
            hasMore = response.hasMore
            cursorPage = response.nextCursorPage
            latestCursor = response.cursor
        } while (hasMore)

        syncCursorStore.lastCursor = latestCursor
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
