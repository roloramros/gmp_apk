package com.gmp.offline.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Espejo de "job_photos". `url` es la ruta relativa que ya arma el backend
// (formatUpsert('job_photos', ...) en syncController.js):
// "/jobs/{jobUuid}/photos/{uuid}/file" — se resuelve contra API_BASE_URL.
//
// Nota: esto es solo el registro/metadata. La Fase 7 (Fotos offline) es la
// que se ocupa de guardar el archivo en almacenamiento interno y encolar su
// subida; acá solo dejamos la entidad lista para que esa fase la use.
@Entity(tableName = "job_photos", indices = [Index("jobUuid")])
data class JobPhotoEntity(
    @PrimaryKey val uuid: String,
    val jobUuid: String,
    val uploadedByUuid: String,
    val url: String,
    val createdAt: String,
    val updatedAt: String,
)
