package com.gmp.offline.ui.comercial

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmp.offline.data.local.entities.StaffEntity
import com.gmp.offline.data.repository.JobsRepository
import com.gmp.offline.data.repository.StaffRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface JobFormUiState {
    data object Idle : JobFormUiState
    data object Saving : JobFormUiState
    data class Saved(val jobUuid: String) : JobFormUiState
    data class Error(val message: String) : JobFormUiState
}

@HiltViewModel
class JobFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val jobsRepository: JobsRepository,
    staffRepository: StaffRepository,
) : ViewModel() {

    // Si viene un jobUuid en la ruta, estamos editando; si no, creando.
    val editingJobUuid: String? = savedStateHandle.get<String>("jobUuid")?.takeIf { it.isNotBlank() }
    val isEditing: Boolean get() = editingJobUuid != null

    val clients: StateFlow<List<StaffEntity>> =
        staffRepository.observeClients().stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    private val _uiState = MutableStateFlow<JobFormUiState>(JobFormUiState.Idle)
    val uiState: StateFlow<JobFormUiState> = _uiState.asStateFlow()

    var title = MutableStateFlow("")
    var description = MutableStateFlow("")
    var address = MutableStateFlow("")
    var selectedClient = MutableStateFlow<StaffEntity?>(null)

    init {
        val uuid = editingJobUuid
        if (uuid != null) {
            viewModelScope.launch {
                val job = jobsRepository.getJob(uuid)
                if (job != null) {
                    title.value = job.title
                    description.value = job.description.orEmpty()
                    address.value = job.address.orEmpty()
                    // El cliente asignado se resuelve contra la lista de
                    // `clients` una vez que también terminó de cargar (ver
                    // JobFormScreen: usa selectedClientUuid como fallback
                    // hasta que el objeto completo esté disponible).
                    selectedClientUuid = job.clientUuid
                }
            }
        }
    }

    // uuid del cliente ya guardado en el job que se está editando, hasta que
    // se pueda resolver contra `clients` y setear `selectedClient` de verdad.
    var selectedClientUuid: String? = null
        private set

    fun save() {
        val currentTitle = title.value.trim()
        if (currentTitle.isBlank()) {
            _uiState.value = JobFormUiState.Error("El título es obligatorio.")
            return
        }

        viewModelScope.launch {
            _uiState.value = JobFormUiState.Saving
            try {
                val clientUuid = selectedClient.value?.uuid ?: selectedClientUuid
                val uuid = editingJobUuid
                if (uuid != null) {
                    jobsRepository.updateJob(
                        jobUuid = uuid,
                        title = currentTitle,
                        description = description.value.trim().ifBlank { null },
                        address = address.value.trim().ifBlank { null },
                        clientUuid = clientUuid,
                    )
                    _uiState.value = JobFormUiState.Saved(uuid)
                } else {
                    val newUuid = jobsRepository.createJob(
                        title = currentTitle,
                        description = description.value.trim().ifBlank { null },
                        address = address.value.trim().ifBlank { null },
                        clientUuid = clientUuid,
                    )
                    _uiState.value = JobFormUiState.Saved(newUuid)
                }
            } catch (e: Exception) {
                _uiState.value = JobFormUiState.Error("No se pudo guardar: ${e.message}")
            }
        }
    }
}
