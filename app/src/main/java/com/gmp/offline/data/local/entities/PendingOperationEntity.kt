package com.gmp.offline.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

// Cola local de comandos pendientes de enviar (el "outbox" del diseño de
// sync, plan-gmp-offline-first.md sección 1 / 3.2).
//
// IMPORTANTE — alcance de esta fase: esta entidad se define acá (Fase 4)
// porque el resto de la capa de datos necesita saber que va a convivir con
// operaciones optimistas, pero todavía NO hay nada que la escriba ni la
// procese. Eso es la Fase 5 (SyncEngine + PendingOperationDao usado desde
// los repositorios en cada acción + SyncWorker que la reproduce con la red).
//
// `commandId` es el mismo UUID que se manda como header X-Command-Id al
// backend (ver middleware/idempotency.js) — es lo que garantiza que
// reintentar no duplique datos.
@Entity(tableName = "pending_operations")
data class PendingOperationEntity(
    @PrimaryKey val commandId: String,
    val endpointPath: String, // p.ej. "/jobs/{uuid}/start", ya resuelto (sin placeholders)
    val httpMethod: String, // "POST" | "PATCH" | "DELETE"
    val payloadJson: String, // body ya serializado, listo para enviar tal cual
    val status: String, // "pending" | "sending" | "failed" | "confirmed"
    val createdAt: Long, // epoch millis local, para reproducir en orden (FIFO)
    val lastAttemptAt: Long? = null,
    val lastErrorMessage: String? = null,
)
