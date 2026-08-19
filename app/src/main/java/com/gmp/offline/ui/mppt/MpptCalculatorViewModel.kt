package com.gmp.offline.ui.mppt

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ceil
import kotlin.math.floor
import javax.inject.Inject

enum class MpptSizingType {
    SUBDIMENSIONADO,
    SANO,
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
    val vMinMppt: String = "",
    val vMaxMppt: String = "",
    val iMaxMppt: String = "",
    val pNomMppt: String = "",
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
            vMinMppt,
            vMaxMppt,
            iMaxMppt,
            pNomMppt,
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
    fun updateVMinMppt(value: String) = update { copy(vMinMppt = value) }
    fun updateVMaxMppt(value: String) = update { copy(vMaxMppt = value) }
    fun updateIMaxMppt(value: String) = update { copy(iMaxMppt = value) }
    fun updatePNomMppt(value: String) = update { copy(pNomMppt = value) }

    fun calculate() {
        val state = _uiState.value
        if (!state.allFieldsCompleted) {
            showError("Completá todos los campos antes de calcular.")
            return
        }

        val voc = state.voc.toDoubleOrNull()
        val isc = state.isc.toDoubleOrNull()
        val vmp = state.vmp.toDoubleOrNull()
        val imp = state.imp.toDoubleOrNull()
        val pmax = state.pmax.toDoubleOrNull()
        val vMinMppt = state.vMinMppt.toDoubleOrNull()
        val vMaxMppt = state.vMaxMppt.toDoubleOrNull()
        val iMaxMppt = state.iMaxMppt.toDoubleOrNull()
        val pNomMppt = state.pNomMppt.toDoubleOrNull()

        if (listOf(
                voc,
                isc,
                vmp,
                imp,
                pmax,
                vMinMppt,
                vMaxMppt,
                iMaxMppt,
                pNomMppt,
            ).any { it == null }
        ) {
            showError("Revisá los valores ingresados: todos deben ser números válidos.")
            return
        }

        val vocValue = voc!!
        val iscValue = isc!!
        val vmpValue = vmp!!
        val impValue = imp!!
        val pmaxValue = pmax!!
        val vMinMpptValue = vMinMppt!!
        val vMaxMpptValue = vMaxMppt!!
        val iMaxMpptValue = iMaxMppt!!
        val pNomMpptValue = pNomMppt!!

        if (vocValue <= 0.0 || iscValue <= 0.0 || vmpValue <= 0.0 || impValue <= 0.0 ||
            pmaxValue <= 0.0 || vMinMpptValue <= 0.0 || vMaxMpptValue <= 0.0 ||
            iMaxMpptValue <= 0.0 || pNomMpptValue <= 0.0
        ) {
            showError("Los valores eléctricos y de potencia deben ser mayores que cero.")
            return
        }

        val vocCorregido = vocValue * (1 + (DEFAULT_BETA_VOC_PERCENT / 100) * (DEFAULT_MIN_TEMP_C - 25))
        val vmpCorregido = vmpValue * (1 + (DEFAULT_BETA_VOC_PERCENT / 100) * (DEFAULT_MAX_TEMP_C - 25))

        if (vocCorregido <= 0.0 || vmpCorregido <= 0.0) {
            showError("Las condiciones térmicas de diseño producen un voltaje corregido no válido.")
            return
        }

        val nMax = floor(vMaxMpptValue / vocCorregido).toInt()
        val nMin = ceil(vMinMpptValue / vmpCorregido).toInt()
        val stringsMax = floor(iMaxMpptValue / iscValue).toInt()

        if (nMax < nMin) {
            showError(
                "El rango de voltaje del MPPT no permite ninguna cantidad de paneles en serie válida con estos datos (el mínimo requerido supera al máximo permitido).",
            )
            return
        }

        if (stringsMax < 1) {
            showError(
                "La corriente Isc del panel supera la corriente máxima admitida por el MPPT: ni un solo string es viable.",
            )
            return
        }

        val combinations = buildList {
            for (n in nMin..nMax) {
                for (s in 1..stringsMax) {
                    val power = n * s * pmaxValue
                    if (power > pNomMpptValue) continue

                    val ratio = power / pNomMpptValue
                    val type = if (ratio < 0.90) {
                        MpptSizingType.SUBDIMENSIONADO
                    } else {
                        MpptSizingType.SANO
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
            showError("Ninguna combinación de paneles en serie/paralelo cabe dentro de la potencia máxima del MPPT con estos datos.")
            return
        }

        val recommended = combinations.maxBy { it.ratio }
        val sorted = combinations.sortedWith(
            compareByDescending<MpptCombination> { it.ratio }
                .thenBy { it.seriesPanels }
                .thenBy { it.strings },
        )

        _uiState.value = state.copy(
            errorMessage = null,
            result = MpptCalculationResult(
                recommended = recommended,
                combinations = sorted,
                hasHealthyOption = true,
            ),
        )
    }

    private fun update(transform: MpptCalculatorUiState.() -> MpptCalculatorUiState) {
        _uiState.value = _uiState.value.transform().copy(
            errorMessage = null,
            result = null,
        )
    }

    private fun showError(message: String) {
        _uiState.value = _uiState.value.copy(
            errorMessage = message,
            result = null,
        )
    }

    private companion object {
        const val DEFAULT_MIN_TEMP_C = 15.0
        const val DEFAULT_MAX_TEMP_C = 40.0
        const val DEFAULT_BETA_VOC_PERCENT = -0.26
    }
}
