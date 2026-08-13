package com.gmp.offline.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Comprime una imagen elegida de la galería antes de subirla, para no
 * consumir datos móviles de más ni tardar en subir. Reduce el lado más
 * largo a [MAX_DIMENSION] px y recomprime a JPEG calidad [JPEG_QUALITY].
 * Una foto de cámara moderna (12+ MP, varios MB) queda típicamente en el
 * orden de 100-300 KB con estos parámetros.
 */
object PhotoCompressor {
    private const val MAX_DIMENSION = 1280
    private const val JPEG_QUALITY = 70

    @Throws(IOException::class)
    fun compress(context: Context, uri: Uri): ByteArray {
        val resolver = context.contentResolver

        // 1) Solo leer las dimensiones primero (inJustDecodeBounds), para
        //    poder calcular un inSampleSize y no cargar la imagen original
        //    completa en memoria si es muy grande.
        //    OJO: BitmapFactory.decodeStream() con inJustDecodeBounds=true
        //    devuelve `null` A PROPÓSITO (solo rellena `bounds`, no decodifica
        //    la imagen) — no se puede usar ese resultado para chequear si el
        //    stream se abrió bien, o el `?:` dispara siempre. Se valida el
        //    stream por separado, antes de usarlo.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri)
            ?: throw IOException("No se pudo abrir la imagen seleccionada")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > MAX_DIMENSION * 2 ||
            bounds.outHeight / sampleSize > MAX_DIMENSION * 2
        ) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val rawBitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: throw IOException("No se pudo decodificar la imagen seleccionada")

        // 2) Escalado final exacto al tamaño máximo permitido.
        val scale = MAX_DIMENSION.toFloat() / maxOf(rawBitmap.width, rawBitmap.height)
        val scaledBitmap = if (scale < 1f) {
            val newWidth = (rawBitmap.width * scale).toInt().coerceAtLeast(1)
            val newHeight = (rawBitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(rawBitmap, newWidth, newHeight, true)
            if (scaled !== rawBitmap) rawBitmap.recycle()
            scaled
        } else {
            rawBitmap
        }

        // 3) Corregir orientación EXIF — sin esto, fotos tomadas en
        //    vertical pueden quedar "acostadas" al perderse el flag de
        //    rotación durante la decodificación con BitmapFactory.
        val finalBitmap = applyExifRotation(resolver, uri, scaledBitmap)

        val output = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        if (finalBitmap !== scaledBitmap) finalBitmap.recycle()
        scaledBitmap.recycle()

        return output.toByteArray()
    }

    private fun applyExifRotation(resolver: ContentResolver, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            resolver.openInputStream(uri)?.use { ExifInterface(it) }
                ?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }

        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
