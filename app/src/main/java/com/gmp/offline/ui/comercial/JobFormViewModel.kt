package com.gmp.offline.ui.comercial

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmp.offline.data.local.entities.StaffEntity
import com.gmp.offline.data.repository.JobsRepository
import com.gmp.offline.data.repository.StaffRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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

// Formas de pago — mismos 3 valores que el <select id="jobPayment"> de la
// web legada (ver index.html), con la misma etiqueta visible.
data class PaymentMethodOption(val value: String, val label: String)

val PAYMENT_METHODS = listOf(
    PaymentMethodOption("usd_efectivo", "USD Efectivo"),
    PaymentMethodOption("usd_zelle", "USD Zelle"),
    PaymentMethodOption("cup_efectivo", "CUP Efectivo"),
)

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
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    private val _uiState = MutableStateFlow<JobFormUiState>(JobFormUiState.Idle)
    val uiState: StateFlow<JobFormUiState> = _uiState.asStateFlow()

    // --- Campos del formulario (réplica 1:1 del modal "Nuevo montaje" de la web) ---
    var clientName = MutableStateFlow("")
    var clientCi = MutableStateFlow("")
    var clientPhone = MutableStateFlow("")
    var address = MutableStateFlow("")
    var latitude = MutableStateFlow("")
    var longitude = MutableStateFlow("")
    var reference = MutableStateFlow("")
    var siteNotes = MutableStateFlow("")
    var description = MutableStateFlow("")
    var price = MutableStateFlow("")
    var paymentMethod = MutableStateFlow(PAYMENT_METHODS.first().value)
    var visitDate = MutableStateFlow("")
    var proposedDate = MutableStateFlow("")
    var selectedClient = MutableStateFlow<StaffEntity?>(null)

    // uuid del cliente ya guardado en el job que se está editando, hasta que
    // se pueda resolver contra `clients` y setear `selectedClient` de verdad.
    var selectedClientUuid: String? = null
        private set

    // Estado del job en edición: si está "cerrado" (terminado/facturado/
    // pago parcial/pagado/cancelado), comercial no puede guardar cambios —
    // misma regla que `openJobEditModal` en la web (jobModalSaveBtn oculto).
    private val closedStatuses = setOf("finished", "invoiced", "partially_paid", "paid", "cancelled")
    var isClosedForEditing: Boolean = false
        private set

    init {
        val uuid = editingJobUuid
        if (uuid != null) {
            viewModelScope.launch {
                val job = jobsRepository.getJob(uuid)
                if (job != null) {
                    clientName.value = job.clientName ?: job.title
                    clientCi.value = job.clientCi.orEmpty()
                    clientPhone.value = job.clientPhone.orEmpty()
                    address.value = job.address.orEmpty()
                    latitude.value = job.latitude?.toString().orEmpty()
                    longitude.value = job.longitude?.toString().orEmpty()
                    reference.value = job.reference.orEmpty()
                    siteNotes.value = job.siteNotes.orEmpty()
                    description.value = job.description.orEmpty()
                    price.value = job.price.orEmpty()
                    paymentMethod.value = job.paymentMethod ?: PAYMENT_METHODS.first().value
                    visitDate.value = job.visitDate.orEmpty()
                    proposedDate.value = job.proposedDate.orEmpty()
                    selectedClientUuid = job.clientUuid
                    isClosedForEditing = job.status in closedStatuses
                }
            }
        }
    }

    fun save() {
        val name = clientName.value.trim()
        val addr = address.value.trim()
        val priceValue = price.value.trim()

        if (name.isBlank() || addr.isBlank() || priceValue.isBlank()) {
            _uiState.value = JobFormUiState.Error("Nombre del cliente, dirección y precio son obligatorios.")
            return
        }
        if (priceValue.toDoubleOrNull() == null) {
            _uiState.value = JobFormUiState.Error("El precio tiene que ser un número.")
            return
        }
        val lat = latitude.value.trim().ifBlank { null }?.toDoubleOrNull()
        val lng = longitude.value.trim().ifBlank { null }?.toDoubleOrNull()
        if ((latitude.value.isNotBlank() && lat == null) || (longitude.value.isNotBlank() && lng == null)) {
            _uiState.value = JobFormUiState.Error("Latitud/longitud tienen que ser números.")
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
                        clientName = name,
                        clientCi = clientCi.value.trim().ifBlank { null },
                        clientPhone = clientPhone.value.trim().ifBlank { null },
                        address = addr,
                        latitude = lat,
                        longitude = lng,
                        reference = reference.value.trim().ifBlank { null },
                        siteNotes = siteNotes.value.trim().ifBlank { null },
                        description = description.value.trim().ifBlank { null },
                        price = priceValue,
                        paymentMethod = paymentMethod.value,
                        visitDate = visitDate.value.trim().ifBlank { null },
                        proposedDate = proposedDate.value.trim().ifBlank { null },
                        clientUuid = clientUuid,
                    )
                    _uiState.value = JobFormUiState.Saved(uuid)
                } else {
                    val newUuid = jobsRepository.createJob(
                        clientName = name,
                        clientCi = clientCi.value.trim().ifBlank { null },
                        clientPhone = clientPhone.value.trim().ifBlank { null },
                        address = addr,
                        latitude = lat,
                        longitude = lng,
                        reference = reference.value.trim().ifBlank { null },
                        siteNotes = siteNotes.value.trim().ifBlank { null },
                        description = description.value.trim().ifBlank { null },
                        price = priceValue,
                        paymentMethod = paymentMethod.value,
                        visitDate = visitDate.value.trim().ifBlank { null },
                        proposedDate = proposedDate.value.trim().ifBlank { null },
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
