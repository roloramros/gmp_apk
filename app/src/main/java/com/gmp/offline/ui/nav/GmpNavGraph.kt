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
import com.gmp.offline.ui.notes.NoteEditorScreen
import com.gmp.offline.ui.notes.NotesListScreen
import com.gmp.offline.ui.worker.WorkerHomeScreen
import com.gmp.offline.ui.worker.WorkerJobDetailScreen

object GmpRoutes {
    const val LOGIN = "login"; const val HOME = "home"
    const val JOB_FORM = "job_form?jobUuid={jobUuid}"; const val JOB_DETAIL = "job_detail/{jobUuid}"
    const val NOTES = "notes"; const val NOTE_EDITOR = "note_editor?noteUuid={noteUuid}"
    const val CALCULATOR_MPPT = "calculator_mppt"; const val CALCULATOR_CONSUMPTION = "calculator_consumption"
    fun jobForm(jobUuid: String? = null) = if (jobUuid != null) "job_form?jobUuid=$jobUuid" else "job_form"
    fun jobDetail(jobUuid: String) = "job_detail/$jobUuid"
    fun noteEditor(noteUuid: String? = null) = if (noteUuid != null) "note_editor?noteUuid=$noteUuid" else "note_editor"
}

@Composable
fun GmpNavGraph(authRepository: AuthRepository, navController: NavHostController = rememberNavController()) {
    val startDestination = if (authRepository.isLoggedIn) GmpRoutes.HOME else GmpRoutes.LOGIN
    val openNotes = { navController.navigate(GmpRoutes.NOTES) }
    val openMppt = { navController.navigate(GmpRoutes.CALCULATOR_MPPT) }
    val openConsumption = { navController.navigate(GmpRoutes.CALCULATOR_CONSUMPTION) }
    val logout = { navController.navigate(GmpRoutes.LOGIN) { popUpTo(GmpRoutes.HOME) { inclusive = true } } }

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
                    onLoggedOut = logout,
                    onCreateJob = { navController.navigate(GmpRoutes.jobForm()) },
                    onOpenJob = { navController.navigate(GmpRoutes.jobDetail(it)) },
                    onOpenNotes = openNotes,
                    onOpenMpptCalculator = openMppt,
                    onOpenConsumptionCalculator = openConsumption,
                )
                "admin" -> AdminHomeScreen(
                    onLoggedOut = logout,
                    onCreateJob = { navController.navigate(GmpRoutes.jobForm()) },
                    onOpenJob = { navController.navigate(GmpRoutes.jobDetail(it)) },
                    onOpenNotes = openNotes,
                    onOpenMpptCalculator = openMppt,
                    onOpenConsumptionCalculator = openConsumption,
                )
                "trabajador" -> WorkerHomeScreen(
                    onLoggedOut = logout,
                    onOpenJob = { navController.navigate(GmpRoutes.jobDetail(it)) },
                    onOpenNotes = openNotes,
                    onOpenMpptCalculator = openMppt,
                    onOpenConsumptionCalculator = openConsumption,
                )
                else -> HomeScreen(onLoggedOut = logout)
            }
        }

        composable(
            route = GmpRoutes.JOB_FORM,
            arguments = listOf(navArgument("jobUuid") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }),
        ) {
            JobFormScreen(
                onBack = { navController.popBackStack() },
                onSaved = { uuid ->
                    navController.navigate(GmpRoutes.jobDetail(uuid)) {
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
                WorkerJobDetailScreen(onBack = { navController.popBackStack() })
            } else {
                JobDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEditJob = { navController.navigate(GmpRoutes.jobForm(it)) },
                )
            }
        }

        composable(GmpRoutes.NOTES) {
            NotesListScreen(
                onBack = { navController.popBackStack() },
                onCreate = { navController.navigate(GmpRoutes.noteEditor()) },
                onOpen = { navController.navigate(GmpRoutes.noteEditor(it)) },
            )
        }

        composable(
            route = GmpRoutes.NOTE_EDITOR,
            arguments = listOf(navArgument("noteUuid") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }),
        ) { entry ->
            NoteEditorScreen(
                noteUuid = entry.arguments?.getString("noteUuid"),
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(GmpRoutes.CALCULATOR_MPPT) {
            MpptCalculatorScreen(onBack = { navController.popBackStack() })
        }

        composable(GmpRoutes.CALCULATOR_CONSUMPTION) {
            CalculatorPlaceholderScreen(
                title = "Calculadora de Consumo",
                onBack = { navController.popBackStack() },
            )
        }
    }
}
