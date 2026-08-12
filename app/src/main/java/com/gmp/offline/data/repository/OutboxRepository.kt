package com.gmp.offline.data.repository

import com.gmp.offline.data.local.dao.PendingOperationDao
import com.gmp.offline.data.local.entities.PendingOperationEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class OutboxRepository @Inject constructor(
    private val pendingOperationDao: PendingOperationDao,
) {
    fun observePending(): Flow<List<PendingOperationEntity>> = pendingOperationDao.observeAll()
}
