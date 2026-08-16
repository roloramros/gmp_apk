package com.gmp.offline.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.gmp.offline.data.local.entities.JobMaterialEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JobMaterialDao {

    @Query("SELECT * FROM job_materials WHERE jobUuid = :jobUuid")
    fun observeByJob(jobUuid: String): Flow<List<JobMaterialEntity>>

    @Query("SELECT * FROM job_materials WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): JobMaterialEntity?

    @Query("SELECT * FROM job_materials WHERE jobUuid = :jobUuid AND materialUuid = :materialUuid LIMIT 1")
    suspend fun findByJobAndMaterial(jobUuid: String, materialUuid: String): JobMaterialEntity?

    @Query("SELECT * FROM job_materials WHERE jobUuid = :jobUuid AND materialUuid IS NULL AND freeTextDescription = :description LIMIT 1")
    suspend fun findByJobAndFreeText(jobUuid: String, description: String): JobMaterialEntity?

    @Upsert
    suspend fun upsertAll(items: List<JobMaterialEntity>)

    @Query("DELETE FROM job_materials WHERE uuid IN (:uuids)")
    suspend fun deleteByUuids(uuids: List<String>)

    @Query("DELETE FROM job_materials WHERE jobUuid = :jobUuid")
    suspend fun deleteByJob(jobUuid: String)

    @Query("DELETE FROM job_materials")
    suspend fun clearAll()
}
