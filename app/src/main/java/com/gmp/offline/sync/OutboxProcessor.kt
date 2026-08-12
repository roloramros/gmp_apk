package com.gmp.offline.sync

import com.gmp.offline.data.local.dao.PendingOperationDao
import javax.inject.Inject

// Reproduce el outbox en orden (FIFO por createdAt), tal como pide el plan
// (sección 3.2: "El SyncWorker las reproduce en orden cuando hay red").
//
// Decisiones de esta fase:
// - Si un comando falla por falta de red (CommandResult.NetworkError), se
//   corta TODO el procesamiento del outbox ahí: si no hubo red para el
//   primero, tampoco la va a haber para el resto — seguir intentando solo
//   gasta batería y llamadas.
// - Si un comando falla por un error de negocio del servidor (4xx/5xx —
//   p.ej. 409 por conflicto de asignación de horario), se marca como
//   "failed" con el detalle, pero se SIGUE procesando el resto de la cola:
//   un comando roto de un job no debería trabar la sincronización de
//   comandos de otros jobs que no tienen nada que ver.
//   Mostrar ese error al usuario (indicador "no se pudo sincronizar") es
//   responsabilidad de la UI — Fase 6.
// - Si un comando tiene éxito, se borra del outbox: el servidor ya lo
//   procesó y quedó registrado en su propio `command_log` para
//   idempotencia, así que no hace falta seguir guardándolo localmente.
class OutboxProcessor @Inject constructor(
    private val pendingOperationDao: PendingOperationDao,
    private val commandDispatcher: CommandDispatcher,
) {
    /**
     * @return true si se pudo intentar mandar toda la cola (con o sin
     * fallos de negocio en el camino); false si se cortó temprano por
     * falta de red.
     */
    suspend fun processPending(): Boolean {
        val pending = pendingOperationDao.getPending()

        for (operation in pending) {
            when (val result = commandDispatcher.send(operation)) {
                is CommandResult.Success -> {
                    pendingOperationDao.delete(operation.commandId)
                }

                is CommandResult.HttpError -> {
                    pendingOperationDao.updateStatus(
                        commandId = operation.commandId,
                        status = "failed",
                        attemptAt = System.currentTimeMillis(),
                        errorMessage = "HTTP ${result.code}: ${result.responseBody.take(500)}",
                    )
                }

                is CommandResult.NetworkError -> {
                    pendingOperationDao.updateStatus(
                        commandId = operation.commandId,
                        status = "pending",
                        attemptAt = System.currentTimeMillis(),
                        errorMessage = result.message,
                    )
                    return false
                }
            }
        }
        return true
    }
}
