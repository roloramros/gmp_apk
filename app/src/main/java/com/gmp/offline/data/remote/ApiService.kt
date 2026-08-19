package com.gmp.offline.data.remote

import com.gmp.offline.data.notes.RemoteNoteDto
import com.gmp.offline.data.remote.dto.CompanyDto
import com.gmp.offline.data.remote.dto.CreateStaffRequest
import com.gmp.offline.data.remote.dto.DeviceTokenRequest
import com.gmp.offline.data.remote.dto.LoginRequest
import com.gmp.offline.data.remote.dto.LoginResponse
import com.gmp.offline.data.remote.dto.StaffResponseDto
import com.gmp.offline.data.remote.dto.SyncResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("auth/login") suspend fun login(@Body body: LoginRequest): LoginResponse
    @GET("companies") suspend fun listCompanies(): List<CompanyDto>
    @GET("sync") suspend fun sync(@Query("since") since: String, @Query("cursor_page") cursorPage: String? = null): SyncResponseDto
    @GET("notes") suspend fun listNotes(): List<RemoteNoteDto>
    @POST("staff") suspend fun createStaff(@Body body: CreateStaffRequest): StaffResponseDto
    @POST("staff/{uuid}/deactivate") suspend fun deactivateStaff(@Path("uuid") uuid: String): StaffResponseDto
    @POST("device-tokens") suspend fun registerDeviceToken(@Body body: DeviceTokenRequest): Response<Unit>
    @DELETE("device-tokens") suspend fun unregisterDeviceToken(@Body body: DeviceTokenRequest): Response<Unit>
}
