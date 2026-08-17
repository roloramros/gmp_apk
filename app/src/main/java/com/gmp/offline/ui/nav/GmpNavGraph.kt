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
import com.gmp.offline.ui.calculators.CalculatorPlaceholderScreen
import com.gmp.offline.ui.comercial.ComercialJobsListScreen
import com.gmp.offline.ui.comercial.JobDetailScreen
import com.gmp.offline.ui.comercial.JobFormScreen
import com.gmp.offline.ui.login.LoginScreen
import com.gmp.offline.ui.mppt.MpptCalculatorScreen
import com.gmp.offline.ui.worker.WorkerHomeScreen
import com.gmp.offline.ui.worker.WorkerJobDetailScreen

object GmpRoutes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val JOB_FORM = "job_form?jobUuid={jobUuid}"
    const val JOB_DETAIL = "job_detail/{jobUuid}"
    const val CALCULATOR_MPPT = "calculator_mppt"
    const val CALCULATOR_CONSUMPTION = "calculator_consumption"

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

    val openMpptCalculator = { navController.navigate(GmpRoutes.CALCULATOR_MPPT) }
    val openConsumptionCalculator = { navController.navigate(GmpRoutes.CALCULATOR_CONSUMPTION) }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(GmpRoutes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(GmpRoutes.HOME) {
                        popUpTo(GmpRoutes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
        composable(GmpRoutes.HOME) {
            when (authRepository.currentRole) {
                "comercial" -> ComercialJobsListScreen(
                    onLoggedOut = {
                        navController.navigate(GmpRoutes.LOGIN) {
                            popUpTo(GmpRoutes.HOME) { inclusive = true }
                        }
                    },
                    onCreateJob = { navController.navigate(GmpRoutes.jobForm()) },
                    onOpenJob = { jobUuid -> navController.navigate(GmpRoutes.jobDetail(jobUuid)) },
                    onOpenMpptCalculator = openMpptCalculator,
                    onOpenConsumptionCalculator = openConsumptionCalculator,
                )
                "admin" -> AdminHomeScreen(
                    onLoggedOut = {
                        navController.navigate(GmpRoutes.LOGIN) {
                            popUpTo(GmpRoutes.HOME) { inclusive = true }
                        }
                    },
                    onCreateJob = { navController.navigate(GmpRoutes.jobForm()) },
                    onOpenJob = { jobUuid -> navController.navigate(GmpRoutes.jobDetail(jobUuid)) },
                    onOpenMpptCalculator = openMpptCalculator,
                    onOpenConsumptionCalculator = openConsumptionCalculator,
                )
                "trabajador" -> WorkerHomeScreen(
                    onLoggedOut = {
                        navController.navigate(GmpRoutes.LOGIN) {
                            popUpTo(GmpRoutes.HOME) { inclusive = true }
                        }
                    },
                    onOpenJob = { jobUuid -> navController.navigate(GmpRoutes.jobDetail(jobUuid)) },
                    onOpenMpptCalculator = openMpptCalculator,
                    onOpenConsumptionCalculator = openConsumptionCalculator,
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
            if (authRepository.currentRole == "trabajador") {
                WorkerJobDetailScreen(
                    onBack = { navController.popBackStack() },
                )
            } else {
                JobDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEditJob = { jobUuid -> navController.navigate(GmpRoutes.jobForm(jobUuid)) },
                )
            }
        }
        composable(GmpRoutes.CALCULATOR_MPPT) {
            MpptCalculatorScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(GmpRoutes.CALCULATOR_CONSUMPTION) {
            CalculatorPlaceholderScreen(
                title = "Calculadora de Consumo",
                onBack = { navController.popBackStack() },
            )
        }
    }
}
