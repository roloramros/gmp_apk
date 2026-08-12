package com.gmp.offline.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.gmp.offline.data.local.entities.JobWorkerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JobWorkerDao {

    @Query("SELECT * FROM job_workers")
    fun observeAll(): Flow<List<JobWorkerEntity>>

    @Query("SELECT * FROM job_workers WHERE jobUuid = :jobUuid")
    fun observeByJob(jobUuid: String): Flow<List<JobWorkerEntity>>

    @Upsert
    suspend fun upsertAll(items: List<JobWorkerEntity>)

    @Query("DELETE FROM job_workers WHERE uuid IN (:uuids)")
    suspend fun deleteByUuids(uuids: List<String>)

    @Query("DELETE FROM job_workers")
    suspend fun clearAll()
}
