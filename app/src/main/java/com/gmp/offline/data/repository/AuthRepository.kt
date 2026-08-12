package com.gmp.offline.data.repository

import com.gmp.offline.data.remote.ApiService
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
    }

    val isLoggedIn: Boolean
        get() = sessionManager.token != null

    fun logout() = sessionManager.clear()
}
