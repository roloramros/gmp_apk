package com.gmp.offline.data.notes

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val uuid: String,
    val userUuid: String,
    val type: String,
    val title: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "note_items", primaryKeys = ["noteUuid", "itemUuid"])
data class NoteItemEntity(
    val noteUuid: String,
    val itemUuid: String,
    val text: String,
    val checked: Boolean,
    val position: Int,
)
