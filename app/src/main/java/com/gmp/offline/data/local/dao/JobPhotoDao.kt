package com.gmp.offline.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.gmp.offline.data.local.entities.JobPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JobPhotoDao {
    @Query("SELECT * FROM job_photos WHERE jobUuid = :jobUuid ORDER BY createdAt ASC")
    fun observeByJob(jobUuid: String): Flow<List<JobPhotoEntity>>

    @Query("SELECT * FROM job_photos WHERE jobUuid = :jobUuid ORDER BY createdAt ASC")
    suspend fun getByJob(jobUuid: String): List<JobPhotoEntity>

    @Query("""
        SELECT * FROM job_photos
        WHERE jobUuid = :jobUuid
          AND uploadedByUuid IN (SELECT uuid FROM staff WHERE role IN ('admin', 'comercial'))
        ORDER BY createdAt ASC LIMIT 1
    """)
    suspend fun getFirstByJob(jobUuid: String): JobPhotoEntity?

    @Query("SELECT * FROM job_photos WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): JobPhotoEntity?

    @Upsert
    suspend fun upsertAll(items: List<JobPhotoEntity>)

    @Query("DELETE FROM job_photos WHERE uuid IN (:uuids)")
    suspend fun deleteByUuids(uuids: List<String>)

    @Query("DELETE FROM job_photos WHERE jobUuid = :jobUuid")
    suspend fun deleteByJob(jobUuid: String)

    @Query("DELETE FROM job_photos")
    suspend fun clearAll()
}
