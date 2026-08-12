package com.gmp.offline.ui.comercial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmp.offline.data.local.entities.JobEntity
import com.gmp.offline.data.repository.AuthRepository
import com.gmp.offline.data.repository.JobsRepository
import com.gmp.offline.data.repository.OutboxRepository
import com.gmp.offline.data.repository.StaffRepository
import com.gmp.offline.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// Fila de la lista: el job tal como está en Room + su nombre de cliente
// resuelto (join en memoria contra `staff`, ya que Room no tiene una vista
// unida para esto) + si tiene algún comando pendiente en el outbox.
data class ComercialJobRow(
    val job: JobEntity,
    val clientName: String?,
    val pendingSync: Boolean,
)

// Mismo orden de estados que STATUS_LABELS en la web legada, para que la
// barra de filtros se vea igual (chips en el mismo orden, con el conteo
// de jobs en cada uno).
val JOB_STATUS_ORDER = listOf(
    "pending", "assigned", "in_progress", "finished",
    "invoiced", "partially_paid", "paid", "cancelled",
)

@HiltViewModel
class ComercialJobsListViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncScheduler: SyncScheduler,
    jobsRepository: JobsRepository,
    staffRepository: StaffRepository,
    outboxRepository: OutboxRepository,
) : ViewModel() {

    val currentFullName: String? get() = authRepository.currentFullName

    // Todos los jobs (sin filtrar), ya con el nombre de cliente resuelto.
    private val allRows: StateFlow<List<ComercialJobRow>> = combine(
        jobsRepository.observeJobs(),
        staffRepository.observeStaff(),
        outboxRepository.observePending(),
    ) { jobs, staff, pending ->
        val staffByUuid = staff.associateBy { it.uuid }
        jobs
            .sortedByDescending { it.updatedAt }
            .map { job ->
                ComercialJobRow(
                    job = job,
                    clientName = job.clientName ?: job.clientUuid?.let { staffByUuid[it]?.fullName },
                    pendingSync = pending.any { it.endpointPath.contains(job.uuid) },
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Filtro de estados activo (multi-selección, igual que `activeStatusFilters`
    // en la web: un Set de status; vacío = sin filtro = se ven todos).
    private val _activeFilters = MutableStateFlow<Set<String>>(emptySet())
    val activeFilters: StateFlow<Set<String>> = _activeFilters.asStateFlow()

    // Conteo de jobs por status, para mostrar el número en cada chip del
    // filtro (igual que `counts` en `renderStatusFilterBar` de la web).
    val statusCounts: StateFlow<Map<String, Int>> = allRows
        .map { rows -> JOB_STATUS_ORDER.associateWith { status -> rows.count { it.job.status == status } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val jobRows: StateFlow<List<ComercialJobRow>> = combine(allRows, _activeFilters) { rows, filters ->
        if (filters.isEmpty()) rows else rows.filter { filters.contains(it.job.status) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleStatusFilter(status: String) {
        _activeFilters.value = if (_activeFilters.value.contains(status)) {
            _activeFilters.value - status
        } else {
            _activeFilters.value + status
        }
    }

    fun clearStatusFilters() {
        _activeFilters.value = emptySet()
    }

    fun syncNow() {
        syncScheduler.triggerImmediateSync()
    }

    fun logout(onLoggedOut: () -> Unit) {
        syncScheduler.cancelAll()
        authRepository.logout()
        onLoggedOut()
    }
}
