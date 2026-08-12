package com.gmp.offline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.gmp.offline.data.repository.AuthRepository
import com.gmp.offline.ui.nav.GmpNavGraph
import com.gmp.offline.ui.theme.GmpTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// Desde Fase 6: la navegación real vive en GmpNavGraph (login -> home,
// arranque condicionado por sesión existente). La pantalla única de debug
// de Fase 4/5 pasó a ser HomeScreen (post-login), como paso intermedio
// hasta separar pantallas reales por rol.
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Se inyecta directo (no vía ViewModel) porque solo se necesita leer
    // isLoggedIn una vez, al decidir el startDestination del NavHost.
    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GmpTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GmpNavGraph(authRepository = authRepository)
                }
            }
        }
    }
}
