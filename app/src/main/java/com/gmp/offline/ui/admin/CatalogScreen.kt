package com.gmp.offline.ui.admin

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

// Wrapper de la pestaña "Catálogo" para comercial, que no tiene un home con
// tabs como admin (ComercialJobsListScreen es una pantalla única con drawer
// lateral) — se llega acá desde el ítem "Catálogo de kits" del drawer. El
// contenido (lista, formulario, fotos) es el mismo CatalogTabContent que usa
// AdminHomeScreen, así que cualquier cambio ahí aplica para los dos roles.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(onBack: () -> Unit) {
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { if (searchVisible) SearchField(searchQuery, { searchQuery = it }) else Text("Catálogo de kits") },
                navigationIcon = {
                    IconButton(onClick = { if (searchVisible) { searchVisible = false; searchQuery = "" } else onBack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { if (searchVisible) { searchVisible = false; searchQuery = "" } else searchVisible = true }) {
                        Icon(if (searchVisible) Icons.Filled.Close else Icons.Filled.Search, contentDescription = "Buscar")
                    }
                },
            )
        },
    ) { padding ->
        CatalogTabContent(searchQuery = searchQuery, modifier = Modifier.padding(padding))
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    androidx.compose.material3.TextField(
        value = query, onValueChange = onChange,
        placeholder = { Text("Buscar kit por nombre") },
        singleLine = true,
        colors = androidx.compose.material3.TextFieldDefaults.colors(
            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
    )
}
