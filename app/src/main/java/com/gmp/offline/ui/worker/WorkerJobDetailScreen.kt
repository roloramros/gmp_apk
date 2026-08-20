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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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

private const val CUSTOM_UNIT_SEPARATOR = "|||unit:"

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
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val workerPhotoState by viewModel.workerPhotoState.collectAsStateWithLifecycle()

    var showMore by remember { mutableStateOf(false) }
    var showMaterialDialog by remember { mutableStateOf(false) }
    var showStartConfirm by remember { mutableStateOf(false) }
    var showFinishConfirm by remember { mutableStateOf(false) }
    val selectedQuantities = remember { mutableStateMapOf<String, String>() }
    var includeOther by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var customUnit by remember { mutableStateOf("") }
    var customQuantity by remember { mutableStateOf("1") }

    val currentJob = job
    val catalogByUuid = materialCatalog.associateBy { it.uuid }
    val materialRows = jobMaterials
        .groupBy { it.materialUuid ?: it.freeTextDescription ?: it.uuid }
        .map { (_, lines) ->
            val first = lines.first()
            val qty = lines.sumOf { it.quantity.toDoubleOrNull() ?: 0.0 }
            if (first.materialUuid != null) {
                Triple(
                    catalogByUuid[first.materialUuid]?.name ?: "Material",
                    formatQuantity(qty),
                    catalogByUuid[first.materialUuid]?.unit.orEmpty(),
                )
            } else {
                val parsed = parseCustomDescription(first.freeTextDescription.orEmpty())
                Triple(parsed.first, formatQuantity(qty), parsed.second)
            }
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
                        onClick = { showStartConfirm = true },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = SolarGreen,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Iniciar trabajo")
                    }
                }
                "in_progress" -> {
                    FloatingActionButton(
                        onClick = { showFinishConfirm = true },
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

            WorkerPhotosCard(
                photos = photos,
                currentUserUuid = viewModel.currentUserUuid,
                photoState = workerPhotoState,
                onAddPhoto = viewModel::addWorkerPhoto,
                onRetryPhoto = viewModel::retryWorkerPhoto,
                onDismissError = viewModel::clearWorkerPhotoError,
                canAddPhoto = currentJob.status == "in_progress",
            )

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
                                    selectedQuantities.clear()
                                    includeOther = false
                                    customName = ""
                                    customUnit = ""
                                    customQuantity = "1"
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

    if (showStartConfirm) {
        AlertDialog(
            onDismissRequest = { showStartConfirm = false },
            title = { Text("Iniciar trabajo") },
            text = { Text("¿Seguro que deseas iniciar este trabajo?") },
            confirmButton = {
                TextButton(onClick = {
                    showStartConfirm = false
                    viewModel.startWorkerJob()
                }) {
                    Text("Sí, iniciar", color = SolarGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartConfirm = false }) {
                    Text("Cancelar")
                }
            },
        )
    }

    if (showFinishConfirm) {
        AlertDialog(
            onDismissRequest = { showFinishConfirm = false },
            title = { Text("Finalizar trabajo") },
            text = { Text("¿Seguro que deseas dar por finalizado este trabajo?") },
            confirmButton = {
                TextButton(onClick = {
                    showFinishConfirm = false
                    viewModel.finishWorkerJob()
                }) {
                    Text("Sí, finalizar", color = SolarError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinishConfirm = false }) {
                    Text("Cancelar")
                }
            },
        )
    }

    if (showMaterialDialog) {
        val catalogSelectionValid = selectedQuantities.values.all { (it.toDoubleOrNull() ?: 0.0) > 0.0 }
        val validCustomQuantity = (customQuantity.toDoubleOrNull() ?: 0.0) > 0.0
        val customValid = !includeOther || (
            customName.isNotBlank() &&
                customUnit.isNotBlank() &&
                validCustomQuantity
            )
        val hasSelection = selectedQuantities.isNotEmpty() || includeOther
        val canAdd = hasSelection && catalogSelectionValid && customValid

        AlertDialog(
            onDismissRequest = { showMaterialDialog = false },
            title = { Text("Agregar materiales") },
            text = {
                Column {
                    Text(
                        "Marca los materiales y escribe la cantidad de cada uno.",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        materialCatalog.forEach { material ->
                            val selected = selectedQuantities.containsKey(material.uuid)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (selected) selectedQuantities.remove(material.uuid)
                                        else selectedQuantities[material.uuid] = "1"
                                    }
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            selectedQuantities[material.uuid] = selectedQuantities[material.uuid] ?: "1"
                                        } else {
                                            selectedQuantities.remove(material.uuid)
                                        }
                                    },
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
                                if (selected) {
                                    Spacer(Modifier.width(8.dp))
                                    OutlinedTextField(
                                        value = selectedQuantities[material.uuid].orEmpty(),
                                        onValueChange = { selectedQuantities[material.uuid] = it },
                                        label = { Text("Cantidad") },
                                        singleLine = true,
                                        modifier = Modifier.width(110.dp),
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { includeOther = !includeOther }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = includeOther,
                                onCheckedChange = { includeOther = it },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Otros", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Material o gasto que no está en el catálogo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (includeOther) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customName,
                                onValueChange = { customName = it },
                                label = { Text("Nombre") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customUnit,
                                onValueChange = { customUnit = it },
                                label = { Text("Unidad de medida") },
                                placeholder = { Text("Ej.: viaje, km, unidad") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customQuantity,
                                onValueChange = { customQuantity = it },
                                label = { Text("Cantidad") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = canAdd,
                    onClick = {
                        selectedQuantities.forEach { (materialUuid, quantity) ->
                            viewModel.addWorkerMaterial(materialUuid, quantity)
                        }
                        if (includeOther) {
                            viewModel.addWorkerCustomMaterial(customName, customUnit, customQuantity)
                        }
                        showMaterialDialog = false
                    },
                ) {
                    Text("Agregar seleccionados", color = SolarGreen)
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

private fun parseCustomDescription(value: String): Pair<String, String> {
    val parts = value.split(CUSTOM_UNIT_SEPARATOR, limit = 2)
    return if (parts.size == 2) parts[0] to parts[1] else value to ""
}

private fun formatQuantity(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
