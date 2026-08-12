package com.gmp.offline.data.repository

import com.gmp.offline.data.local.dao.JobMaterialDao
import com.gmp.offline.data.local.dao.JobPhotoDao
import com.gmp.offline.data.local.dao.JobWorkerDao
import com.gmp.offline.data.local.entities.JobMaterialEntity
import com.gmp.offline.data.local.entities.JobPhotoEntity
import com.gmp.offline.data.local.entities.JobWorkerEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// Agrupa lo que necesita una pantalla de detalle de job: sus trabajadores
// asignados, materiales y fotos — todo leído de Room.
class JobDetailRepository @Inject constructor(
    private val jobWorkerDao: JobWorkerDao,
    private val jobMaterialDao: JobMaterialDao,
    private val jobPhotoDao: JobPhotoDao,
) {
    fun observeWorkers(jobUuid: String): Flow<List<JobWorkerEntity>> =
        jobWorkerDao.observeByJob(jobUuid)

    fun observeMaterials(jobUuid: String): Flow<List<JobMaterialEntity>> =
        jobMaterialDao.observeByJob(jobUuid)

    fun observePhotos(jobUuid: String): Flow<List<JobPhotoEntity>> =
        jobPhotoDao.observeByJob(jobUuid)
}
