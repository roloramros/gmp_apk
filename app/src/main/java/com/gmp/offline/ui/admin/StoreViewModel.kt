package com.gmp.offline.ui.admin

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmp.offline.data.local.entities.CatalogProductEntity
import com.gmp.offline.data.local.entities.CatalogProductPhotoEntity
import com.gmp.offline.data.repository.CatalogProductPhotoRepository
import com.gmp.offline.data.repository.CatalogProductsRepository
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

@HiltViewModel
class StoreViewModel @Inject constructor(
    private val catalogProductsRepository: CatalogProductsRepository,
    private val catalogProductPhotoRepository: CatalogProductPhotoRepository,
) : ViewModel() {

    val products: StateFlow<List<CatalogProductEntity>> = catalogProductsRepository.observeProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _photoState = MutableStateFlow<PhotoUiState>(PhotoUiState.Idle)
    val photoState: StateFlow<PhotoUiState> = _photoState.asStateFlow()

    fun clearError() { _errorMessage.value = null }

    fun photosForProduct(productUuid: String): Flow<List<CatalogProductPhotoEntity>> =
        catalogProductPhotoRepository.observeByProduct(productUuid)

    fun save(
        editingUuid: String?,
        name: String,
        priceUsd: String,
        description: String,
        inStock: Boolean,
        active: Boolean,
        onSaved: () -> Unit,
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            _errorMessage.value = "El nombre del producto es obligatorio."
            return
        }
        if (priceUsd.isNotBlank() && priceUsd.trim().toDoubleOrNull() == null) {
            _errorMessage.value = "El precio tiene que ser un número."
            return
        }

        viewModelScope.launch {
            try {
                val priceUsdTrimmed = priceUsd.trim().ifBlank { null }
                val descriptionTrimmed = description.trim().ifBlank { null }

                if (editingUuid != null) {
                    catalogProductsRepository.updateProduct(editingUuid, trimmedName, priceUsdTrimmed, descriptionTrimmed, inStock, active)
                } else {
                    catalogProductsRepository.createProduct(trimmedName, priceUsdTrimmed, descriptionTrimmed, inStock, active)
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
                catalogProductsRepository.deleteProduct(uuid)
            } catch (e: Exception) {
                _errorMessage.value = "No se pudo eliminar: ${e.message}"
            }
        }
    }

    fun addPhoto(productUuid: String, uri: Uri) {
        viewModelScope.launch {
            _photoState.value = PhotoUiState.Uploading
            when (val result = catalogProductPhotoRepository.addPhoto(productUuid, uri)) {
                is PhotoActionResult.Success -> _photoState.value = PhotoUiState.Idle
                is PhotoActionResult.Error -> _photoState.value = PhotoUiState.Error(result.message)
            }
        }
    }

    fun retryPhoto(productUuid: String, photoUuid: String) {
        viewModelScope.launch {
            when (val result = catalogProductPhotoRepository.retryPhoto(productUuid, photoUuid)) {
                is PhotoActionResult.Success -> Unit
                is PhotoActionResult.Error -> _photoState.value = PhotoUiState.Error(result.message)
            }
        }
    }

    fun removePhoto(productUuid: String, photoUuid: String) {
        viewModelScope.launch { catalogProductPhotoRepository.removePhoto(productUuid, photoUuid) }
    }

    fun dismissPhotoError() { _photoState.value = PhotoUiState.Idle }
}
