package com.gmp.offline.data.repository

import com.gmp.offline.data.local.dao.StaffDao
import com.gmp.offline.data.local.entities.StaffEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class StaffRepository @Inject constructor(
    private val staffDao: StaffDao,
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
}
