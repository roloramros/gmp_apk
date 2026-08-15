package com.gmp.offline.ui.comercial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gmp.offline.ui.common.jobStatusColor
import com.gmp.offline.ui.common.jobStatusLabel
import com.gmp.offline.ui.theme.SolarAmber
import com.gmp.offline.ui.theme.SolarGreen
import com.gmp.offline.ui.theme.SolarGreenDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComercialJobsListScreen(
    onLoggedOut: () -> Unit,
    onCreateJob: () -> Unit,
    onOpenJob: (String) -> Unit,
    viewModel: ComercialJobsListViewModel = hiltViewModel(),
) {
    val jobRows by viewModel.jobRows.collectAsStateWithLifecycle()
    val statusCounts by viewModel.statusCounts.collectAsStateWithLifecycle()
    val activeFilters by viewModel.activeFilters.collectAsStateWithLifecycle()
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Planificación de Montajes", style = MaterialTheme.typography.titleMedium)
                        Text(
                            viewModel.currentFullName ?: "Comercial",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searchVisible = !searchVisible
                        if (!searchVisible) searchQuery = ""
                    }) {
                        Icon(Icons.Filled.Search, contentDescription = "Buscar montaje", tint = SolarGreen)
                    }
                    IconButton(onClick = { viewModel.syncNow() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Sincronizar ahora", tint = SolarGreen)
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
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateJob, containerColor = SolarAmber, contentColor = SolarGreenDark) {
                Icon(Icons.Filled.Add, contentDescription = "Nuevo montaje")
            }
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

@Composable
fun JobsListContent(
    jobRows: List<ComercialJobRow>,
    statusCounts: Map<String, Int>,
    activeFilters: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onOpenJob: (String) -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String = "Aún no hay montajes registrados. Tocá el + para crear el primero.",
    showDescriptionInsteadOfPrice: Boolean = false,
    searchVisible: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onCloseSearch: () -> Unit = {},
) {
    val normalizedQuery = searchQuery.trim()
    val visibleRows = if (normalizedQuery.isBlank()) {
        jobRows
    } else {
        jobRows.filter { row ->
            val name = row.clientName ?: row.job.clientName ?: row.job.title
            name.contains(normalizedQuery, ignoreCase = true)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (searchVisible) {
            JobSearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onClose = onCloseSearch,
            )
        }

        StatusFilterBar(
            counts = statusCounts,
            activeFilters = activeFilters,
            onToggle = onToggle,
            onClear = onClear,
        )

        if (visibleRows.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        normalizedQuery.isNotBlank() -> "No hay montajes que coincidan con \"$normalizedQuery\"."
                        activeFilters.isEmpty() -> emptyMessage
                        else -> "No hay montajes con los filtros seleccionados."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visibleRows, key = { it.job.uuid }) { row ->
                    JobRowCard(
                        row = row,
                        onClick = { onOpenJob(row.job.uuid) },
                        showDescriptionInsteadOfPrice = showDescriptionInsteadOfPrice,
                    )
                }
            }
        }
    }
}

@Composable
private fun JobSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            singleLine = true,
            placeholder = { Text("Buscar por nombre") },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null, tint = SolarGreen)
            },
            trailingIcon = {
                IconButton(onClick = {
                    if (query.isNotEmpty()) onQueryChange("") else onClose()
                }) {
                    Icon(Icons.Filled.Clear, contentDescription = if (query.isNotEmpty()) "Limpiar búsqueda" else "Cerrar búsqueda")
                }
            },
        )
    }
}

@Composable
private fun StatusFilterBar(
    counts: Map<String, Int>,
    activeFilters: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(JOB_STATUS_ORDER) { status ->
            val isActive = activeFilters.contains(status)
            val color = jobStatusColor(status)
            FilterChip(
                selected = isActive,
                onClick = { onToggle(status) },
                label = { Text("${jobStatusLabel(status)} · ${counts[status] ?: 0}") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color,
                    selectedLabelColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
        if (activeFilters.isNotEmpty()) {
            item {
                TextButton(onClick = onClear) {
                    Text("Quitar filtros ✕")
                }
            }
        }
    }
}

@Composable
private fun JobRowCard(
    row: ComercialJobRow,
    onClick: () -> Unit,
    showDescriptionInsteadOfPrice: Boolean = false,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.clientName ?: row.job.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(status = row.job.status)
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (showDescriptionInsteadOfPrice) row.job.description ?: "—" else row.job.price?.let { "${'$'}$it" } ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    row.job.scheduledAt?.take(10) ?: "sin fecha",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            if (!row.job.address.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    row.job.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (row.pendingSync) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "⏳ Pendiente de sincronizar",
                    style = MaterialTheme.typography.labelSmall,
                    color = SolarAmber,
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val color = jobStatusColor(status)
    Box(
        modifier = Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(50)),
    ) {
        Text(
            jobStatusLabel(status),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
