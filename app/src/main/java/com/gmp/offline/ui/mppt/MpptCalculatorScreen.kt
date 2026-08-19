package com.gmp.offline.ui.mppt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gmp.offline.ui.theme.SolarAmber
import com.gmp.offline.ui.theme.SolarAmberDeep
import com.gmp.offline.ui.theme.SolarError
import com.gmp.offline.ui.theme.SolarGreen
import com.gmp.offline.ui.theme.SolarGreenDark
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MpptCalculatorScreen(
    onBack: () -> Unit,
    viewModel: MpptCalculatorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var availablePanels by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calculadora Dimensionado MPPT", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = SolarGreen,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InputSection(title = "Datos del panel (STC)") {
                CalculatorField("Voc", state.voc, "V", viewModel::updateVoc)
                CalculatorField("Isc", state.isc, "A", viewModel::updateIsc)
                CalculatorField("Vmp", state.vmp, "V", viewModel::updateVmp)
                CalculatorField("Imp", state.imp, "A", viewModel::updateImp)
                CalculatorField("Pmax", state.pmax, "W", viewModel::updatePmax)
            }

            InputSection(title = "Datos del MPPT / inversor") {
                CalculatorField("V mín. de trabajo", state.vMinMppt, "V", viewModel::updateVMinMppt)
                CalculatorField("V máx. de entrada", state.vMaxMppt, "V", viewModel::updateVMaxMppt)
                CalculatorField("I máx. por MPPT", state.iMaxMppt, "A", viewModel::updateIMaxMppt)
                CalculatorField("Potencia máxima del arreglo (MPPT)", state.pNomMppt, "W", viewModel::updatePNomMppt)
            }

            Button(
                onClick = {
                    availablePanels = ""
                    viewModel.calculate()
                },
                enabled = state.allFieldsCompleted,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Calcular")
            }

            state.errorMessage?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = SolarError.copy(alpha = 0.10f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = message,
                        color = SolarError,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            state.result?.let { result ->
                val filteredResult = filterResultByPanelCount(result, availablePanels)
                RecommendedCard(
                    result = filteredResult ?: result,
                    availablePanels = availablePanels,
                    onAvailablePanelsChange = { value ->
                        if (value.all { it.isDigit() }) availablePanels = value
                    },
                    noMatchingCombination = availablePanels.isNotBlank() && filteredResult == null,
                )

                if (availablePanels.isNotBlank() && filteredResult == null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = SolarAmber.copy(alpha = 0.14f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "No existe ninguna combinación eléctricamente válida que utilice exactamente $availablePanels paneles con estos datos de panel y MPPT.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SolarGreenDark,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else {
                    val visibleResult = filteredResult ?: result
                    CombinationsTable(result = visibleResult)
                    ResultFootnote(
                        availablePanels = availablePanels.toIntOrNull(),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun InputSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = SolarGreenDark,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun CalculatorField(
    label: String,
    value: String,
    unit: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = { Text(unit) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RecommendedCard(
    result: MpptCalculationResult,
    availablePanels: String,
    onAvailablePanelsChange: (String) -> Unit,
    noMatchingCombination: Boolean,
) {
    val recommended = result.recommended
    val healthy = recommended.type == MpptSizingType.SANO
    val accent = when (recommended.type) {
        MpptSizingType.SANO -> SolarGreen
        MpptSizingType.SUBDIMENSIONADO -> SolarAmberDeep
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (healthy) SolarGreen.copy(alpha = 0.10f) else accent.copy(alpha = 0.08f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (availablePanels.isBlank()) "Configuración recomendada" else "Configuración recomendada para tus paneles",
                style = MaterialTheme.typography.titleMedium,
                color = SolarGreenDark,
                fontWeight = FontWeight.Bold,
            )

            if (!noMatchingCombination) {
                Text(
                    text = "${recommended.seriesPanels} paneles en serie × ${recommended.strings} ${stringLabel(recommended.strings)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                RatioBadge(type = recommended.type, ratio = recommended.ratio)
            }

            Divider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text = "¿Cuántos paneles tienes?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = availablePanels,
                onValueChange = onAvailablePanelsChange,
                label = { Text("Cantidad de paneles disponibles") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    Text("Al indicar una cantidad, se mostrarán solo las configuraciones que utilicen exactamente ese total de paneles.")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CombinationsTable(result: MpptCalculationResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Combinaciones válidas",
                style = MaterialTheme.typography.titleMedium,
                color = SolarGreenDark,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Configuración", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                Text("Potencia", fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(0.8f))
                Text("Uso", fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(0.8f))
            }
            Divider()

            result.combinations.forEach { combination ->
                val isRecommended = combination == result.recommended
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1.5f)) {
                        Text(
                            text = "${combination.seriesPanels} en serie × ${combination.strings} ${stringLabel(combination.strings)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isRecommended) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (isRecommended) {
                            Text(
                                text = "Recomendada",
                                style = MaterialTheme.typography.labelSmall,
                                color = SolarGreen,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Text(
                        text = "${formatNumber(combination.totalPowerW, 0)} W",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.8f),
                    )
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.weight(0.8f),
                    ) {
                        RatioBadge(type = combination.type, ratio = combination.ratio, compact = true)
                    }
                }
                Divider()
            }

            Spacer(Modifier.height(12.dp))
            LegendRow(type = MpptSizingType.SUBDIMENSIONADO, text = "< 0.90 · Subdimensionado")
            LegendRow(type = MpptSizingType.SANO, text = "0.90–1.00 · Sano")
        }
    }
}

@Composable
private fun RatioBadge(
    type: MpptSizingType,
    ratio: Double,
    compact: Boolean = false,
) {
    val background = when (type) {
        MpptSizingType.SANO -> SolarGreen
        MpptSizingType.SUBDIMENSIONADO -> SolarAmber
    }
    val foreground = when (type) {
        MpptSizingType.SUBDIMENSIONADO -> SolarGreenDark
        MpptSizingType.SANO -> Color.White
    }
    val typeText = when (type) {
        MpptSizingType.SANO -> "SANO"
        MpptSizingType.SUBDIMENSIONADO -> "SUBDIMENSIONADO"
    }
    val label = if (compact) formatNumber(ratio, 2) else "$typeText · ${formatNumber(ratio, 2)}"

    Surface(
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = if (compact) 8.dp else 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun LegendRow(type: MpptSizingType, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RatioBadge(
            type = type,
            ratio = when (type) {
                MpptSizingType.SUBDIMENSIONADO -> 0.89
                MpptSizingType.SANO -> 1.00
            },
            compact = true,
        )
        Spacer(Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ResultFootnote(
    availablePanels: Int?,
) {
    val panelFilterText = availablePanels?.let {
        " Se filtraron las configuraciones para utilizar exactamente $it paneles."
    }.orEmpty()

    Text(
        text = "Se recomienda la combinación que más aprovecha la potencia máxima del MPPT sin superarla. Ninguna configuración mostrada excede ese límite.$panelFilterText",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

private fun filterResultByPanelCount(
    result: MpptCalculationResult,
    availablePanels: String,
): MpptCalculationResult? {
    if (availablePanels.isBlank()) return result
    val panelCount = availablePanels.toIntOrNull() ?: return null
    if (panelCount <= 0) return null

    val filtered = result.combinations.filter { combination ->
        combination.seriesPanels * combination.strings == panelCount
    }
    if (filtered.isEmpty()) return null

    val recommended = filtered.maxBy { it.ratio }
    val sorted = filtered.sortedWith(
        compareByDescending<MpptCombination> { it.ratio }
            .thenBy { it.seriesPanels }
            .thenBy { it.strings },
    )

    return MpptCalculationResult(
        recommended = recommended,
        combinations = sorted,
        hasHealthyOption = true,
    )
}

private fun stringLabel(strings: Int): String = if (strings == 1) "string" else "strings"

private fun formatNumber(value: Double, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", value)
