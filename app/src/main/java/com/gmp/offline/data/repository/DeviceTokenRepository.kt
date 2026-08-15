package com.gmp.offline.data.repository

import android.util.Log
import com.gmp.offline.data.remote.ApiService
import com.gmp.offline.data.remote.dto.DeviceTokenRequest
import com.gmp.offline.data.session.SessionManager
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceTokenRepository @Inject constructor(
    private val apiService: ApiService,
    private val sessionManager: SessionManager,
) {
    suspend fun registerCurrentToken() {
        if (sessionManager.token.isNullOrBlank()) return
        try {
            val token = awaitCurrentToken()
            registerToken(token)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo registrar el token FCM actual", e)
        }
    }

    suspend fun registerToken(token: String) {
        if (token.isBlank() || sessionManager.token.isNullOrBlank()) return
        try {
            val response = apiService.registerDeviceToken(DeviceTokenRequest(token))
            if (!response.isSuccessful) {
                Log.w(TAG, "POST /device-tokens respondió HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error registrando token FCM", e)
        }
    }

    suspend fun unregisterCurrentToken() {
        if (sessionManager.token.isNullOrBlank()) return
        try {
            val token = awaitCurrentToken()
            val response = apiService.unregisterDeviceToken(DeviceTokenRequest(token))
            if (!response.isSuccessful) {
                Log.w(TAG, "DELETE /device-tokens respondió HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            // Logout debe continuar incluso sin red o si Firebase no entrega token.
            Log.w(TAG, "No se pudo eliminar el token FCM del backend", e)
        }
    }

    private suspend fun awaitCurrentToken(): String = suspendCoroutine { continuation ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> continuation.resume(token) }
            .addOnFailureListener { error -> continuation.resumeWithException(error) }
    }

    private companion object {
        const val TAG = "DeviceTokenRepository"
    }
}
