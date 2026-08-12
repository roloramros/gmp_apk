package com.gmp.offline.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

// Espejo de "materials": catálogo de materiales de la empresa.
@Entity(tableName = "materials")
data class MaterialEntity(
    @PrimaryKey val uuid: String,
    val name: String,
    val unit: String?,
    val defaultPrice: String?,
    val createdAt: String,
    val updatedAt: String,
)
