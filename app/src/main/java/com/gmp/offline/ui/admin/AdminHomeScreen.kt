package com.gmp.offline.ui.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gmp.offline.ui.comercial.ComercialJobsListViewModel
import com.gmp.offline.ui.comercial.JobsListContent
import com.gmp.offline.ui.theme.SolarAmber
import com.gmp.offline.ui.theme.SolarGreen
import com.gmp.offline.ui.theme.SolarGreenDark

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
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

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
                    if (currentTab == AdminTab.MONTAJES) {
                        IconButton(onClick = {
                            searchVisible = !searchVisible
                            if (!searchVisible) searchQuery = ""
                        }) {
                            Icon(Icons.Filled.Search, contentDescription = "Buscar montaje", tint = SolarGreen)
                        }
                    }
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
                        onClick = {
                            selectedTab = index
                            if (AdminTab.entries[index] != AdminTab.MONTAJES) {
                                searchVisible = false
                                searchQuery = ""
                            }
                        },
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
                    searchVisible = searchVisible,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onCloseSearch = {
                        searchVisible = false
                        searchQuery = ""
                    },
                )
                AdminTab.PERSONAL -> StaffTabContent()
                AdminTab.MATERIALES -> MaterialsTabContent()
            }
        }
    }
}
