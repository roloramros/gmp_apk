package com.gmp.offline.data.repository

import com.gmp.offline.data.local.GmpDatabase
import com.gmp.offline.data.remote.ApiService
import com.gmp.offline.data.remote.dto.CompanyDto
import com.gmp.offline.data.remote.dto.LoginRequest
import com.gmp.offline.data.session.SessionManager
import com.gmp.offline.data.session.SyncCursorStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val sessionManager: SessionManager,
    private val syncCursorStore: SyncCursorStore,
    private val database: GmpDatabase,
) {
    suspend fun login(companyId: Int, phone: String, password: String) {
        val response = apiService.login(LoginRequest(companyId, phone, password))

        // Se limpia TODO el estado local (Room + cursor de sync) antes de
        // guardar la sesión nueva, sin importar si la empresa cambió o es
        // la misma. Es lo que evita el bug reportado: loguearse con la
        // empresa A, cerrar sesión (o no) y loguearse con la empresa B
        // mostraba los datos de A hasta el próximo full resync, porque
        // SyncEngine.pull(forceFullResync = true) solo hace upsert de lo
        // que trae /sync — nunca borraba filas que ya no pertenecen a la
        // empresa nueva (tienen uuids distintos, así que el upsert no las
        // toca). Al forzar el wipe acá, el full resync que sigue en
        // LoginViewModel repuebla Room desde cero solo con datos de la
        // empresa que acaba de loguearse.
        wipeLocalData()

        sessionManager.token = response.token
        sessionManager.userUuid = response.user.uuid
        sessionManager.role = response.user.role
        sessionManager.fullName = response.user.fullName
        sessionManager.companyId = response.user.companyId
    }

    /** Selector de empresa en el login: GET /companies (listado público). */
    suspend fun listCompanies(): List<CompanyDto> = apiService.listCompanies()

    val isLoggedIn: Boolean
        get() = sessionManager.token != null

    val currentRole: String?
        get() = sessionManager.role

    val currentFullName: String?
        get() = sessionManager.fullName

    suspend fun logout() {
        // Redundante con el wipe en login() para el caso de cambio manual
        // de cuenta, pero se hace también acá para no dejar datos de la
        // empresa saliente en Room mientras la app está deslogueada (por
        // ejemplo, si alguien inspecciona la base con un debugger, o si
        // una fase futura agrega una pantalla que lea Room sin chequear
        // sesión primero).
        wipeLocalData()
        sessionManager.clear()
    }

    private suspend fun wipeLocalData() {
        withContext(Dispatchers.IO) {
            database.clearAllTables()
        }
        syncCursorStore.clear()
    }
}
