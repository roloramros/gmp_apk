package com.gmp.offline.data.remote.dto

import com.google.gson.annotations.SerializedName

// Espejo de POST /staff (ver staffController.js). role: "admin" | "comercial" | "trabajador".
data class CreateStaffRequest(
    val phone: String,
    val password: String,
    val role: String,
    @SerializedName("full_name") val fullName: String,
)

// Respuesta de POST /staff y POST /staff/:uuid/deactivate. Ambas devuelven
// formas distintas (createStaff trae phone/role/created_at, deactivate NO
// los trae — solo uuid/full_name/active), por eso todo menos `uuid` es
// nullable acá: cada caller usa los campos que sabe que van a venir y
// completa el resto con lo que ya tenía en Room.
data class StaffResponseDto(
    val uuid: String,
    val phone: String? = null,
    val role: String? = null,
    @SerializedName("full_name") val fullName: String? = null,
    val active: Boolean = true,
    @SerializedName("created_at") val createdAt: String? = null,
)
