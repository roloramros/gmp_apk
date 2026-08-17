package com.gmp.offline.ui.mppt

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import javax.inject.Inject

enum class MpptSizingType {
    SUBDIMENSIONADO,
    SANO,
    SOBREDIMENSIONADO,
}

data class MpptCombination(
    val seriesPanels: Int,
    val strings: Int,
    val totalPowerW: Double,
    val ratio: Double,
    val type: MpptSizingType,
)

data class MpptCalculationResult(
    val recommended: MpptCombination,
    val combinations: List<MpptCombination>,
    val hasHealthyOption: Boolean,
)

data class MpptCalculatorUiState(
    val voc: String = "",
    val isc: String = "",
    val vmp: String = "",
    val imp: String = "",
    val pmax: String = "",
    val betaVoc: String = "",
    val vMinMppt: String = "",
    val vMaxMppt: String = "",
    val iMaxMppt: String = "",
    val pNomMppt: String = "",
    val tempMin: String = "",
    val tempMax: String = "",
    val errorMessage: String? = null,
    val result: MpptCalculationResult? = null,
) {
    val allFieldsCompleted: Boolean
        get() = listOf(
            voc,
            isc,
            vmp,
            imp,
            pmax,
            betaVoc,
            vMinMppt,
            vMaxMppt,
            iMaxMppt,
            pNomMppt,
            tempMin,
            tempMax,
        ).all { it.isNotBlank() }
}

@HiltViewModel
class MpptCalculatorViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(MpptCalculatorUiState())
    val uiState: StateFlow<MpptCalculatorUiState> = _uiState.asStateFlow()

    fun updateVoc(value: String) = update { copy(voc = value) }
    fun updateIsc(value: String) = update { copy(isc = value) }
    fun updateVmp(value: String) = update { copy(vmp = value) }
    fun updateImp(value: String) = update { copy(imp = value) }
    fun updatePmax(value: String) = update { copy(pmax = value) }
    fun updateBetaVoc(value: String) = update { copy(betaVoc = value) }
    fun updateVMinMppt(value: String) = update { copy(vMinMppt = value) }
    fun updateVMaxMppt(value: String) = update { copy(vMaxMppt = value) }
    fun updateIMaxMppt(value: String) = update { copy(iMaxMppt = value) }
    fun updatePNomMppt(value: String) = update { copy(pNomMppt = value) }
    fun updateTempMin(value: String) = update { copy(tempMin = value) }
    fun updateTempMax(value: String) = update { copy(tempMax = value) }

    fun calculate() {
        val state = _uiState.value
        if (!state.allFieldsCompleted) {
            _uiState.value = state.copy(
                errorMessage = "Completá todos los campos antes de calcular.",
                result = null,
            )
            return
        }

        val voc = state.voc.toDoubleOrNull()
        val isc = state.isc.toDoubleOrNull()
        val vmp = state.vmp.toDoubleOrNull()
        val imp = state.imp.toDoubleOrNull()
        val pmax = state.pmax.toDoubleOrNull()
        val betaVoc = state.betaVoc.toDoubleOrNull()
        val vMinMppt = state.vMinMppt.toDoubleOrNull()
        val vMaxMppt = state.vMaxMppt.toDoubleOrNull()
        val iMaxMppt = state.iMaxMppt.toDoubleOrNull()
        val pNomMppt = state.pNomMppt.toDoubleOrNull()
        val tempMin = state.tempMin.toDoubleOrNull()
        val tempMax = state.tempMax.toDoubleOrNull()

        if (listOf(
                voc,
                isc,
                vmp,
                imp,
                pmax,
                betaVoc,
                vMinMppt,
                vMaxMppt,
                iMaxMppt,
                pNomMppt,
                tempMin,
                tempMax,
            ).any { it == null }
        ) {
            _uiState.value = state.copy(
                errorMessage = "Revisá los valores ingresados: todos deben ser números válidos.",
                result = null,
            )
            return
        }

        if (voc!! <= 0.0 || isc!! <= 0.0 || vmp!! <= 0.0 || imp!! <= 0.0 ||
            pmax!! <= 0.0 || vMinMppt!! <= 0.0 || vMaxMppt!! <= 0.0 ||
            iMaxMppt!! <= 0.0 || pNomMppt!! <= 0.0
        ) {
            _uiState.value = state.copy(
                errorMessage = "Los valores eléctricos y de potencia deben ser mayores que cero.",
                result = null,
            )
            return
        }

        val vocCorregido = voc * (1 + (betaVoc!! / 100) * (tempMin!! - 25))
        val vmpCorregido = vmp * (1 + (betaVoc / 100) * (tempMax!! - 25))

        if (vocCorregido <= 0.0 || vmpCorregido <= 0.0) {
            _uiState.value = state.copy(
                errorMessage = "Las condiciones ingresadas producen un voltaje corregido no válido.",
                result = null,
            )
            return
        }

        val nMax = floor(vMaxMppt / vocCorregido).toInt()
        val nMin = ceil(vMinMppt / vmpCorregido).toInt()
        val stringsMax = floor(iMaxMppt / isc).toInt()

        if (nMax < nMin) {
            _uiState.value = state.copy(
                errorMessage = "El rango de voltaje del MPPT no permite ninguna cantidad de paneles en serie válida con estos datos (el mínimo requerido supera al máximo permitido).",
                result = null,
            )
            return
        }

        if (stringsMax < 1) {
            _uiState.value = state.copy(
                errorMessage = "La corriente Isc del panel supera la corriente máxima admitida por el MPPT: ni un solo string es viable.",
                result = null,
            )
            return
        }

        val combinations = buildList {
            for (n in nMin..nMax) {
                for (s in 1..stringsMax) {
                    val power = n * s * pmax
                    val ratio = power / pNomMppt
                    val type = when {
                        ratio < 1.10 -> MpptSizingType.SUBDIMENSIONADO
                        ratio <= 1.30 -> MpptSizingType.SANO
                        else -> MpptSizingType.SOBREDIMENSIONADO
                    }
                    add(
                        MpptCombination(
                            seriesPanels = n,
                            strings = s,
                            totalPowerW = power,
                            ratio = ratio,
                            type = type,
                        ),
                    )
                }
            }
        }

        if (combinations.isEmpty()) {
            _uiState.value = state.copy(
                errorMessage = "No se encontraron combinaciones válidas con los datos ingresados.",
                result = null,
            )
            return
        }

        val healthy = combinations.filter { it.type == MpptSizingType.SANO }
        val recommendedPool = healthy.ifEmpty { combinations }
        val recommended = recommendedPool.minBy { abs(it.ratio - 1.20) }
        val sorted = combinations.sortedWith(
            compareBy<MpptCombination> { abs(it.ratio - 1.20) }
                .thenBy { it.seriesPanels }
                .thenBy { it.strings },
        )

        _uiState.value = state.copy(
            errorMessage = null,
            result = MpptCalculationResult(
                recommended = recommended,
                combinations = sorted,
                hasHealthyOption = healthy.isNotEmpty(),
            ),
        )
    }

    private fun update(transform: MpptCalculatorUiState.() -> MpptCalculatorUiState) {
        _uiState.value = _uiState.value.transform().copy(
            errorMessage = null,
            result = null,
        )
    }
}
