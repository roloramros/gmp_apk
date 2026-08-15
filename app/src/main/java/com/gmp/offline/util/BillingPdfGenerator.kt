package com.gmp.offline.util

import android.content.ContentValues
import android.content.Context
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
    private const val MARGIN = 42f
    private const val CUSTOM_UNIT_SEPARATOR = "|||unit:"

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
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f }

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        fun newPage() {
            document.finishPage(page)
            pageNumber += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN
        }

        fun ensureSpace(height: Float) {
            if (y + height > PAGE_HEIGHT - MARGIN) newPage()
        }

        fun line(label: String, value: String?) {
            val clean = value?.trim().orEmpty().ifBlank { "—" }
            ensureSpace(18f)
            canvas.drawText("$label: $clean", MARGIN, y, bodyPaint)
            y += 18f
        }

        canvas.drawText("Factura de montaje", MARGIN, y, titlePaint)
        y += 28f
        line("Nombre", job.clientName ?: job.title)
        line("CI", job.clientCi)
        line("Teléfono", job.clientPhone)
        line("Dirección", job.address)
        line("Fecha oficial de montaje", job.scheduledAt?.take(10))

        y += 10f
        ensureSpace(28f)
        canvas.drawText("Materiales utilizados", MARGIN, y, headingPaint)
        y += 20f

        materials.sortedBy { item ->
            item.materialUuid?.let { catalogByUuid[it]?.name }
                ?: parseCustom(item.freeTextDescription.orEmpty()).first
        }.forEach { item ->
            val catalogMaterial = item.materialUuid?.let { catalogByUuid[it] }
            val custom = parseCustom(item.freeTextDescription.orEmpty())
            val name = catalogMaterial?.name ?: custom.first.ifBlank { "Material" }
            val unit = catalogMaterial?.unit.orEmpty().ifBlank { custom.second }.ifBlank { "unidad" }
            val quantity = item.quantity.toBigDecimalOrNull() ?: BigDecimal.ZERO
            val unitPrice = item.unitPrice?.toBigDecimalOrNull() ?: BigDecimal.ZERO
            val subtotal = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP)

            ensureSpace(38f)
            canvas.drawText(name, MARGIN, y, bodyPaint)
            y += 15f
            val detail = "${formatNumber(quantity)} $unit × " + '$' + formatMoney(unitPrice) + " = " + '$' + formatMoney(subtotal)
            canvas.drawText(detail, MARGIN + 12f, y, smallPaint)
            y += 20f
        }

        y += 8f
        ensureSpace(32f)
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, bodyPaint)
        y += 22f
        val total = totalAmount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        canvas.drawText("PRECIO TOTAL: " + '$' + formatMoney(total), MARGIN, y, headingPaint)

        document.finishPage(page)
        return document
    }

    private fun parseCustom(value: String): Pair<String, String> {
        val parts = value.split(CUSTOM_UNIT_SEPARATOR, limit = 2)
        return if (parts.size == 2) parts[0] to parts[1] else value to ""
    }

    private fun formatMoney(value: BigDecimal): String =
        value.setScale(2, RoundingMode.HALF_UP).toPlainString()

    private fun formatNumber(value: BigDecimal): String =
        value.stripTrailingZeros().toPlainString()
}