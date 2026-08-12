package com.gmp.offline.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Espejo de "job_materials": un material (de catálogo o texto libre) añadido
// a un job. `materialUuid` es nullable porque puede ser una descripción libre
// (ver jobMaterialsController.js en el backend).
@Entity(tableName = "job_materials", indices = [Index("jobUuid")])
data class JobMaterialEntity(
    @PrimaryKey val uuid: String,
    val jobUuid: String,
    val materialUuid: String?,
    val freeTextDescription: String?,
    val quantity: String,
    val unitPrice: String?,
    val createdAt: String,
    val updatedAt: String,
)
