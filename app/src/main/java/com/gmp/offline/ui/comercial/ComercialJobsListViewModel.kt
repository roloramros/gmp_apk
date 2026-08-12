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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

@HiltViewModel
class ComercialJobsListViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncScheduler: SyncScheduler,
    jobsRepository: JobsRepository,
    staffRepository: StaffRepository,
    outboxRepository: OutboxRepository,
) : ViewModel() {

    val currentFullName: String? get() = authRepository.currentFullName

    val jobRows: StateFlow<List<ComercialJobRow>> = combine(
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
                    clientName = job.clientUuid?.let { staffByUuid[it]?.fullName },
                    pendingSync = pending.any { it.endpointPath.contains(job.uuid) },
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun syncNow() {
        syncScheduler.triggerImmediateSync()
    }

    fun logout(onLoggedOut: () -> Unit) {
        syncScheduler.cancelAll()
        authRepository.logout()
        onLoggedOut()
    }
}
