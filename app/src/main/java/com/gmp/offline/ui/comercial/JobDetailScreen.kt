package com.gmp.offline.ui.comercial

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gmp.offline.BuildConfig
import com.gmp.offline.data.local.entities.JobPhotoEntity
import com.gmp.offline.data.local.entities.StaffEntity
import com.gmp.offline.ui.common.jobStatusColor
import com.gmp.offline.ui.common.jobStatusLabel
import com.gmp.offline.ui.theme.SolarAmber
import com.gmp.offline.ui.theme.SolarError
import com.gmp.offline.ui.theme.SolarGreen
import com.gmp.offline.ui.theme.SolarGreenDark
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val CUSTOM_UNIT_SEPARATOR = "|||unit:"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(
    onBack: () -> Unit,
    onEditJob: (String) -> Unit,
    viewModel: JobDetailViewModel = hiltViewModel(),
) {
    val job by viewModel.job.collectAsStateWithLifecycle()
    val clientName by viewModel.clientName.collectAsStateWithLifecycle()
    val workers by viewModel.workers.collectAsStateWithLifecycle()
    val assignableStaff by viewModel.assignableStaff.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val photoState by viewModel.photoState.collectAsStateWithLifecycle()
    val jobMaterials by viewModel.jobMaterials.collectAsStateWithLifecycle()
    val materialCatalog by viewModel.materialCatalog.collectAsStateWithLifecycle()

    var showCancelConfirm by remember { mutableStateOf(false) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.addPhoto(it) } }

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
                    formatMaterialQuantity(qty),
                    catalogByUuid[first.materialUuid]?.unit.orEmpty(),
                )
            } else {
                val parsed = parseCustomMaterialDescription(first.freeTextDescription.orEmpty())
                Triple(parsed.first, formatMaterialQuantity(qty), parsed.second)
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
                actions = {
                    if (currentJob != null && viewModel.canEditJob) {
                        IconButton(onClick = { onEditJob(currentJob.uuid) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = SolarGreen)
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (viewModel.isAdmin) {
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
            var showFullDetails by remember { mutableStateOf(false) }

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

                    DetailRow("Cliente", currentJob.clientName ?: clientName)
                    DetailRow("Teléfono", currentJob.clientPhone)

                    if (showFullDetails) {
                        DetailRow("CI", currentJob.clientCi)
                        DetailRow("Dirección", currentJob.address)
                        if (currentJob.latitude != null && currentJob.longitude != null) {
                            DetailRow("Coordenadas", "${currentJob.latitude}, ${currentJob.longitude}")
                        }
                        DetailRow("Referencia", currentJob.reference)
                        DetailRow("Notas del sitio", currentJob.siteNotes)
                        DetailRow("Descripción del trabajo", currentJob.description)
                        DetailRow("Precio", currentJob.price?.let { "$$it" })
                        DetailRow("Forma de pago", currentJob.paymentMethod?.let { pm -> PAYMENT_METHODS.find { it.value == pm }?.label ?: pm })
                        DetailRow("Fecha de visita", currentJob.visitDate)
                        DetailRow("Fecha propuesta", currentJob.proposedDate)
                        DetailRow("Fecha de montaje (oficial)", currentJob.scheduledAt?.take(10))

                        if (workers.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Trabajadores asignados: ${workers.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        val canCancel = viewModel.canCancelJob &&
                            (currentJob.status == "pending" || currentJob.status == "assigned")
                        if (canCancel) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { showCancelConfirm = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SolarError),
                            ) {
                                Text("Cancelar montaje")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showFullDetails = !showFullDetails }) {
                            Text(
                                if (showFullDetails) "Mostrar menos" else "Mostrar más",
                                color = SolarGreen,
                            )
                        }
                    }
                }
            }

            if (viewModel.isAdmin) {
                AssignmentCard(
                    scheduledDate = currentJob.scheduledAt?.take(10),
                    assignedWorkerUuids = workers.map { it.userUuid }.toSet(),
                    assignableStaff = assignableStaff,
                    onConfirm = { date, selected -> viewModel.confirmAssignment(date, selected) },
                )
            }

            PhotoSection(
                photos = photos,
                photoState = photoState,
                canManagePhoto = viewModel.canManagePhoto,
                canRemovePhoto = !viewModel.isAdmin,
                onPickPhoto = {
                    pickImageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onRetry = { viewModel.retryPhotoUpload() },
                onRemove = { viewModel.removePhoto() },
                onDismissError = { viewModel.clearPhotoError() },
            )

            if (viewModel.isAdmin) {
                AdminMaterialsCard(materialRows = materialRows)
            }

            Spacer(Modifier.height(72.dp))
        }
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
private fun AdminMaterialsCard(materialRows: List<Triple<String, String, String>>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Materiales utilizados",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignmentCard(
    scheduledDate: String?,
    assignedWorkerUuids: Set<String>,
    assignableStaff: List<StaffEntity>,
    onConfirm: (scheduledDate: String?, selectedWorkerUuids: Set<String>) -> Unit,
) {
    var dateDraft by remember(scheduledDate) { mutableStateOf(scheduledDate.orEmpty()) }
    var selectedDraft by remember(assignedWorkerUuids) { mutableStateOf(assignedWorkerUuids) }
    var showFullAssignment by remember { mutableStateOf(false) }

    val hasChanges = dateDraft != scheduledDate.orEmpty() || selectedDraft != assignedWorkerUuids

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Asignación de personal y fecha",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(12.dp))

            AssignmentDateField(
                value = dateDraft,
                onValueChange = { dateDraft = it },
                label = "Fecha oficial del montaje",
            )

            if (showFullAssignment) {
                Spacer(Modifier.height(16.dp))

                Text(
                    "Personal (admin / trabajador)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (assignableStaff.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No hay personal disponible para asignar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column {
                        assignableStaff.forEach { person ->
                            val isSelected = person.uuid in selectedDraft
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedDraft = if (isSelected) {
                                            selectedDraft - person.uuid
                                        } else {
                                            selectedDraft + person.uuid
                                        }
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedDraft = if (checked) selectedDraft + person.uuid else selectedDraft - person.uuid
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = SolarGreen),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(person.fullName, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        if (person.role == "admin") "Admin" else "Trabajador",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { showFullAssignment = !showFullAssignment }) {
                    Text(
                        if (showFullAssignment) "Mostrar menos" else "Mostrar más",
                        color = SolarGreen,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { onConfirm(dateDraft.ifBlank { null }, selectedDraft) },
                enabled = hasChanges,
                colors = ButtonDefaults.buttonColors(containerColor = SolarGreen),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Confirmar asignación")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignmentDateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null, tint = SolarGreen) },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SolarGreen,
                unfocusedBorderColor = SolarGreen.copy(alpha = 0.35f),
                focusedLabelColor = SolarGreen,
                cursorColor = SolarGreen,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { showDialog = true },
        )
    }

    if (showDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = isoAssignDateToUtcMillis(value) ?: System.currentTimeMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onValueChange(utcMillisToAssignIsoDate(millis))
                    }
                    showDialog = false
                }) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancelar")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun utcAssignDateFormat(): SimpleDateFormat =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

private fun utcMillisToAssignIsoDate(millis: Long): String = utcAssignDateFormat().format(Date(millis))

private fun isoAssignDateToUtcMillis(isoDate: String): Long? {
    if (isoDate.isBlank()) return null
    return try {
        utcAssignDateFormat().parse(isoDate)?.time
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun PhotoSection(
    photos: List<JobPhotoEntity>,
    photoState: PhotoUiState,
    canManagePhoto: Boolean,
    canRemovePhoto: Boolean,
    onPickPhoto: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onDismissError: () -> Unit,
) {
    var selectedPhoto by remember { mutableStateOf<JobPhotoEntity?>(null) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Fotos del montaje",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))

            if (photoState is PhotoUiState.Uploading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(color = SolarGreen, modifier = Modifier.size(24.dp))
                    Text("Subiendo foto...", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(10.dp))
            }

            if (photos.isEmpty()) {
                Text(
                    if (canManagePhoto) "Todavía no hay fotos del montaje." else "No hay fotos del montaje.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (canManagePhoto) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onPickPhoto,
                        colors = ButtonDefaults.buttonColors(containerColor = SolarGreen),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Agregar foto")
                    }
                }
            } else {
                photos.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = item.uploadStatus != "error") { selectedPhoto = item }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SolarGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("📷", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Foto ${index + 1}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                when (item.uploadStatus) {
                                    "error" -> "Error al subir"
                                    "uploading" -> "Subiendo..."
                                    else -> "Tocar para ver"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (item.uploadStatus == "error") SolarError else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (index == 0 && canManagePhoto && (item.uploadStatus == "error" || canRemovePhoto)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (item.uploadStatus == "error") {
                                Button(
                                    onClick = onRetry,
                                    colors = ButtonDefaults.buttonColors(containerColor = SolarGreen),
                                ) {
                                    Text("Reintentar")
                                }
                            }
                            if (canRemovePhoto) {
                                OutlinedButton(onClick = onRemove) {
                                    Icon(Icons.Filled.Delete, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Quitar")
                                }
                            }
                        }
                        if (photos.size > 1) Spacer(Modifier.height(6.dp))
                    }
                }
            }

            if (photoState is PhotoUiState.Error && canManagePhoto) {
                Spacer(Modifier.height(10.dp))
                Text(photoState.message, color = SolarError, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onDismissError) { Text("Cerrar") }
            }
        }
    }

    selectedPhoto?.let { item ->
        if (item.uploadStatus != "error") {
            Dialog(
                onDismissRequest = { selectedPhoto = null },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    val model: Any = item.localPath?.let { File(it) }
                        ?: "${BuildConfig.API_BASE_URL.trimEnd('/')}${item.url}"
                    AsyncImage(
                        model = model,
                        contentDescription = "Foto del montaje",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    TextButton(
                        onClick = { selectedPhoto = null },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                    ) {
                        Text("Cerrar", color = Color.White)
                    }
                }
            }
        }
    }
}

private fun parseCustomMaterialDescription(value: String): Pair<String, String> {
    val parts = value.split(CUSTOM_UNIT_SEPARATOR, limit = 2)
    return if (parts.size == 2) parts[0] to parts[1] else value to ""
}

private fun formatMaterialQuantity(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

@Composable
private fun DetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Spacer(Modifier.height(6.dp))
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodyMedium)
}
