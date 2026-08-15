package com.gmp.offline.ui.comercial

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gmp.offline.data.local.entities.JobMaterialEntity
import com.gmp.offline.ui.theme.SolarError
import com.gmp.offline.ui.theme.SolarGreen
import com.gmp.offline.util.BillingPdfGenerator
import java.math.BigDecimal
import java.math.RoundingMode

private const val ADMIN_CUSTOM_UNIT_SEPARATOR = "|||unit:"
private const val ADMIN_OTHER_MATERIAL = "__other__"
private val ADMIN_MATERIAL_BLOCKED_STATUSES = setOf("cancelled", "invoiced", "partially_paid", "paid")

@Composable
internal fun AdminJobMaterialsManager(
    viewModel: JobDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val job by viewModel.job.collectAsStateWithLifecycle()
    val jobMaterials by viewModel.jobMaterials.collectAsStateWithLifecycle()
    val catalog by viewModel.materialCatalog.collectAsStateWithLifecycle()

    var expanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showInvoiceConfirm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<JobMaterialEntity?>(null) }
    var deleting by remember { mutableStateOf<JobMaterialEntity?>(null) }

    val catalogByUuid = remember(catalog) { catalog.associateBy { it.uuid } }
    val canManage = job?.status?.let { it !in ADMIN_MATERIAL_BLOCKED_STATUSES } == true
    val rows = remember(jobMaterials, catalog) {
        jobMaterials.sortedBy { item ->
            val name = item.materialUuid?.let { catalogByUuid[it]?.name }
                ?: parseAdminCustomDescription(item.freeTextDescription.orEmpty()).first
            name.lowercase()
        }
    }
    val missingPriceRows = remember(rows) { rows.filter { it.unitPrice?.toBigDecimalOrNull() == null } }
    val invoiceTotal = remember(rows) {
        rows.fold(BigDecimal.ZERO) { total, item ->
            val quantity = item.quantity.toBigDecimalOrNull() ?: BigDecimal.ZERO
            val unitPrice = item.unitPrice?.toBigDecimalOrNull() ?: BigDecimal.ZERO
            total + quantity.multiply(unitPrice)
        }.setScale(2, RoundingMode.HALF_UP)
    }
    val currentJob = job
    val hasInvoice = currentJob?.invoicedAt != null && !currentJob.totalAmount.isNullOrBlank()
    val canInvoice = currentJob?.status == "finished" && rows.isNotEmpty() && missingPriceRows.isEmpty() && invoiceTotal > BigDecimal.ZERO

    fun savePdf(total: String) {
        val sourceJob = currentJob ?: return
        BillingPdfGenerator.saveToDownloads(
            context = context,
            job = sourceJob,
            materials = rows,
            catalog = catalog,
            totalAmount = total,
        ).onSuccess { path ->
            Toast.makeText(context, "PDF guardado en $path", Toast.LENGTH_LONG).show()
        }.onFailure { error ->
            Toast.makeText(context, error.message ?: "No se pudo guardar el PDF.", Toast.LENGTH_LONG).show()
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Materiales utilizados",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (hasInvoice) {
                        IconButton(onClick = { savePdf(currentJob?.totalAmount.orEmpty()) }) {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = "Descargar factura PDF",
                                tint = SolarGreen,
                            )
                        }
                    }
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        if (expanded) "Mostrar menos" else "Mostrar más",
                        style = MaterialTheme.typography.labelSmall,
                        color = SolarGreen,
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { showAddDialog = true },
                    enabled = canManage,
                    colors = ButtonDefaults.buttonColors(containerColor = SolarGreen),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(" Agregar material")
                }

                if (!canManage) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Los materiales no se pueden modificar en el estado actual del trabajo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (rows.isEmpty()) {
                    Text(
                        "Todavía no se han agregado materiales.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    rows.forEachIndexed { index, item ->
                        val catalogMaterial = item.materialUuid?.let { catalogByUuid[it] }
                        val custom = parseAdminCustomDescription(item.freeTextDescription.orEmpty())
                        val name = catalogMaterial?.name ?: custom.first.ifBlank { "Material" }
                        val unit = catalogMaterial?.unit.orEmpty().ifBlank { custom.second }
                        val unitPrice = item.unitPrice?.toBigDecimalOrNull()
                        val quantity = item.quantity.toBigDecimalOrNull() ?: BigDecimal.ZERO
                        val subtotal = unitPrice?.let { quantity.multiply(it).setScale(2, RoundingMode.HALF_UP) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    buildString {
                                        append("${item.quantity} ${unit.ifBlank { "unidad" }}")
                                        if (unitPrice != null) {
                                            append(" · $${unitPrice.setScale(2, RoundingMode.HALF_UP).toPlainString()} c/u")
                                            append(" · $${subtotal?.toPlainString()}")
                                        } else {
                                            append(" · Sin precio")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { editing = item },
                                enabled = canManage,
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = "Modificar cantidad de $name", tint = SolarGreen)
                            }
                            IconButton(
                                onClick = { deleting = item },
                                enabled = canManage,
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Eliminar $name", tint = SolarError)
                            }
                        }
                        if (index != rows.lastIndex) HorizontalDivider()
                    }
                }

                if (!hasInvoice) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = { showInvoiceConfirm = true },
                            enabled = canInvoice,
                            colors = ButtonDefaults.buttonColors(containerColor = SolarGreen),
                        ) {
                            Icon(Icons.Filled.ReceiptLong, contentDescription = null)
                            Text(" Facturar")
                        }
                    }
                    if (currentJob?.status != "finished") {
                        Text(
                            "La facturación se habilita cuando el montaje está finalizado.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (missingPriceRows.isNotEmpty()) {
                        Text(
                            "Hay materiales sin precio. Corrige esas líneas antes de facturar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SolarError,
                        )
                    }
                }
            }
        }
    }

    if (showInvoiceConfirm && currentJob != null) {
        AlertDialog(
            onDismissRequest = { showInvoiceConfirm = false },
            title = { Text("Generar factura") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("¿Confirmas la facturación de este montaje?")
                    Text(
                        "Precio total: $${invoiceTotal.toPlainString()}",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Se guardará el total como precio oficial y se generará el PDF con los datos del montaje y el detalle de materiales.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val total = invoiceTotal.toPlainString()
                    viewModel.invoiceJob(total)
                    savePdf(total)
                    showInvoiceConfirm = false
                }) {
                    Text("Sí, facturar", color = SolarGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showInvoiceConfirm = false }) { Text("Cancelar") }
            },
        )
    }

    if (showAddDialog) {
        AdminAddMaterialDialog(
            catalog = catalog,
            onDismiss = { showAddDialog = false },
            onAddCatalog = { materialUuid, quantity ->
                viewModel.addAdminMaterial(materialUuid, quantity)
                showAddDialog = false
            },
            onAddCustom = { name, unit, quantity, unitPrice ->
                viewModel.addAdminCustomMaterial(name, unit, quantity, unitPrice)
                showAddDialog = false
            },
        )
    }

    editing?.let { item ->
        val catalogMaterial = item.materialUuid?.let { catalogByUuid[it] }
        val custom = parseAdminCustomDescription(item.freeTextDescription.orEmpty())
        val name = catalogMaterial?.name ?: custom.first.ifBlank { "Material" }
        var quantity by remember(item.uuid, item.quantity) { mutableStateOf(item.quantity) }
        val valid = (quantity.toDoubleOrNull() ?: 0.0) > 0.0

        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Modificar cantidad") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(name, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Cantidad") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = valid,
                    onClick = {
                        viewModel.updateAdminMaterialQuantity(item.uuid, quantity)
                        editing = null
                    },
                ) {
                    Text("Guardar", color = SolarGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("Cancelar") }
            },
        )
    }

    deleting?.let { item ->
        val catalogMaterial = item.materialUuid?.let { catalogByUuid[it] }
        val custom = parseAdminCustomDescription(item.freeTextDescription.orEmpty())
        val name = catalogMaterial?.name ?: custom.first.ifBlank { "Material" }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Eliminar material") },
            text = { Text("¿Eliminar completamente \"$name\" de este trabajo?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeAdminMaterial(item.uuid)
                    deleting = null
                }) {
                    Text("Sí, eliminar", color = SolarError)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun AdminAddMaterialDialog(
    catalog: List<com.gmp.offline.data.local.entities.MaterialEntity>,
    onDismiss: () -> Unit,
    onAddCatalog: (materialUuid: String, quantity: String) -> Unit,
    onAddCustom: (name: String, unit: String, quantity: String, unitPrice: String) -> Unit,
) {
    var selectedMaterialUuid by remember { mutableStateOf<String?>(null) }
    var quantity by remember { mutableStateOf("1") }
    var customName by remember { mutableStateOf("") }
    var customUnit by remember { mutableStateOf("") }
    var customUnitPrice by remember { mutableStateOf("") }

    val isOther = selectedMaterialUuid == ADMIN_OTHER_MATERIAL
    val validQuantity = (quantity.toDoubleOrNull() ?: 0.0) > 0.0
    val validCustomPrice = customUnitPrice.toBigDecimalOrNull()?.let { it >= BigDecimal.ZERO } == true
    val canAdd = if (isOther) {
        customName.isNotBlank() && customUnit.isNotBlank() && validQuantity && validCustomPrice
    } else {
        selectedMaterialUuid != null && validQuantity
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar material") },
        text = {
            Column {
                Text(
                    "Selecciona del catálogo",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    catalog.forEach { material ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedMaterialUuid = material.uuid }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedMaterialUuid == material.uuid,
                                onClick = { selectedMaterialUuid = material.uuid },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(material.name)
                                Text(
                                    buildString {
                                        if (!material.unit.isNullOrBlank()) append(material.unit)
                                        if (!material.defaultPrice.isNullOrBlank()) {
                                            if (isNotEmpty()) append(" · ")
                                            append("$${material.defaultPrice}")
                                        }
                                    }.ifBlank { "Sin unidad/precio configurado" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMaterialUuid = ADMIN_OTHER_MATERIAL }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isOther,
                            onClick = { selectedMaterialUuid = ADMIN_OTHER_MATERIAL },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Otros", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Material que no está en el catálogo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                if (isOther) {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customUnit,
                        onValueChange = { customUnit = it },
                        label = { Text("Unidad de medida") },
                        placeholder = { Text("Ej.: unidad, metro, viaje") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customUnitPrice,
                        onValueChange = { customUnitPrice = it },
                        label = { Text("Precio unitario") },
                        prefix = { Text("$") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Cantidad") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canAdd,
                onClick = {
                    if (isOther) {
                        onAddCustom(customName, customUnit, quantity, customUnitPrice)
                    } else {
                        selectedMaterialUuid?.let { onAddCatalog(it, quantity) }
                    }
                },
            ) {
                Text("Agregar", color = SolarGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

private fun parseAdminCustomDescription(value: String): Pair<String, String> {
    val parts = value.split(ADMIN_CUSTOM_UNIT_SEPARATOR, limit = 2)
    return if (parts.size == 2) parts[0] to parts[1] else value to ""
}