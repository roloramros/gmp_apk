package com.gmp.offline.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

// Espejo de "catalog_products" — tienda de componentes sueltos. Mismo
// espíritu que CatalogKitEntity, ver ese archivo para el patrón general.
@Entity(tableName = "catalog_products")
data class CatalogProductEntity(
    @PrimaryKey val uuid: String,
    val name: String,
    val priceUsd: String?,
    val description: String?,
    val inStock: Boolean,
    val active: Boolean,
    val sortOrder: Int,
    val createdAt: String,
    val updatedAt: String,
)
