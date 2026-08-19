package com.gmp.offline.ui.comercial

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gmp.offline.ui.common.GmpNavigationDrawer
import com.gmp.offline.ui.common.jobStatusColor
import com.gmp.offline.ui.common.jobStatusLabel
import com.gmp.offline.ui.theme.SolarAmber
import com.gmp.offline.ui.theme.SolarGreen
import com.gmp.offline.ui.theme.SolarGreenDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComercialJobsListScreen(onLoggedOut: () -> Unit, onCreateJob: () -> Unit, onOpenJob: (String) -> Unit, onOpenNotes: () -> Unit, onOpenMpptCalculator: () -> Unit, onOpenConsumptionCalculator: () -> Unit, viewModel: ComercialJobsListViewModel = hiltViewModel()) {
    val jobRows by viewModel.jobRows.collectAsStateWithLifecycle(); val statusCounts by viewModel.statusCounts.collectAsStateWithLifecycle(); val activeFilters by viewModel.activeFilters.collectAsStateWithLifecycle()
    var searchVisible by remember { mutableStateOf(false) }; var searchQuery by remember { mutableStateOf("") }
    GmpNavigationDrawer(viewModel.currentFullName, viewModel.currentCompanyName, { viewModel.syncNow() }, onOpenNotes, onOpenMpptCalculator, onOpenConsumptionCalculator, { viewModel.logout(onLoggedOut) }) { openDrawer ->
        Scaffold(topBar = { TopAppBar(title = { Text("Planificación de Montajes", style = MaterialTheme.typography.titleMedium) }, navigationIcon = { IconButton(onClick = openDrawer) { Icon(Icons.Filled.Menu, "Abrir menú", tint = SolarGreen) } }, actions = { IconButton(onClick = { searchVisible = !searchVisible; if (!searchVisible) searchQuery = "" }) { Icon(Icons.Filled.Search, "Buscar montaje", tint = SolarGreen) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)) }, floatingActionButton = { FloatingActionButton(onClick = onCreateJob, containerColor = SolarAmber, contentColor = SolarGreenDark) { Icon(Icons.Filled.Add, "Nuevo montaje") } }) { innerPadding ->
            JobsListContent(jobRows, statusCounts, activeFilters, { viewModel.toggleStatusFilter(it) }, { viewModel.clearStatusFilters() }, onOpenJob, { uuid, status -> viewModel.regularizeJob(uuid, status) }, { viewModel.deleteJobPermanently(it) }, Modifier.padding(innerPadding), searchVisible = searchVisible, searchQuery = searchQuery, onSearchQueryChange = { searchQuery = it }, onCloseSearch = { searchVisible = false; searchQuery = "" })
        }
    }
}

@Composable
fun JobsListContent(jobRows: List<ComercialJobRow>, statusCounts: Map<String, Int>, activeFilters: Set<String>, onToggle: (String) -> Unit, onClear: () -> Unit, onOpenJob: (String) -> Unit, onRegularizeJob: ((String, String) -> Unit)? = null, onDeleteJob: ((String) -> Unit)? = null, modifier: Modifier = Modifier, emptyMessage: String = "Aún no hay montajes registrados. Tocá el + para crear el primero.", showDescriptionInsteadOfPrice: Boolean = false, searchVisible: Boolean = false, searchQuery: String = "", onSearchQueryChange: (String) -> Unit = {}, onCloseSearch: () -> Unit = {}) {
    val normalizedQuery = searchQuery.trim(); val visibleRows = if (normalizedQuery.isBlank()) jobRows else jobRows.filter { (it.clientName ?: it.job.clientName ?: it.job.title).contains(normalizedQuery, true) }
    Column(modifier.fillMaxSize()) {
        if (searchVisible) JobSearchBar(searchQuery, onSearchQueryChange, onCloseSearch)
        StatusFilterBar(statusCounts, activeFilters, onToggle, onClear)
        if (visibleRows.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(when { normalizedQuery.isNotBlank() -> "No hay montajes que coincidan con \"$normalizedQuery\"."; activeFilters.isEmpty() -> emptyMessage; else -> "No hay montajes con los filtros seleccionados." }, modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(visibleRows, key = { it.job.uuid }) { row -> JobRowCard(row, { onOpenJob(row.job.uuid) }, onRegularizeJob?.let { cb -> { s -> cb(row.job.uuid, s) } }, onDeleteJob?.let { cb -> { cb(row.job.uuid) } }, showDescriptionInsteadOfPrice) } }
    }
}

@Composable private fun JobSearchBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) { Surface(tonalElevation = 4.dp, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), shape = RoundedCornerShape(16.dp)) { OutlinedTextField(query, onQueryChange, Modifier.fillMaxWidth().padding(8.dp), singleLine = true, placeholder = { Text("Buscar por nombre") }, leadingIcon = { Icon(Icons.Filled.Search, null, tint = SolarGreen) }, trailingIcon = { IconButton(onClick = { if (query.isNotEmpty()) onQueryChange("") else onClose() }) { Icon(Icons.Filled.Clear, if (query.isNotEmpty()) "Limpiar búsqueda" else "Cerrar búsqueda") } }) } }

@Composable private fun StatusFilterBar(counts: Map<String, Int>, activeFilters: Set<String>, onToggle: (String) -> Unit, onClear: () -> Unit) { LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(JOB_STATUS_ORDER) { status -> val active = status in activeFilters; val color = jobStatusColor(status); FilterChip(active, { onToggle(status) }, { Text("${jobStatusLabel(status)} · ${counts[status] ?: 0}") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color, selectedLabelColor = MaterialTheme.colorScheme.surface)) }; if (activeFilters.isNotEmpty()) item { TextButton(onClick = onClear) { Text("Quitar filtros ✕") } } } }

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun JobRowCard(row: ComercialJobRow, onClick: () -> Unit, onRegularize: ((String) -> Unit)? = null, onDelete: (() -> Unit)? = null, showDescriptionInsteadOfPrice: Boolean = false) {
    var menuExpanded by remember(row.job.uuid) { mutableStateOf(false) }; var confirmDelete by remember(row.job.uuid) { mutableStateOf(false) }
    val dateText = when { !row.job.scheduledAt.isNullOrBlank() -> row.job.scheduledAt.take(10); !row.job.proposedDate.isNullOrBlank() -> "${row.job.proposedDate.take(10)} (propuesta)"; else -> "sin fecha" }
    val canRegularize = onRegularize != null && row.job.status in setOf("pending", "assigned"); val canOpenActions = canRegularize || onDelete != null; val hasDate = !row.job.scheduledAt.isNullOrBlank() || !row.job.proposedDate.isNullOrBlank(); val validPrice = row.job.price?.toDoubleOrNull()?.let { it > 0.0 } == true
    val targets = buildList { if (hasDate) { add("in_progress"); add("finished"); if (validPrice) { add("invoiced"); add("paid") } }; add("cancelled") }
    Box(Modifier.fillMaxWidth()) { Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { if (canOpenActions) menuExpanded = true })) { Column(Modifier.padding(16.dp)) { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text(row.clientName ?: row.job.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); StatusBadge(row.job.status) }; Spacer(Modifier.height(4.dp)); Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(if (showDescriptionInsteadOfPrice) row.job.description ?: "—" else row.job.price?.let { "${'$'}$it" } ?: "—", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant); Text(dateText, modifier = Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }; if (!row.job.address.isNullOrBlank()) Text(row.job.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (row.pendingSync) Text("⏳ Pendiente de sincronizar", style = MaterialTheme.typography.labelSmall, color = SolarAmber) } }; DropdownMenu(menuExpanded, { menuExpanded = false }) { if (canRegularize) targets.forEach { status -> DropdownMenuItem({ Text("Marcar como ${jobStatusLabel(status)}") }, { menuExpanded = false; onRegularize?.invoke(status) }) }; if (onDelete != null) DropdownMenuItem({ Text("Eliminar") }, { menuExpanded = false; confirmDelete = true }) } }
    if (confirmDelete) AlertDialog({ confirmDelete = false }, title = { Text("Eliminar montaje") }, text = { Text("Se eliminará definitivamente este montaje, sus asignaciones, materiales y fotos. Esta acción no se puede deshacer.") }, confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete?.invoke() }) { Text("Eliminar definitivamente") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } })
}
@Composable private fun StatusBadge(status: String) { val color = jobStatusColor(status); Box(Modifier.background(color.copy(alpha = .14f), RoundedCornerShape(50))) { Text(jobStatusLabel(status), style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) } }
