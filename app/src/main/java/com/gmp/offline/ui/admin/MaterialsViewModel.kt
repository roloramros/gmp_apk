package com.gmp.offline.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmp.offline.data.local.entities.MaterialEntity
import com.gmp.offline.data.repository.MaterialsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Unidades del dropdown del modal — mismas dos opciones que trae el
// <select id="modalUnit"> de la web legada ("unidad" / "metro").
val MATERIAL_UNITS = listOf("unidad", "metro")

@HiltViewModel
class MaterialsViewModel @Inject constructor(
    private val materialsRepository: MaterialsRepository,
) : ViewModel() {

    val materials: StateFlow<List<MaterialEntity>> = materialsRepository.observeMaterials()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Crea o actualiza según si `editingUuid` es null, replicando la
     * validación de `saveMaterial()` de la web: nombre y precio son
     * obligatorios, precio debe ser numérico.
     */
    fun save(editingUuid: String?, name: String, unit: String, price: String, onSaved: () -> Unit) {
        val trimmedName = name.trim()
        val trimmedPrice = price.trim()

        if (trimmedName.isBlank() || trimmedPrice.isBlank()) {
            _errorMessage.value = "Completá nombre y precio."
            return
        }
        if (trimmedPrice.toDoubleOrNull() == null) {
            _errorMessage.value = "El precio tiene que ser un número."
            return
        }

        viewModelScope.launch {
            try {
                if (editingUuid != null) {
                    materialsRepository.updateMaterial(editingUuid, trimmedName, unit, trimmedPrice)
                } else {
                    materialsRepository.createMaterial(trimmedName, unit, trimmedPrice)
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
                materialsRepository.deleteMaterial(uuid)
            } catch (e: Exception) {
                _errorMessage.value = "No se pudo eliminar: ${e.message}"
            }
        }
    }
}
