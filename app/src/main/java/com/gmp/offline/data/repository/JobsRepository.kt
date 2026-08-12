package com.gmp.offline.data.repository

import com.gmp.offline.data.local.dao.JobDao
import com.gmp.offline.data.local.entities.JobEntity
import com.gmp.offline.sync.CommandQueue
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

class JobsRepository @Inject constructor(
    private val jobDao: JobDao,
    private val commandQueue: CommandQueue,
) {
    fun observeJobs(): Flow<List<JobEntity>> = jobDao.observeAll()

    fun observeJob(uuid: String): Flow<JobEntity?> = jobDao.observeByUuid(uuid)

    fun observeJobsByStatus(status: String): Flow<List<JobEntity>> = jobDao.observeByStatus(status)

    /** Lectura puntual, sin Flow — para botones de prueba/debug, no para UI reactiva. */
    suspend fun findFirstJobByStatus(status: String): JobEntity? = jobDao.getFirstByStatus(status)

    /**
     * Ejemplo end-to-end del patrón de escritura offline-first que va a
     * seguir el resto de las acciones (finish, pay, assign, etc.) cuando se
     * conecten de verdad en la Fase 6:
     *
     * 1) Aplicar el cambio optimista en Room YA — la UI lo refleja al
     *    toque, sin esperar red ni respuesta del servidor.
     * 2) Encolar el comando real en el outbox (CommandQueue) para que
     *    SyncWorker lo mande cuando haya conexión, con el mismo UUID como
     *    X-Command-Id para que el servidor lo trate con idempotencia.
     *
     * Nota: el valor exacto que jobsActionsController.js asigna a `status`
     * tras `/start` no está confirmado en este código (no tuve el archivo
     * a la vista al escribir esto) — "in_progress" es un placeholder
     * ilustrativo del patrón, no un valor verificado contra el backend.
     * Cuando se conecte esta acción de verdad en Fase 6, confirmar el
     * string exacto contra jobsActionsController.js antes de usarlo.
     */
    suspend fun startJob(jobUuid: String) {
        val job = jobDao.getByUuid(jobUuid) ?: return
        val nowIso = isoNowUtc()

        jobDao.upsertAll(
            listOf(
                job.copy(
                    status = "in_progress", // TODO: confirmar contra jobsActionsController.js
                    startedAt = nowIso,
                    updatedAt = nowIso,
                ),
            ),
        )

        commandQueue.enqueue(
            endpointPath = "/jobs/$jobUuid/start",
            httpMethod = "POST",
            payload = emptyMap(),
        )
    }

    private fun isoNowUtc(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }
}
