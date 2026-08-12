package com.gmp.offline.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmp.offline.data.remote.dto.CompanyDto
import com.gmp.offline.data.repository.AuthRepository
import com.gmp.offline.sync.SyncEngine
import com.gmp.offline.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object LoadingCompanies : LoginUiState
    data object LoggingIn : LoginUiState
    data class Error(val message: String) : LoginUiState
    // El destino final (qué pantalla mostrar por rol) lo decide el NavGraph
    // leyendo AuthRepository.currentRole una vez que success = true.
    data object Success : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncEngine: SyncEngine,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _companies = MutableStateFlow<List<CompanyDto>>(emptyList())
    val companies: StateFlow<List<CompanyDto>> = _companies.asStateFlow()

    init {
        loadCompanies()
    }

    fun loadCompanies() {
        viewModelScope.launch {
            _uiState.value = LoginUiState.LoadingCompanies
            try {
                _companies.value = authRepository.listCompanies()
                _uiState.value = LoginUiState.Idle
            } catch (e: Exception) {
                // No bloqueamos el login por esto: si falla, el usuario puede
                // reintentar con el botón de recarga; el listado es solo
                // conveniencia, no es estrictamente necesario para loguearse
                // si en algún momento se agrega un campo manual de fallback.
                _uiState.value = LoginUiState.Error("No se pudo cargar la lista de empresas: ${e.message}")
            }
        }
    }

    fun login(companyId: String, phone: String, password: String) {
        val companyIdInt = companyId.toIntOrNull()
        if (companyIdInt == null) {
            _uiState.value = LoginUiState.Error("Elegí una empresa de la lista.")
            return
        }
        if (phone.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("Completá teléfono y contraseña.")
            return
        }
        viewModelScope.launch {
            _uiState.value = LoginUiState.LoggingIn
            try {
                authRepository.login(companyIdInt, phone, password)
                // Full dump inicial + arranque del sync periódico, igual que
                // hacía DebugViewModel.loginAndSync en Fase 4/5.
                syncEngine.pull(forceFullResync = true)
                syncScheduler.schedulePeriodic()
                _uiState.value = LoginUiState.Success
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error("No se pudo iniciar sesión: ${e.message}")
            }
        }
    }
}
