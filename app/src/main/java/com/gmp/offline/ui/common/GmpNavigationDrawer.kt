package com.gmp.offline.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gmp.offline.ui.theme.SolarAmber
import com.gmp.offline.ui.theme.SolarError
import com.gmp.offline.ui.theme.SolarGreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GmpNavigationDrawer(
    fullName: String?,
    companyName: String?,
    onSync: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenMpptCalculator: () -> Unit,
    onOpenConsumptionCalculator: () -> Unit,
    onLogout: () -> Unit,
    // admin y comercial la usan (catálogo/tienda/galería viven en el drawer
    // para los dos); trabajador no gestiona nada de esto — por eso son
    // opcionales y no se agrega el ítem al drawer cuando viene null.
    onOpenCatalog: (() -> Unit)? = null,
    onOpenStore: (() -> Unit)? = null,
    onOpenGallery: (() -> Unit)? = null,
    content: @Composable (onOpenDrawer: () -> Unit) -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Estado de sync leído directo del singleton (ver SyncStatusViewModel) —
    // así el drawer no depende de que cada pantalla (Admin/Comercial/
    // Trabajador) se lo pase a mano.
    val syncStatusViewModel: SyncStatusViewModel = hiltViewModel()
    val syncStatus by syncStatusViewModel.status.collectAsStateWithLifecycle()
    val pendingCount by syncStatusViewModel.pendingCount.collectAsStateWithLifecycle()

    fun closeDrawer() { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.fillMaxHeight().fillMaxWidth().padding(vertical = 20.dp)) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text("GM PRO", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(companyName ?: "Empresa", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Divider(modifier = Modifier.padding(vertical = 16.dp))
                    NavigationDrawerItem(
                        label = {
                            Column {
                                Text("Sincronizar")
                                Text(
                                    syncStatusSubtitle(syncStatus),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (syncStatus.lastErrorMessage != null) SolarError else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        selected = false,
                        // A propósito NO se cierra el drawer acá (a diferencia
                        // del resto de los ítems, que navegan a otra pantalla):
                        // así se ve el spinner y el resultado sin tener que
                        // volver a abrir el menú.
                        onClick = { if (!syncStatus.syncing) onSync() },
                        icon = {
                            if (syncStatus.syncing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = SolarGreen)
                            } else {
                                Icon(Icons.Filled.Refresh, null)
                            }
                        },
                        badge = {
                            if (pendingCount > 0) {
                                Box(
                                    modifier = Modifier.size(20.dp).background(SolarAmber, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        if (pendingCount > 99) "99+" else pendingCount.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF25200A),
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text("Mis apuntes") }, selected = false,
                        onClick = { closeDrawer(); onOpenNotes() },
                        icon = { Icon(Icons.Filled.EditNote, null) }, modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    if (onOpenCatalog != null) {
                        NavigationDrawerItem(
                            label = { Text("Catálogo de kits") }, selected = false,
                            onClick = { closeDrawer(); onOpenCatalog() },
                            icon = { Icon(Icons.Filled.Storefront, null) }, modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    if (onOpenStore != null) {
                        NavigationDrawerItem(
                            label = { Text("Tienda") }, selected = false,
                            onClick = { closeDrawer(); onOpenStore() },
                            icon = { Icon(Icons.Filled.ShoppingCart, null) }, modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    if (onOpenGallery != null) {
                        NavigationDrawerItem(
                            label = { Text("Galería") }, selected = false,
                            onClick = { closeDrawer(); onOpenGallery() },
                            icon = { Icon(Icons.Filled.PhotoLibrary, null) }, modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    NavigationDrawerItem(
                        label = { Text("Calculadora Dimensionado MPPT") }, selected = false,
                        onClick = { closeDrawer(); onOpenMpptCalculator() }, modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text("Calculadora de Consumo") }, selected = false,
                        onClick = { closeDrawer(); onOpenConsumptionCalculator() }, modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Divider(modifier = Modifier.padding(bottom = 12.dp))
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text(fullName ?: "Usuario", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { closeDrawer(); onLogout() }) { Text("Cerrar sesión") }
                    }
                }
            }
        },
    ) { content { scope.launch { drawerState.open() } } }
}

private fun syncStatusSubtitle(status: com.gmp.offline.sync.SyncUiStatus): String {
    if (status.syncing) return "Sincronizando…"
    if (status.lastErrorMessage != null) {
        val lastOk = status.lastSuccessAtMillis?.let { "última vez OK: " + formatSyncTimestamp(it) }
        return "No se pudo sincronizar" + if (lastOk != null) " ($lastOk)" else ""
    }
    val lastSuccess = status.lastSuccessAtMillis
    return if (lastSuccess != null) "Última vez: ${formatSyncTimestamp(lastSuccess)}" else "Todavía no se sincronizó"
}

private fun formatSyncTimestamp(millis: Long): String {
    val format = SimpleDateFormat("dd/MM HH:mm", Locale("es", "CU"))
    return format.format(Date(millis))
}
