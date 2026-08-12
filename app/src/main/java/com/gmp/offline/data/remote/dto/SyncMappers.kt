package com.gmp.offline.data.remote.dto

import com.gmp.offline.data.local.entities.JobEntity
import com.gmp.offline.data.local.entities.JobMaterialEntity
import com.gmp.offline.data.local.entities.JobPhotoEntity
import com.gmp.offline.data.local.entities.JobWorkerEntity
import com.gmp.offline.data.local.entities.MaterialEntity
import com.gmp.offline.data.local.entities.StaffEntity

// Conversión directa DTO (lo que llega de /sync) -> Entity (lo que vive en
// Room). Sin lógica de negocio acá: eso vive en los repositorios/ViewModels.

fun JobDto.toEntity(): JobEntity = JobEntity(
    uuid = uuid,
    clientUuid = clientUuid,
    createdByUuid = createdByUuid,
    title = title,
    description = description,
    status = status,
    address = address,
    scheduledAt = scheduledAt,
    startedAt = startedAt,
    finishedAt = finishedAt,
    invoicedAt = invoicedAt,
    totalAmount = totalAmount,
    amountPaid = amountPaid,
    cancelledAt = cancelledAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun JobWorkerDto.toEntity(): JobWorkerEntity = JobWorkerEntity(
    uuid = uuid,
    jobUuid = jobUuid,
    userUuid = userUuid,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun MaterialDto.toEntity(): MaterialEntity = MaterialEntity(
    uuid = uuid,
    name = name,
    unit = unit,
    defaultPrice = defaultPrice,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun JobMaterialDto.toEntity(): JobMaterialEntity = JobMaterialEntity(
    uuid = uuid,
    jobUuid = jobUuid,
    materialUuid = materialUuid,
    freeTextDescription = freeTextDescription,
    quantity = quantity,
    unitPrice = unitPrice,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun JobPhotoDto.toEntity(): JobPhotoEntity = JobPhotoEntity(
    uuid = uuid,
    jobUuid = jobUuid,
    uploadedByUuid = uploadedByUuid,
    url = url,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun StaffDto.toEntity(): StaffEntity = StaffEntity(
    uuid = uuid,
    phone = phone,
    role = role,
    fullName = fullName,
    active = active,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
