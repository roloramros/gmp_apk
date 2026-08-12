package com.gmp.offline.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Espejo de la entidad "jobs" tal como la devuelve GET /sync (ver
// syncController.js -> formatUpsert('jobs', ...) en el backend).
//
// Se usa `uuid` (String) como PK, no el id numérico interno de Postgres:
// es el identificador que habla toda la app y el protocolo de sync.
//
// Los montos (`totalAmount`, `amountPaid`) se guardan como String tal como
// los serializa el backend (columnas NUMERIC de Postgres -> string en JSON),
// para no perder precisión convirtiendo a Double innecesariamente. Se
// parsean a BigDecimal recién en la capa de UI/dominio si hace falta operar.
@Entity(tableName = "jobs", indices = [Index("status"), Index("clientUuid")])
data class JobEntity(
    @PrimaryKey val uuid: String,
    val clientUuid: String?,
    val createdByUuid: String,
    val title: String,
    val description: String?,
    val status: String,
    val address: String?,
    val scheduledAt: String?,
    val startedAt: String?,
    val finishedAt: String?,
    val invoicedAt: String?,
    val totalAmount: String?,
    val amountPaid: String,
    val cancelledAt: String?,
    val createdAt: String,
    val updatedAt: String,
)
