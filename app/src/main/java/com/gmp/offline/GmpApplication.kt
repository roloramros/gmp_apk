package com.gmp.offline

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.gmp.offline.sync.NetworkConnectivityObserver
import com.gmp.offline.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GmpApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var connectivityObserver: NetworkConnectivityObserver

    @Inject
    lateinit var syncScheduler: SyncScheduler

    // Vive tanto como el proceso de la app — se usa solo para escuchar
    // conectividad y disparar syncs, no para trabajo pesado.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        observeConnectivityForSync()
    }

    // Disparador "por conectividad" del plan de Fase 5: apenas hay red de
    // nuevo, se encola un sync inmediato en vez de esperar al próximo slot
    // del trabajo periódico (que corre cada 15 min como mínimo). Si todavía
    // no hay sesión iniciada, SyncWorker.doWork() lo detecta y no hace nada
    // (ver SyncWorker) — así que es seguro escuchar esto desde el arranque
    // de la app, sin esperar login.
    private fun observeConnectivityForSync() {
        applicationScope.launch {
            connectivityObserver.observe()
                .drop(1) // no disparar por el valor inicial al registrar el callback
                .filter { isOnline -> isOnline }
                .collect { syncScheduler.triggerImmediateSync() }
        }
    }
}
