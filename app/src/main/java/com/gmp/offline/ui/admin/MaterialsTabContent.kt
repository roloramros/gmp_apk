package com.gmp.offline.ui.admin

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gmp.offline.data.local.entities.MaterialEntity
import com.gmp.offline.ui.theme.SolarError
import com.gmp.offline.ui.theme.SolarGreen

@Composable
fun MaterialsTabContent(
    searchQuery: String = "",
    viewModel: MaterialsViewModel = hiltViewModel(),
) {
    val materials by viewModel.materials.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var showForm by remember { mutableStateOf(false) }
    var editingMaterial by remember { mutableStateOf<MaterialEntity?>(null) }
    var deletingMaterial by remember { mutableStateOf<MaterialEntity?>(null) }

    val normalizedQuery = searchQuery.trim()
    val visibleMaterials = remember(materials, normalizedQuery) {
        if (normalizedQuery.isBlank()) materials
        else materials.filter { it.name.contains(normalizedQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Listado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Button(
                onClick = {
                    editingMaterial = null
                    showForm = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = SolarGreen),
            ) {
                Text("+ Añadir material")
            }
        }

        if (visibleMaterials.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (normalizedQuery.isNotBlank()) {
                        "No hay materiales que coincidan con \"$normalizedQuery\"."
                    } else {
                        "Aún no hay materiales registrados."
                    },
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
                items(visibleMaterials, key = { it.uuid }) { material ->
                    MaterialRow(
                        material = material,
                        onEdit = {
                            editingMaterial = material
                            showForm = true
                        },
                        onDelete = { deletingMaterial = material },
                    )
                }
            }
        }
    }

    if (showForm) {
        MaterialFormDialog(
            editing = editingMaterial,
            errorMessage = errorMessage,
            onDismiss = {
                showForm = false
                viewModel.clearError()
            },
            onSave = { name, unit, price ->
                viewModel.save(editingMaterial?.uuid, name, unit, price) {
                    showForm = false
                }
            },
        )
    }

    deletingMaterial?.let { material ->
        AlertDialog(
            onDismissRequest = { deletingMaterial = null },
            title = { Text("Eliminar material") },
            text = { Text("¿Eliminar \"${material.name}\"? Esta acción se puede revertir solo desde la base de datos.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(material.uuid)
                    deletingMaterial = null
                }) {
                    Text("Sí, eliminar", color = SolarError)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingMaterial = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun MaterialRow(material: MaterialEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(material.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UnitTag(material.unit)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        material.defaultPrice?.let { "$${it}" } ?: "—",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Editar", tint = SolarGreen)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = SolarError)
            }
        }
    }
}

@Composable
private fun UnitTag(unit: String?) {
    if (unit.isNullOrBlank()) return
    Box(
        modifier = Modifier.background(SolarGreen.copy(alpha = 0.12f), RoundedCornerShape(50)),
    ) {
        Text(
            unit,
            style = MaterialTheme.typography.labelSmall,
            color = SolarGreen,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun MaterialFormDialog(
    editing: MaterialEntity?,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, unit: String, price: String) -> Unit,
) {
    var name by remember(editing) { mutableStateOf(editing?.name ?: "") }
    var unit by remember(editing) { mutableStateOf(editing?.unit ?: MATERIAL_UNITS.first()) }
    var price by remember(editing) { mutableStateOf(editing?.defaultPrice ?: "") }
    var unitMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing != null) "Editar material" else "Añadir material") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    placeholder = { Text("Ej. Cable 12 AWG") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = materialFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Unidad de medida") },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = SolarGreen) },
                        shape = RoundedCornerShape(14.dp),
                        colors = materialFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { unitMenuExpanded = true },
                    )
                    DropdownMenu(expanded = unitMenuExpanded, onDismissRequest = { unitMenuExpanded = false }) {
                        MATERIAL_UNITS.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    unit = option
                                    unitMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Precio (USD)") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = materialFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (errorMessage != null) {
                    Text(errorMessage, color = SolarError, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, unit, price) }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun materialFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SolarGreen,
    unfocusedBorderColor = SolarGreen.copy(alpha = 0.35f),
    focusedLabelColor = SolarGreen,
    cursorColor = SolarGreen,
)
