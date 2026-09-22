package com.gmp.offline.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.gmp.offline.data.local.entities.CatalogKitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogKitDao {

    @Query("SELECT * FROM catalog_kits ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<CatalogKitEntity>>

    @Query("SELECT * FROM catalog_kits WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): CatalogKitEntity?

    @Upsert
    suspend fun upsertAll(items: List<CatalogKitEntity>)

    @Query("DELETE FROM catalog_kits WHERE uuid IN (:uuids)")
    suspend fun deleteByUuids(uuids: List<String>)

    @Query("DELETE FROM catalog_kits")
    suspend fun clearAll()
}
