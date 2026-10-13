package ru.daystream.calc.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import java.io.ByteArrayOutputStream
import kotlin.math.max

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
}
