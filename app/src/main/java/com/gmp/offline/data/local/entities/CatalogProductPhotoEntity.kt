package com.gmp.offline.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Espejo de "catalog_product_photos". Ver CatalogKitPhotoEntity para el
// razonamiento de localPath/uploadStatus.
@Entity(tableName = "catalog_product_photos", indices = [Index("productUuid")])
data class CatalogProductPhotoEntity(
    @PrimaryKey val uuid: String,
    val productUuid: String,
    val url: String,
    val sortOrder: Int,
    val createdAt: String,
    val updatedAt: String,
    val localPath: String? = null,
    val uploadStatus: String = "synced",
)
