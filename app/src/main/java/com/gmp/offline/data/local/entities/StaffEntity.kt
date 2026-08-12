package com.gmp.offline.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

// Espejo de la entidad "staff" del payload de /sync (tabla `users` en el
// backend). No incluye password_hash: el backend nunca lo expone en /sync.
@Entity(tableName = "staff")
data class StaffEntity(
    @PrimaryKey val uuid: String,
    val phone: String,
    val role: String,
    val fullName: String,
    val active: Boolean,
    val createdAt: String,
    val updatedAt: String,
)
