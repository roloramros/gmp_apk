package com.gmp.offline.data.session

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Guarda el `cursor` devuelto por el último GET /sync exitoso (con
// has_more:false), para que la próxima sincronización sea incremental de
// verdad en vez de pedir un full dump cada vez. Ver sección 3.1 del plan y
// §5.1 de fase3-jobs-materiales-sync.md.
@Singleton
class SyncCursorStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gmp_sync_state", Context.MODE_PRIVATE)

    var lastCursor: String?
        get() = prefs.getString(KEY_CURSOR, null)
        set(value) = prefs.edit().putString(KEY_CURSOR, value).apply()

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_CURSOR = "last_cursor"
    }
}
