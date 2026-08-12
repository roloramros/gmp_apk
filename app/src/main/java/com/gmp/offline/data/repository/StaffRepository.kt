package com.gmp.offline.data.repository

import com.gmp.offline.data.local.dao.StaffDao
import com.gmp.offline.data.local.entities.StaffEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class StaffRepository @Inject constructor(
    private val staffDao: StaffDao,
) {
    fun observeStaff(): Flow<List<StaffEntity>> = staffDao.observeAll()

    fun observeStaffMember(uuid: String): Flow<StaffEntity?> = staffDao.observeByUuid(uuid)
}
