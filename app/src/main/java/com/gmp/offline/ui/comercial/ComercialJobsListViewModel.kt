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
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ComercialJobRow(
    val job: JobEntity,
    val clientName: String?,
    val pendingSync: Boolean,
)

val JOB_STATUS_ORDER = listOf(
    "pending", "assigned", "in_progress", "finished",
    "invoiced", "partially_paid", "paid", "cancelled",
)

@HiltViewModel
class ComercialJobsListViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncScheduler: SyncScheduler,
    private val jobsRepository: JobsRepository,
    staffRepository: StaffRepository,
    outboxRepository: OutboxRepository,
) : ViewModel() {

    val currentFullName: String? get() = authRepository.currentFullName
    val currentCompanyName: String? get() = authRepository.currentCompanyName

    private val allRows: StateFlow<List<ComercialJobRow>> = combine(
        jobsRepository.observeJobs(),
        staffRepository.observeStaff(),
        outboxRepository.observePending(),
    ) { jobs, staff, pending ->
        val staffByUuid = staff.associateBy { it.uuid }
        jobs
            .sortedWith(
                compareByDescending<JobEntity> { it.scheduledAt ?: it.proposedDate ?: "" }
                    .thenByDescending { it.updatedAt },
            )
            .map { job ->
                ComercialJobRow(
                    job = job,
                    clientName = job.clientName ?: job.clientUuid?.let { staffByUuid[it]?.fullName },
                    pendingSync = pending.any { operation ->
                        if (operation.status != "pending") return@any false
                        operation.endpointPath.contains(job.uuid) ||
                            (operation.endpointPath == "/jobs" &&
                                operation.httpMethod == "POST" &&
                                operation.payloadJson.contains(job.uuid))
                    },
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _activeFilters = MutableStateFlow<Set<String>>(emptySet())
    val activeFilters: StateFlow<Set<String>> = _activeFilters.asStateFlow()

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

    fun regularizeJob(jobUuid: String, targetStatus: String) {
        viewModelScope.launch {
            jobsRepository.regularizeJob(jobUuid, targetStatus)
            syncScheduler.triggerImmediateSync()
        }
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
