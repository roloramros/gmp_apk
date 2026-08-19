package com.gmp.offline.ui.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
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
import com.gmp.offline.ui.common.GmpNavigationDrawer
import com.gmp.offline.ui.theme.SolarAmber
import com.gmp.offline.ui.theme.SolarGreen
import com.gmp.offline.ui.theme.SolarGreenDark

private enum class AdminTab(val label: String) { MONTAJES("Montajes"), PERSONAL("Personal"), MATERIALES("Materiales") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHomeScreen(
    onLoggedOut: () -> Unit,
    onCreateJob: () -> Unit,
    onOpenJob: (String) -> Unit,
    onOpenNotes: () -> Unit,
    onOpenMpptCalculator: () -> Unit,
    onOpenConsumptionCalculator: () -> Unit,
    jobsViewModel: ComercialJobsListViewModel = hiltViewModel(),
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val currentTab = AdminTab.entries[selectedTab]
    val jobRows by jobsViewModel.jobRows.collectAsStateWithLifecycle()
    val statusCounts by jobsViewModel.statusCounts.collectAsStateWithLifecycle()
    val activeFilters by jobsViewModel.activeFilters.collectAsStateWithLifecycle()
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    fun closeSearch() { searchVisible = false; searchQuery = "" }

    GmpNavigationDrawer(
        fullName = jobsViewModel.currentFullName, companyName = jobsViewModel.currentCompanyName,
        onSync = { jobsViewModel.syncNow() }, onOpenNotes = onOpenNotes,
        onOpenMpptCalculator = onOpenMpptCalculator, onOpenConsumptionCalculator = onOpenConsumptionCalculator,
        onLogout = { jobsViewModel.logout(onLoggedOut) },
    ) { openDrawer ->
        Scaffold(
            topBar = { TopAppBar(
                title = { Text("GM Pro · Administración", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = openDrawer) { Icon(Icons.Filled.Menu, "Abrir menú", tint = SolarGreen) } },
                actions = { IconButton(onClick = { searchVisible = !searchVisible; if (!searchVisible) searchQuery = "" }) {
                    val description = when (currentTab) { AdminTab.MONTAJES -> "Buscar montaje"; AdminTab.PERSONAL -> "Buscar personal"; AdminTab.MATERIALES -> "Buscar material" }
                    Icon(Icons.Filled.Search, description, tint = SolarGreen)
                } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            ) },
            floatingActionButton = { if (currentTab == AdminTab.MONTAJES) FloatingActionButton(onClick = onCreateJob, containerColor = SolarAmber, contentColor = SolarGreenDark) { Icon(Icons.Filled.Add, "Nuevo montaje") } },
        ) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding)) {
                TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface, contentColor = SolarGreen) {
                    AdminTab.entries.forEachIndexed { index, tab -> Tab(selected = selectedTab == index, onClick = { selectedTab = index; closeSearch() }, text = { Text(tab.label) }) }
                }
                if (searchVisible && currentTab != AdminTab.MONTAJES) AdminSearchBar(
                    query = searchQuery,
                    placeholder = when (currentTab) { AdminTab.PERSONAL -> "Buscar por nombre"; AdminTab.MATERIALES -> "Buscar material por nombre"; AdminTab.MONTAJES -> "Buscar por nombre" },
                    onQueryChange = { searchQuery = it }, onClose = { closeSearch() },
                )
                when (currentTab) {
                    AdminTab.MONTAJES -> JobsListContent(jobRows, statusCounts, activeFilters, { jobsViewModel.toggleStatusFilter(it) }, { jobsViewModel.clearStatusFilters() }, onOpenJob, { uuid, status -> jobsViewModel.regularizeJob(uuid, status) }, { jobsViewModel.deleteJobPermanently(it) }, searchVisible = searchVisible, searchQuery = searchQuery, onSearchQueryChange = { searchQuery = it }, onCloseSearch = { closeSearch() })
                    AdminTab.PERSONAL -> StaffTabContent(searchQuery)
                    AdminTab.MATERIALES -> MaterialsTabContent(searchQuery)
                }
            }
        }
    }
}
