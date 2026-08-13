package com.gmp.offline.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Espejo de "job_photos". `url` es la ruta relativa que ya arma el backend
// (formatUpsert('job_photos', ...) en syncController.js):
// "/jobs/{jobUuid}/photos/{uuid}/file" — se resuelve contra API_BASE_URL.
//
// `localPath` / `uploadStatus` (Fase 6, Paso 4 — foto única de comercial):
// se agregan para poder mostrar la foto de inmediato (archivo comprimido ya
// guardado en almacenamiento interno) mientras se sube, y para poder
// reintentar si la subida falla. No forman parte del contrato del backend
// — son puramente locales, y un pull de /sync que traiga esta misma fila
// las deja en sus valores por defecto (`null` / "synced"), lo cual es
// correcto porque en ese punto la foto ya está confirmada en el servidor.
@Entity(tableName = "job_photos", indices = [Index("jobUuid")])
data class JobPhotoEntity(
    @PrimaryKey val uuid: String,
    val jobUuid: String,
    val uploadedByUuid: String,
    val url: String,
    val createdAt: String,
    val updatedAt: String,
    val localPath: String? = null,
    // "synced" | "uploading" | "error" — nunca viaja al backend.
    val uploadStatus: String = "synced",
)
