package com.gmp.offline.data.repository

import android.content.Context
import android.net.Uri
import com.gmp.offline.BuildConfig
import com.gmp.offline.data.local.dao.CatalogProductPhotoDao
import com.gmp.offline.data.local.entities.CatalogProductPhotoEntity
import com.gmp.offline.data.remote.dto.CatalogProductPhotoDto
import com.gmp.offline.data.remote.dto.toEntity
import com.gmp.offline.sync.CommandQueue
import com.gmp.offline.sync.CommandResult
import com.gmp.offline.util.PhotoCompressor
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject

// Calco de CatalogKitPhotoRepository, para catalog_product_photos. Ver ese
// archivo para el razonamiento completo (multipart directo fuera del
// outbox, retry manual, sin reintento automático por reconexión).
class CatalogProductPhotoRepository @Inject constructor(
    private val catalogProductPhotoDao: CatalogProductPhotoDao,
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val commandQueue: CommandQueue,
) {
    fun observeByProduct(productUuid: String): Flow<List<CatalogProductPhotoEntity>> =
        catalogProductPhotoDao.observeByProduct(productUuid)

    suspend fun addPhoto(productUuid: String, imageUri: Uri): PhotoActionResult = withContext(Dispatchers.IO) {
        val currentCount = catalogProductPhotoDao.getByProduct(productUuid).size
        if (currentCount >= MAX_PHOTOS_PER_PRODUCT) {
            return@withContext PhotoActionResult.Error("Ya hay $MAX_PHOTOS_PER_PRODUCT fotos en este producto, el máximo permitido.")
        }

        val compressedBytes = try {
            PhotoCompressor.compress(context, imageUri)
        } catch (e: IOException) {
            return@withContext PhotoActionResult.Error("No se pudo procesar la imagen: ${e.message}")
        }

        val photoUuid = UUID.randomUUID().toString()
        val localFile = File(photosDir(), "$photoUuid.jpg")
        try {
            localFile.writeBytes(compressedBytes)
        } catch (e: IOException) {
            return@withContext PhotoActionResult.Error("No se pudo guardar la imagen: ${e.message}")
        }

        val nowIso = isoNowUtc()
        catalogProductPhotoDao.upsertAll(
            listOf(
                CatalogProductPhotoEntity(
                    uuid = photoUuid, productUuid = productUuid, url = "", sortOrder = currentCount,
                    createdAt = nowIso, updatedAt = nowIso, localPath = localFile.absolutePath, uploadStatus = "uploading",
                ),
            ),
        )

        applyUploadResult(
            productUuid = productUuid, photoUuid = photoUuid, sortOrder = currentCount,
            localPath = localFile.absolutePath, nowIso = nowIso,
            result = uploadToServer(productUuid, photoUuid, currentCount, localFile),
        )
    }

    suspend fun retryPhoto(productUuid: String, photoUuid: String): PhotoActionResult = withContext(Dispatchers.IO) {
        val existing = catalogProductPhotoDao.getByUuid(photoUuid)
            ?: return@withContext PhotoActionResult.Error("No se encontró la foto para reintentar.")
        val localPath = existing.localPath
            ?: return@withContext PhotoActionResult.Error("No se encontró el archivo local de la foto.")
        val file = File(localPath)
        if (!file.exists()) return@withContext PhotoActionResult.Error("El archivo local de la foto ya no existe.")

        catalogProductPhotoDao.upsertAll(listOf(existing.copy(uploadStatus = "uploading")))
        applyUploadResult(
            productUuid = productUuid, photoUuid = existing.uuid, sortOrder = existing.sortOrder,
            localPath = localPath, nowIso = isoNowUtc(),
            result = uploadToServer(productUuid, existing.uuid, existing.sortOrder, file),
        )
    }

    suspend fun removePhoto(productUuid: String, photoUuid: String) = withContext(Dispatchers.IO) {
        val existing = catalogProductPhotoDao.getByUuid(photoUuid)
        catalogProductPhotoDao.deleteByUuids(listOf(photoUuid))
        existing?.localPath?.let { path -> runCatching { File(path).delete() } }

        if (existing?.uploadStatus == "synced") {
            commandQueue.enqueue(
                endpointPath = "/catalog-products/$productUuid/photos/$photoUuid",
                httpMethod = "DELETE",
                payload = emptyMap(),
            )
        }
        Unit
    }

    private suspend fun applyUploadResult(
        productUuid: String, photoUuid: String, sortOrder: Int, localPath: String, nowIso: String, result: CommandResult,
    ): PhotoActionResult = when (result) {
        is CommandResult.Success -> {
            val confirmed = try {
                Gson().fromJson(result.responseBody, CatalogProductPhotoDto::class.java).toEntity()
                    .copy(localPath = localPath, uploadStatus = "synced")
            } catch (_: Exception) {
                CatalogProductPhotoEntity(
                    uuid = photoUuid, productUuid = productUuid, url = "", sortOrder = sortOrder,
                    createdAt = nowIso, updatedAt = nowIso, localPath = localPath, uploadStatus = "synced",
                )
            }
            catalogProductPhotoDao.upsertAll(listOf(confirmed))
            PhotoActionResult.Success
        }
        is CommandResult.HttpError -> {
            catalogProductPhotoDao.upsertAll(
                listOf(
                    CatalogProductPhotoEntity(
                        uuid = photoUuid, productUuid = productUuid, url = "", sortOrder = sortOrder,
                        createdAt = nowIso, updatedAt = nowIso, localPath = localPath, uploadStatus = "error",
                    ),
                ),
            )
            PhotoActionResult.Error("El servidor rechazó la foto (código ${result.code}).")
        }
        is CommandResult.NetworkError -> {
            catalogProductPhotoDao.upsertAll(
                listOf(
                    CatalogProductPhotoEntity(
                        uuid = photoUuid, productUuid = productUuid, url = "", sortOrder = sortOrder,
                        createdAt = nowIso, updatedAt = nowIso, localPath = localPath, uploadStatus = "error",
                    ),
                ),
            )
            PhotoActionResult.Error("Sin conexión — la foto quedó guardada para reintentar.")
        }
    }

    private suspend fun uploadToServer(productUuid: String, photoUuid: String, sortOrder: Int, file: File): CommandResult =
        withContext(Dispatchers.IO) {
            try {
                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("uuid", photoUuid)
                    .addFormDataPart("sort_order", sortOrder.toString())
                    .addFormDataPart("photo", file.name, file.asRequestBody("image/jpeg".toMediaType()))
                    .build()
                val request = Request.Builder()
                    .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/catalog-products/$productUuid/photos")
                    .header("X-Command-Id", UUID.randomUUID().toString())
                    .post(multipartBody)
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (response.isSuccessful) CommandResult.Success(text) else CommandResult.HttpError(response.code, text)
                }
            } catch (e: IOException) {
                CommandResult.NetworkError(e.message)
            }
        }

    private fun photosDir(): File = File(context.filesDir, "catalog_product_photos").apply { mkdirs() }

    private fun isoNowUtc(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }

    companion object {
        const val MAX_PHOTOS_PER_PRODUCT = 4
    }
}
