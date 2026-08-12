package com.gmp.offline.sync

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// Los 3 disparadores que pide el plan (Fase 5, sección "App Android: motor
// de sincronización"): conectividad, periódico, manual.
//
// El de conectividad se resuelve en dos capas: Constraints(NetworkType.CONNECTED)
// hace que WorkManager no corra NINGUNO de estos workers hasta que haya red
// (protege tanto al periódico como al manual); además, GmpApplication
// escucha NetworkConnectivityObserver y llama a triggerImmediateSync() en
// el instante en que la conexión vuelve, en vez de esperar al próximo slot
// del periódico (que solo corre cada 15 min, el mínimo que permite
// WorkManager para PeriodicWorkRequest).
class SyncScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Se llama una vez al iniciar sesión: deja el sync corriendo solo de ahí en más. */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            SyncWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Disparo inmediato: botón de "sincronizar ahora", reconexión de red, o reintento manual. */
    fun triggerImmediateSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            SyncWorker.UNIQUE_MANUAL_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(SyncWorker.UNIQUE_PERIODIC_NAME)
        workManager.cancelUniqueWork(SyncWorker.UNIQUE_MANUAL_NAME)
    }
}
