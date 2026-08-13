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
// sincronizables + el outbox.
// Versión 2 (Fase 6, Paso 3): se agregan campos de "montaje" a JobEntity
// (clientName, clientCi, clientPhone, latitude, longitude, reference,
// siteNotes, price, paymentMethod, visitDate, proposedDate) para replicar
// exactamente la pantalla de comercial de la web legada.
// Versión 3 (Fase 6, Paso 4): se agregan `localPath`/`uploadStatus` a
// JobPhotoEntity para la foto única de comercial (ver comentario en la
// entidad).
// Se usa `fallbackToDestructiveMigration()` (ver DatabaseModule.kt) porque
// el proyecto todavía está en desarrollo temprano — el próximo `/sync`
// repuebla Room desde cero sin pérdida de datos real (la fuente de verdad
// es el backend). Cuando la app esté en producción con usuarios reales,
// cambiar esto por una Migration explícita.
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
    version = 3,
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
