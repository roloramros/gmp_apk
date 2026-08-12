package com.gmp.offline.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gmp.offline.data.local.dao.JobDao
import com.gmp.offline.data.local.dao.JobMaterialDao
import com.gmp.offline.data.local.dao.JobPhotoDao
import com.gmp.offline.data.local.dao.JobWorkerDao
import com.gmp.offline.data.local.dao.MaterialDao
import com.gmp.offline.data.local.dao.PendingOperationDao
import com.gmp.offline.data.local.dao.StaffDao
import com.gmp.offline.data.local.entities.JobEntity
import com.gmp.offline.data.local.entities.JobMaterialEntity
import com.gmp.offline.data.local.entities.JobPhotoEntity
import com.gmp.offline.data.local.entities.JobWorkerEntity
import com.gmp.offline.data.local.entities.MaterialEntity
import com.gmp.offline.data.local.entities.PendingOperationEntity
import com.gmp.offline.data.local.entities.StaffEntity

// Versión 1: primer esquema de la Fase 4, espejo 1:1 de las 6 entidades
// sincronizables + el outbox. Cuando se agreguen/cambien columnas en fases
// futuras, subir `version` y agregar una Migration explícita — no usar
// fallbackToDestructiveMigration en el builder salvo durante desarrollo
// temprano (ver DatabaseModule.kt).
@Database(
    entities = [
        JobEntity::class,
        JobWorkerEntity::class,
        MaterialEntity::class,
        JobMaterialEntity::class,
        JobPhotoEntity::class,
        StaffEntity::class,
        PendingOperationEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class GmpDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
    abstract fun jobWorkerDao(): JobWorkerDao
    abstract fun materialDao(): MaterialDao
    abstract fun jobMaterialDao(): JobMaterialDao
    abstract fun jobPhotoDao(): JobPhotoDao
    abstract fun staffDao(): StaffDao
    abstract fun pendingOperationDao(): PendingOperationDao
}
