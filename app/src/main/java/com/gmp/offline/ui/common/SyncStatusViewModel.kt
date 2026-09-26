package com.gmp.offline.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmp.offline.sync.SyncStatusRepository
import com.gmp.offline.sync.SyncUiStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// Existe solo para que GmpNavigationDrawer pueda leer el estado de sync sin
// que cada pantalla (Admin/Comercial/Trabajador) tenga que recibirlo y
// pasarlo a mano — el drawer lo pide con hiltViewModel() internamente. El
// repositorio de fondo (SyncStatusRepository) es el mismo para los 3 roles,
// así que da lo mismo qué ViewModel de pantalla haya disparado el sync.
@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    syncStatusRepository: SyncStatusRepository,
) : ViewModel() {

    val status: StateFlow<SyncUiStatus> = syncStatusRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), syncStatusRepository.state.value)

    val pendingCount: StateFlow<Int> = syncStatusRepository.pendingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
