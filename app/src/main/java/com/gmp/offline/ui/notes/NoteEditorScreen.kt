package com.gmp.offline.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gmp.offline.data.notes.NoteDraftItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteUuid: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    var type by remember { mutableStateOf("text") }
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    val checklist = remember { mutableStateListOf<NoteDraftItem>() }
    var deleteConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(noteUuid) {
        if (noteUuid != null) viewModel.load(noteUuid)?.let { loaded ->
            type = loaded.note.type
            title = loaded.note.title
            text = loaded.note.text
            checklist.clear()
            checklist.addAll(loaded.items.map { NoteDraftItem(it.itemUuid, it.text, it.checked) })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (noteUuid == null) "Nuevo apunte" else "Editar apunte") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Volver") } },
                actions = {
                    if (noteUuid != null) IconButton(onClick = { deleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, "Eliminar apunte")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (noteUuid == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { type = "text" }, enabled = type != "text") { Text("Texto") }
                    Button(onClick = {
                        type = "checklist"
                        if (checklist.isEmpty()) checklist.add(NoteDraftItem())
                    }, enabled = type != "checklist") { Text("Lista de tareas") }
                }
            }
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Título (opcional)") }, modifier = Modifier.fillMaxWidth())
            if (type == "text") {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Escribe tu nota") },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(checklist, key = { _, item -> item.uuid }) { index, item ->
                        Row(Modifier.fillMaxWidth()) {
                            Checkbox(checked = item.checked, onCheckedChange = { checklist[index] = item.copy(checked = it) })
                            OutlinedTextField(
                                value = item.text,
                                onValueChange = { checklist[index] = item.copy(text = it) },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Tarea") },
                            )
                            IconButton(onClick = { checklist.removeAt(index) }) { Icon(Icons.Filled.Delete, "Quitar tarea") }
                        }
                    }
                }
                OutlinedButton(onClick = { checklist.add(NoteDraftItem()) }) {
                    Icon(Icons.Filled.Add, null)
                    Text("Añadir tarea")
                }
            }
            Button(
                onClick = {
                    scope.launch {
                        viewModel.save(noteUuid, type, title, text, checklist.toList())
                        onSaved()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar") }
        }
    }

    if (deleteConfirm && noteUuid != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("Eliminar apunte") },
            text = { Text("Este apunte se eliminará definitivamente de este dispositivo.") },
            confirmButton = { TextButton(onClick = { scope.launch { viewModel.delete(noteUuid); onSaved() } }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("Cancelar") } },
        )
    }
}
