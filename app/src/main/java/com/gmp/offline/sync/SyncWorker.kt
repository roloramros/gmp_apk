package com.gmp.offline.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gmp.offline.data.session.SessionManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

// Reproduce en orden: primero vacía el outbox (push), después trae cambios
// del servidor (pull). Ese orden importa — si fuera al revés, un pull
// podría pisar con datos del servidor un cambio optimista que todavía no
// se mandó.
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val outboxProcessor: OutboxProcessor,
    private val syncEngine: SyncEngine,
    private val sessionManager: SessionManager,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (sessionManager.token == null) {
            // Sin sesión todavía (p.ej. la app recién instalada, nadie
            // hizo login aún): no hay nada que sincronizar. No es un error,
            // así que no se reintenta ni se cuenta como fallo.
            return Result.success()
        }

        return try {
            val outboxCompleted = outboxProcessor.processPending()
            syncEngine.pull()
            if (outboxCompleted) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "gmp_sync_periodic"
        const val UNIQUE_MANUAL_NAME = "gmp_sync_manual"
    }
}
