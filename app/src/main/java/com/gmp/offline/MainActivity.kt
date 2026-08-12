package com.gmp.offline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gmp.offline.ui.DebugViewModel
import dagger.hilt.android.AndroidEntryPoint

// Pantalla única de esta fase: sirve para comprobar a ojo que login + sync
// incremental + outbox funcionan juntos. La navegación y las pantallas
// reales por rol (worker/comercial/admin) llegan en Fase 6.
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DebugScreen()
                }
            }
        }
    }
}

@Composable
fun DebugScreen(viewModel: DebugViewModel = hiltViewModel()) {
    var companyId by remember { mutableStateOf("1") }
    var phone by remember { mutableStateOf("5551234") }
    var password by remember { mutableStateOf("") }

    val status by viewModel.status.collectAsStateWithLifecycle()
    val jobs by viewModel.jobs.collectAsStateWithLifecycle(initialValue = emptyList())
    val staff by viewModel.staff.collectAsStateWithLifecycle(initialValue = emptyList())
    val pending by viewModel.pendingOperations.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Fase 5 — motor de sync (pull + outbox)", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = companyId,
            onValueChange = { companyId = it },
            label = { Text("company_id") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("phone") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("password") },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            viewModel.loginAndSync(companyId.toIntOrNull() ?: 1, phone, password)
        }) {
            Text("Login + sync inicial")
        }

        Spacer(Modifier.height(8.dp))
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
