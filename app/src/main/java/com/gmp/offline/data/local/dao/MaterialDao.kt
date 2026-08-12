package com.gmp.offline.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.gmp.offline.data.local.entities.MaterialEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialDao {

    @Query("SELECT * FROM materials ORDER BY name ASC")
    fun observeAll(): Flow<List<MaterialEntity>>

    @Upsert
    suspend fun upsertAll(items: List<MaterialEntity>)

    @Query("DELETE FROM materials WHERE uuid IN (:uuids)")
    suspend fun deleteByUuids(uuids: List<String>)

    @Query("DELETE FROM materials")
    suspend fun clearAll()
}
