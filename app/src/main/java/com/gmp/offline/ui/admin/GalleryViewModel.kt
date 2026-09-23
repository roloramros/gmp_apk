package com.gmp.offline.ui.admin

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmp.offline.data.local.entities.GalleryPhotoEntity
import com.gmp.offline.data.repository.GalleryRepository
import com.gmp.offline.data.repository.PhotoActionResult
import com.gmp.offline.ui.comercial.PhotoUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val galleryRepository: GalleryRepository,
) : ViewModel() {

    val photos: StateFlow<List<GalleryPhotoEntity>> = galleryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _photoState = MutableStateFlow<PhotoUiState>(PhotoUiState.Idle)
    val photoState: StateFlow<PhotoUiState> = _photoState.asStateFlow()

    fun addPhoto(uri: Uri, caption: String?) {
        viewModelScope.launch {
            _photoState.value = PhotoUiState.Uploading
            when (val result = galleryRepository.addPhoto(uri, caption)) {
                is PhotoActionResult.Success -> _photoState.value = PhotoUiState.Idle
                is PhotoActionResult.Error -> _photoState.value = PhotoUiState.Error(result.message)
            }
        }
    }

    fun retryPhoto(uuid: String) {
        viewModelScope.launch {
            when (val result = galleryRepository.retryPhoto(uuid)) {
                is PhotoActionResult.Success -> Unit
                is PhotoActionResult.Error -> _photoState.value = PhotoUiState.Error(result.message)
            }
        }
    }

    fun updateCaption(uuid: String, caption: String?) {
        viewModelScope.launch { galleryRepository.updateCaption(uuid, caption) }
    }

    fun removePhoto(uuid: String) {
        viewModelScope.launch { galleryRepository.removePhoto(uuid) }
    }

    fun dismissPhotoError() { _photoState.value = PhotoUiState.Idle }
}
