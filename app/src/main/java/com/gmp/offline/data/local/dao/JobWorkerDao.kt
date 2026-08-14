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

    // Lectura puntual (no-Flow), usada para diffear la selección contra lo
    // ya asignado antes de confirmar (ver
    // JobDetailRepository.confirmAssignment).
    @Query("SELECT * FROM job_workers WHERE jobUuid = :jobUuid")
    suspend fun getByJob(jobUuid: String): List<JobWorkerEntity>

    // Lectura puntual (no-Flow), usada para decidir si un trabajador ya
    // está asignado antes de encolar assign/unassign (ver
    // JobDetailRepository.toggleWorkerAssignment).
    @Query("SELECT * FROM job_workers WHERE jobUuid = :jobUuid AND userUuid = :userUuid LIMIT 1")
    suspend fun findByJobAndUser(jobUuid: String, userUuid: String): JobWorkerEntity?

    @Upsert
    suspend fun upsertAll(items: List<JobWorkerEntity>)

    @Query("DELETE FROM job_workers WHERE uuid IN (:uuids)")
    suspend fun deleteByUuids(uuids: List<String>)

    @Query("DELETE FROM job_workers")
    suspend fun clearAll()
}
