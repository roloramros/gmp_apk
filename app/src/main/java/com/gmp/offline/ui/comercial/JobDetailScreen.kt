package com.gmp.offline.ui.comercial

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.gmp.offline.data.local.entities.MaterialEntity
import com.gmp.offline.ui.common.jobStatusColor
import com.gmp.offline.ui.common.jobStatusLabel
import com.gmp.offline.ui.theme.SolarAmber
import com.gmp.offline.ui.theme.SolarError
import com.gmp.offline.ui.theme.SolarGreen
import com.gmp.offline.ui.theme.SolarGreenDark

@Composable
fun JobDetailScreen(
    onBack: () -> Unit,
    onEditJob: (String) -> Unit,
    viewModel: JobDetailViewModel = hiltViewModel(),
) {
    val job by viewModel.job.collectAsStateWithLifecycle()
    val clientName by viewModel.clientName.collectAsStateWithLifecycle()
    val materialRows by viewModel.materialRows.collectAsStateWithLifecycle()
    val workers by viewModel.workers.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val addMaterialState by viewModel.addMaterialState.collectAsStateWithLifecycle()

    var showAddMaterialDialog by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }

    val currentJob = job

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentJob?.title ?: "Trabajo", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (currentJob != null) {
                        IconButton(onClick = { onEditJob(currentJob.uuid) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = SolarGreen)
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (currentJob == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
            ) {
                Text("Cargando...", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // --- Info del job ---
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            jobStatusLabel(currentJob.status),
                            color = jobStatusColor(currentJob.status),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (!clientName.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Cliente: $clientName", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (!currentJob.address.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Dirección: ${currentJob.address}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (!currentJob.description.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(currentJob.description, style = MaterialTheme.typography.bodyMedium)
                    }

                    if (workers.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Trabajadores asignados: ${workers.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    val canCancel = currentJob.status == "pending" || currentJob.status == "assigned"
                    if (canCancel) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { showCancelConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SolarError),
                        ) {
                            Text("Cancelar trabajo")
                        }
                    }
                }
            }

            // --- Materiales ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Materiales", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { showAddMaterialDialog = true }) {
                    Text("+ Agregar")
                }
            }

            if (materialRows.isEmpty()) {
                Text(
                    "Todavía no se agregaron materiales.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    materialRows.forEach { row ->
                        MaterialRowCard(
                            displayName = row.displayName,
                            quantity = row.item.quantity,
                            unitPrice = row.item.unitPrice,
                            subtotal = row.subtotal,
                            onRemove = { viewModel.removeMaterial(row.item.uuid) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showAddMaterialDialog) {
        AddMaterialDialog(
            catalog = catalog,
            state = addMaterialState,
            onDismiss = {
                showAddMaterialDialog = false
                viewModel.clearAddMaterialError()
            },
            onConfirm = { materialUuid, freeText, quantity, unitPrice ->
                viewModel.addMaterial(materialUuid, freeText, quantity, unitPrice)
            },
            onSaved = {
                showAddMaterialDialog = false
                viewModel.clearAddMaterialError()
            },
        )
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Cancelar trabajo") },
            text = { Text("¿Seguro que querés cancelar este trabajo? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cancelJob()
                    showCancelConfirm = false
                }) {
                    Text("Sí, cancelar", color = SolarError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text("Volver")
                }
            },
        )
    }
}

@Composable
private fun MaterialRowCard(
    displayName: String,
    quantity: String,
    unitPrice: String?,
    subtotal: Double?,
    onRemove: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                val priceText = unitPrice?.let { " · $it c/u" } ?: ""
                Text(
                    "Cantidad: $quantity$priceText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (subtotal != null) {
                    Text(
                        "Subtotal: ${"%.2f".format(subtotal)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Quitar", tint = SolarError)
            }
        }
    }
}

@Composable
private fun AddMaterialDialog(
    catalog: List<MaterialEntity>,
    state: AddMaterialUiState,
    onDismiss: () -> Unit,
    onConfirm: (materialUuid: String?, freeText: String?, quantity: String, unitPrice: String?) -> Unit,
    onSaved: () -> Unit,
) {
    var useCatalog by remember { mutableStateOf(catalog.isNotEmpty()) }
    var expanded by remember { mutableStateOf(false) }
    var selectedMaterial by remember { mutableStateOf<MaterialEntity?>(null) }
    var freeText by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var unitPrice by remember { mutableStateOf("") }
    // Se pone en true recién cuando el usuario aprieta "Agregar" al menos
    // una vez; evita que el diálogo se cierre solo apenas se abre (el
    // estado inicial también es Idle).
    var attemptedSave by remember { mutableStateOf(false) }

    // El ViewModel no tiene un estado "Saved" propio para agregar material
    // (a diferencia de crear/editar job, acá no se navega a ningún lado):
    // se interpreta éxito como "volvió a Idle después de haber intentado
    // guardar", y ahí se cierra el diálogo solo.
    LaunchedEffect(state, attemptedSave) {
        if (attemptedSave && state is AddMaterialUiState.Idle) {
            onSaved()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar material") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = useCatalog,
                        onClick = { useCatalog = true },
                        label = { Text("Del catálogo") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SolarGreen.copy(alpha = 0.18f)),
                    )
                    FilterChip(
                        selected = !useCatalog,
                        onClick = { useCatalog = false },
                        label = { Text("Texto libre") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SolarGreen.copy(alpha = 0.18f)),
                    )
                }

                if (useCatalog) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedMaterial?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Material") },
                            shape = RoundedCornerShape(12.dp),
                            colors = gmpFieldColorsDialog(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { expanded = true },
                        )
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            if (catalog.isEmpty()) {
                                DropdownMenuItem(text = { Text("No hay materiales en el catálogo") }, onClick = {}, enabled = false)
                            }
                            catalog.forEach { material ->
                                DropdownMenuItem(
                                    text = { Text(material.name) },
                                    onClick = {
                                        selectedMaterial = material
                                        unitPrice = material.defaultPrice ?: unitPrice
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = freeText,
                        onValueChange = { freeText = it },
                        label = { Text("Descripción") },
                        shape = RoundedCornerShape(12.dp),
                        colors = gmpFieldColorsDialog(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Cantidad") },
                        shape = RoundedCornerShape(12.dp),
                        colors = gmpFieldColorsDialog(),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = unitPrice,
                        onValueChange = { unitPrice = it },
                        label = { Text("Precio unit. (opc.)") },
                        shape = RoundedCornerShape(12.dp),
                        colors = gmpFieldColorsDialog(),
                        modifier = Modifier.weight(1f),
                    )
                }

                if (state is AddMaterialUiState.Error) {
                    Text(state.message, color = SolarError, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    attemptedSave = true
                    onConfirm(
                        if (useCatalog) selectedMaterial?.uuid else null,
                        if (!useCatalog) freeText else null,
                        quantity,
                        unitPrice.ifBlank { null },
                    )
                },
                enabled = state !is AddMaterialUiState.Saving,
                colors = ButtonDefaults.buttonColors(containerColor = SolarAmber, contentColor = SolarGreenDark),
            ) {
                Text("Agregar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
    )
}

@Composable
private fun gmpFieldColorsDialog() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SolarGreen,
    unfocusedBorderColor = SolarGreen.copy(alpha = 0.35f),
    focusedLabelColor = SolarGreen,
    cursorColor = SolarGreen,
)
