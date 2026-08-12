package com.gmp.offline.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.gmp.offline.ui.theme.SolarAmber
import com.gmp.offline.ui.theme.SolarAmberDeep
import com.gmp.offline.ui.theme.SolarError
import com.gmp.offline.ui.theme.SolarGreen
import com.gmp.offline.ui.theme.SolarGreenDark
import com.gmp.offline.ui.theme.SolarOnSurfaceVariant
import com.gmp.offline.ui.theme.SolarSky

// Traducción de los valores de `jobs.status` (ver jobsActionsController.js /
// avance_fase_3.md sección 4.1) a etiquetas legibles en español + un color
// de acento para el badge de estado. Centralizado acá para no repetir el
// `when` en cada pantalla que muestre un job.
fun jobStatusLabel(status: String): String = when (status) {
    "pending" -> "Pendiente"
    "assigned" -> "Asignado"
    "in_progress" -> "En curso"
    "finished" -> "Finalizado"
    "invoiced" -> "Facturado"
    "partially_paid" -> "Pago parcial"
    "paid" -> "Pagado"
    "cancelled" -> "Cancelado"
    else -> status
}

@Composable
fun jobStatusColor(status: String): Color = when (status) {
    "pending" -> SolarOnSurfaceVariant
    "assigned" -> SolarSky
    "in_progress" -> SolarAmberDeep
    "finished" -> SolarGreenDark
    "invoiced" -> SolarAmber
    "partially_paid" -> SolarAmber
    "paid" -> SolarGreen
    "cancelled" -> SolarError
    else -> SolarOnSurfaceVariant
}
