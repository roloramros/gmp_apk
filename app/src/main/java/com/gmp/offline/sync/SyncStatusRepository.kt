package com.gmp.offline.sync

import android.content.Context
import android.content.SharedPreferences
import com.gmp.offline.data.local.dao.PendingOperationDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// Estado de sincronización visible en el drawer (ítem "Sincronizar"): en
// curso o no, cuándo fue la última vez que terminó bien, y el mensaje si la
// última terminó mal. Lo actualiza SyncWorker.doWork() en cada corrida —
// manual, periódica o por reconexión, todas pasan por acá — y lo lee
// SyncStatusViewModel (que a su vez usa GmpNavigationDrawer).
//
// `lastSuccessAtMillis` se persiste en SharedPreferences (mismo patrón que
// SessionManager) para que sobreviva a que la app se cierre — un sync
// periódico en background también cuenta como "última vez sincronizado".
@Singleton
class SyncStatusRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val pendingOperationDao: PendingOperationDao,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gmp_sync_status", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        SyncUiStatus(
            syncing = false,
            lastSuccessAtMillis = prefs.getLong(KEY_LAST_SUCCESS, 0L).takeIf { it > 0L },
            lastErrorMessage = null,
        ),
    )
    val state: StateFlow<SyncUiStatus> = _state.asStateFlow()

    val pendingCount: Flow<Int> = pendingOperationDao.observeCount()

    fun onSyncStart() {
        _state.value = _state.value.copy(syncing = true)
    }

    fun onSyncSuccess() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_SUCCESS, now).apply()
        _state.value = _state.value.copy(syncing = false, lastSuccessAtMillis = now, lastErrorMessage = null)
    }

    fun onSyncError(message: String) {
        _state.value = _state.value.copy(syncing = false, lastErrorMessage = message)
    }

    companion object {
        private const val KEY_LAST_SUCCESS = "last_success_at_millis"
    }
}

data class SyncUiStatus(
    val syncing: Boolean,
    val lastSuccessAtMillis: Long?,
    val lastErrorMessage: String?,
)
