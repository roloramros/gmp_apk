package com.gmp.offline.ui.comercial

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmp.offline.data.local.entities.JobEntity
import com.gmp.offline.data.local.entities.JobMaterialEntity
import com.gmp.offline.data.local.entities.JobPhotoEntity
import com.gmp.offline.data.local.entities.JobWorkerEntity
import com.gmp.offline.data.local.entities.MaterialEntity
import com.gmp.offline.data.local.entities.StaffEntity
import com.gmp.offline.data.repository.AssignmentRepository
import com.gmp.offline.data.repository.JobDetailRepository
import com.gmp.offline.data.repository.JobsRepository
import com.gmp.offline.data.repository.PhotoActionResult
import com.gmp.offline.data.repository.StaffRepository
import com.gmp.offline.data.repository.WorkerJobRepository
import com.gmp.offline.data.session.SessionManager
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

sealed interface PhotoUiState {
    data object Idle : PhotoUiState
    data object Uploading : PhotoUiState
    data class Error(val message: String) : PhotoUiState
}

@HiltViewModel
class JobDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val jobsRepository: JobsRepository,
    private val jobDetailRepository: JobDetailRepository,
    private val assignmentRepository: AssignmentRepository,
    private val staffRepository: StaffRepository,
    private val workerJobRepository: WorkerJobRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    private val jobUuid: String = requireNotNull(savedStateHandle["jobUuid"]) {
        "JobDetailViewModel requiere jobUuid en la ruta"
    }

    val isAdmin: Boolean = sessionManager.role == "admin"
    val isWorker: Boolean = sessionManager.role == "trabajador"
    val canEditJob: Boolean = sessionManager.role == "admin" || sessionManager.role == "comercial"
    val canCancelJob: Boolean = canEditJob
    val canManagePhoto: Boolean = !isWorker

    val job: StateFlow<JobEntity?> = jobsRepository.observeJob(jobUuid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val workers: StateFlow<List<JobWorkerEntity>> = jobDetailRepository.observeWorkers(jobUuid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val staff: StateFlow<List<StaffEntity>> = staffRepository.observeStaff()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val assignedWorkerNames: StateFlow<List<String>> = combine(workers, staff) { assigned, people ->
        val namesByUuid = people.associate { it.uuid to it.fullName }
        assigned.mapNotNull { namesByUuid[it.userUuid] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val assignableStaff: StateFlow<List<StaffEntity>> = staff
        .map { list -> list.filter { it.active && (it.role == "admin" || it.role == "trabajador") } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val clientName: StateFlow<String?> = combine(job, staff) { j, s ->
        j?.clientUuid?.let { uuid -> s.find { it.uuid == uuid }?.fullName }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val jobMaterials: StateFlow<List<JobMaterialEntity>> = workerJobRepository.observeJobMaterials(jobUuid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val materialCatalog: StateFlow<List<MaterialEntity>> = workerJobRepository.observeCatalog()
        .map { items -> items.sortedBy { it.name.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val photo: StateFlow<JobPhotoEntity?> = jobDetailRepository.observePhotos(jobUuid)
        .combine(job) { photos, _ -> photos.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _photoState = MutableStateFlow<PhotoUiState>(PhotoUiState.Idle)
    val photoState: StateFlow<PhotoUiState> = _photoState.asStateFlow()

    fun startWorkerJob() {
        if (!isWorker) return
        viewModelScope.launch { workerJobRepository.startJob(jobUuid) }
    }

    fun finishWorkerJob() {
        if (!isWorker) return
        viewModelScope.launch { workerJobRepository.finishJob(jobUuid) }
    }

    fun addWorkerMaterial(materialUuid: String, quantity: String) {
        if (!isWorker) return
        viewModelScope.launch { workerJobRepository.addCatalogMaterial(jobUuid, materialUuid, quantity) }
    }

    fun addPhoto(imageUri: Uri) {
        if (!canManagePhoto) return
        viewModelScope.launch {
            _photoState.value = PhotoUiState.Uploading
            when (val result = jobDetailRepository.addPhoto(jobUuid, imageUri)) {
                is PhotoActionResult.Success -> _photoState.value = PhotoUiState.Idle
                is PhotoActionResult.Error -> _photoState.value = PhotoUiState.Error(result.message)
            }
        }
    }

    fun retryPhotoUpload() {
        if (!canManagePhoto) return
        viewModelScope.launch {
            _photoState.value = PhotoUiState.Uploading
            when (val result = jobDetailRepository.retryPhotoUpload(jobUuid)) {
                is PhotoActionResult.Success -> _photoState.value = PhotoUiState.Idle
                is PhotoActionResult.Error -> _photoState.value = PhotoUiState.Error(result.message)
            }
        }
    }

    fun removePhoto() {
        if (!canManagePhoto) return
        viewModelScope.launch { jobDetailRepository.removePhoto(jobUuid) }
    }

    fun clearPhotoError() {
        _photoState.value = PhotoUiState.Idle
    }

    fun cancelJob() {
        if (!canCancelJob) return
        viewModelScope.launch { jobsRepository.cancelJob(jobUuid) }
    }

    fun confirmAssignment(scheduledDate: String?, selectedWorkerUuids: Set<String>) {
        if (!isAdmin) return
        viewModelScope.launch {
            assignmentRepository.confirmAssignment(jobUuid, scheduledDate, selectedWorkerUuids)
        }
    }
}
