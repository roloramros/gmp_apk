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
// Los montos (`totalAmount`, `amountPaid`, `price`) se guardan como String
// tal como los serializa el backend (columnas NUMERIC de Postgres -> string
// en JSON), para no perder precisión convirtiendo a Double innecesariamente.
// Se parsean recién en la capa de UI/dominio si hace falta operar.
//
// Campos agregados en Fase 6 Paso 3 para replicar exactamente la pantalla
// de "Planificación de Montajes" del panel web legado (comercial): datos
// del cliente sueltos en el propio job (no vía `clientUuid`, que sigue
// existiendo pero sin uso desde la UI de comercial), ubicación, precio
// cotizado y forma de pago. Requiere la migración 003 del backend (ver
// avance correspondiente) — columnas nuevas en `jobs`.
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
    // --- Campos "montaje" (Fase 6 Paso 3, réplica exacta de la web legada) ---
    val clientName: String? = null,
    val clientCi: String? = null,
    val clientPhone: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val reference: String? = null,
    val siteNotes: String? = null,
    val price: String? = null,
    val paymentMethod: String? = null,
    val visitDate: String? = null,
    val proposedDate: String? = null,
)
