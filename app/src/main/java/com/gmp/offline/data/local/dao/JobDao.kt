package com.gmp.offline.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.gmp.offline.data.local.entities.JobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {

    @Query("SELECT * FROM jobs ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE uuid = :uuid")
    fun observeByUuid(uuid: String): Flow<JobEntity?>

    // Lectura puntual (no-Flow), para los casos de escritura optimista donde
    // hace falta el valor actual una sola vez antes de modificarlo (ver
    // JobsRepository.startJob).
    @Query("SELECT * FROM jobs WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): JobEntity?

    @Query("SELECT * FROM jobs WHERE status = :status ORDER BY updatedAt DESC")
    fun observeByStatus(status: String): Flow<List<JobEntity>>

    // Lectura puntual (no-Flow) del primer job en un estado dado, usada por
    // el botón de prueba manual del outbox (ver JobsRepository, DebugViewModel).
    @Query("SELECT * FROM jobs WHERE status = :status ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getFirstByStatus(status: String): JobEntity?

    @Upsert
    suspend fun upsertAll(jobs: List<JobEntity>)

    @Query("DELETE FROM jobs WHERE uuid IN (:uuids)")
    suspend fun deleteByUuids(uuids: List<String>)

    @Query("DELETE FROM jobs")
    suspend fun clearAll()
}
