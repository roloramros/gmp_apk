package com.gmp.offline.ui.worker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.gmp.offline.ui.comercial.JobDetailViewModel
import com.gmp.offline.ui.common.jobStatusColor
import com.gmp.offline.ui.common.jobStatusLabel
import com.gmp.offline.ui.theme.SolarError
import com.gmp.offline.ui.theme.SolarGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerJobDetailScreen(
    onBack: () -> Unit,
    viewModel: JobDetailViewModel = hiltViewModel(),
) {
    val job by viewModel.job.collectAsStateWithLifecycle()
    val clientName by viewModel.clientName.collectAsStateWithLifecycle()
    val assignedWorkerNames by viewModel.assignedWorkerNames.collectAsStateWithLifecycle()
    val jobMaterials by viewModel.jobMaterials.collectAsStateWithLifecycle()
    val materialCatalog by viewModel.materialCatalog.collectAsStateWithLifecycle()

    var showMore by remember { mutableStateOf(false) }
    var showMaterialDialog by remember { mutableStateOf(false) }
    var selectedMaterialUuid by remember { mutableStateOf<String?>(null) }
    var quantity by remember { mutableStateOf("1") }

    val currentJob = job
    val catalogByUuid = materialCatalog.associateBy { it.uuid }
    val materialRows = jobMaterials
        .groupBy { it.materialUuid ?: it.uuid }
        .map { (_, lines) ->
            val first = lines.first()
            val qty = lines.sumOf { it.quantity.toDoubleOrNull() ?: 0.0 }
            Triple(
                first.materialUuid?.let { catalogByUuid[it]?.name } ?: first.freeTextDescription ?: "Material",
                formatQuantity(qty),
                first.materialUuid?.let { catalogByUuid[it]?.unit }.orEmpty(),
            )
        }
        .sortedBy { it.first.lowercase() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentJob?.clientName ?: currentJob?.title ?: "Montaje", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        floatingActionButton = {
            when (currentJob?.status) {
                "assigned" -> {
                    FloatingActionButton(
                        onClick = { viewModel.startWorkerJob() },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = SolarGreen,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Iniciar trabajo")
                    }
                }
                "in_progress" -> {
                    FloatingActionButton(
                        onClick = { viewModel.finishWorkerJob() },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = SolarError,
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "Finalizar trabajo")
                    }
                }
            }
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
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        jobStatusLabel(currentJob.status),
                        color = jobStatusColor(currentJob.status),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )

                    DetailRow("Cliente", currentJob.clientName ?: clientName)
                    DetailRow("Dirección", currentJob.address)

                    if (showMore) {
                        DetailRow("Teléfono", currentJob.clientPhone)
                        DetailRow("Referencia", currentJob.reference)
                        DetailRow("Notas del sitio", currentJob.siteNotes)
                        DetailRow("Descripción del trabajo", currentJob.description)
                        DetailRow("Fecha oficial del montaje", currentJob.scheduledAt?.take(10))
                        DetailRow(
                            "Trabajadores asignados",
                            assignedWorkerNames.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "—",
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { showMore = !showMore }) {
                            Text(if (showMore) "Mostrar menos" else "Mostrar más", color = SolarGreen)
                        }
                    }
                }
            }

            if (currentJob.status == "in_progress") {
                Card(
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
                                "Materiales utilizados",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Button(
                                onClick = {
                                    selectedMaterialUuid = null
                                    quantity = "1"
                                    showMaterialDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SolarGreen),
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Text(" Agregar")
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        if (materialRows.isEmpty()) {
                            Text(
                                "Todavía no se han agregado materiales.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("Material", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text("Cant.", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.35f))
                                Text("Unidad", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.45f))
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                            materialRows.forEach { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp),
                                ) {
                                    Text(row.first, modifier = Modifier.weight(1f))
                                    Text(row.second, modifier = Modifier.weight(0.35f))
                                    Text(row.third.ifBlank { "—" }, modifier = Modifier.weight(0.45f))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(72.dp))
        }
    }

    if (showMaterialDialog) {
        AlertDialog(
            onDismissRequest = { showMaterialDialog = false },
            title = { Text("Agregar material") },
            text = {
                Column {
                    Text(
                        "Selecciona del catálogo",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        materialCatalog.forEach { material ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedMaterialUuid = material.uuid }
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selectedMaterialUuid == material.uuid,
                                    onClick = { selectedMaterialUuid = material.uuid },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(material.name)
                                    if (!material.unit.isNullOrBlank()) {
                                        Text(
                                            material.unit,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Cantidad") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedMaterialUuid != null && (quantity.toDoubleOrNull() ?: 0.0) > 0,
                    onClick = {
                        selectedMaterialUuid?.let { viewModel.addWorkerMaterial(it, quantity) }
                        showMaterialDialog = false
                    },
                ) {
                    Text("Agregar", color = SolarGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMaterialDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Spacer(Modifier.height(6.dp))
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(value, style = MaterialTheme.typography.bodyMedium)
}

private fun formatQuantity(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
