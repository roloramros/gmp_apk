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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.gmp.offline.ui.common.jobStatusColor
import com.gmp.offline.ui.common.jobStatusLabel
import com.gmp.offline.ui.theme.SolarAmber
import com.gmp.offline.ui.theme.SolarError
import com.gmp.offline.ui.theme.SolarGreen
import com.gmp.offline.ui.theme.SolarGreenDark
import java.io.File

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
    val photo by viewModel.photo.collectAsStateWithLifecycle()
    val photoState by viewModel.photoState.collectAsStateWithLifecycle()

    var showCancelConfirm by remember { mutableStateOf(false) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.addPhoto(it) } }

    val currentJob = job

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
            // --- Info del job ("montaje") ---
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
                    DetailRow("CI", currentJob.clientCi)
                    DetailRow("Teléfono", currentJob.clientPhone)
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

                    val canCancel = currentJob.status == "pending" || currentJob.status == "assigned"
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
            }

            // --- Foto (una sola por montaje; comercial no ve materiales) ---
            Text("Foto del montaje", style = MaterialTheme.typography.titleMedium)

            PhotoSection(
                photo = photo,
                photoState = photoState,
                onPickPhoto = {
                    pickImageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onRetry = { viewModel.retryPhotoUpload() },
                onRemove = { viewModel.removePhoto() },
                onDismissError = { viewModel.clearPhotoError() },
            )

            Spacer(Modifier.height(8.dp))
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
private fun PhotoSection(
    photo: JobPhotoEntity?,
    photoState: PhotoUiState,
    onPickPhoto: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onDismissError: () -> Unit,
) {
    var showFullScreen by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when {
                photoState is PhotoUiState.Uploading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(color = SolarGreen, modifier = Modifier.size(24.dp))
                        Text("Subiendo foto...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                photo != null -> {
                    // Indicador compacto (no la imagen en sí) — tocarlo abre
                    // la foto en pantalla completa. Evita cargar/decodificar
                    // la imagen grande dentro de la lista de detalle.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = photo.uploadStatus != "error") { showFullScreen = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SolarGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("📷", style = MaterialTheme.typography.titleLarge)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Foto cargada", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                if (photo.uploadStatus == "error") "No se pudo subir todavía" else "Tocar para ver en pantalla completa",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (photo.uploadStatus == "error") SolarError else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (photo.uploadStatus == "error") {
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(containerColor = SolarAmber, contentColor = SolarGreenDark),
                            ) {
                                Text("Reintentar subida")
                            }
                        } else {
                            OutlinedButton(onClick = onPickPhoto) {
                                Text("Cambiar foto")
                            }
                        }
                        IconButton(onClick = onRemove) {
                            Icon(Icons.Filled.Delete, contentDescription = "Quitar foto", tint = SolarError)
                        }
                    }
                }
                else -> {
                    OutlinedButton(
                        onClick = onPickPhoto,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Añadir foto")
                    }
                }
            }

            if (photoState is PhotoUiState.Error) {
                Spacer(Modifier.height(8.dp))
                Text(photoState.message, color = SolarError, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onDismissError) {
                    Text("Ok")
                }
            }
        }
    }

    if (showFullScreen && photo != null) {
        FullScreenPhotoViewer(photo = photo, onDismiss = { showFullScreen = false })
    }
}

@Composable
private fun FullScreenPhotoViewer(photo: JobPhotoEntity, onDismiss: () -> Unit) {
    val imageModel = if (photo.uploadStatus != "synced" && photo.localPath != null) {
        File(photo.localPath)
    } else {
        BuildConfig.API_BASE_URL.trimEnd('/') + photo.url
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = "Foto del montaje",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Cerrar", tint = Color.White)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
