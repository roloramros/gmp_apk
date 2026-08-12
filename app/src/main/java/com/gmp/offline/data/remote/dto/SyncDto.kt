package com.gmp.offline.data.remote.dto

import com.google.gson.annotations.SerializedName

// Espejo exacto de la respuesta de GET /sync (ver syncController.js,
// función `sync` y `formatUpsert`, sección 3.1 de plan-gmp-offline-first.md).

data class SyncResponseDto(
    val cursor: String,
    @SerializedName("has_more") val hasMore: Boolean,
    @SerializedName("next_cursor_page") val nextCursorPage: String?,
    val entities: SyncEntitiesDto,
)

data class SyncEntitiesDto(
    val jobs: SyncBucketDto<JobDto>,
    @SerializedName("job_workers") val jobWorkers: SyncBucketDto<JobWorkerDto>,
    val materials: SyncBucketDto<MaterialDto>,
    @SerializedName("job_materials") val jobMaterials: SyncBucketDto<JobMaterialDto>,
    @SerializedName("job_photos") val jobPhotos: SyncBucketDto<JobPhotoDto>,
    val staff: SyncBucketDto<StaffDto>,
)

data class SyncBucketDto<T>(
    val upserts: List<T>,
    val deletes: List<String>,
)

data class JobDto(
    val uuid: String,
    @SerializedName("client_uuid") val clientUuid: String?,
    @SerializedName("created_by_uuid") val createdByUuid: String,
    val title: String,
    val description: String?,
    val status: String,
    val address: String?,
    @SerializedName("scheduled_at") val scheduledAt: String?,
    @SerializedName("started_at") val startedAt: String?,
    @SerializedName("finished_at") val finishedAt: String?,
    @SerializedName("invoiced_at") val invoicedAt: String?,
    @SerializedName("total_amount") val totalAmount: String?,
    @SerializedName("amount_paid") val amountPaid: String,
    @SerializedName("cancelled_at") val cancelledAt: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
)

data class JobWorkerDto(
    val uuid: String,
    @SerializedName("job_uuid") val jobUuid: String,
    @SerializedName("user_uuid") val userUuid: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
)

data class MaterialDto(
    val uuid: String,
    val name: String,
    val unit: String?,
    @SerializedName("default_price") val defaultPrice: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
)

data class JobMaterialDto(
    val uuid: String,
    @SerializedName("job_uuid") val jobUuid: String,
    @SerializedName("material_uuid") val materialUuid: String?,
    @SerializedName("free_text_description") val freeTextDescription: String?,
    val quantity: String,
    @SerializedName("unit_price") val unitPrice: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
)

data class JobPhotoDto(
    val uuid: String,
    @SerializedName("job_uuid") val jobUuid: String,
    @SerializedName("uploaded_by_uuid") val uploadedByUuid: String,
    val url: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
)

data class StaffDto(
    val uuid: String,
    val phone: String,
    val role: String,
    @SerializedName("full_name") val fullName: String,
    val active: Boolean,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
)
