package com.gmp.offline.ui.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gmp.offline.data.remote.dto.CompanyDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val companies by viewModel.companies.collectAsStateWithLifecycle()

    var expanded by remember { mutableStateOf(false) }
    var selectedCompany by remember { mutableStateOf<CompanyDto?>(null) }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Navega apenas el login (login + sync inicial) termina con éxito.
    // El destino exacto por rol se resuelve en el NavGraph, no acá.
    if (uiState is LoginUiState.Success) {
        onLoginSuccess()
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("GMP — Gestión Montajes Pro", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selectedCompany?.name ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Empresa") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                if (companies.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Cargando empresas...") },
                        onClick = {},
                        enabled = false,
                    )
                }
                companies.forEach { company ->
                    DropdownMenuItem(
                        text = { Text(company.name) },
                        onClick = {
                            selectedCompany = company
                            expanded = false
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Teléfono") },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { viewModel.login(selectedCompany?.id ?: "", phone, password) },
            enabled = uiState !is LoginUiState.LoggingIn,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState is LoginUiState.LoggingIn) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
            } else {
                Text("Iniciar sesión")
            }
        }

        val currentState = uiState
        if (currentState is LoginUiState.Error) {
            Spacer(Modifier.height(12.dp))
            Text(currentState.message, color = MaterialTheme.colorScheme.error)
        }
    }
}
