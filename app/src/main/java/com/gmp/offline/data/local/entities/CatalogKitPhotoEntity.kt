package com.gmp.offline.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Espejo de "catalog_kit_photos". `url` es la ruta relativa que arma el
// backend (formatUpsert('catalog_kit_photos', ...) en syncController.js):
// "/catalog-kits/{kitUuid}/photos/{uuid}/file".
//
// `localPath` / `uploadStatus`: mismo patrón que JobPhotoEntity (ver ese
// archivo) — se agregan para poder mostrar la foto de inmediato mientras se
// sube, y para poder reintentar si falla. Puramente locales: un pull de
// /sync que traiga esta fila las deja en sus valores por defecto (`null` /
// "synced"), correcto porque en ese punto ya está confirmada en el server.
@Entity(tableName = "catalog_kit_photos", indices = [Index("kitUuid")])
data class CatalogKitPhotoEntity(
    @PrimaryKey val uuid: String,
    val kitUuid: String,
    val url: String,
    val sortOrder: Int,
    val createdAt: String,
    val updatedAt: String,
    val localPath: String? = null,
    // "synced" | "uploading" | "error" — nunca viaja al backend.
    val uploadStatus: String = "synced",
)
