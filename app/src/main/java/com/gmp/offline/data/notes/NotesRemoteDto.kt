package com.gmp.offline.data.notes

import com.google.gson.annotations.SerializedName

data class RemoteNoteDto(
    val uuid: String,
    val type: String,
    val title: String,
    val body: String,
    val items: List<RemoteNoteItemDto> = emptyList(),
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
)

data class RemoteNoteItemDto(
    val uuid: String,
    val text: String,
    val checked: Boolean,
    val position: Int,
)

data class UpsertNoteRequest(
    val type: String,
    val title: String,
    val body: String,
    val items: List<RemoteNoteItemDto>,
)
