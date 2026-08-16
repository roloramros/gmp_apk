package com.gmp.offline.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.gmp.offline.data.local.entities.PendingOperationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingOperationDao {

    @Query("SELECT * FROM pending_operations ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PendingOperationEntity>>

    @Query("SELECT * FROM pending_operations WHERE status = 'pending' ORDER BY createdAt ASC")
    suspend fun getPending(): List<PendingOperationEntity>

    @Insert
    suspend fun insert(operation: PendingOperationEntity)

    @Query("UPDATE pending_operations SET status = :status, lastAttemptAt = :attemptAt, lastErrorMessage = :errorMessage WHERE commandId = :commandId")
    suspend fun updateStatus(commandId: String, status: String, attemptAt: Long?, errorMessage: String?)

    @Query("DELETE FROM pending_operations WHERE commandId = :commandId")
    suspend fun delete(commandId: String)

    @Query("DELETE FROM pending_operations WHERE endpointPath LIKE '%' || :jobUuid || '%' OR payloadJson LIKE '%' || :jobUuid || '%'")
    suspend fun deleteForJob(jobUuid: String)
}
