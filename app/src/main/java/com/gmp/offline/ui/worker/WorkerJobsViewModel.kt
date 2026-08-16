package com.gmp.offline.ui.worker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmp.offline.data.local.dao.JobWorkerDao
import com.gmp.offline.data.local.entities.JobEntity
import com.gmp.offline.data.repository.AuthRepository
import com.gmp.offline.data.repository.JobsRepository
import com.gmp.offline.data.repository.OutboxRepository
import com.gmp.offline.data.repository.StaffRepository
import com.gmp.offline.data.session.SessionManager
import com.gmp.offline.sync.SyncScheduler
import com.gmp.offline.ui.comercial.ComercialJobRow
import com.gmp.offline.ui.comercial.JOB_STATUS_ORDER
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkerJobsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncScheduler: SyncScheduler,
    jobsRepository: JobsRepository,
    jobWorkerDao: JobWorkerDao,
    staffRepository: StaffRepository,
    outboxRepository: OutboxRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    private val currentUserUuid = sessionManager.userUuid.orEmpty()

    val currentFullName: String? get() = authRepository.currentFullName
    val currentCompanyName: String? get() = authRepository.currentCompanyName

    private val allRows: StateFlow<List<ComercialJobRow>> = combine(
        jobsRepository.observeJobs(),
        jobWorkerDao.observeAll(),
        staffRepository.observeStaff(),
        outboxRepository.observePending(),
    ) { jobs, assignments, staff, pending ->
        val assignedJobUuids = assignments
            .asSequence()
            .filter { it.userUuid == currentUserUuid }
            .map { it.jobUuid }
            .toSet()
        val staffByUuid = staff.associateBy { it.uuid }

        jobs
            .asSequence()
            .filter { it.uuid in assignedJobUuids }
            .sortedWith(
                compareBy<JobEntity> { it.scheduledAt == null }
                    .thenBy { it.scheduledAt ?: "" }
                    .thenByDescending { it.updatedAt },
            )
            .map { job ->
                ComercialJobRow(
                    job = job,
                    clientName = job.clientName ?: job.clientUuid?.let { staffByUuid[it]?.fullName },
                    pendingSync = pending.any {
                        it.status == "pending" && it.endpointPath.contains(job.uuid)
                    },
                )
            }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _activeFilters = MutableStateFlow<Set<String>>(emptySet())
    val activeFilters: StateFlow<Set<String>> = _activeFilters.asStateFlow()

    val statusCounts: StateFlow<Map<String, Int>> = allRows
        .map { rows -> JOB_STATUS_ORDER.associateWith { status -> rows.count { it.job.status == status } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val jobRows: StateFlow<List<ComercialJobRow>> = combine(allRows, _activeFilters) { rows, filters ->
        if (filters.isEmpty()) rows else rows.filter { it.job.status in filters }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleStatusFilter(status: String) {
        _activeFilters.value = if (status in _activeFilters.value) {
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
        viewModelScope.launch {
            authRepository.logout()
            onLoggedOut()
        }
    }
}
