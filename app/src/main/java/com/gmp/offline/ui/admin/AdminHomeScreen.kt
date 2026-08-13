package com.gmp.offline.ui.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gmp.offline.ui.comercial.ComercialJobsListViewModel
import com.gmp.offline.ui.comercial.JobsListContent
import com.gmp.offline.ui.theme.SolarAmber
import com.gmp.offline.ui.theme.SolarGreen
import com.gmp.offline.ui.theme.SolarGreenDark

// Pantalla principal del rol admin: 3 pestañas — Montajes, Personal,
// Materiales — según lo acordado en Fase 6 Paso 5. La pestaña "Gestión de
// Montajes" reusa EXACTAMENTE el mismo contenido que ve el rol comercial
// (JobsListContent, definido en ui.comercial junto con
// ComercialJobsListScreen), incluida la misma ComercialJobsListViewModel:
// no hay ninguna diferencia de negocio todavía entre lo que ve un admin y
// un comercial en la lista de montajes, así que reusar en vez de duplicar
// evita tener que mantener dos copias de la barra de filtros y las filas.
// Cuando haya que agregar algo admin-only en esta pestaña (por ejemplo
// asignar trabajador, facturar, pagar), se decide ahí si conviene separar
// una ViewModel/composable propios o extender los existentes con un flag
// de rol — no se anticipa esa decisión ahora.
//
// Las pestañas "Gestión de Personal" y "Gestión de Materiales" son
// placeholders: se completan en los próximos pasos de Fase 6.
private enum class AdminTab(val label: String) {
    MONTAJES("Montajes"),
    PERSONAL("Personal"),
    MATERIALES("Materiales"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHomeScreen(
    onLoggedOut: () -> Unit,
    onCreateJob: () -> Unit,
    onOpenJob: (String) -> Unit,
    jobsViewModel: ComercialJobsListViewModel = hiltViewModel(),
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val currentTab = AdminTab.entries[selectedTab]

    val jobRows by jobsViewModel.jobRows.collectAsStateWithLifecycle()
    val statusCounts by jobsViewModel.statusCounts.collectAsStateWithLifecycle()
    val activeFilters by jobsViewModel.activeFilters.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("GM Pro · Administración", style = MaterialTheme.typography.titleMedium)
                        Text(
                            jobsViewModel.currentFullName ?: "Admin",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { jobsViewModel.syncNow() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Sincronizar ahora", tint = SolarGreen)
                    }
                    TextButton(onClick = { jobsViewModel.logout(onLoggedOut) }) {
                        Text("Salir")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            // Por ahora solo la pestaña de Montajes tiene acción de "+"
            // (crear job). Personal y Materiales van a tener la suya
            // propia cuando se construyan esas pestañas.
            if (currentTab == AdminTab.MONTAJES) {
                FloatingActionButton(
                    onClick = onCreateJob,
                    containerColor = SolarAmber,
                    contentColor = SolarGreenDark,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Nuevo montaje")
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = SolarGreen,
            ) {
                AdminTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(tab.label) },
                    )
                }
            }

            when (currentTab) {
                AdminTab.MONTAJES -> JobsListContent(
                    jobRows = jobRows,
                    statusCounts = statusCounts,
                    activeFilters = activeFilters,
                    onToggle = { jobsViewModel.toggleStatusFilter(it) },
                    onClear = { jobsViewModel.clearStatusFilters() },
                    onOpenJob = onOpenJob,
                )
                AdminTab.PERSONAL -> AdminTabPlaceholder("Gestión de Personal")
                AdminTab.MATERIALES -> AdminTabPlaceholder("Gestión de Materiales")
            }
        }
    }
}

@Composable
private fun AdminTabPlaceholder(label: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$label — próximamente",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
