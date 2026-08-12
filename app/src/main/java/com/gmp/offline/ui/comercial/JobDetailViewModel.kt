package com.gmp.offline.ui.comercial

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmp.offline.data.local.entities.JobEntity
import com.gmp.offline.data.local.entities.JobMaterialEntity
import com.gmp.offline.data.local.entities.JobWorkerEntity
import com.gmp.offline.data.local.entities.MaterialEntity
import com.gmp.offline.data.local.entities.StaffEntity
import com.gmp.offline.data.repository.JobDetailRepository
import com.gmp.offline.data.repository.JobsRepository
import com.gmp.offline.data.repository.MaterialsRepository
import com.gmp.offline.data.repository.StaffRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Fila de material ya enriquecida con el nombre a mostrar (del catálogo o
// el texto libre) y el subtotal calculado, para que la pantalla no tenga
// que resolver nada — solo pintar.
data class JobMaterialRow(
    val item: JobMaterialEntity,
    val displayName: String,
    val subtotal: Double?,
)

sealed interface AddMaterialUiState {
    data object Idle : AddMaterialUiState
    data object Saving : AddMaterialUiState
    data class Error(val message: String) : AddMaterialUiState
}

@HiltViewModel
class JobDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val jobsRepository: JobsRepository,
    private val jobDetailRepository: JobDetailRepository,
    staffRepository: StaffRepository,
    materialsRepository: MaterialsRepository,
) : ViewModel() {

    private val jobUuid: String = requireNotNull(savedStateHandle["jobUuid"]) {
        "JobDetailViewModel requiere jobUuid en la ruta"
    }

    val job: StateFlow<JobEntity?> = jobsRepository.observeJob(jobUuid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val workers: StateFlow<List<JobWorkerEntity>> = jobDetailRepository.observeWorkers(jobUuid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Catálogo completo, para el selector "de catálogo" al agregar un material.
    val catalog: StateFlow<List<MaterialEntity>> = materialsRepository.observeMaterials()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val staff: StateFlow<List<StaffEntity>> = staffRepository.observeStaff()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val clientName: StateFlow<String?> = combine(job, staff) { j, s ->
        j?.clientUuid?.let { uuid -> s.find { it.uuid == uuid }?.fullName }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val materialRows: StateFlow<List<JobMaterialRow>> = combine(
        jobDetailRepository.observeMaterials(jobUuid),
        catalog,
    ) { materials, catalogList ->
        val catalogByUuid = catalogList.associateBy { it.uuid }
        materials.map { item ->
            val catalogMaterial = item.materialUuid?.let { catalogByUuid[it] }
            val displayName = catalogMaterial?.name ?: item.freeTextDescription ?: "Material"
            val subtotal = item.unitPrice?.toDoubleOrNull()?.let { price ->
                item.quantity.toDoubleOrNull()?.let { qty -> price * qty }
            }
            JobMaterialRow(item = item, displayName = displayName, subtotal = subtotal)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _addMaterialState = MutableStateFlow<AddMaterialUiState>(AddMaterialUiState.Idle)
    val addMaterialState: StateFlow<AddMaterialUiState> = _addMaterialState.asStateFlow()

    /**
     * `materialUuid` y `freeText` son mutuamente excluyentes: la pantalla
     * decide cuál mandar según si el usuario eligió del catálogo o escribió
     * texto libre. `unitPrice` puede venir vacío — se hereda el
     * `defaultPrice` del catálogo si corresponde (ver JobDetailRepository).
     */
    fun addMaterial(materialUuid: String?, freeText: String?, quantity: String, unitPrice: String?) {
        if (materialUuid == null && freeText.isNullOrBlank()) {
            _addMaterialState.value = AddMaterialUiState.Error("Elegí un material del catálogo o escribí una descripción.")
            return
        }
        val qty = quantity.trim()
        if (qty.toDoubleOrNull() == null || qty.toDouble() <= 0) {
            _addMaterialState.value = AddMaterialUiState.Error("La cantidad tiene que ser un número mayor a 0.")
            return
        }
        viewModelScope.launch {
            _addMaterialState.value = AddMaterialUiState.Saving
            try {
                jobDetailRepository.addMaterial(
                    jobUuid = jobUuid,
                    materialUuid = materialUuid,
                    freeTextDescription = if (materialUuid == null) freeText?.trim() else null,
                    quantity = qty,
                    unitPrice = unitPrice?.trim()?.ifBlank { null },
                )
                _addMaterialState.value = AddMaterialUiState.Idle
            } catch (e: Exception) {
                _addMaterialState.value = AddMaterialUiState.Error("No se pudo agregar: ${e.message}")
            }
        }
    }

    fun removeMaterial(jobMaterialUuid: String) {
        viewModelScope.launch {
            jobDetailRepository.removeMaterial(jobUuid, jobMaterialUuid)
        }
    }

    fun clearAddMaterialError() {
        _addMaterialState.value = AddMaterialUiState.Idle
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
}
