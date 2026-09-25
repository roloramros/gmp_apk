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
    @SerializedName("catalog_kits") val catalogKits: SyncBucketDto<CatalogKitDto> = SyncBucketDto(emptyList(), emptyList()),
    @SerializedName("catalog_kit_photos") val catalogKitPhotos: SyncBucketDto<CatalogKitPhotoDto> = SyncBucketDto(emptyList(), emptyList()),
    @SerializedName("catalog_products") val catalogProducts: SyncBucketDto<CatalogProductDto> = SyncBucketDto(emptyList(), emptyList()),
    @SerializedName("catalog_product_photos") val catalogProductPhotos: SyncBucketDto<CatalogProductPhotoDto> = SyncBucketDto(emptyList(), emptyList()),
    @SerializedName("gallery_photos") val galleryPhotos: SyncBucketDto<GalleryPhotoDto> = SyncBucketDto(emptyList(), emptyList()),
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
    // --- Campos "montaje" (Fase 6 Paso 3) — agregados a syncController.js
    // en el backend; faltaban acá, por eso cada /sync los pisaba con null
    // en Room (ver CommandQueue/SyncEngine, bug reportado 13/08).
    @SerializedName("client_name") val clientName: String? = null,
    @SerializedName("client_ci") val clientCi: String? = null,
    @SerializedName("client_phone") val clientPhone: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val reference: String? = null,
    @SerializedName("site_notes") val siteNotes: String? = null,
    val price: String? = null,
    @SerializedName("payment_method") val paymentMethod: String? = null,
    @SerializedName("visit_date") val visitDate: String? = null,
    @SerializedName("proposed_date") val proposedDate: String? = null,
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

// Espejo de catalog_kits (feature "Catálogo"). power_kw/battery_kwh/price_usd
// viajan como string desde Postgres (columnas NUMERIC), igual que
// default_price en MaterialDto — se guardan tal cual, sin castear a Double,
// para no perder precisión ni arrastrar problemas de locale al mostrarlos.
data class CatalogKitDto(
    val uuid: String,
    val name: String,
    @SerializedName("power_kw") val powerKw: String?,
    val voltage: String?,
    @SerializedName("battery_kwh") val batteryKwh: String?,
    @SerializedName("panels_count") val panelsCount: Int?,
    @SerializedName("price_usd") val priceUsd: String?,
    val description: String?,
    val active: Boolean,
    @SerializedName("sort_order") val sortOrder: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
)

data class CatalogKitPhotoDto(
    val uuid: String,
    @SerializedName("kit_uuid") val kitUuid: String,
    @SerializedName("sort_order") val sortOrder: Int,
    val url: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
)

// Espejo de catalog_products (tienda de componentes sueltos).
data class CatalogProductDto(
    val uuid: String,
    val name: String,
    @SerializedName("price_usd") val priceUsd: String?,
    val description: String?,
    @SerializedName("in_stock") val inStock: Boolean,
    val active: Boolean,
    @SerializedName("sort_order") val sortOrder: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
)

data class CatalogProductPhotoDto(
    val uuid: String,
    @SerializedName("product_uuid") val productUuid: String,
    @SerializedName("sort_order") val sortOrder: Int,
    val url: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
)

// Espejo de gallery_photos (instalaciones terminadas, sin "padre").
data class GalleryPhotoDto(
    val uuid: String,
    val caption: String?,
    val active: Boolean,
    @SerializedName("sort_order") val sortOrder: Int,
    val url: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
)
