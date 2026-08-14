package com.gmp.offline.data.remote

import com.gmp.offline.data.remote.dto.CompanyDto
import com.gmp.offline.data.remote.dto.CreateStaffRequest
import com.gmp.offline.data.remote.dto.LoginRequest
import com.gmp.offline.data.remote.dto.LoginResponse
import com.gmp.offline.data.remote.dto.StaffResponseDto
import com.gmp.offline.data.remote.dto.SyncResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// Alcance de esta fase: login, sync, y las acciones de "Gestión de
// Personal" (crear/desactivar staff) — ambas se llaman de forma directa
// (no pasan por el outbox/CommandQueue) porque staffController.js no las
// envuelve en el middleware de idempotencia (X-Command-Id), y porque
// crear un usuario nuevo con contraseña es una acción que tiene sentido
// requerir conexión (igual que hace la web legada). El resto de acciones
// (start/finish/pay/...) con X-Command-Id pasan por CommandDispatcher, no
// por acá.
interface ApiService {

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    // Listado público de empresas, para el selector del login (Fase 6).
    // Devuelve un array plano (sin wrapper), confirmado contra el backend real.
    @GET("companies")
    suspend fun listCompanies(): List<CompanyDto>

    @GET("sync")
    suspend fun sync(
        @Query("since") since: String,
        @Query("cursor_page") cursorPage: String? = null,
    ): SyncResponseDto

    @POST("staff")
    suspend fun createStaff(@Body body: CreateStaffRequest): StaffResponseDto

    @POST("staff/{uuid}/deactivate")
    suspend fun deactivateStaff(@Path("uuid") uuid: String): StaffResponseDto
}
