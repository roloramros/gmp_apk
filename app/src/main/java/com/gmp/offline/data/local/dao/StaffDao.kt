package com.gmp.offline.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.gmp.offline.data.local.entities.StaffEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StaffDao {

    @Query("SELECT * FROM staff ORDER BY fullName ASC")
    fun observeAll(): Flow<List<StaffEntity>>

    @Query("SELECT * FROM staff WHERE uuid = :uuid")
    fun observeByUuid(uuid: String): Flow<StaffEntity?>

    @Upsert
    suspend fun upsertAll(items: List<StaffEntity>)

    @Query("DELETE FROM staff WHERE uuid IN (:uuids)")
    suspend fun deleteByUuids(uuids: List<String>)

    @Query("DELETE FROM staff")
    suspend fun clearAll()
}
