package com.gmp.offline.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.gmp.offline.data.local.entities.JobPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JobPhotoDao {

    @Query("SELECT * FROM job_photos WHERE jobUuid = :jobUuid")
    fun observeByJob(jobUuid: String): Flow<List<JobPhotoEntity>>

    @Upsert
    suspend fun upsertAll(items: List<JobPhotoEntity>)

    @Query("DELETE FROM job_photos WHERE uuid IN (:uuids)")
    suspend fun deleteByUuids(uuids: List<String>)

    @Query("DELETE FROM job_photos")
    suspend fun clearAll()
}
