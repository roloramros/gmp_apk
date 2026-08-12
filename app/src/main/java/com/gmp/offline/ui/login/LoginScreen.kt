package com.gmp.offline.ui.login

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gmp.offline.data.remote.dto.CompanyDto
import com.gmp.offline.ui.theme.SolarAmber
import com.gmp.offline.ui.theme.SolarAmberDeep
import com.gmp.offline.ui.theme.SolarGreen
import com.gmp.offline.ui.theme.SolarGreenDark
import com.gmp.offline.ui.theme.SolarSky
import com.gmp.offline.ui.theme.SolarSkyDeep

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
    var passwordVisible by remember { mutableStateOf(false) }

    // Navega apenas el login (login + sync inicial) termina con éxito.
    // El destino exacto por rol se resuelve en el NavGraph, no acá.
    if (uiState is LoginUiState.Success) {
        onLoginSuccess()
    }

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(SolarGreenDark, SolarSkyDeep, SolarSky),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(top = 64.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SolarBadge()

            Spacer(Modifier.height(20.dp))

            Text(
                "Gestión Montajes Pro",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Montaje e instalación de paneles solares",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )

            Spacer(Modifier.height(36.dp))

            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Iniciar sesión",
                        style = MaterialTheme.typography.titleMedium,
                        color = SolarGreenDark,
                    )
                    Spacer(Modifier.height(18.dp))

                    if (uiState is LoginUiState.LoadingCompanies) {
                        Text(
                            "Cargando empresas...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    // Selector de empresa
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedCompany?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Empresa") },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    tint = SolarGreen,
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = gmpFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // Capa transparente ENCIMA del OutlinedTextField para capturar
                        // el toque: un OutlinedTextField (aunque sea readOnly) maneja
                        // sus propios eventos de puntero para foco/cursor, así que un
                        // `.clickable` puesto directamente sobre él no llega a disparar.
                        // Este Box invisible del mismo tamaño intercepta el tap antes
                        // de que llegue al campo.
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

                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Teléfono") },
                        leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null, tint = SolarGreen) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = gmpFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = SolarGreen) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                                    tint = SolarGreen,
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = gmpFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.login(selectedCompany?.id ?: "", phone, password) },
                        enabled = uiState !is LoginUiState.LoggingIn,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SolarAmber,
                            contentColor = SolarGreenDark,
                            disabledContainerColor = SolarAmber.copy(alpha = 0.5f),
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        if (uiState is LoginUiState.LoggingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = SolarGreenDark,
                                strokeWidth = 2.5.dp,
                            )
                        } else {
                            Text(
                                "Iniciar sesión",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }

                    val currentState = uiState
                    if (currentState is LoginUiState.Error) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            currentState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    if (companies.isEmpty() && currentState !is LoginUiState.LoadingCompanies) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No se cargó ninguna empresa.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = { viewModel.loadCompanies() },
                            colors = ButtonDefaults.buttonColors(containerColor = SolarSky),
                        ) {
                            Text("Reintentar")
                        }
                    }
                }
            }
        }
    }
}

/** Colores M3 para los OutlinedTextField, en línea con la paleta solar. */
@Composable
private fun gmpFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SolarGreen,
    unfocusedBorderColor = SolarGreen.copy(alpha = 0.35f),
    focusedLabelColor = SolarGreen,
    cursorColor = SolarGreen,
)

/**
 * Insignia circular con un sol dibujado a mano (Canvas): un disco dorado con
 * rayos alrededor, con una leve animación de "pulso" en los rayos — evoca
 * energía solar sin depender de un set de íconos extendido.
 */
@Composable
private fun SolarBadge() {
    val infiniteTransition = rememberInfiniteTransition(label = "solarPulse")
    val rayLength by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rayLength",
    )

    Box(
        modifier = Modifier
            .size(92.dp)
            .background(Color.White.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(72.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val coreRadius = size.minDimension * 0.28f
            val rayInner = coreRadius * 1.35f
            val rayOuter = coreRadius * (1.9f * rayLength)

            // Rayos
            for (i in 0 until 8) {
                val angle = (i * 45f)
                rotate(degrees = angle, pivot = center) {
                    drawLine(
                        color = SolarAmber,
                        start = Offset(center.x, center.y - rayInner),
                        end = Offset(center.x, center.y - rayOuter),
                        strokeWidth = 5f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            // Disco central con degradado dorado
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SolarAmber, SolarAmberDeep),
                    center = center,
                    radius = coreRadius,
                ),
                radius = coreRadius,
                center = center,
            )
        }
    }
}
