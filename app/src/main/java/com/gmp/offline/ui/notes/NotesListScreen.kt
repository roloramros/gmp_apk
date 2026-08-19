package com.gmp.offline.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gmp.offline.ui.theme.SolarAmber
import com.gmp.offline.ui.theme.SolarGreenDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis apuntes") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Volver") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate, containerColor = SolarAmber, contentColor = SolarGreenDark) {
                Icon(Icons.Filled.Add, "Nuevo apunte")
            }
        },
    ) { padding ->
        if (notes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Aún no tienes apuntes. Toca + para crear el primero.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(notes, key = { it.uuid }) { note ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(note.uuid) },
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                note.title.ifBlank { if (note.type == "checklist") "Lista de tareas" else "Nota" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (note.type == "text" && note.text.isNotBlank()) {
                                Text(note.text, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else if (note.type == "checklist") {
                                Text("Lista de tareas", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
