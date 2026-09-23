package com.gmp.offline.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.gmp.offline.data.local.entities.CatalogProductPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogProductPhotoDao {

    @Query("SELECT * FROM catalog_product_photos WHERE productUuid = :productUuid ORDER BY sortOrder ASC, createdAt ASC")
    fun observeByProduct(productUuid: String): Flow<List<CatalogProductPhotoEntity>>

    @Query("SELECT * FROM catalog_product_photos WHERE productUuid = :productUuid ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getByProduct(productUuid: String): List<CatalogProductPhotoEntity>

    @Query("SELECT * FROM catalog_product_photos WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): CatalogProductPhotoEntity?

    @Upsert
    suspend fun upsertAll(items: List<CatalogProductPhotoEntity>)

    @Query("DELETE FROM catalog_product_photos WHERE uuid IN (:uuids)")
    suspend fun deleteByUuids(uuids: List<String>)

    @Query("DELETE FROM catalog_product_photos")
    suspend fun clearAll()
}
