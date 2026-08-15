package com.gmp.offline.data.remote.dto

import com.google.gson.annotations.SerializedName

// Espejo de POST /auth/login (ver authController.js en el backend).
data class LoginRequest(
    @SerializedName("company_id") val companyId: Int,
    val phone: String,
    val password: String,
)

data class LoginResponse(
    val token: String,
    val user: LoginUserDto,
)

data class LoginUserDto(
    val uuid: String,
    @SerializedName("full_name") val fullName: String,
    val role: String,
    @SerializedName("company_id") val companyId: String,
)

// Espejo de GET /companies (listado público para el selector del login).
// OJO: el backend devuelve "id" como string (ej. "1"), no como número —
// confirmado con curl real contra /companies. Se convierte a Int recién
// al armar el LoginRequest.
data class CompanyDto(
    val id: String,
    val uuid: String,
    val name: String,
)

data class DeviceTokenRequest(
    @SerializedName("fcm_token") val fcmToken: String,
    val platform: String = "android",
)
