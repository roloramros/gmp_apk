package com.gmp.offline.data.session

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Guarda el token de sesión (JWT de usuario de empresa, 365 días de validez
// según lo cerrado en Fase 2) localmente.
//
// NOTA de seguridad: por ahora usa SharedPreferences simple. Migrar a
// EncryptedSharedPreferences (androidx.security:security-crypto) es una
// mejora recomendada antes de distribuir la app fuera de pruebas internas,
// pero no bloquea probar la capa Room de esta fase.
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gmp_session", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var userUuid: String?
        get() = prefs.getString(KEY_USER_UUID, null)
        set(value) = prefs.edit().putString(KEY_USER_UUID, value).apply()

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_USER_UUID = "user_uuid"
    }
}
