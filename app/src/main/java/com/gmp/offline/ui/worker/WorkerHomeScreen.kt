package com.gmp.offline.ui.worker

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gmp.offline.ui.comercial.JobsListContent
import com.gmp.offline.ui.common.GmpNavigationDrawer
import com.gmp.offline.ui.theme.SolarGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerHomeScreen(onLoggedOut: () -> Unit, onOpenJob: (String) -> Unit, onOpenNotes: () -> Unit, onOpenMpptCalculator: () -> Unit, onOpenConsumptionCalculator: () -> Unit, viewModel: WorkerJobsViewModel = hiltViewModel()) {
    val jobRows by viewModel.jobRows.collectAsStateWithLifecycle(); val statusCounts by viewModel.statusCounts.collectAsStateWithLifecycle(); val activeFilters by viewModel.activeFilters.collectAsStateWithLifecycle()
    var searchVisible by remember { mutableStateOf(false) }; var searchQuery by remember { mutableStateOf("") }
    GmpNavigationDrawer(viewModel.currentFullName, viewModel.currentCompanyName, { viewModel.syncNow() }, onOpenNotes, onOpenMpptCalculator, onOpenConsumptionCalculator, { viewModel.logout(onLoggedOut) }) { openDrawer ->
        Scaffold(topBar = { TopAppBar(title = { Text("Mis Montajes", style = MaterialTheme.typography.titleMedium) }, navigationIcon = { IconButton(onClick = openDrawer) { Icon(Icons.Filled.Menu, "Abrir menú", tint = SolarGreen) } }, actions = { IconButton(onClick = { searchVisible = !searchVisible; if (!searchVisible) searchQuery = "" }) { Icon(Icons.Filled.Search, "Buscar montaje", tint = SolarGreen) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)) }) { padding ->
            JobsListContent(jobRows, statusCounts, activeFilters, { viewModel.toggleStatusFilter(it) }, { viewModel.clearStatusFilters() }, onOpenJob, modifier = Modifier.padding(padding), emptyMessage = "No tienes montajes asignados por el momento.", showDescriptionInsteadOfPrice = true, searchVisible = searchVisible, searchQuery = searchQuery, onSearchQueryChange = { searchQuery = it }, onCloseSearch = { searchVisible = false; searchQuery = "" })
        }
    }
}
