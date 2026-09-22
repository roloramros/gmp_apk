package com.gmp.offline.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.gmp.offline.data.local.entities.CatalogKitPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogKitPhotoDao {

    @Query("SELECT * FROM catalog_kit_photos WHERE kitUuid = :kitUuid ORDER BY sortOrder ASC, createdAt ASC")
    fun observeByKit(kitUuid: String): Flow<List<CatalogKitPhotoEntity>>

    @Query("SELECT * FROM catalog_kit_photos WHERE kitUuid = :kitUuid ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getByKit(kitUuid: String): List<CatalogKitPhotoEntity>

    @Query("SELECT * FROM catalog_kit_photos WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): CatalogKitPhotoEntity?

    @Upsert
    suspend fun upsertAll(items: List<CatalogKitPhotoEntity>)

    @Query("DELETE FROM catalog_kit_photos WHERE uuid IN (:uuids)")
    suspend fun deleteByUuids(uuids: List<String>)

    @Query("DELETE FROM catalog_kit_photos")
    suspend fun clearAll()
}
