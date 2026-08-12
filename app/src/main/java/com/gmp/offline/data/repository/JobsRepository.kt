package com.gmp.offline.data.repository

import com.gmp.offline.data.local.dao.JobDao
import com.gmp.offline.data.local.entities.JobEntity
import com.gmp.offline.data.session.SessionManager
import com.gmp.offline.sync.CommandQueue
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject

class JobsRepository @Inject constructor(
    private val jobDao: JobDao,
    private val commandQueue: CommandQueue,
    private val sessionManager: SessionManager,
) {
    fun observeJobs(): Flow<List<JobEntity>> = jobDao.observeAll()

    fun observeJob(uuid: String): Flow<JobEntity?> = jobDao.observeByUuid(uuid)

    fun observeJobsByStatus(status: String): Flow<List<JobEntity>> = jobDao.observeByStatus(status)

    /** Lectura puntual, sin Flow — para botones de prueba/debug, no para UI reactiva. */
    suspend fun findFirstJobByStatus(status: String): JobEntity? = jobDao.getFirstByStatus(status)

    /** Lectura puntual, sin Flow — usada para precargar el formulario de edición. */
    suspend fun getJob(jobUuid: String): JobEntity? = jobDao.getByUuid(jobUuid)

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

    /**
     * Crea un job offline-first (patrón comercial, Fase 6 Paso 2):
     * 1) Genera el `uuid` acá mismo (identidad definitiva del recurso, la
     *    misma que viaja en el body de POST /jobs).
     * 2) Inserta en Room YA, en estado "pending", para que la UI lo muestre
     *    al toque sin esperar red.
     * 3) Encola el comando POST /jobs con el mismo uuid en el payload, para
     *    que el servidor cree exactamente ese registro (idempotente por
     *    X-Command-Id, no por el uuid del job en sí).
     *
     * Devuelve el `uuid` generado para que la UI pueda navegar al detalle
     * del job recién creado sin esperar la respuesta del servidor.
     */
    suspend fun createJob(
        title: String,
        description: String?,
        address: String?,
        clientUuid: String?,
    ): String {
        val jobUuid = UUID.randomUUID().toString()
        val nowIso = isoNowUtc()
        val createdByUuid = sessionManager.userUuid.orEmpty()

        jobDao.upsertAll(
            listOf(
                JobEntity(
                    uuid = jobUuid,
                    clientUuid = clientUuid,
                    createdByUuid = createdByUuid,
                    title = title,
                    description = description,
                    status = "pending",
                    address = address,
                    scheduledAt = null,
                    startedAt = null,
                    finishedAt = null,
                    invoicedAt = null,
                    totalAmount = null,
                    amountPaid = "0",
                    cancelledAt = null,
                    createdAt = nowIso,
                    updatedAt = nowIso,
                ),
            ),
        )

        commandQueue.enqueue(
            endpointPath = "/jobs",
            httpMethod = "POST",
            payload = mapOf(
                "uuid" to jobUuid,
                "title" to title,
                "description" to description,
                "address" to address,
                "client_uuid" to clientUuid,
            ),
        )

        return jobUuid
    }

    /**
     * Actualiza los campos editables de un job ya existente (título,
     * descripción, dirección, cliente). Mismo patrón optimista + outbox que
     * `createJob`, pero contra `PATCH /jobs/:uuid`.
     */
    suspend fun updateJob(
        jobUuid: String,
        title: String,
        description: String?,
        address: String?,
        clientUuid: String?,
    ) {
        val job = jobDao.getByUuid(jobUuid) ?: return
        val nowIso = isoNowUtc()

        jobDao.upsertAll(
            listOf(
                job.copy(
                    title = title,
                    description = description,
                    address = address,
                    clientUuid = clientUuid,
                    updatedAt = nowIso,
                ),
            ),
        )

        commandQueue.enqueue(
            endpointPath = "/jobs/$jobUuid",
            httpMethod = "PATCH",
            payload = mapOf(
                "title" to title,
                "description" to description,
                "address" to address,
                "client_uuid" to clientUuid,
            ),
        )
    }

    /**
     * Cancela un job (rol comercial/admin, ver jobsActionsController.js).
     * Solo válido desde "pending"/"assigned" del lado del servidor — acá se
     * aplica el cambio optimista igual, y si el servidor rechaza (409) la
     * corrección real llega en el próximo `/sync` (última escritura gana).
     */
    suspend fun cancelJob(jobUuid: String) {
        val job = jobDao.getByUuid(jobUuid) ?: return
        val nowIso = isoNowUtc()

        jobDao.upsertAll(
            listOf(
                job.copy(
                    status = "cancelled",
                    cancelledAt = nowIso,
                    updatedAt = nowIso,
                ),
            ),
        )

        commandQueue.enqueue(
            endpointPath = "/jobs/$jobUuid/cancel",
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
