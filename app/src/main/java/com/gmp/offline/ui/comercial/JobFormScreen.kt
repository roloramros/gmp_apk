package com.gmp.offline.ui.comercial

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gmp.offline.data.local.entities.StaffEntity
import com.gmp.offline.ui.theme.SolarAmber
import com.gmp.offline.ui.theme.SolarGreen
import com.gmp.offline.ui.theme.SolarGreenDark

// Formulario de crear/editar un job (rol comercial). Mismo patrón offline
// -first que el resto de la app: al guardar, JobFormViewModel aplica el
// cambio en Room al toque y encola el comando (POST /jobs o PATCH
// /jobs/:uuid) — no espera respuesta de red para navegar de vuelta.
@Composable
fun JobFormScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: JobFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clients by viewModel.clients.collectAsStateWithLifecycle()

    val title by viewModel.title.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()
    val address by viewModel.address.collectAsStateWithLifecycle()
    val selectedClient by viewModel.selectedClient.collectAsStateWithLifecycle()

    // Una vez cargados los clientes, resolver el que ya tenía asignado el
    // job en edición (guardado como uuid suelto hasta este punto).
    LaunchedEffect(clients) {
        if (viewModel.isEditing && selectedClient == null) {
            viewModel.selectedClientUuid?.let { uuid ->
                clients.find { it.uuid == uuid }?.let { viewModel.selectedClient.value = it }
            }
        }
    }

    LaunchedEffect(uiState) {
        val state = uiState
        if (state is JobFormUiState.Saved) {
            onSaved(state.jobUuid)
        }
    }

    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditing) "Editar trabajo" else "Nuevo trabajo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { viewModel.title.value = it },
                label = { Text("Título del trabajo *") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = gmpFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = description,
                onValueChange = { viewModel.description.value = it },
                label = { Text("Descripción") },
                minLines = 2,
                shape = RoundedCornerShape(14.dp),
                colors = gmpFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = address,
                onValueChange = { viewModel.address.value = it },
                label = { Text("Dirección") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = gmpFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            // Selector de cliente (opcional)
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = selectedClient?.fullName ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Cliente (opcional)") },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = SolarGreen) },
                    shape = RoundedCornerShape(14.dp),
                    colors = gmpFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                // Ver nota en LoginScreen.kt: un OutlinedTextField readOnly se
                // come el clickable puesto encima; se necesita esta capa
                // transparente aparte para poder abrir el menú.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { expanded = true },
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.85f),
                ) {
                    DropdownMenuItem(
                        text = { Text("Sin cliente asignado") },
                        onClick = {
                            viewModel.selectedClient.value = null
                            expanded = false
                        },
                    )
                    clients.forEach { client: StaffEntity ->
                        DropdownMenuItem(
                            text = { Text(client.fullName) },
                            onClick = {
                                viewModel.selectedClient.value = client
                                expanded = false
                            },
                        )
                    }
                }
            }

            val state = uiState
            if (state is JobFormUiState.Error) {
                Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = { viewModel.save() },
                enabled = state !is JobFormUiState.Saving,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SolarAmber, contentColor = SolarGreenDark),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state is JobFormUiState.Saving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SolarGreenDark, strokeWidth = 2.5.dp)
                } else {
                    Text(if (viewModel.isEditing) "Guardar cambios" else "Crear trabajo")
                }
            }
        }
    }
}

@Composable
private fun gmpFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SolarGreen,
    unfocusedBorderColor = SolarGreen.copy(alpha = 0.35f),
    focusedLabelColor = SolarGreen,
    cursorColor = SolarGreen,
)
