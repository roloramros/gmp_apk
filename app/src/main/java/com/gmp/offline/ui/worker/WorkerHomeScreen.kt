package com.gmp.offline.ui.worker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gmp.offline.ui.comercial.JobsListContent
import com.gmp.offline.ui.theme.SolarGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerHomeScreen(
    onLoggedOut: () -> Unit,
    onOpenJob: (String) -> Unit,
    viewModel: WorkerJobsViewModel = hiltViewModel(),
) {
    val jobRows by viewModel.jobRows.collectAsStateWithLifecycle()
    val statusCounts by viewModel.statusCounts.collectAsStateWithLifecycle()
    val activeFilters by viewModel.activeFilters.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mis Montajes", style = MaterialTheme.typography.titleMedium)
                        Text(
                            viewModel.currentFullName ?: "Trabajador",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.syncNow() }) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Sincronizar ahora",
                            tint = SolarGreen,
                        )
                    }
                    TextButton(onClick = { viewModel.logout(onLoggedOut) }) {
                        Text("Salir")
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
        )
    }
}
