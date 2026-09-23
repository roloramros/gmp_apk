package com.gmp.offline.ui.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gmp.offline.BuildConfig
import com.gmp.offline.data.local.entities.GalleryPhotoEntity
import com.gmp.offline.ui.comercial.PhotoUiState
import com.gmp.offline.ui.theme.SolarError
import com.gmp.offline.ui.theme.SolarGreen
import java.io.File

@Composable
fun GalleryTabContent(
    searchQuery: String = "",
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val photoState by viewModel.photoState.collectAsStateWithLifecycle()

    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var editingPhoto by remember { mutableStateOf<GalleryPhotoEntity?>(null) }

    val normalizedQuery = searchQuery.trim()
    val visiblePhotos = remember(photos, normalizedQuery) {
        if (normalizedQuery.isBlank()) photos
        else photos.filter { it.caption?.contains(normalizedQuery, ignoreCase = true) == true }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) pendingUri = uri }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Galería de instalaciones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Button(onClick = { picker.launch("image/*") }, colors = ButtonDefaults.buttonColors(containerColor = SolarGreen)) {
                Text("+ Foto")
            }
        }

        if (photoState is PhotoUiState.Uploading) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = SolarGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Subiendo foto...", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (photoState is PhotoUiState.Error) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text((photoState as PhotoUiState.Error).message, color = SolarError, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.dismissPhotoError() }) { Text("Cerrar") }
            }
        }

        if (visiblePhotos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (normalizedQuery.isNotBlank()) "No hay fotos que coincidan con \"$normalizedQuery\"." else "Aún no hay fotos en la galería.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(visiblePhotos, key = { it.uuid }) { photo ->
                    GalleryPhotoCell(photo = photo, onClick = { editingPhoto = photo }, onRetry = { viewModel.retryPhoto(photo.uuid) })
                }
            }
        }
    }

    pendingUri?.let { uri ->
        GalleryCaptionDialog(
            title = "Agregar foto",
            initialCaption = "",
            onConfirm = { caption -> viewModel.addPhoto(uri, caption.ifBlank { null }); pendingUri = null },
            onDismiss = { pendingUri = null },
        )
    }

    editingPhoto?.let { photo ->
        GalleryCaptionDialog(
            title = "Editar foto",
            initialCaption = photo.caption ?: "",
            showDelete = true,
            onConfirm = { caption -> viewModel.updateCaption(photo.uuid, caption.ifBlank { null }); editingPhoto = null },
            onDelete = { viewModel.removePhoto(photo.uuid); editingPhoto = null },
            onDismiss = { editingPhoto = null },
        )
    }
}

@Composable
private fun GalleryPhotoCell(photo: GalleryPhotoEntity, onClick: () -> Unit, onRetry: () -> Unit) {
    Box(modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).clickable { if (photo.uploadStatus == "error") onRetry() else onClick() }) {
        val model: Any = photo.localPath?.let { File(it) } ?: "${BuildConfig.API_BASE_URL.trimEnd('/')}${photo.url}"
        AsyncImage(model = model, contentDescription = photo.caption ?: "Foto de instalación", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (photo.uploadStatus == "uploading") {
            Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(22.dp))
            }
        }
        if (photo.uploadStatus == "error") {
            Box(modifier = Modifier.fillMaxSize().background(SolarError.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                Text("Reintentar", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White)
            }
        }
        if (!photo.active) {
            Box(modifier = Modifier.padding(4.dp).background(SolarError.copy(alpha = 0.85f), RoundedCornerShape(4.dp)).align(Alignment.TopStart)) {
                Text("Oculta", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
            }
        }
    }
}

@Composable
private fun GalleryCaptionDialog(
    title: String,
    initialCaption: String,
    showDelete: Boolean = false,
    onConfirm: (String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var caption by remember { mutableStateOf(initialCaption) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = caption, onValueChange = { caption = it },
                label = { Text("Descripción (opcional)") },
                placeholder = { Text("Ej. Instalación de 6 kW en Vista Alegre") },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SolarGreen, unfocusedBorderColor = SolarGreen.copy(alpha = 0.35f),
                    focusedLabelColor = SolarGreen, cursorColor = SolarGreen,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(caption) }) { Text("Guardar") } },
        dismissButton = {
            Row {
                if (showDelete && onDelete != null) {
                    TextButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = null, tint = SolarError, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Quitar", color = SolarError) }
                    Spacer(Modifier.width(4.dp))
                }
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        },
    )
}
