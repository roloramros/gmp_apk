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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gmp.offline.data.local.entities.StaffEntity
import com.gmp.offline.ui.theme.SolarAmberDeep
import com.gmp.offline.ui.theme.SolarError
import com.gmp.offline.ui.theme.SolarGreen
import com.gmp.offline.ui.theme.SolarSky

// Pestaña "Gestión de Personal" del admin — réplica de la sección
// "Personal" de la web legada (index.html: tabla Nombre/Teléfono/Rol +
// botón "+ Añadir" arriba, modal para crear con nombre/teléfono/contraseña
// inicial/rol, y baja con confirmación). El selector de código de país que
// tenía la web se sacó a pedido: el teléfono va tal cual se escribe.
@Composable
fun StaffTabContent(
    viewModel: StaffViewModel = hiltViewModel(),
) {
    val staff by viewModel.staff.collectAsStateWithLifecycle()
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val jobWorkers by viewModel.jobWorkers.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var showForm by remember { mutableStateOf(false) }
    var deactivating by remember { mutableStateOf<StaffEntity?>(null) }
    var historyMember by remember { mutableStateOf<StaffEntity?>(null) }

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
                    viewModel.clearError()
                    showForm = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = SolarGreen),
            ) {
                Text("+ Añadir")
            }
        }

        if (staff.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Aún no hay personal registrado.",
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
                items(staff, key = { it.uuid }) { member ->
                    StaffRow(
                        member = member,
                        onHistory = { historyMember = member },
                        onDeactivate = { deactivating = member },
                    )
                }
            }
        }
    }

    if (showForm) {
        StaffFormDialog(
            isSaving = isSaving,
            errorMessage = errorMessage,
            onDismiss = {
                showForm = false
                viewModel.clearError()
            },
            onSave = { fullName, phoneNumber, password, role ->
                viewModel.createStaff(fullName, phoneNumber, password, role) {
                    showForm = false
                }
            },
        )
    }

    historyMember?.let { member ->
        StaffWorkHistoryDialog(
            member = member,
            staff = staff,
            jobs = jobs,
            jobWorkers = jobWorkers,
            onDismiss = { historyMember = null },
        )
    }

    deactivating?.let { member ->
        AlertDialog(
            onDismissRequest = { deactivating = null },
            title = { Text("Eliminar usuario") },
            text = {
                Text(
                    "¿Eliminar a \"${member.fullName}\"? Perderá acceso de inmediato, pero su historial de trabajos se conserva.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deactivate(member.uuid)
                    deactivating = null
                }) {
                    Text("Sí, eliminar", color = SolarError)
                }
            },
            dismissButton = {
                TextButton(onClick = { deactivating = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun StaffRow(
    member: StaffEntity,
    onHistory: () -> Unit,
    onDeactivate: () -> Unit,
) {
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
                Text(member.fullName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        member.phone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    RoleTag(member.role)
                }
            }

            IconButton(onClick = onHistory) {
                Icon(
                    Icons.Filled.DateRange,
                    contentDescription = "Ver trabajos de ${member.fullName}",
                    tint = SolarGreen,
                )
            }

            // Igual que la web (u.role !== 'admin'): un admin no se puede
            // desactivar a sí mismo ni a otro admin desde esta pantalla.
            if (member.role != "admin") {
                IconButton(onClick = onDeactivate) {
                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = SolarError)
                }
            }
        }
    }
}

@Composable
private fun RoleTag(role: String) {
    val (label, color) = when (role) {
        "admin" -> "Admin" to SolarSky
        "comercial" -> "Comercial" to SolarAmberDeep
        else -> "Trabajador" to SolarGreen
    }
    Box(
        modifier = Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(50)),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

// Modal de alta — Nombre, Teléfono, Contraseña inicial, Rol. (La web
// legada tenía además un selector de código de país; se sacó a pedido —
// el teléfono se guarda tal cual se escribe acá).
@Composable
private fun StaffFormDialog(
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (fullName: String, phoneNumber: String, password: String, role: String) -> Unit,
) {
    var fullName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(STAFF_ROLES.first().value) }
    var roleMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir trabajador o comercial") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Nombre") },
                    placeholder = { Text("Nombre y apellidos") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = staffFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Teléfono") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(14.dp),
                    colors = staffFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña inicial") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(14.dp),
                    colors = staffFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = STAFF_ROLES.first { it.value == role }.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Rol") },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = SolarGreen) },
                        shape = RoundedCornerShape(14.dp),
                        colors = staffFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { roleMenuExpanded = true },
                    )
                    DropdownMenu(expanded = roleMenuExpanded, onDismissRequest = { roleMenuExpanded = false }) {
                        STAFF_ROLES.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    role = option.value
                                    roleMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(errorMessage, color = SolarError, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.padding(horizontal = 16.dp), color = SolarGreen, strokeWidth = 2.dp)
            } else {
                TextButton(onClick = { onSave(fullName, phoneNumber, password, role) }) {
                    Text("Crear usuario")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun staffFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SolarGreen,
    unfocusedBorderColor = SolarGreen.copy(alpha = 0.35f),
    focusedLabelColor = SolarGreen,
    cursorColor = SolarGreen,
)
