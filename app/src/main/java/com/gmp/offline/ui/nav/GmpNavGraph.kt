package com.gmp.offline.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gmp.offline.data.repository.AuthRepository
import com.gmp.offline.ui.HomeScreen
import com.gmp.offline.ui.admin.AdminHomeScreen
import com.gmp.offline.ui.comercial.ComercialJobsListScreen
import com.gmp.offline.ui.comercial.JobDetailScreen
import com.gmp.offline.ui.comercial.JobFormScreen
import com.gmp.offline.ui.login.LoginScreen

// Rutas del grafo. Desde Fase 6 Paso 2: se agregan job_form y job_detail
// para el flujo del rol comercial. Desde Paso 5: rol admin tiene su propia
// pantalla (AdminHomeScreen, con pestañas) pero reusa las mismas rutas
// job_form/job_detail que comercial, porque esas pantallas son de negocio
// puro (sin gating por rol). HOME sigue existiendo como router: elige qué
// pantalla mostrar según el rol de la sesión. trabajador sigue viendo el
// placeholder de debug hasta que se construya su pantalla (fuera de
// alcance de este paso).
object GmpRoutes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val JOB_FORM = "job_form?jobUuid={jobUuid}"
    const val JOB_DETAIL = "job_detail/{jobUuid}"

    fun jobForm(jobUuid: String? = null): String =
        if (jobUuid != null) "job_form?jobUuid=$jobUuid" else "job_form"

    fun jobDetail(jobUuid: String): String = "job_detail/$jobUuid"
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
            // Router por rol: comercial y admin ya tienen pantallas reales
            // (Fase 6, Pasos 2-5). Trabajador sigue viendo el placeholder
            // de debug hasta que se construya su pantalla.
            when (authRepository.currentRole) {
                "comercial" -> ComercialJobsListScreen(
                    onLoggedOut = {
                        navController.navigate(GmpRoutes.LOGIN) {
                            popUpTo(GmpRoutes.HOME) { inclusive = true }
                        }
                    },
                    onCreateJob = {
                        navController.navigate(GmpRoutes.jobForm())
                    },
                    onOpenJob = { jobUuid ->
                        navController.navigate(GmpRoutes.jobDetail(jobUuid))
                    },
                )
                "admin" -> AdminHomeScreen(
                    onLoggedOut = {
                        navController.navigate(GmpRoutes.LOGIN) {
                            popUpTo(GmpRoutes.HOME) { inclusive = true }
                        }
                    },
                    onCreateJob = {
                        navController.navigate(GmpRoutes.jobForm())
                    },
                    onOpenJob = { jobUuid ->
                        navController.navigate(GmpRoutes.jobDetail(jobUuid))
                    },
                )
                else -> HomeScreen(
                    onLoggedOut = {
                        navController.navigate(GmpRoutes.LOGIN) {
                            popUpTo(GmpRoutes.HOME) { inclusive = true }
                        }
                    },
                )
            }
        }
        composable(
            route = GmpRoutes.JOB_FORM,
            arguments = listOf(
                navArgument("jobUuid") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            JobFormScreen(
                onBack = { navController.popBackStack() },
                onSaved = { jobUuid ->
                    // Al guardar, vuelve al detalle del job (nuevo o
                    // editado), sacando el formulario del back stack para
                    // que "atrás" desde el detalle no vuelva a abrirlo.
                    navController.navigate(GmpRoutes.jobDetail(jobUuid)) {
                        popUpTo(GmpRoutes.JOB_FORM) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = GmpRoutes.JOB_DETAIL,
            arguments = listOf(navArgument("jobUuid") { type = NavType.StringType }),
        ) {
            JobDetailScreen(
                onBack = { navController.popBackStack() },
                onEditJob = { jobUuid -> navController.navigate(GmpRoutes.jobForm(jobUuid)) },
            )
        }
    }
}
