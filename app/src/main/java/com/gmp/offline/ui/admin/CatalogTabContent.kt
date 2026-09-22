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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.gmp.offline.data.local.entities.CatalogKitEntity
import com.gmp.offline.data.local.entities.CatalogKitPhotoEntity
import com.gmp.offline.data.repository.CatalogKitPhotoRepository
import com.gmp.offline.ui.comercial.PhotoUiState
import com.gmp.offline.ui.theme.SolarError
import com.gmp.offline.ui.theme.SolarGreen
import java.io.File

@Composable
fun CatalogTabContent(
    searchQuery: String = "",
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val kits by viewModel.kits.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var showForm by remember { mutableStateOf(false) }
    var editingKit by remember { mutableStateOf<CatalogKitEntity?>(null) }
    var deletingKit by remember { mutableStateOf<CatalogKitEntity?>(null) }

    val normalizedQuery = searchQuery.trim()
    val visibleKits = remember(kits, normalizedQuery) {
        if (normalizedQuery.isBlank()) kits
        else kits.filter { it.name.contains(normalizedQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Catálogo de kits", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Button(
                onClick = { editingKit = null; showForm = true },
                colors = ButtonDefaults.buttonColors(containerColor = SolarGreen),
            ) {
                Text("+ Añadir kit")
            }
        }

        if (visibleKits.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (normalizedQuery.isNotBlank()) "No hay kits que coincidan con \"$normalizedQuery\"."
                    else "Aún no hay kits en el catálogo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visibleKits, key = { it.uuid }) { kit ->
                    CatalogKitRow(
                        kit = kit,
                        onEdit = { editingKit = kit; showForm = true },
                        onDelete = { deletingKit = kit },
                    )
                }
            }
        }
    }

    if (showForm) {
        CatalogKitFormDialog(
            editing = editingKit,
            errorMessage = errorMessage,
            photoState = viewModel.photoState.collectAsStateWithLifecycle().value,
            photosFlowProvider = { uuid -> viewModel.photosForKit(uuid) },
            onDismiss = { showForm = false; viewModel.clearError(); viewModel.dismissPhotoError() },
            onSave = { name, powerKw, voltage, batteryKwh, panelsCount, priceUsd, description, active ->
                viewModel.save(editingKit?.uuid, name, powerKw, voltage, batteryKwh, panelsCount, priceUsd, description, active) {
                    showForm = false
                }
            },
            onAddPhoto = { uri -> editingKit?.let { viewModel.addPhoto(it.uuid, uri) } },
            onRetryPhoto = { photoUuid -> editingKit?.let { viewModel.retryPhoto(it.uuid, photoUuid) } },
            onRemovePhoto = { photoUuid -> editingKit?.let { viewModel.removePhoto(it.uuid, photoUuid) } },
            onDismissPhotoError = { viewModel.dismissPhotoError() },
        )
    }

    deletingKit?.let { kit ->
        AlertDialog(
            onDismissRequest = { deletingKit = null },
            title = { Text("Eliminar kit") },
            text = { Text("¿Eliminar \"${kit.name}\"? Deja de mostrarse en el catálogo público de inmediato.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(kit.uuid); deletingKit = null }) {
                    Text("Sí, eliminar", color = SolarError)
                }
            },
            dismissButton = { TextButton(onClick = { deletingKit = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun CatalogKitRow(kit: CatalogKitEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(kit.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    if (!kit.active) {
                        Spacer(Modifier.width(8.dp))
                        InactiveTag()
                    }
                }
                Text(
                    listOfNotNull(
                        kit.powerKw?.let { "$it kW" },
                        kit.voltage?.let { "$it V" },
                        kit.batteryKwh?.let { "Batería ${it} kWh" },
                        kit.panelsCount?.let { "$it paneles" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    kit.priceUsd?.let { "$${it}" } ?: "Sin precio",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SolarGreen,
                    fontWeight = FontWeight.Medium,
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = SolarGreen) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = SolarError) }
        }
    }
}

@Composable
private fun InactiveTag() {
    Box(modifier = Modifier.background(SolarError.copy(alpha = 0.12f), RoundedCornerShape(50))) {
        Text(
            "Oculto",
            style = MaterialTheme.typography.labelSmall,
            color = SolarError,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun CatalogKitFormDialog(
    editing: CatalogKitEntity?,
    errorMessage: String?,
    photoState: PhotoUiState,
    photosFlowProvider: (String) -> kotlinx.coroutines.flow.Flow<List<CatalogKitPhotoEntity>>,
    onDismiss: () -> Unit,
    onSave: (
        name: String, powerKw: String, voltage: String, batteryKwh: String,
        panelsCount: String, priceUsd: String, description: String, active: Boolean,
    ) -> Unit,
    onAddPhoto: (android.net.Uri) -> Unit,
    onRetryPhoto: (String) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onDismissPhotoError: () -> Unit,
) {
    var name by remember(editing) { mutableStateOf(editing?.name ?: "") }
    var powerKw by remember(editing) { mutableStateOf(editing?.powerKw ?: "") }
    var voltage by remember(editing) { mutableStateOf(editing?.voltage ?: KIT_VOLTAGES.first()) }
    var batteryKwh by remember(editing) { mutableStateOf(editing?.batteryKwh ?: "") }
    var panelsCount by remember(editing) { mutableStateOf(editing?.panelsCount?.toString() ?: "") }
    var priceUsd by remember(editing) { mutableStateOf(editing?.priceUsd ?: "") }
    var description by remember(editing) { mutableStateOf(editing?.description ?: "") }
    var active by remember(editing) { mutableStateOf(editing?.active ?: true) }
    var voltageMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing != null) "Editar kit" else "Añadir kit") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Nombre") },
                    placeholder = { Text("Ej. Kit 3 kW") }, singleLine = true,
                    shape = RoundedCornerShape(14.dp), colors = catalogFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = powerKw, onValueChange = { powerKw = it }, label = { Text("Potencia (kW)") },
                        singleLine = true, shape = RoundedCornerShape(14.dp), colors = catalogFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = voltage, onValueChange = {}, readOnly = true, label = { Text("Voltaje") },
                            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, tint = SolarGreen) },
                            shape = RoundedCornerShape(14.dp), colors = catalogFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Box(modifier = Modifier.matchParentSize().clickable { voltageMenuExpanded = true })
                        DropdownMenu(expanded = voltageMenuExpanded, onDismissRequest = { voltageMenuExpanded = false }) {
                            KIT_VOLTAGES.forEach { option ->
                                DropdownMenuItem(text = { Text(option) }, onClick = { voltage = option; voltageMenuExpanded = false })
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = batteryKwh, onValueChange = { batteryKwh = it }, label = { Text("Batería (kWh)") },
                        singleLine = true, shape = RoundedCornerShape(14.dp), colors = catalogFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = panelsCount, onValueChange = { panelsCount = it }, label = { Text("Paneles") },
                        singleLine = true, shape = RoundedCornerShape(14.dp), colors = catalogFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = priceUsd, onValueChange = { priceUsd = it }, label = { Text("Precio (USD)") },
                    placeholder = { Text("0.00") }, singleLine = true,
                    shape = RoundedCornerShape(14.dp), colors = catalogFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it }, label = { Text("Descripción (opcional)") },
                    shape = RoundedCornerShape(14.dp), colors = catalogFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Visible en el catálogo público", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = active, onCheckedChange = { active = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = SolarGreen),
                    )
                }

                if (editing != null) {
                    HorizontalDividerSpacer()
                    CatalogKitPhotosSection(
                        kitUuid = editing.uuid,
                        photosFlow = photosFlowProvider(editing.uuid),
                        photoState = photoState,
                        onAddPhoto = onAddPhoto,
                        onRetryPhoto = onRetryPhoto,
                        onRemovePhoto = onRemovePhoto,
                        onDismissPhotoError = onDismissPhotoError,
                    )
                } else {
                    Text(
                        "Guardá el kit primero para poder agregarle fotos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (errorMessage != null) {
                    Text(errorMessage, color = SolarError, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, powerKw, voltage, batteryKwh, panelsCount, priceUsd, description, active) }) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun HorizontalDividerSpacer() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun CatalogKitPhotosSection(
    kitUuid: String,
    photosFlow: kotlinx.coroutines.flow.Flow<List<CatalogKitPhotoEntity>>,
    photoState: PhotoUiState,
    onAddPhoto: (android.net.Uri) -> Unit,
    onRetryPhoto: (String) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onDismissPhotoError: () -> Unit,
) {
    val photos by photosFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val remaining = (CatalogKitPhotoRepository.MAX_PHOTOS_PER_KIT - photos.size).coerceAtLeast(0)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && remaining > 0) onAddPhoto(uri)
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Fotos ($remaining libres)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (remaining > 0 && photoState !is PhotoUiState.Uploading) {
                TextButton(onClick = { picker.launch("image/*") }) { Text("+ Foto", color = SolarGreen) }
            }
        }

        if (photos.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos, key = { it.uuid }) { photo ->
                    CatalogKitPhotoThumb(photo = photo, onRetry = { onRetryPhoto(photo.uuid) }, onRemove = { onRemovePhoto(photo.uuid) })
                }
            }
        }

        if (photoState is PhotoUiState.Uploading) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = SolarGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Subiendo foto...", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (photoState is PhotoUiState.Error) {
            Spacer(Modifier.height(8.dp))
            Text(photoState.message, color = SolarError, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDismissPhotoError) { Text("Cerrar") }
        }
    }
}

@Composable
private fun CatalogKitPhotoThumb(photo: CatalogKitPhotoEntity, onRetry: () -> Unit, onRemove: () -> Unit) {
    Box(modifier = Modifier.size(76.dp)) {
        val model: Any = photo.localPath?.let { File(it) }
            ?: "${BuildConfig.API_BASE_URL.trimEnd('/')}${photo.url}"
        AsyncImage(
            model = model,
            contentDescription = "Foto del kit",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(76.dp).clip(RoundedCornerShape(10.dp)),
        )
        if (photo.uploadStatus == "uploading") {
            Box(
                modifier = Modifier.size(76.dp).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp)) }
        }
        if (photo.uploadStatus == "error") {
            Box(
                modifier = Modifier.size(76.dp).background(SolarError.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .clickable { onRetry() },
                contentAlignment = Alignment.Center,
            ) { Text("Reintentar", style = MaterialTheme.typography.labelSmall, color = SolarError) }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(22.dp).align(Alignment.TopEnd)
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50)),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Quitar foto", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun catalogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SolarGreen,
    unfocusedBorderColor = SolarGreen.copy(alpha = 0.35f),
    focusedLabelColor = SolarGreen,
    cursorColor = SolarGreen,
)
