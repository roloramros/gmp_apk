package com.gmp.offline.data.repository

import com.gmp.offline.data.remote.ApiService
import com.gmp.offline.data.remote.dto.CompanyDto
import com.gmp.offline.data.remote.dto.LoginRequest
import com.gmp.offline.data.session.SessionManager
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val sessionManager: SessionManager,
) {
    suspend fun login(companyId: Int, phone: String, password: String) {
        val response = apiService.login(LoginRequest(companyId, phone, password))
        sessionManager.token = response.token
        sessionManager.userUuid = response.user.uuid
        sessionManager.role = response.user.role
        sessionManager.fullName = response.user.fullName
        sessionManager.companyId = response.user.companyId
    }

    /** Selector de empresa en el login: GET /companies (listado público). */
    suspend fun listCompanies(): List<CompanyDto> = apiService.listCompanies()

    val isLoggedIn: Boolean
        get() = sessionManager.token != null

    val currentRole: String?
        get() = sessionManager.role

    val currentFullName: String?
        get() = sessionManager.fullName

    fun logout() = sessionManager.clear()
}
