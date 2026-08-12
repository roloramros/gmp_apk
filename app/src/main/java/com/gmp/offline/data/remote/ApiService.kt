package com.gmp.offline.data.remote

import com.gmp.offline.data.remote.dto.LoginRequest
import com.gmp.offline.data.remote.dto.LoginResponse
import com.gmp.offline.data.remote.dto.SyncResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// Alcance de esta fase: solo login (para poder autenticar) y sync (para
// poder probar la capa Room). Los endpoints de acción (start/finish/pay/...)
// con X-Command-Id se agregan cuando se conecte el outbox real en la Fase 5.
interface ApiService {

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @GET("sync")
    suspend fun sync(
        @Query("since") since: String,
        @Query("cursor_page") cursorPage: String? = null,
    ): SyncResponseDto
}
