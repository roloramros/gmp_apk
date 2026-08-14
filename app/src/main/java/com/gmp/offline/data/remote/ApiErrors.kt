package com.gmp.offline.data.remote

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import retrofit2.HttpException

// Espejo del formato de error consistente que usan todos los controllers
// del backend: { error_code, message }. Ej: { "error_code": "phone_taken",
// "message": "Ya existe un usuario con ese teléfono en la empresa" }.
private data class ApiErrorDto(
    @SerializedName("error_code") val errorCode: String? = null,
    val message: String? = null,
)

/**
 * Envuelve una llamada de red y, si el servidor responde con un error HTTP
 * (4xx/5xx), reemplaza el mensaje genérico de Retrofit ("HTTP 409
 * Conflict") por el `message` real que manda el backend en el body JSON
 * (ej. "Ya existe un usuario con ese teléfono en la empresa"), para poder
 * mostrárselo tal cual al usuario en vez de un código HTTP pelado.
 */
suspend fun <T> apiCall(block: suspend () -> T): T {
    try {
        return block()
    } catch (e: HttpException) {
        val rawBody = e.response()?.errorBody()?.string()
        val parsedMessage = rawBody?.let {
            try {
                Gson().fromJson(it, ApiErrorDto::class.java)?.message
            } catch (parseError: Exception) {
                null
            }
        }
        throw Exception(parsedMessage ?: e.message())
    }
}
