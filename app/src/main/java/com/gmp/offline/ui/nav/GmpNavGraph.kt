package com.gmp.offline.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gmp.offline.data.repository.AuthRepository
import com.gmp.offline.ui.HomeScreen
import com.gmp.offline.ui.login.LoginScreen

// Rutas del grafo. Por ahora solo login + home; job_list/job_detail/staff/
// materials_catalog se agregan en los próximos pasos de la Fase 6, cuando
// se separen las pantallas por rol (ver punto 7.1 del plan).
object GmpRoutes {
    const val LOGIN = "login"
    const val HOME = "home"
}

@Composable
fun GmpNavGraph(
    authRepository: AuthRepository,
    navController: NavHostController = rememberNavController(),
) {
    val startDestination = if (authRepository.isLoggedIn) GmpRoutes.HOME else GmpRoutes.LOGIN

    NavHost(navController = navController, startDestination = startDestination) {
        composable(GmpRoutes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(GmpRoutes.HOME) {
                        // Saca "login" del back stack: apretar "atrás" en home
                        // no debe volver a la pantalla de login ya logueado.
                        popUpTo(GmpRoutes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(GmpRoutes.HOME) {
            HomeScreen(
                onLoggedOut = {
                    navController.navigate(GmpRoutes.LOGIN) {
                        popUpTo(GmpRoutes.HOME) { inclusive = true }
                    }
                },
            )
        }
    }
}
