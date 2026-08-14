package com.gmp.offline.data.repository

import android.content.Context
import android.net.Uri
import com.gmp.offline.BuildConfig
import com.gmp.offline.data.local.dao.JobPhotoDao
import com.gmp.offline.data.local.entities.JobPhotoEntity
import com.gmp.offline.data.remote.dto.JobPhotoDto
import com.gmp.offline.data.remote.dto.toEntity
import com.gmp.offline.data.session.SessionManager
import com.gmp.offline.sync.CommandResult
import com.gmp.offline.util.PhotoCompressor
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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

class WorkerPhotoRepository @Inject constructor(
    private val jobPhotoDao: JobPhotoDao,
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val sessionManager: SessionManager,
) {
    suspend fun addPhoto(jobUuid: String, imageUri: Uri): PhotoActionResult = withContext(Dispatchers.IO) {
        val userUuid = sessionManager.userUuid.orEmpty()
        val workerPhotos = jobPhotoDao.getByJob(jobUuid).count { it.uploadedByUuid == userUuid }
        if (workerPhotos >= MAX_WORKER_PHOTOS) {
            return@withContext PhotoActionResult.Error("Ya agregaste las 3 fotos permitidas para este trabajo.")
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
        jobPhotoDao.upsertAll(
            listOf(
                JobPhotoEntity(
                    uuid = photoUuid,
                    jobUuid = jobUuid,
                    uploadedByUuid = userUuid,
                    url = "",
                    createdAt = nowIso,
                    updatedAt = nowIso,
                    localPath = localFile.absolutePath,
                    uploadStatus = "uploading",
                ),
            ),
        )

        applyUploadResult(
            jobUuid = jobUuid,
            photoUuid = photoUuid,
            userUuid = userUuid,
            localPath = localFile.absolutePath,
            nowIso = nowIso,
            result = uploadToServer(jobUuid, photoUuid, localFile),
        )
    }

    suspend fun retryPhoto(jobUuid: String, photoUuid: String): PhotoActionResult = withContext(Dispatchers.IO) {
        val existing = jobPhotoDao.getByUuid(photoUuid)
            ?: return@withContext PhotoActionResult.Error("No se encontró la foto para reintentar.")
        if (existing.uploadedByUuid != sessionManager.userUuid) {
            return@withContext PhotoActionResult.Error("Solo puedes reintentar tus propias fotos.")
        }
        val localPath = existing.localPath
            ?: return@withContext PhotoActionResult.Error("No se encontró el archivo local de la foto.")
        val file = File(localPath)
        if (!file.exists()) {
            return@withContext PhotoActionResult.Error("El archivo local de la foto ya no existe.")
        }

        jobPhotoDao.upsertAll(listOf(existing.copy(uploadStatus = "uploading")))
        applyUploadResult(
            jobUuid = jobUuid,
            photoUuid = existing.uuid,
            userUuid = existing.uploadedByUuid,
            localPath = localPath,
            nowIso = isoNowUtc(),
            result = uploadToServer(jobUuid, existing.uuid, file),
        )
    }

    private suspend fun applyUploadResult(
        jobUuid: String,
        photoUuid: String,
        userUuid: String,
        localPath: String,
        nowIso: String,
        result: CommandResult,
    ): PhotoActionResult = when (result) {
        is CommandResult.Success -> {
            val confirmed = try {
                Gson().fromJson(result.responseBody, JobPhotoDto::class.java).toEntity()
                    .copy(localPath = localPath, uploadStatus = "synced")
            } catch (_: Exception) {
                JobPhotoEntity(
                    uuid = photoUuid,
                    jobUuid = jobUuid,
                    uploadedByUuid = userUuid,
                    url = "",
                    createdAt = nowIso,
                    updatedAt = nowIso,
                    localPath = localPath,
                    uploadStatus = "synced",
                )
            }
            jobPhotoDao.upsertAll(listOf(confirmed))
            PhotoActionResult.Success
        }
        is CommandResult.HttpError -> {
            jobPhotoDao.upsertAll(
                listOf(
                    JobPhotoEntity(
                        uuid = photoUuid,
                        jobUuid = jobUuid,
                        uploadedByUuid = userUuid,
                        url = "",
                        createdAt = nowIso,
                        updatedAt = nowIso,
                        localPath = localPath,
                        uploadStatus = "error",
                    ),
                ),
            )
            PhotoActionResult.Error("El servidor rechazó la foto (código ${result.code}).")
        }
        is CommandResult.NetworkError -> {
            jobPhotoDao.upsertAll(
                listOf(
                    JobPhotoEntity(
                        uuid = photoUuid,
                        jobUuid = jobUuid,
                        uploadedByUuid = userUuid,
                        url = "",
                        createdAt = nowIso,
                        updatedAt = nowIso,
                        localPath = localPath,
                        uploadStatus = "error",
                    ),
                ),
            )
            PhotoActionResult.Error("Sin conexión — la foto quedó guardada para reintentar.")
        }
    }

    private suspend fun uploadToServer(jobUuid: String, photoUuid: String, file: File): CommandResult =
        withContext(Dispatchers.IO) {
            try {
                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("uuid", photoUuid)
                    .addFormDataPart("photo", file.name, file.asRequestBody("image/jpeg".toMediaType()))
                    .build()

                val request = Request.Builder()
                    .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/jobs/$jobUuid/photos")
                    .header("X-Command-Id", UUID.randomUUID().toString())
                    .post(multipartBody)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (response.isSuccessful) CommandResult.Success(text)
                    else CommandResult.HttpError(response.code, text)
                }
            } catch (e: IOException) {
                CommandResult.NetworkError(e.message)
            }
        }

    private fun photosDir(): File = File(context.filesDir, "job_photos").apply { mkdirs() }

    private fun isoNowUtc(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }

    companion object {
        const val MAX_WORKER_PHOTOS = 3
    }
}
