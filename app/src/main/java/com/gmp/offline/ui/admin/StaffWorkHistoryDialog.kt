package com.gmp.offline.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gmp.offline.data.local.entities.JobEntity
import com.gmp.offline.data.local.entities.JobWorkerEntity
import com.gmp.offline.data.local.entities.StaffEntity
import com.gmp.offline.ui.theme.SolarGreen
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val historyDisplayDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private data class StaffWorkHistoryItem(
    val job: JobEntity,
    val scheduledDate: LocalDate,
    val coworkers: List<String>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffWorkHistoryDialog(
    member: StaffEntity,
    staff: List<StaffEntity>,
    jobs: List<JobEntity>,
    jobWorkers: List<JobWorkerEntity>,
    onDismiss: () -> Unit,
) {
    val dateRangeState = rememberDateRangePickerState()
    var showCalendar by remember { mutableStateOf(false) }
    var requestedRange by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }

    val selectedStart = dateRangeState.selectedStartDateMillis?.toUtcLocalDate()
    val selectedEnd = dateRangeState.selectedEndDateMillis?.toUtcLocalDate()

    val results = remember(member.uuid, jobs, jobWorkers, staff, requestedRange) {
        val range = requestedRange ?: return@remember emptyList()
        val jobIds = jobWorkers
            .asSequence()
            .filter { it.userUuid == member.uuid }
            .map { it.jobUuid }
            .toSet()
        val staffByUuid = staff.associateBy { it.uuid }
        val workersByJob = jobWorkers.groupBy { it.jobUuid }

        jobs.asSequence()
            .filter { it.uuid in jobIds }
            .mapNotNull { job ->
                val scheduledDate = job.scheduledAt.toLocalDateOrNull() ?: return@mapNotNull null
                if (scheduledDate < range.first || scheduledDate > range.second) return@mapNotNull null

                val coworkers = workersByJob[job.uuid].orEmpty()
                    .asSequence()
                    .filter { it.userUuid != member.uuid }
                    .map { relation -> staffByUuid[relation.userUuid]?.fullName ?: "Usuario ${relation.userUuid.take(8)}" }
                    .distinct()
                    .sorted()
                    .toList()

                StaffWorkHistoryItem(job, scheduledDate, coworkers)
            }
            .sortedByDescending { it.scheduledDate }
            .toList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trabajos de ${member.fullName}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Selecciona el rango de fechas de la fecha oficial del trabajo.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                OutlinedButton(
                    onClick = { showCalendar = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            selectedStart != null && selectedEnd != null ->
                                "${selectedStart.format(historyDisplayDateFormatter)} - ${selectedEnd.format(historyDisplayDateFormatter)}"
                            selectedStart != null -> "Desde ${selectedStart.format(historyDisplayDateFormatter)}"
                            else -> "Seleccionar rango de fechas"
                        },
                    )
                }

                Button(
                    onClick = {
                        if (selectedStart != null && selectedEnd != null) {
                            requestedRange = selectedStart to selectedEnd
                        }
                    },
                    enabled = selectedStart != null && selectedEnd != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Mostrar")
                }

                if (requestedRange != null) {
                    if (results.isEmpty()) {
                        Text(
                            "No hay trabajos en el rango seleccionado.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(results, key = { it.job.uuid }) { item ->
                                WorkHistoryRow(item)
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
    )

    if (showCalendar) {
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = {
                TextButton(
                    onClick = { showCalendar = false },
                    enabled = dateRangeState.selectedStartDateMillis != null && dateRangeState.selectedEndDateMillis != null,
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCalendar = false }) {
                    Text("Cancelar")
                }
            },
        ) {
            DateRangePicker(
                state = dateRangeState,
                title = {
                    Text(
                        "Selecciona un rango",
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp),
                    )
                },
                headline = null,
                showModeToggle = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 480.dp),
            )
        }
    }
}

@Composable
private fun WorkHistoryRow(item: StaffWorkHistoryItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            item.job.address?.takeIf { it.isNotBlank() } ?: "Sin dirección",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = SolarGreen,
        )
        Text(
            item.job.description?.takeIf { it.isNotBlank() } ?: "Sin descripción",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Fecha oficial: ",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                item.scheduledDate.format(historyDisplayDateFormatter),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            "Trabajó con: ${if (item.coworkers.isEmpty()) "Nadie más" else item.coworkers.joinToString(", ")}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Long.toUtcLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private fun String?.toLocalDateOrNull(): LocalDate? {
    val raw = this?.trim().orEmpty()
    if (raw.length < 10) return null
    return runCatching { LocalDate.parse(raw.substring(0, 10)) }.getOrNull()
}
