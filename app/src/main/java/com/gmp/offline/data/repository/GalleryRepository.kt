package com.gmp.offline.data.repository

import android.content.Context
import android.net.Uri
import com.gmp.offline.BuildConfig
import com.gmp.offline.data.local.dao.GalleryPhotoDao
import com.gmp.offline.data.local.entities.GalleryPhotoEntity
import com.gmp.offline.data.remote.dto.GalleryPhotoDto
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

// Galería de instalaciones terminadas — fotos sueltas, sin un "padre" como
// kit/producto (a diferencia de CatalogKitPhotoRepository/
// CatalogProductPhotoRepository). Mismo patrón offline-first: multipart
// directo fuera del outbox + retry manual para la subida; caption/visible
// sí pasan por el outbox normal porque son solo texto (JSON, sin adjunto).
class GalleryRepository @Inject constructor(
    private val galleryPhotoDao: GalleryPhotoDao,
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val commandQueue: CommandQueue,
) {
    fun observeAll(): Flow<List<GalleryPhotoEntity>> = galleryPhotoDao.observeAll()

    suspend fun addPhoto(imageUri: Uri, caption: String?): PhotoActionResult = withContext(Dispatchers.IO) {
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
        galleryPhotoDao.upsertAll(
            listOf(
                GalleryPhotoEntity(
                    uuid = photoUuid, caption = caption, active = true, sortOrder = 0, url = "",
                    createdAt = nowIso, updatedAt = nowIso, localPath = localFile.absolutePath, uploadStatus = "uploading",
                ),
            ),
        )

        applyUploadResult(
            photoUuid = photoUuid, caption = caption, localPath = localFile.absolutePath, nowIso = nowIso,
            result = uploadToServer(photoUuid, caption, localFile),
        )
    }

    suspend fun retryPhoto(photoUuid: String): PhotoActionResult = withContext(Dispatchers.IO) {
        val existing = galleryPhotoDao.getByUuid(photoUuid)
            ?: return@withContext PhotoActionResult.Error("No se encontró la foto para reintentar.")
        val localPath = existing.localPath
            ?: return@withContext PhotoActionResult.Error("No se encontró el archivo local de la foto.")
        val file = File(localPath)
        if (!file.exists()) return@withContext PhotoActionResult.Error("El archivo local de la foto ya no existe.")

        galleryPhotoDao.upsertAll(listOf(existing.copy(uploadStatus = "uploading")))
        applyUploadResult(
            photoUuid = existing.uuid, caption = existing.caption, localPath = localPath, nowIso = isoNowUtc(),
            result = uploadToServer(existing.uuid, existing.caption, file),
        )
    }

    /** Solo tiene efecto en el servidor si la foto ya está "synced" — si sigue subiendo o en error, se guarda local nomás. */
    suspend fun updateCaption(photoUuid: String, caption: String?) = withContext(Dispatchers.IO) {
        val existing = galleryPhotoDao.getByUuid(photoUuid) ?: return@withContext
        galleryPhotoDao.upsertAll(listOf(existing.copy(caption = caption, updatedAt = isoNowUtc())))
        if (existing.uploadStatus == "synced") {
            commandQueue.enqueue(endpointPath = "/gallery/$photoUuid", httpMethod = "PATCH", payload = mapOf("caption" to caption))
        }
    }

    suspend fun removePhoto(photoUuid: String) = withContext(Dispatchers.IO) {
        val existing = galleryPhotoDao.getByUuid(photoUuid)
        galleryPhotoDao.deleteByUuids(listOf(photoUuid))
        existing?.localPath?.let { path -> runCatching { File(path).delete() } }
        if (existing?.uploadStatus == "synced") {
            commandQueue.enqueue(endpointPath = "/gallery/$photoUuid", httpMethod = "DELETE", payload = emptyMap())
        }
        Unit
    }

    private suspend fun applyUploadResult(
        photoUuid: String, caption: String?, localPath: String, nowIso: String, result: CommandResult,
    ): PhotoActionResult = when (result) {
        is CommandResult.Success -> {
            val confirmed = try {
                Gson().fromJson(result.responseBody, GalleryPhotoDto::class.java).toEntity()
                    .copy(localPath = localPath, uploadStatus = "synced")
            } catch (_: Exception) {
                GalleryPhotoEntity(
                    uuid = photoUuid, caption = caption, active = true, sortOrder = 0, url = "",
                    createdAt = nowIso, updatedAt = nowIso, localPath = localPath, uploadStatus = "synced",
                )
            }
            galleryPhotoDao.upsertAll(listOf(confirmed))
            PhotoActionResult.Success
        }
        is CommandResult.HttpError -> {
            galleryPhotoDao.upsertAll(
                listOf(
                    GalleryPhotoEntity(
                        uuid = photoUuid, caption = caption, active = true, sortOrder = 0, url = "",
                        createdAt = nowIso, updatedAt = nowIso, localPath = localPath, uploadStatus = "error",
                    ),
                ),
            )
            PhotoActionResult.Error("El servidor rechazó la foto (código ${result.code}).")
        }
        is CommandResult.NetworkError -> {
            galleryPhotoDao.upsertAll(
                listOf(
                    GalleryPhotoEntity(
                        uuid = photoUuid, caption = caption, active = true, sortOrder = 0, url = "",
                        createdAt = nowIso, updatedAt = nowIso, localPath = localPath, uploadStatus = "error",
                    ),
                ),
            )
            PhotoActionResult.Error("Sin conexión — la foto quedó guardada para reintentar.")
        }
    }

    private suspend fun uploadToServer(photoUuid: String, caption: String?, file: File): CommandResult =
        withContext(Dispatchers.IO) {
            try {
                val builder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("uuid", photoUuid)
                if (!caption.isNullOrBlank()) builder.addFormDataPart("caption", caption)
                builder.addFormDataPart("photo", file.name, file.asRequestBody("image/jpeg".toMediaType()))

                val request = Request.Builder()
                    .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/gallery")
                    .header("X-Command-Id", UUID.randomUUID().toString())
                    .post(builder.build())
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (response.isSuccessful) CommandResult.Success(text) else CommandResult.HttpError(response.code, text)
                }
            } catch (e: IOException) {
                CommandResult.NetworkError(e.message)
            }
        }

    private fun photosDir(): File = File(context.filesDir, "gallery_photos").apply { mkdirs() }

    private fun isoNowUtc(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }
}
