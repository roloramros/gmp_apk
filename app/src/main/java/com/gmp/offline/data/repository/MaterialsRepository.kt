package com.gmp.offline.data.repository

import com.gmp.offline.data.local.dao.MaterialDao
import com.gmp.offline.data.local.entities.MaterialEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MaterialsRepository @Inject constructor(
    private val materialDao: MaterialDao,
) {
    fun observeMaterials(): Flow<List<MaterialEntity>> = materialDao.observeAll()
}
