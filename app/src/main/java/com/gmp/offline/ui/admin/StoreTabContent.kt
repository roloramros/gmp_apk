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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.gmp.offline.data.local.entities.CatalogProductEntity
import com.gmp.offline.data.local.entities.CatalogProductPhotoEntity
import com.gmp.offline.data.repository.CatalogProductPhotoRepository
import com.gmp.offline.ui.comercial.PhotoUiState
import com.gmp.offline.ui.theme.SolarError
import com.gmp.offline.ui.theme.SolarGreen
import java.io.File

@Composable
fun StoreTabContent(
    searchQuery: String = "",
    modifier: Modifier = Modifier,
    viewModel: StoreViewModel = hiltViewModel(),
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var showForm by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<CatalogProductEntity?>(null) }
    var deletingProduct by remember { mutableStateOf<CatalogProductEntity?>(null) }

    val normalizedQuery = searchQuery.trim()
    val visibleProducts = remember(products, normalizedQuery) {
        if (normalizedQuery.isBlank()) products
        else products.filter { it.name.contains(normalizedQuery, ignoreCase = true) || it.category?.contains(normalizedQuery, ignoreCase = true) == true }
    }
    val grouped = remember(visibleProducts) {
        visibleProducts.groupBy { it.category?.trim().takeUnless { c -> c.isNullOrBlank() } ?: "Sin categoría" }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tienda de componentes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Button(onClick = { editingProduct = null; showForm = true }, colors = ButtonDefaults.buttonColors(containerColor = SolarGreen)) {
                Text("+ Añadir")
            }
        }

        if (visibleProducts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (normalizedQuery.isNotBlank()) "No hay productos que coincidan con \"$normalizedQuery\"." else "Aún no hay productos en la tienda.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                grouped.forEach { (category, items) ->
                    item(key = "header-$category") {
                        Text(
                            category, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                            color = SolarGreen, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                    items(items, key = { it.uuid }) { product ->
                        StoreProductRow(product = product, onEdit = { editingProduct = product; showForm = true }, onDelete = { deletingProduct = product })
                    }
                }
            }
        }
    }

    if (showForm) {
        StoreProductFormDialog(
            editing = editingProduct,
            errorMessage = errorMessage,
            photoState = viewModel.photoState.collectAsStateWithLifecycle().value,
            photosFlowProvider = { uuid -> viewModel.photosForProduct(uuid) },
            onDismiss = { showForm = false; viewModel.clearError(); viewModel.dismissPhotoError() },
            onSave = { category, name, priceUsd, description, inStock, active ->
                viewModel.save(editingProduct?.uuid, category, name, priceUsd, description, inStock, active) { showForm = false }
            },
            onAddPhoto = { uri -> editingProduct?.let { viewModel.addPhoto(it.uuid, uri) } },
            onRetryPhoto = { photoUuid -> editingProduct?.let { viewModel.retryPhoto(it.uuid, photoUuid) } },
            onRemovePhoto = { photoUuid -> editingProduct?.let { viewModel.removePhoto(it.uuid, photoUuid) } },
            onDismissPhotoError = { viewModel.dismissPhotoError() },
        )
    }

    deletingProduct?.let { product ->
        AlertDialog(
            onDismissRequest = { deletingProduct = null },
            title = { Text("Eliminar producto") },
            text = { Text("¿Eliminar \"${product.name}\"? Deja de mostrarse en la tienda pública de inmediato.") },
            confirmButton = { TextButton(onClick = { viewModel.delete(product.uuid); deletingProduct = null }) { Text("Sí, eliminar", color = SolarError) } },
            dismissButton = { TextButton(onClick = { deletingProduct = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun StoreProductRow(product: CatalogProductEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(product.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    if (!product.active) { Spacer(Modifier.width(8.dp)); Tag("Oculto", SolarError) }
                    if (!product.inStock) { Spacer(Modifier.width(8.dp)); Tag("Sin stock", MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (!product.description.isNullOrBlank()) {
                    Text(product.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Text(
                    product.priceUsd?.let { "$${it}" } ?: "Sin precio",
                    style = MaterialTheme.typography.bodyMedium, color = SolarGreen, fontWeight = FontWeight.Medium,
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = SolarGreen) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = SolarError) }
        }
    }
}

@Composable
private fun Tag(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(50))) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

@Composable
private fun StoreProductFormDialog(
    editing: CatalogProductEntity?,
    errorMessage: String?,
    photoState: PhotoUiState,
    photosFlowProvider: (String) -> kotlinx.coroutines.flow.Flow<List<CatalogProductPhotoEntity>>,
    onDismiss: () -> Unit,
    onSave: (category: String, name: String, priceUsd: String, description: String, inStock: Boolean, active: Boolean) -> Unit,
    onAddPhoto: (android.net.Uri) -> Unit,
    onRetryPhoto: (String) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onDismissPhotoError: () -> Unit,
) {
    var category by remember(editing) { mutableStateOf(editing?.category ?: "") }
    var name by remember(editing) { mutableStateOf(editing?.name ?: "") }
    var priceUsd by remember(editing) { mutableStateOf(editing?.priceUsd ?: "") }
    var description by remember(editing) { mutableStateOf(editing?.description ?: "") }
    var inStock by remember(editing) { mutableStateOf(editing?.inStock ?: true) }
    var active by remember(editing) { mutableStateOf(editing?.active ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing != null) "Editar producto" else "Añadir producto") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = category, onValueChange = { category = it }, label = { Text("Categoría") },
                    placeholder = { Text("Ej. Paneles, Inversores, Baterías...") }, singleLine = true,
                    shape = RoundedCornerShape(14.dp), colors = storeFieldColors(), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Nombre") }, singleLine = true,
                    shape = RoundedCornerShape(14.dp), colors = storeFieldColors(), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = priceUsd, onValueChange = { priceUsd = it }, label = { Text("Precio (USD)") },
                    placeholder = { Text("0.00") }, singleLine = true,
                    shape = RoundedCornerShape(14.dp), colors = storeFieldColors(), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it }, label = { Text("Descripción (opcional)") },
                    shape = RoundedCornerShape(14.dp), colors = storeFieldColors(), modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("En stock", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = inStock, onCheckedChange = { inStock = it }, colors = SwitchDefaults.colors(checkedTrackColor = SolarGreen))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Visible en la tienda pública", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = active, onCheckedChange = { active = it }, colors = SwitchDefaults.colors(checkedTrackColor = SolarGreen))
                }

                if (editing != null) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    StoreProductPhotosSection(
                        productUuid = editing.uuid, photosFlow = photosFlowProvider(editing.uuid), photoState = photoState,
                        onAddPhoto = onAddPhoto, onRetryPhoto = onRetryPhoto, onRemovePhoto = onRemovePhoto, onDismissPhotoError = onDismissPhotoError,
                    )
                } else {
                    Text("Guardá el producto primero para poder agregarle fotos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (errorMessage != null) Text(errorMessage, color = SolarError, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(category, name, priceUsd, description, inStock, active) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun StoreProductPhotosSection(
    productUuid: String,
    photosFlow: kotlinx.coroutines.flow.Flow<List<CatalogProductPhotoEntity>>,
    photoState: PhotoUiState,
    onAddPhoto: (android.net.Uri) -> Unit,
    onRetryPhoto: (String) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onDismissPhotoError: () -> Unit,
) {
    val photos by photosFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val remaining = (CatalogProductPhotoRepository.MAX_PHOTOS_PER_PRODUCT - photos.size).coerceAtLeast(0)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null && remaining > 0) onAddPhoto(uri) }

    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Fotos ($remaining libres)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (remaining > 0 && photoState !is PhotoUiState.Uploading) TextButton(onClick = { picker.launch("image/*") }) { Text("+ Foto", color = SolarGreen) }
        }
        if (photos.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos, key = { it.uuid }) { photo ->
                    StorePhotoThumb(photo = photo, onRetry = { onRetryPhoto(photo.uuid) }, onRemove = { onRemovePhoto(photo.uuid) })
                }
            }
        }
        if (photoState is PhotoUiState.Uploading) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = SolarGreen, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Subiendo foto...", style = MaterialTheme.typography.bodySmall)
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
private fun StorePhotoThumb(photo: CatalogProductPhotoEntity, onRetry: () -> Unit, onRemove: () -> Unit) {
    Box(modifier = Modifier.size(76.dp)) {
        val model: Any = photo.localPath?.let { File(it) } ?: "${BuildConfig.API_BASE_URL.trimEnd('/')}${photo.url}"
        AsyncImage(model = model, contentDescription = "Foto del producto", contentScale = ContentScale.Crop, modifier = Modifier.size(76.dp).clip(RoundedCornerShape(10.dp)))
        if (photo.uploadStatus == "uploading") {
            Box(modifier = Modifier.size(76.dp).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
            }
        }
        if (photo.uploadStatus == "error") {
            Box(modifier = Modifier.size(76.dp).background(SolarError.copy(alpha = 0.25f), RoundedCornerShape(10.dp)).clickable { onRetry() }, contentAlignment = Alignment.Center) {
                Text("Reintentar", style = MaterialTheme.typography.labelSmall, color = SolarError)
            }
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(22.dp).align(Alignment.TopEnd).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))) {
            Icon(Icons.Filled.Close, contentDescription = "Quitar foto", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun storeFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SolarGreen, unfocusedBorderColor = SolarGreen.copy(alpha = 0.35f),
    focusedLabelColor = SolarGreen, cursorColor = SolarGreen,
)
