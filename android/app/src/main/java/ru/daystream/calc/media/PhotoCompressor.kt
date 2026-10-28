package ru.daystream.calc.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Сжатие фото на клиенте: JPEG 1280, q=80, без EXIF.
 */
object PhotoCompressor {
    /**
     * Сжать байты картинки.
     * @param data исходник ≤ 10 МБ
     */
    fun compress(data: ByteArray): ByteArray {
        if (data.size > 10 * 1024 * 1024) error("Фото больше 10 МБ")
        val src = BitmapFactory.decodeByteArray(data, 0, data.size) ?: error("Не изображение")
        val scale = 1280f / max(src.width, src.height).coerceAtLeast(1)
        val w = (src.width * scale.coerceAtMost(1f)).toInt().coerceAtLeast(1)
        val h = (src.height * scale.coerceAtMost(1f)).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        val rgb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(rgb)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(scaled, 0f, 0f, null)
        fun jpeg(q: Int): ByteArray {
            val out = ByteArrayOutputStream()
            rgb.compress(Bitmap.CompressFormat.JPEG, q, out)
            return out.toByteArray()
        }
        var result = jpeg(80)
        if (result.size > 10 * 1024 * 1024) result = jpeg(60)
        if (result.size > 10 * 1024 * 1024) error("Фото слишком большое")
        return result
    }

    /**
     * Открыть картинку из галереи для обрезки: EXIF и даунскейл, чтобы не съесть память.
     */
    fun decodeForCrop(context: Context, uri: Uri): Bitmap? {
        val data = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (data.isEmpty() || data.size > 25 * 1024 * 1024) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val longest = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        var sample = 1
        while (longest / sample > 2048) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = BitmapFactory.decodeByteArray(data, 0, data.size, opts) ?: return null
        val orient = try {
            ExifInterface(ByteArrayInputStream(data)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        return applyExif(raw, orient)
    }

    /**
     * Квадрат аватара в JPEG.
     */
    fun compressSquare(src: Bitmap, maxSide: Int = 640): ByteArray {
        val side = min(src.width, src.height).coerceAtLeast(1)
        val square = if (src.width == src.height) src else Bitmap.createBitmap(src, 0, 0, side, side)
        val outSide = side.coerceAtMost(maxSide)
        val sized = if (square.width == outSide) square else Bitmap.createScaledBitmap(square, outSide, outSide, true)
        val rgb = Bitmap.createBitmap(outSide, outSide, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(rgb)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(sized, 0f, 0f, null)
        val out = ByteArrayOutputStream()
        rgb.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return out.toByteArray()
    }

    /**
     * Вырезать квадрат видимой зоны после pan/zoom.
     */
    fun cropVisibleSquare(src: Bitmap, zoom: Float, offsetX: Float, offsetY: Float, viewPx: Float): Bitmap {
        val imgW = src.width.toFloat()
        val imgH = src.height.toFloat()
        val minScale = viewPx / min(imgW, imgH).coerceAtLeast(1f)
        val scale = minScale * zoom.coerceAtLeast(1f)
        val drawW = imgW * scale
        val drawH = imgH * scale
        val left = (viewPx - drawW) / 2f + offsetX
        val top = (viewPx - drawH) / 2f + offsetY
        val srcX = ((-left) / scale).toInt().coerceIn(0, src.width - 1)
        val srcY = ((-top) / scale).toInt().coerceIn(0, src.height - 1)
        val srcSize = (viewPx / scale).toInt().coerceAtLeast(1)
            .coerceAtMost(min(src.width - srcX, src.height - srcY))
        return Bitmap.createBitmap(src, srcX, srcY, srcSize, srcSize)
    }

    private fun applyExif(src: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return src
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }
}
