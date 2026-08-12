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
class CommandQueue @Inject constructor(
    private val pendingOperationDao: PendingOperationDao,
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
        return commandId
    }
}
