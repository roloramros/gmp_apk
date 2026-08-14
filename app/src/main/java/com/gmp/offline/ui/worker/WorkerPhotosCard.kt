package com.gmp.offline.ui.worker

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import coil.compose.AsyncImage
import com.gmp.offline.BuildConfig
import com.gmp.offline.data.local.entities.JobPhotoEntity
import com.gmp.offline.data.repository.WorkerPhotoRepository
import com.gmp.offline.ui.comercial.PhotoUiState
import com.gmp.offline.ui.theme.SolarError
import com.gmp.offline.ui.theme.SolarGreen
import java.io.File

@Composable
fun WorkerPhotosCard(
    photos: List<JobPhotoEntity>,
    currentUserUuid: String,
    photoState: PhotoUiState,
    onAddPhoto: (android.net.Uri) -> Unit,
    onRetryPhoto: (String) -> Unit,
    onDismissError: () -> Unit,
) {
    var selectedPhoto by remember { mutableStateOf<JobPhotoEntity?>(null) }
    val workerPhotos = photos.filter { it.uploadedByUuid == currentUserUuid }
    val remaining = (WorkerPhotoRepository.MAX_WORKER_PHOTOS - workerPhotos.size).coerceAtLeast(0)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && remaining > 0) onAddPhoto(uri)
    }

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Fotos del montaje",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Puedes agregar hasta 3 fotos adicionales · quedan $remaining",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (remaining > 0 && photoState !is PhotoUiState.Uploading) {
                    Button(
                        onClick = { picker.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = SolarGreen),
                    ) {
                        androidx.compose.material3.Icon(Icons.Filled.Add, contentDescription = null)
                        Text(" Foto")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (photos.isEmpty()) {
                Text(
                    "Todavía no hay fotos del montaje.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                photos.forEachIndexed { index, photo ->
                    val isOwnWorkerPhoto = photo.uploadedByUuid == currentUserUuid
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = photo.uploadStatus != "error") { selectedPhoto = photo }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SolarGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("📷", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (isOwnWorkerPhoto) "Foto del trabajador ${workerPhotos.indexOf(photo) + 1}" else "Foto original del montaje",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                when (photo.uploadStatus) {
                                    "error" -> "Error al subir"
                                    "uploading" -> "Subiendo..."
                                    else -> "Tocar para ver"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (photo.uploadStatus == "error") SolarError else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isOwnWorkerPhoto && photo.uploadStatus == "error") {
                            TextButton(onClick = { onRetryPhoto(photo.uuid) }) {
                                Text("Reintentar", color = SolarGreen)
                            }
                        }
                    }
                }
            }

            if (photoState is PhotoUiState.Uploading) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = SolarGreen, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Subiendo foto...", style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (photoState is PhotoUiState.Error) {
                Spacer(Modifier.height(10.dp))
                Text(photoState.message, color = SolarError, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onDismissError) { Text("Cerrar") }
            }
        }
    }

    selectedPhoto?.let { photo ->
        if (photo.uploadStatus != "error") {
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
                    val model: Any = photo.localPath?.let { File(it) }
                        ?: "${BuildConfig.API_BASE_URL.trimEnd('/')}${photo.url}"
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
