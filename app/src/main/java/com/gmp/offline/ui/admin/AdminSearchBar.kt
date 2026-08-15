package com.gmp.offline.ui.admin

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gmp.offline.ui.theme.SolarGreen

@Composable
internal fun AdminSearchBar(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            singleLine = true,
            placeholder = { Text(placeholder) },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null, tint = SolarGreen)
            },
            trailingIcon = {
                IconButton(onClick = {
                    if (query.isNotEmpty()) onQueryChange("") else onClose()
                }) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = if (query.isNotEmpty()) "Limpiar búsqueda" else "Cerrar búsqueda",
                    )
                }
            },
        )
    }
}
