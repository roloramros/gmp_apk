package com.gmp.offline.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.gmp.offline.data.local.entities.CatalogProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogProductDao {

    @Query("SELECT * FROM catalog_products ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<CatalogProductEntity>>

    @Query("SELECT * FROM catalog_products WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): CatalogProductEntity?

    @Upsert
    suspend fun upsertAll(items: List<CatalogProductEntity>)

    @Query("DELETE FROM catalog_products WHERE uuid IN (:uuids)")
    suspend fun deleteByUuids(uuids: List<String>)

    @Query("DELETE FROM catalog_products")
    suspend fun clearAll()
}
