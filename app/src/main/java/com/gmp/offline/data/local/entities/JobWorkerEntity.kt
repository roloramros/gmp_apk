package com.gmp.offline.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Espejo de "job_workers": asignación de un trabajador (userUuid) a un job.
@Entity(
    tableName = "job_workers",
    indices = [Index("jobUuid"), Index("userUuid")],
)
data class JobWorkerEntity(
    @PrimaryKey val uuid: String,
    val jobUuid: String,
    val userUuid: String,
    val createdAt: String,
    val updatedAt: String,
)
