package com.gmp.offline.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmp.offline.data.local.dao.JobDao
import com.gmp.offline.data.local.dao.JobWorkerDao
import com.gmp.offline.data.local.entities.JobEntity
import com.gmp.offline.data.local.entities.JobWorkerEntity
import com.gmp.offline.data.local.entities.StaffEntity
import com.gmp.offline.data.repository.StaffRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Mismo orden que <select id="staffRole"> en la web (trabajador primero,
// que es también el valor por defecto del formulario).
data class RoleOption(val value: String, val label: String)
val STAFF_ROLES = listOf(
    RoleOption("trabajador", "Trabajador"),
    RoleOption("comercial", "Comercial"),
    RoleOption("admin", "Admin"),
)

@HiltViewModel
class StaffViewModel @Inject constructor(
    private val staffRepository: StaffRepository,
    jobDao: JobDao,
    jobWorkerDao: JobWorkerDao,
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Solo personal (admin/comercial/trabajador) — se excluye "cliente" acá
    // porque esta pestaña replica la sección "Personal" de la web, que no
    // administra clientes (esos no tienen login propio en este dominio).
    val staff: StateFlow<List<StaffEntity>> = staffRepository.observeStaff()
        .map { list -> list.filter { it.role != "cliente" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Historial local offline-first. La relación job_workers es la fuente de
    // verdad para saber en qué trabajos participó cada miembro del personal.
    // Se exponen las tablas completas porque el filtrado por persona/rango y
    // la resolución de compañeros son operaciones de presentación pequeñas.
    val jobs: StateFlow<List<JobEntity>> = jobDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val jobWorkers: StateFlow<List<JobWorkerEntity>> = jobWorkerDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Crea un usuario de personal. Requiere red (ver nota en
     * StaffRepository.createStaff) — si falla por falta de conexión, el
     * error se lo decimos tal cual al usuario en vez de fingir que quedó
     * encolado, porque acá no hay outbox real cubriendo esta acción.
     */
    fun createStaff(fullName: String, phoneNumber: String, password: String, role: String, onSaved: () -> Unit) {
        val trimmedName = fullName.trim()
        val trimmedPhoneNumber = phoneNumber.trim()

        if (trimmedName.isBlank() || trimmedPhoneNumber.isBlank() || password.isBlank()) {
            _errorMessage.value = "Completá nombre, teléfono y contraseña."
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            _errorMessage.value = null
            try {
                staffRepository.createStaff(
                    fullName = trimmedName,
                    phone = trimmedPhoneNumber,
                    password = password,
                    role = role,
                )
                onSaved()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "No se pudo crear el usuario."
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun deactivate(uuid: String) {
        viewModelScope.launch {
            try {
                staffRepository.deactivateStaffMember(uuid)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "No se pudo eliminar el usuario."
            }
        }
    }
}
