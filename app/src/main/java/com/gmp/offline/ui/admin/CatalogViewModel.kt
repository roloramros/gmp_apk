package com.gmp.offline.ui.admin

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmp.offline.data.local.entities.CatalogKitEntity
import com.gmp.offline.data.local.entities.CatalogKitPhotoEntity
import com.gmp.offline.data.repository.CatalogKitPhotoRepository
import com.gmp.offline.data.repository.CatalogKitsRepository
import com.gmp.offline.data.repository.PhotoActionResult
import com.gmp.offline.ui.comercial.PhotoUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Voltajes de trabajo típicos de los kits — texto libre en el backend, pero
// se ofrece como dropdown para no tipear a mano cada vez (mismo espíritu que
// MATERIAL_UNITS en MaterialsViewModel).
val KIT_VOLTAGES = listOf("110", "110/220", "220")

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val catalogKitsRepository: CatalogKitsRepository,
    private val catalogKitPhotoRepository: CatalogKitPhotoRepository,
) : ViewModel() {

    val kits: StateFlow<List<CatalogKitEntity>> = catalogKitsRepository.observeKits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _photoState = MutableStateFlow<PhotoUiState>(PhotoUiState.Idle)
    val photoState: StateFlow<PhotoUiState> = _photoState.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    fun photosForKit(kitUuid: String): Flow<List<CatalogKitPhotoEntity>> =
        catalogKitPhotoRepository.observeByKit(kitUuid)

    /**
     * Crea o actualiza según si `editingUuid` es null. Validación: nombre
     * obligatorio; el resto de las specs son opcionales (a diferencia de
     * materiales, no todos los kits tienen todos los campos cargados).
     */
    fun save(
        editingUuid: String?,
        name: String,
        powerKw: String,
        voltage: String,
        batteryKwh: String,
        panelsCount: String,
        priceUsd: String,
        description: String,
        active: Boolean,
        onSaved: () -> Unit,
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            _errorMessage.value = "El nombre del kit es obligatorio."
            return
        }
        val panelsCountInt = panelsCount.trim().takeIf { it.isNotBlank() }?.toIntOrNull()
        if (panelsCount.isNotBlank() && panelsCountInt == null) {
            _errorMessage.value = "La cantidad de paneles tiene que ser un número entero."
            return
        }
        if (powerKw.isNotBlank() && powerKw.trim().toDoubleOrNull() == null) {
            _errorMessage.value = "La potencia (kW) tiene que ser un número."
            return
        }
        if (batteryKwh.isNotBlank() && batteryKwh.trim().toDoubleOrNull() == null) {
            _errorMessage.value = "La batería (kWh) tiene que ser un número."
            return
        }
        if (priceUsd.isNotBlank() && priceUsd.trim().toDoubleOrNull() == null) {
            _errorMessage.value = "El precio tiene que ser un número."
            return
        }

        viewModelScope.launch {
            try {
                val powerKwTrimmed = powerKw.trim().ifBlank { null }
                val voltageTrimmed = voltage.trim().ifBlank { null }
                val batteryKwhTrimmed = batteryKwh.trim().ifBlank { null }
                val priceUsdTrimmed = priceUsd.trim().ifBlank { null }
                val descriptionTrimmed = description.trim().ifBlank { null }

                if (editingUuid != null) {
                    catalogKitsRepository.updateKit(
                        uuid = editingUuid, name = trimmedName, powerKw = powerKwTrimmed,
                        voltage = voltageTrimmed, batteryKwh = batteryKwhTrimmed,
                        panelsCount = panelsCountInt, priceUsd = priceUsdTrimmed,
                        description = descriptionTrimmed, active = active,
                    )
                } else {
                    catalogKitsRepository.createKit(
                        name = trimmedName, powerKw = powerKwTrimmed, voltage = voltageTrimmed,
                        batteryKwh = batteryKwhTrimmed, panelsCount = panelsCountInt,
                        priceUsd = priceUsdTrimmed, description = descriptionTrimmed, active = active,
                    )
                }
                onSaved()
            } catch (e: Exception) {
                _errorMessage.value = "No se pudo guardar: ${e.message}"
            }
        }
    }

    fun delete(uuid: String) {
        viewModelScope.launch {
            try {
                catalogKitsRepository.deleteKit(uuid)
            } catch (e: Exception) {
                _errorMessage.value = "No se pudo eliminar: ${e.message}"
            }
        }
    }

    fun addPhoto(kitUuid: String, uri: Uri) {
        viewModelScope.launch {
            _photoState.value = PhotoUiState.Uploading
            when (val result = catalogKitPhotoRepository.addPhoto(kitUuid, uri)) {
                is PhotoActionResult.Success -> _photoState.value = PhotoUiState.Idle
                is PhotoActionResult.Error -> _photoState.value = PhotoUiState.Error(result.message)
            }
        }
    }

    fun retryPhoto(kitUuid: String, photoUuid: String) {
        viewModelScope.launch {
            when (val result = catalogKitPhotoRepository.retryPhoto(kitUuid, photoUuid)) {
                is PhotoActionResult.Success -> Unit
                is PhotoActionResult.Error -> _photoState.value = PhotoUiState.Error(result.message)
            }
        }
    }

    fun removePhoto(kitUuid: String, photoUuid: String) {
        viewModelScope.launch {
            catalogKitPhotoRepository.removePhoto(kitUuid, photoUuid)
        }
    }

    fun dismissPhotoError() {
        _photoState.value = PhotoUiState.Idle
    }
}
