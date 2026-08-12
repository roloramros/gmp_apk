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
