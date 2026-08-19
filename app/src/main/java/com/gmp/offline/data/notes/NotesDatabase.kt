package com.gmp.offline.data.notes

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [NoteEntity::class, NoteItemEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun notesDao(): NotesDao
}
