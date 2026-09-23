package com.gmp.offline.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

// Espejo de "gallery_photos" — fotos sueltas de instalaciones terminadas,
// sin "padre" (a diferencia de las fotos de kit/producto). localPath/
// uploadStatus: mismo patrón offline-first que las demás fotos.
@Entity(tableName = "gallery_photos")
data class GalleryPhotoEntity(
    @PrimaryKey val uuid: String,
    val caption: String?,
    val active: Boolean,
    val sortOrder: Int,
    val url: String,
    val createdAt: String,
    val updatedAt: String,
    val localPath: String? = null,
    val uploadStatus: String = "synced",
)
