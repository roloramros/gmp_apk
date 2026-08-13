package com.gmp.offline.sync

import com.gmp.offline.data.local.dao.PendingOperationDao
import com.gmp.offline.data.local.entities.PendingOperationEntity
import com.google.gson.Gson
import java.util.UUID
import javax.inject.Inject

// Punto único de entrada para encolar un comando en el outbox local. Los
// repositorios llaman a `enqueue` DESPUÉS de aplicar el cambio optimista
// correspondiente en Room (ver JobsRepository.startJob para el patrón
// completo). El `commandId` generado acá es el mismo UUID que después viaja
// como header X-Command-Id — es lo que le da idempotencia del lado del
// servidor (middleware/idempotency.js).
//
// Además de insertar el comando, dispara un intento de sync INMEDIATO
// (SyncScheduler.triggerImmediateSync) en cuanto se registra en Room. Antes
// solo se subía en 3 momentos (manual, periódico cada 15 min, reconexión de
// red), lo que dejaba una acción recién creada esperando sin necesidad si
// había red disponible en el momento. El disparo inmediato ya trae la
// constraint NetworkType.CONNECTED (ver SyncScheduler): si no hay red en
// ese instante, WorkManager simplemente mantiene el trabajo encolado hasta
// que la haya, así que el comportamiento de "encolar si no hay conexión"
// se conserva sin código extra.
class CommandQueue @Inject constructor(
    private val pendingOperationDao: PendingOperationDao,
    private val syncScheduler: SyncScheduler,
) {
    private val gson = Gson()

    suspend fun enqueue(
        endpointPath: String,
        httpMethod: String,
        payload: Map<String, Any?>,
        commandId: String = UUID.randomUUID().toString(),
    ): String {
        pendingOperationDao.insert(
            PendingOperationEntity(
                commandId = commandId,
                endpointPath = endpointPath,
                httpMethod = httpMethod,
                payloadJson = gson.toJson(payload),
                status = "pending",
                createdAt = System.currentTimeMillis(),
            ),
        )
        syncScheduler.triggerImmediateSync()
        return commandId
    }
}
