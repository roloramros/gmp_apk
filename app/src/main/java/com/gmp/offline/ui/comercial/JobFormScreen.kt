package com.gmp.offline.ui.comercial

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gmp.offline.ui.theme.SolarAmber
import com.gmp.offline.ui.theme.SolarError
import com.gmp.offline.ui.theme.SolarGreen
import com.gmp.offline.ui.theme.SolarGreenDark

// Formulario de crear/editar un "montaje" (rol comercial) — réplica exacta
// de los campos del modal "Nuevo montaje" de la web legada (ver
// index.html: jobClientName, jobClientCi, jobClientPhone, jobAddress,
// jobLat/jobLng, jobReference, jobSiteNotes, jobDescription, jobPrice,
// jobPayment, jobVisitDate, jobProposedDate). Los campos de "Gestión
// (Admin)" de la web (fecha oficial, estado, asignar trabajadores) no
// aparecen acá: esta pantalla es solo la vista de comercial.
//
// Mismo patrón offline-first que el resto de la app: al guardar,
// JobFormViewModel aplica el cambio en Room al toque y encola el comando
// (POST /jobs o PATCH /jobs/:uuid) — no espera respuesta de red para
// navegar de vuelta.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobFormScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: JobFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clients by viewModel.clients.collectAsStateWithLifecycle()

    val clientName by viewModel.clientName.collectAsStateWithLifecycle()
    val clientCi by viewModel.clientCi.collectAsStateWithLifecycle()
    val clientPhone by viewModel.clientPhone.collectAsStateWithLifecycle()
    val address by viewModel.address.collectAsStateWithLifecycle()
    val latitude by viewModel.latitude.collectAsStateWithLifecycle()
    val longitude by viewModel.longitude.collectAsStateWithLifecycle()
    val reference by viewModel.reference.collectAsStateWithLifecycle()
    val siteNotes by viewModel.siteNotes.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()
    val price by viewModel.price.collectAsStateWithLifecycle()
    val paymentMethod by viewModel.paymentMethod.collectAsStateWithLifecycle()
    val visitDate by viewModel.visitDate.collectAsStateWithLifecycle()
    val proposedDate by viewModel.proposedDate.collectAsStateWithLifecycle()
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

    var clientMenuExpanded by remember { mutableStateOf(false) }
    var paymentMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditing) "Editar montaje" else "Nuevo montaje") },
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
            if (viewModel.isClosedForEditing) {
                Text(
                    "Este montaje está cerrado y no se puede editar.",
                    color = SolarError,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            SectionLabel("Datos del cliente")
            GmpTextField(clientName, { viewModel.clientName.value = it }, "Nombre del cliente *")
            GmpTextField(clientCi, { viewModel.clientCi.value = it }, "CI")
            GmpTextField(clientPhone, { viewModel.clientPhone.value = it }, "Teléfono")

            SectionLabel("Ubicación")
            GmpTextField(address, { viewModel.address.value = it }, "Dirección *")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GmpTextField(latitude, { viewModel.latitude.value = it }, "Latitud", modifier = Modifier.weight(1f))
                GmpTextField(longitude, { viewModel.longitude.value = it }, "Longitud", modifier = Modifier.weight(1f))
            }
            GmpTextField(reference, { viewModel.reference.value = it }, "Referencia de ubicación")
            GmpTextField(siteNotes, { viewModel.siteNotes.value = it }, "Notas del sitio")

            SectionLabel("Trabajo")
            GmpTextField(description, { viewModel.description.value = it }, "Descripción del trabajo", minLines = 2)
            GmpTextField(price, { viewModel.price.value = it }, "Precio (USD) *")

            // Selector de forma de pago
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = PAYMENT_METHODS.first { it.value == paymentMethod }.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Forma de pago") },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = SolarGreen) },
                    shape = RoundedCornerShape(14.dp),
                    colors = gmpFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { paymentMenuExpanded = true },
                )
                DropdownMenu(expanded = paymentMenuExpanded, onDismissRequest = { paymentMenuExpanded = false }) {
                    PAYMENT_METHODS.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                viewModel.paymentMethod.value = option.value
                                paymentMenuExpanded = false
                            },
                        )
                    }
                }
            }

            SectionLabel("Fechas")
            GmpTextField(visitDate, { viewModel.visitDate.value = it }, "Fecha de visita previa (AAAA-MM-DD)")
            GmpTextField(proposedDate, { viewModel.proposedDate.value = it }, "Fecha propuesta de montaje (AAAA-MM-DD)")

            SectionLabel("Cliente registrado (opcional)")
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = selectedClient?.fullName ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Vincular a un cliente existente") },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = SolarGreen) },
                    shape = RoundedCornerShape(14.dp),
                    colors = gmpFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { clientMenuExpanded = true },
                )
                DropdownMenu(
                    expanded = clientMenuExpanded,
                    onDismissRequest = { clientMenuExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.85f),
                ) {
                    DropdownMenuItem(
                        text = { Text("Sin vincular") },
                        onClick = {
                            viewModel.selectedClient.value = null
                            clientMenuExpanded = false
                        },
                    )
                    clients.forEach { client ->
                        DropdownMenuItem(
                            text = { Text(client.fullName) },
                            onClick = {
                                viewModel.selectedClient.value = client
                                clientMenuExpanded = false
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
                enabled = state !is JobFormUiState.Saving && !viewModel.isClosedForEditing,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SolarAmber, contentColor = SolarGreenDark),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state is JobFormUiState.Saving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SolarGreenDark, strokeWidth = 2.5.dp)
                } else {
                    Text(if (viewModel.isEditing) "Guardar cambios" else "Crear montaje")
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun GmpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    minLines: Int = 1,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        minLines = minLines,
        shape = RoundedCornerShape(14.dp),
        colors = gmpFieldColors(),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun gmpFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SolarGreen,
    unfocusedBorderColor = SolarGreen.copy(alpha = 0.35f),
    focusedLabelColor = SolarGreen,
    cursorColor = SolarGreen,
)
