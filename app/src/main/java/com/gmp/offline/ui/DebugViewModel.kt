package com.gmp.offline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmp.offline.data.repository.AuthRepository
import com.gmp.offline.data.repository.JobsRepository
import com.gmp.offline.data.repository.OutboxRepository
import com.gmp.offline.data.repository.StaffRepository
import com.gmp.offline.sync.SyncEngine
import com.gmp.offline.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Este ViewModel sigue siendo "de debug" (igual que en Fase 4): su
// propósito es validar visualmente que login -> SyncEngine.pull() ->
// SyncScheduler -> outbox funcionan juntos. No es la arquitectura final de
// pantallas por rol (eso es Fase 6).
@HiltViewModel
class DebugViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncEngine: SyncEngine,
    private val syncScheduler: SyncScheduler,
    private val jobsRepository: JobsRepository,
    staffRepository: StaffRepository,
    outboxRepository: OutboxRepository,
) : ViewModel() {

    // Estos Flow vienen DIRECTO de Room. La UI que los colecta nunca toca
    // la red: solo refleja lo que hay en la base local.
    val jobs = jobsRepository.observeJobs()
    val staff = staffRepository.observeStaff()
    val pendingOperations = outboxRepository.observePending()

    private val _status = MutableStateFlow("Sin datos todavía. Inicia sesión y sincroniza.")
    val status: StateFlow<String> = _status.asStateFlow()

    fun loginAndSync(companyId: Int, phone: String, password: String) {
        viewModelScope.launch {
            try {
                _status.value = "Iniciando sesión..."
                authRepository.login(companyId, phone, password)

                _status.value = "Sesión OK. Sincronizando (full dump inicial)..."
                syncEngine.pull(forceFullResync = true)

                // A partir de acá, el sync corre solo: periódico cada 15 min
                // + apenas vuelva la red (ver GmpApplication) + manual con
                // el botón de abajo.
                syncScheduler.schedulePeriodic()

                _status.value = "Sincronizado. Lo de abajo viene de Room, no de la red."
            } catch (e: Exception) {
                _status.value = "Error: ${e.message}"
            }
        }
    }

    /** Botón "Sincronizar ahora": dispara el mismo SyncWorker que corre solo. */
    fun syncNow() {
        _status.value = "Sync manual encolado..."
        syncScheduler.triggerImmediateSync()
    }

    /**
     * Prueba manual del patrón optimista + outbox (ver JobsRepository.startJob):
     * inicia el primer job en estado "pending" que encuentre, para poder ver
     * el cambio reflejado al toque en la lista, y el comando apareciendo en
     * `pendingOperations` hasta que el SyncWorker lo mande.
     */
    fun startFirstPendingJobAsTest() {
        viewModelScope.launch {
            val firstPending = jobsRepository.findFirstJobByStatus("pending")
            if (firstPending != null) {
                jobsRepository.startJob(firstPending.uuid)
                _status.value = "Job '${firstPending.title}' iniciado localmente (optimista). Mira 'pendientes' abajo."
            } else {
                _status.value = "No hay ningún job en estado 'pending' para probar."
            }
        }
    }
}
