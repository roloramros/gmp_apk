package com.gmp.offline.sync

import com.gmp.offline.data.local.dao.JobDao
import com.gmp.offline.data.local.dao.JobMaterialDao
import com.gmp.offline.data.local.dao.JobPhotoDao
import com.gmp.offline.data.local.dao.JobWorkerDao
import com.gmp.offline.data.local.dao.MaterialDao
import com.gmp.offline.data.local.dao.StaffDao
import com.gmp.offline.data.notes.NoteEntity
import com.gmp.offline.data.notes.NoteItemEntity
import com.gmp.offline.data.notes.NotesDao
import com.gmp.offline.data.remote.ApiService
import com.gmp.offline.data.remote.dto.SyncEntitiesDto
import com.gmp.offline.data.remote.dto.toEntity
import com.gmp.offline.data.session.SessionManager
import com.gmp.offline.data.session.SyncCursorStore
import java.time.Instant
import javax.inject.Inject

class SyncEngine @Inject constructor(
    private val apiService: ApiService,
    private val syncCursorStore: SyncCursorStore,
    private val sessionManager: SessionManager,
    private val jobDao: JobDao,
    private val jobWorkerDao: JobWorkerDao,
    private val materialDao: MaterialDao,
    private val jobMaterialDao: JobMaterialDao,
    private val jobPhotoDao: JobPhotoDao,
    private val staffDao: StaffDao,
    private val notesDao: NotesDao,
) {
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
        restoreNotes()
    }

    private suspend fun restoreNotes() {
        val userUuid = sessionManager.userUuid ?: return
        val remote = apiService.listNotes()
        val notes = remote.map {
            NoteEntity(
                uuid = it.uuid,
                userUuid = userUuid,
                type = it.type,
                title = it.title,
                text = it.body,
                createdAt = Instant.parse(it.createdAt).toEpochMilli(),
                updatedAt = Instant.parse(it.updatedAt).toEpochMilli(),
            )
        }
        val items = remote.flatMap { note ->
            note.items.mapIndexed { index, item ->
                NoteItemEntity(
                    noteUuid = note.uuid,
                    itemUuid = item.uuid,
                    text = item.text,
                    checked = item.checked,
                    position = item.position.takeIf { it >= 0 } ?: index,
                )
            }
        }
        notesDao.replaceForUser(userUuid, notes, items)
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
