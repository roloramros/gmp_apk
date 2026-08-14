package com.gmp.offline.data.repository

import com.gmp.offline.data.local.dao.StaffDao
import com.gmp.offline.data.local.entities.StaffEntity
import com.gmp.offline.data.remote.ApiService
import com.gmp.offline.data.remote.apiCall
import com.gmp.offline.data.remote.dto.CreateStaffRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

class StaffRepository @Inject constructor(
    private val staffDao: StaffDao,
    private val apiService: ApiService,
) {
    fun observeStaff(): Flow<List<StaffEntity>> = staffDao.observeAll()

    fun observeStaffMember(uuid: String): Flow<StaffEntity?> = staffDao.observeByUuid(uuid)

    /**
     * Clientes de la empresa (role = "cliente"), para el selector al crear/
     * editar un job. `staff` en /sync incluye todos los `users` visibles
     * para el rol del token (ver tabla de visibilidad, fase1-diseno-datos-sync.md
     * sección 5) — para comercial/admin eso incluye a los clientes.
     */
    fun observeClients(): Flow<List<StaffEntity>> =
        staffDao.observeAll().map { list -> list.filter { it.role == "cliente" && it.active } }

    /**
     * Crea un usuario de personal (admin/comercial/trabajador). A
     * diferencia de jobs/materiales, esto NO pasa por el outbox offline:
     * staffController.js genera el `uuid` en el servidor (no lo acepta
     * del cliente) y la ruta no está envuelta en el middleware de
     * idempotencia, así que no hay forma segura de precargarlo en Room
     * de forma optimista ni de reintentarlo por X-Command-Id. Es una
     * llamada directa que requiere red — igual que hace la web legada
     * (`createStaff()` en index.html) — y recién se refleja en Room
     * cuando el servidor confirma con el uuid real.
     */
    suspend fun createStaff(fullName: String, phone: String, password: String, role: String) {
        val response = apiCall {
            apiService.createStaff(CreateStaffRequest(phone = phone, password = password, role = role, fullName = fullName))
        }
        val nowIso = response.createdAt ?: isoNowUtc()
        staffDao.upsertAll(
            listOf(
                StaffEntity(
                    uuid = response.uuid,
                    phone = response.phone ?: phone,
                    role = response.role ?: role,
                    fullName = response.fullName ?: fullName,
                    active = response.active,
                    createdAt = nowIso,
                    updatedAt = nowIso,
                ),
            ),
        )
    }

    /**
     * Desactivación (soft, `active = false` — el backend nunca borra
     * staff). Mismo motivo que `createStaff`: llamada directa, no
     * offline-queued. La respuesta de POST /staff/:uuid/deactivate solo
     * trae uuid/full_name/active (ver staffController.js), así que se
     * parte de la fila que ya está en Room y se le pisa `active`/`updatedAt`
     * en vez de reconstruir la entidad entera desde una respuesta parcial.
     */
    suspend fun deactivateStaffMember(uuid: String) {
        apiCall { apiService.deactivateStaff(uuid) }
        val existing = staffDao.getByUuid(uuid) ?: return
        staffDao.upsertAll(listOf(existing.copy(active = false, updatedAt = isoNowUtc())))
    }

    private fun isoNowUtc(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }
}
