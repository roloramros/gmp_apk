package com.gmp.offline.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.gmp.offline.data.local.entities.JobEntity
import com.gmp.offline.data.local.entities.JobMaterialEntity
import com.gmp.offline.data.local.entities.MaterialEntity
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.RoundingMode

object BillingPdfGenerator {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 28f
    private const val CUSTOM_UNIT_SEPARATOR = "|||unit:"

    private const val TABLE_X_1 = MARGIN
    private const val TABLE_X_2 = 242f
    private const val TABLE_X_3 = 348f
    private const val TABLE_X_4 = 472f
    private const val TABLE_RIGHT = PAGE_WIDTH - MARGIN
    private const val TABLE_HEADER_HEIGHT = 24f
    private const val TABLE_ROW_HEIGHT = 23f

    fun saveToDownloads(
        context: Context,
        job: JobEntity,
        materials: List<JobMaterialEntity>,
        catalog: List<MaterialEntity>,
        totalAmount: String,
    ): Result<String> = runCatching {
        val document = buildDocument(job, materials, catalog, totalAmount)
        val safeName = (job.clientName ?: job.title.ifBlank { "montaje" })
            .replace(Regex("[^A-Za-z0-9áéíóúÁÉÍÓÚñÑ _-]"), "")
            .trim()
            .replace(Regex("\\s+"), "_")
            .ifBlank { "montaje" }
        val fileName = "Factura_${safeName}_${job.uuid.take(8)}.pdf"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GM Pro")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("No se pudo crear el archivo en Descargas.")
                resolver.openOutputStream(uri)?.use { document.writeTo(it) }
                    ?: error("No se pudo abrir el archivo PDF.")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Descargas/GM Pro/$fileName"
            } else {
                val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "GM Pro")
                if (!dir.exists() && !dir.mkdirs()) error("No se pudo crear la carpeta de descargas.")
                val file = File(dir, fileName)
                FileOutputStream(file).use { document.writeTo(it) }
                file.absolutePath
            }
        } finally {
            document.close()
        }
    }

    private fun buildDocument(
        job: JobEntity,
        materials: List<JobMaterialEntity>,
        catalog: List<MaterialEntity>,
        totalAmount: String,
    ): PdfDocument {
        val document = PdfDocument()
        val catalogByUuid = catalog.associateBy { it.uuid }

        val companyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(38, 38, 38)
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(90, 90, 90)
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 35, 35)
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(55, 55, 55)
            textSize = 10.5f
        }
        val tableHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val tableTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(75, 75, 75)
            textSize = 9.2f
        }
        val totalLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 35, 35)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val totalValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 35, 35)
            textSize = 14f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(125, 125, 125)
            textSize = 9f
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(215, 215, 215)
            strokeWidth = 0.8f
        }
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(205, 205, 205)
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
        }
        val headerBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 35, 35)
            style = Paint.Style.FILL
        }

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        fun finishAndStartPage() {
            document.finishPage(page)
            pageNumber += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN
        }

        fun drawHeader() {
            canvas.drawText("Grupo Ricali", MARGIN, y + 18f, companyPaint)
            y += 34f
            val serviceTitle = job.title.trim().takeIf { it.isNotBlank() } ?: "Montaje"
            canvas.drawText("Factura de servicio · $serviceTitle", MARGIN, y, subtitlePaint)
            y += 18f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, dividerPaint)
            y += 24f
        }

        fun drawClientLine(label: String, value: String?) {
            val clean = value?.trim().orEmpty().ifBlank { "—" }
            canvas.drawText("$label:", MARGIN, y, labelPaint)
            canvas.drawText(clean, 152f, y, valuePaint)
            y += 18f
        }

        fun drawTableHeader() {
            canvas.drawRect(TABLE_X_1, y, TABLE_RIGHT, y + TABLE_HEADER_HEIGHT, headerBackgroundPaint)
            val textY = y + 16f
            canvas.drawText("Material / Descripción", TABLE_X_1 + 6f, textY, tableHeaderPaint)
            canvas.drawText("Cantidad", TABLE_X_2 + 6f, textY, tableHeaderPaint)
            canvas.drawText("Precio unit.", TABLE_X_3 + 6f, textY, tableHeaderPaint)
            canvas.drawText("Subtotal", TABLE_X_4 + 6f, textY, tableHeaderPaint)
            y += TABLE_HEADER_HEIGHT
        }

        fun drawTableRow(name: String, quantity: BigDecimal, unitPrice: BigDecimal, subtotal: BigDecimal) {
            if (y + TABLE_ROW_HEIGHT > PAGE_HEIGHT - 70f) {
                finishAndStartPage()
                drawHeader()
                drawTableHeader()
            }

            canvas.drawRect(TABLE_X_1, y, TABLE_RIGHT, y + TABLE_ROW_HEIGHT, gridPaint)
            canvas.drawLine(TABLE_X_2, y, TABLE_X_2, y + TABLE_ROW_HEIGHT, gridPaint)
            canvas.drawLine(TABLE_X_3, y, TABLE_X_3, y + TABLE_ROW_HEIGHT, gridPaint)
            canvas.drawLine(TABLE_X_4, y, TABLE_X_4, y + TABLE_ROW_HEIGHT, gridPaint)

            val textY = y + 15f
            canvas.drawText(ellipsize(name, tableTextPaint, TABLE_X_2 - TABLE_X_1 - 12f), TABLE_X_1 + 6f, textY, tableTextPaint)
            canvas.drawText(formatQuantity(quantity), TABLE_X_2 + 6f, textY, tableTextPaint)
            canvas.drawText("$" + formatMoney(unitPrice), TABLE_X_3 + 6f, textY, tableTextPaint)
            canvas.drawText("$" + formatMoney(subtotal), TABLE_X_4 + 6f, textY, tableTextPaint)
            y += TABLE_ROW_HEIGHT
        }

        drawHeader()
        drawClientLine("Cliente", job.clientName ?: job.title)
        drawClientLine("CI", job.clientCi)
        drawClientLine("Teléfono", job.clientPhone)
        drawClientLine("Dirección", job.address)
        drawClientLine("Fecha de montaje", job.scheduledAt?.take(10))
        y += 14f

        drawTableHeader()

        materials.sortedBy { item ->
            item.materialUuid?.let { catalogByUuid[it]?.name }
                ?: parseCustom(item.freeTextDescription.orEmpty()).first
        }.forEach { item ->
            val catalogMaterial = item.materialUuid?.let { catalogByUuid[it] }
            val custom = parseCustom(item.freeTextDescription.orEmpty())
            val name = catalogMaterial?.name ?: custom.first.ifBlank { "Material" }
            val quantity = item.quantity.toBigDecimalOrNull() ?: BigDecimal.ZERO
            val unitPrice = item.unitPrice?.toBigDecimalOrNull() ?: BigDecimal.ZERO
            val subtotal = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP)
            drawTableRow(name, quantity, unitPrice, subtotal)
        }

        if (y + 72f > PAGE_HEIGHT - MARGIN) {
            finishAndStartPage()
            drawHeader()
        }

        y += 24f
        val total = totalAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        canvas.drawText("Total a Pagar:", MARGIN, y, totalLabelPaint)
        canvas.drawText("$" + formatMoney(total), PAGE_WIDTH - 105f, y, totalValuePaint)

        canvas.drawText("Gracias por confiar en Grupo Ricali.", MARGIN, PAGE_HEIGHT - 34f, footerPaint)

        document.finishPage(page)
        return document
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val suffix = "…"
        var candidate = text
        while (candidate.isNotEmpty() && paint.measureText(candidate + suffix) > maxWidth) {
            candidate = candidate.dropLast(1)
        }
        return candidate + suffix
    }

    private fun parseCustom(value: String): Pair<String, String> {
        val parts = value.split(CUSTOM_UNIT_SEPARATOR, limit = 2)
        return if (parts.size == 2) parts[0] to parts[1] else value to ""
    }

    private fun formatMoney(value: BigDecimal): String =
        value.setScale(2, RoundingMode.HALF_UP).toPlainString()

    private fun formatQuantity(value: BigDecimal): String =
        value.setScale(2, RoundingMode.HALF_UP).toPlainString()
}
