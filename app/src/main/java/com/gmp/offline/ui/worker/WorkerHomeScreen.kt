package com.gmp.offline.ui.worker

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gmp.offline.ui.comercial.JobsListContent
import com.gmp.offline.ui.common.GmpNavigationDrawer
import com.gmp.offline.ui.theme.SolarGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerHomeScreen(
    onLoggedOut: () -> Unit,
    onOpenJob: (String) -> Unit,
    onOpenMpptCalculator: () -> Unit,
    onOpenConsumptionCalculator: () -> Unit,
    viewModel: WorkerJobsViewModel = hiltViewModel(),
) {
    val jobRows by viewModel.jobRows.collectAsStateWithLifecycle()
    val statusCounts by viewModel.statusCounts.collectAsStateWithLifecycle()
    val activeFilters by viewModel.activeFilters.collectAsStateWithLifecycle()
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    GmpNavigationDrawer(
        fullName = viewModel.currentFullName,
        companyName = viewModel.currentCompanyName,
        onSync = { viewModel.syncNow() },
        onOpenMpptCalculator = onOpenMpptCalculator,
        onOpenConsumptionCalculator = onOpenConsumptionCalculator,
        onLogout = { viewModel.logout(onLoggedOut) },
    ) { openDrawer ->
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("Mis Montajes", style = MaterialTheme.typography.titleMedium)
                    },
                    navigationIcon = {
                        IconButton(onClick = openDrawer) {
                            Icon(Icons.Filled.Menu, contentDescription = "Abrir menú", tint = SolarGreen)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            searchVisible = !searchVisible
                            if (!searchVisible) searchQuery = ""
                        }) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = "Buscar montaje",
                                tint = SolarGreen,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { innerPadding ->
            JobsListContent(
                jobRows = jobRows,
                statusCounts = statusCounts,
                activeFilters = activeFilters,
                onToggle = { viewModel.toggleStatusFilter(it) },
                onClear = { viewModel.clearStatusFilters() },
                onOpenJob = onOpenJob,
                modifier = Modifier.padding(innerPadding),
                emptyMessage = "No tienes montajes asignados por el momento.",
                showDescriptionInsteadOfPrice = true,
                searchVisible = searchVisible,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onCloseSearch = {
                    searchVisible = false
                    searchQuery = ""
                },
            )
        }
    }
}
