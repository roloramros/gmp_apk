package com.gmp.offline.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// Pantalla "home" temporal post-login: todavía muestra lo mismo que
// DebugScreen mostraba en Fase 4/5 (jobs, staff y outbox tal como están en
// Room), solo que ahora el login pasó a LoginScreen y acá se agregó
// logout + datos de la sesión. Se reemplaza por pantallas reales separadas
// por rol (worker/comercial/admin) en el siguiente paso de la Fase 6 — ver
// punto 7.1 de avance-fase4-fase5-cierre.md.
@Composable
fun HomeScreen(
    onLoggedOut: () -> Unit,
    viewModel: DebugViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val jobs by viewModel.jobs.collectAsStateWithLifecycle(initialValue = emptyList())
    val staff by viewModel.staff.collectAsStateWithLifecycle(initialValue = emptyList())
    val pending by viewModel.pendingOperations.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(viewModel.currentFullName ?: "—", style = MaterialTheme.typography.titleMedium)
                Text(viewModel.currentRole ?: "—", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { viewModel.logout(onLoggedOut) }) {
                Text("Cerrar sesión")
            }
        }
        Spacer(Modifier.height(12.dp))

        Row {
            OutlinedButton(onClick = { viewModel.syncNow() }) {
                Text("Sincronizar ahora")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { viewModel.startFirstPendingJobAsTest() }) {
                Text("Probar outbox")
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(status, style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(16.dp))
        Text("Jobs en Room (${jobs.size})", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(jobs) { job ->
                Text("• ${job.title} — ${job.status}")
            }
        }

        Text("Pendientes en outbox (${pending.size})", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(pending) { op ->
                Text("• ${op.httpMethod} ${op.endpointPath} — ${op.status}")
            }
        }

        Text("Staff en Room (${staff.size})", style = MaterialTheme.typography.titleMedium)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(staff) { member ->
                Text("• ${member.fullName} (${member.role})")
            }
        }
    }
}
