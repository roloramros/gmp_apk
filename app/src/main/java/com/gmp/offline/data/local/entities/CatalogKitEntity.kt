package com.gmp.offline.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

// Espejo de "catalog_kits": catálogo de kits/ofertas configurable por el
// admin (feature "Catálogo"), expuesto también en la página pública
// GET /public/catalog/:slug. No hay noción de trabajador/comercial acá —
// solo admin lo gestiona (ver catalogKitsController.js, visibilidad de
// /sync restringida a admin/comercial en syncController.js).
@Entity(tableName = "catalog_kits")
data class CatalogKitEntity(
    @PrimaryKey val uuid: String,
    val name: String,
    val powerKw: String?,
    val voltage: String?,
    val batteryKwh: String?,
    val panelsCount: Int?,
    val priceUsd: String?,
    val description: String?,
    val active: Boolean,
    val sortOrder: Int,
    val createdAt: String,
    val updatedAt: String,
)
