package com.gmp.offline.data.notes

import com.gmp.offline.data.session.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class NoteDraftItem(val uuid: String = UUID.randomUUID().toString(), val text: String = "", val checked: Boolean = false)
data class NoteWithItems(val note: NoteEntity, val items: List<NoteItemEntity>)

@Singleton
class NotesRepository @Inject constructor(
    private val dao: NotesDao,
    private val sessionManager: SessionManager,
) {
    fun observeMyNotes(): Flow<List<NoteEntity>> =
        sessionManager.userUuid?.let(dao::observeNotes) ?: flowOf(emptyList())

    suspend fun get(uuid: String): NoteWithItems? {
        val userUuid = sessionManager.userUuid ?: return null
        val note = dao.getNote(uuid) ?: return null
        if (note.userUuid != userUuid) return null
        return NoteWithItems(note, dao.getItems(uuid))
    }

    suspend fun save(
        uuid: String?,
        type: String,
        title: String,
        text: String,
        items: List<NoteDraftItem>,
    ): String? {
        val userUuid = sessionManager.userUuid ?: return null
        val now = System.currentTimeMillis()
        val existing = uuid?.let { dao.getNote(it) }
        if (existing != null && existing.userUuid != userUuid) return null
        val noteUuid = existing?.uuid ?: UUID.randomUUID().toString()
        val note = NoteEntity(
            uuid = noteUuid,
            userUuid = userUuid,
            type = type,
            title = title.trim(),
            text = if (type == "text") text else "",
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        val entities = if (type == "checklist") items.mapIndexed { index, item ->
            NoteItemEntity(noteUuid, item.uuid, item.text, item.checked, index)
        } else emptyList()
        dao.save(note, entities)
        return noteUuid
    }

    suspend fun delete(uuid: String) {
        val userUuid = sessionManager.userUuid ?: return
        if (dao.getNote(uuid)?.userUuid == userUuid) dao.delete(uuid)
    }
}
