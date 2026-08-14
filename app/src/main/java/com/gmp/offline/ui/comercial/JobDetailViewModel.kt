package com.gmp.offline.ui.comercial

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmp.offline.data.local.entities.JobEntity
import com.gmp.offline.data.local.entities.JobPhotoEntity
import com.gmp.offline.data.local.entities.JobWorkerEntity
import com.gmp.offline.data.local.entities.StaffEntity
import com.gmp.offline.data.repository.JobDetailRepository
import com.gmp.offline.data.repository.JobsRepository
import com.gmp.offline.data.repository.PhotoActionResult
import com.gmp.offline.data.repository.StaffRepository
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
    private val staffRepository: StaffRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    private val jobUuid: String = requireNotNull(savedStateHandle["jobUuid"]) {
        "JobDetailViewModel requiere jobUuid en la ruta"
    }

    // La asignación de personal/fecha es "solo admin" según
    // jobsActionsController.js — comercial no ve esa card, aunque reuse
    // esta misma pantalla de detalle.
    val isAdmin: Boolean = sessionManager.role == "admin"

    val job: StateFlow<JobEntity?> = jobsRepository.observeJob(jobUuid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val workers: StateFlow<List<JobWorkerEntity>> = jobDetailRepository.observeWorkers(jobUuid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val staff: StateFlow<List<StaffEntity>> = staffRepository.observeStaff()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Personal asignable al montaje: admin o trabajador, nunca comercial
    // (pedido explícito), y solo activos.
    val assignableStaff: StateFlow<List<StaffEntity>> = staff
        .map { list -> list.filter { it.active && (it.role == "admin" || it.role == "trabajador") } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val clientName: StateFlow<String?> = combine(job, staff) { j, s ->
        j?.clientUuid?.let { uuid -> s.find { it.uuid == uuid }?.fullName }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Solo se permite una foto por montaje — se observa como valor único
    // (null si todavía no se agregó ninguna), no como lista.
    val photo: StateFlow<JobPhotoEntity?> = jobDetailRepository.observePhotos(jobUuid)
        .combine(job) { photos, _ -> photos.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _photoState = MutableStateFlow<PhotoUiState>(PhotoUiState.Idle)
    val photoState: StateFlow<PhotoUiState> = _photoState.asStateFlow()

    /** La imagen ya llega comprimida — ver [JobDetailRepository.addPhoto]. */
    fun addPhoto(imageUri: Uri) {
        viewModelScope.launch {
            _photoState.value = PhotoUiState.Uploading
            when (val result = jobDetailRepository.addPhoto(jobUuid, imageUri)) {
                is PhotoActionResult.Success -> _photoState.value = PhotoUiState.Idle
                is PhotoActionResult.Error -> _photoState.value = PhotoUiState.Error(result.message)
            }
        }
    }

    fun retryPhotoUpload() {
        viewModelScope.launch {
            _photoState.value = PhotoUiState.Uploading
            when (val result = jobDetailRepository.retryPhotoUpload(jobUuid)) {
                is PhotoActionResult.Success -> _photoState.value = PhotoUiState.Idle
                is PhotoActionResult.Error -> _photoState.value = PhotoUiState.Error(result.message)
            }
        }
    }

    fun removePhoto() {
        viewModelScope.launch {
            jobDetailRepository.removePhoto(jobUuid)
        }
    }

    fun clearPhotoError() {
        _photoState.value = PhotoUiState.Idle
    }

    /**
     * Cancelar solo tiene sentido (según jobsActionsController.js) desde
     * "pending"/"assigned" — se deshabilita en la UI para otros estados,
     * pero igual se deja la llamada acá simple: si el servidor la rechaza
     * con 409, el próximo /sync corrige el estado local.
     */
    fun cancelJob() {
        viewModelScope.launch {
            jobsRepository.cancelJob(jobUuid)
        }
    }

    /**
     * Asigna o quita a un trabajador/admin del montaje. Se aplica al toque
     * del checkbox en la UI (sin botón "Guardar" aparte), siguiendo el
     * mismo criterio de "sync inmediato" ya usado en el resto de la app.
     */
    fun toggleWorker(workerUuid: String, assign: Boolean) {
        viewModelScope.launch {
            jobDetailRepository.toggleWorkerAssignment(jobUuid, workerUuid, assign)
        }
    }

    /** Fija la fecha oficial confirmada del montaje ("yyyy-MM-dd" o null). */
    fun setScheduledDate(isoDate: String?) {
        viewModelScope.launch {
            jobDetailRepository.setScheduledDate(jobUuid, isoDate)
        }
    }
}
