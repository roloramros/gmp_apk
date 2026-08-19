package com.gmp.offline.sync

import com.gmp.offline.BuildConfig
import com.gmp.offline.data.local.entities.PendingOperationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject

sealed class CommandResult {
    data class Success(val responseBody: String) : CommandResult()
    data class HttpError(val code: Int, val responseBody: String) : CommandResult()
    data class NetworkError(val message: String?) : CommandResult()
}

class CommandDispatcher @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun send(operation: PendingOperationEntity): CommandResult = withContext(Dispatchers.IO) {
        try {
            val url = BuildConfig.API_BASE_URL.trimEnd('/') + operation.endpointPath
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = operation.payloadJson.toRequestBody(mediaType)
            val requestBuilder = Request.Builder().url(url).header("X-Command-Id", operation.commandId)
            val request = when (operation.httpMethod.uppercase()) {
                "POST" -> requestBuilder.post(body).build()
                "PUT" -> requestBuilder.put(body).build()
                "PATCH" -> requestBuilder.patch(body).build()
                "DELETE" -> requestBuilder.delete(body).build()
                else -> throw IllegalArgumentException("Método HTTP no soportado en outbox: ${operation.httpMethod}")
            }
            okHttpClient.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (response.isSuccessful) CommandResult.Success(responseText)
                else CommandResult.HttpError(response.code, responseText)
            }
        } catch (e: IOException) {
            CommandResult.NetworkError(e.message)
        }
    }
}
