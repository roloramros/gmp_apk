package com.gmp.offline.data.notes

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class NotesDao {
    @Query("SELECT * FROM notes WHERE userUuid = :userUuid ORDER BY updatedAt DESC")
    abstract fun observeNotes(userUuid: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE uuid = :uuid LIMIT 1")
    abstract suspend fun getNote(uuid: String): NoteEntity?

    @Query("SELECT * FROM note_items WHERE noteUuid = :noteUuid ORDER BY position ASC")
    abstract suspend fun getItems(noteUuid: String): List<NoteItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertItems(items: List<NoteItemEntity>)

    @Query("DELETE FROM note_items WHERE noteUuid = :noteUuid")
    abstract suspend fun deleteItems(noteUuid: String)

    @Query("DELETE FROM notes WHERE uuid = :uuid")
    abstract suspend fun deleteNote(uuid: String)

    @Transaction
    open suspend fun save(note: NoteEntity, items: List<NoteItemEntity>) {
        upsertNote(note)
        deleteItems(note.uuid)
        if (items.isNotEmpty()) upsertItems(items)
    }

    @Transaction
    open suspend fun delete(uuid: String) {
        deleteItems(uuid)
        deleteNote(uuid)
    }
}
