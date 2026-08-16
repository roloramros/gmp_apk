package com.gmp.offline.data.repository

import com.gmp.offline.data.local.GmpDatabase
import com.gmp.offline.data.remote.ApiService
import com.gmp.offline.data.remote.dto.CompanyDto
import com.gmp.offline.data.remote.dto.LoginRequest
import com.gmp.offline.data.session.SessionManager
import com.gmp.offline.data.session.SyncCursorStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val sessionManager: SessionManager,
    private val syncCursorStore: SyncCursorStore,
    private val database: GmpDatabase,
    private val deviceTokenRepository: DeviceTokenRepository,
) {
    suspend fun login(companyId: Int, phone: String, password: String, companyName: String? = null) {
        val response = apiService.login(LoginRequest(companyId, phone, password))

        wipeLocalData()

        sessionManager.token = response.token
        sessionManager.userUuid = response.user.uuid
        sessionManager.role = response.user.role
        sessionManager.fullName = response.user.fullName
        sessionManager.companyId = response.user.companyId
        sessionManager.companyName = companyName

        // El registro es best-effort: DeviceTokenRepository captura sus propios
        // errores para que una falla de FCM no convierta un login válido en error.
        deviceTokenRepository.registerCurrentToken()
    }

    suspend fun listCompanies(): List<CompanyDto> = apiService.listCompanies()

    val isLoggedIn: Boolean
        get() = sessionManager.token != null

    val currentRole: String?
        get() = sessionManager.role

    val currentFullName: String?
        get() = sessionManager.fullName

    val currentCompanyName: String?
        get() = sessionManager.companyName

    suspend fun logout() {
        // El DELETE necesita todavía el JWT. Si no hay red, se ignora y el
        // logout local continúa; el backend también reasigna el token si otro
        // usuario inicia sesión luego en el mismo dispositivo.
        deviceTokenRepository.unregisterCurrentToken()
        wipeLocalData()
        sessionManager.clear()
    }

    private suspend fun wipeLocalData() {
        withContext(Dispatchers.IO) {
            database.clearAllTables()
        }
        syncCursorStore.clear()
    }
}
