package ru.daystream.calc.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import ru.daystream.calc.ChatApp
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Аватары на диске: показ сразу, сеть только если URL сменился.
 */
object AvatarCache {
    private val mem = ConcurrentHashMap<Long, Pair<String, Bitmap>>()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Уже сохранённое фото, без сети. Для первого кадра настроек и списка.
     */
    fun peek(userId: Long): Bitmap? {
        mem[userId]?.second?.let { return it }
        synchronized(this) {
            mem[userId]?.second?.let { return it }
            val file = fileOf(userId)
            if (!file.exists()) return null
            val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            val url = readIndex().optString(userId.toString())
            mem[userId] = url to bmp
            return bmp
        }
    }

    /**
     * Показать кэш, скачать только при новом URL. Пустой URL — убрать фото.
     */
    suspend fun ensure(userId: Long, url: String?): Bitmap? {
        val want = url?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        if (want == null) {
            remove(userId)
            return null
        }
        mem[userId]?.let { if (it.first == want) return it.second }
        synchronized(this) {
            mem[userId]?.let { if (it.first == want) return it.second }
            val stored = readIndex().optString(userId.toString())
            val file = fileOf(userId)
            if (stored == want && file.exists()) {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) {
                    mem[userId] = want to bmp
                    return bmp
                }
            }
        }
        val bytes = download(want) ?: return peek(userId)
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return peek(userId)
        putBytes(userId, want, bytes, bmp)
        return bmp
    }

    /**
     * Своё только что загруженное фото — без повторной загрузки.
     */
    fun putLocal(userId: Long, url: String, jpeg: ByteArray) {
        val want = url.trim().takeIf { it.isNotBlank() } ?: return
        val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return
        putBytes(userId, want, jpeg, bmp)
    }

    private fun putBytes(userId: Long, url: String, jpeg: ByteArray, bmp: Bitmap) {
        synchronized(this) {
            dir().mkdirs()
            File(dir(), ".nomedia").writeText("")
            fileOf(userId).writeBytes(jpeg)
            val index = readIndex()
            index.put(userId.toString(), url)
            indexFile().writeText(index.toString())
            mem[userId] = url to bmp
        }
    }

    private fun remove(userId: Long) {
        synchronized(this) {
            mem.remove(userId)
            fileOf(userId).delete()
            val index = readIndex()
            if (index.has(userId.toString())) {
                index.remove(userId.toString())
                indexFile().writeText(index.toString())
            }
        }
    }

    private suspend fun download(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.bytes()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun dir(): File = File(ChatApp.instance.filesDir, "avatars")

    private fun fileOf(userId: Long): File = File(dir(), "$userId.jpg")

    private fun indexFile(): File = File(dir(), "index.json")

    private fun readIndex(): JSONObject {
        val file = indexFile()
        if (!file.exists()) return JSONObject()
        return try {
            JSONObject(file.readText())
        } catch (_: Exception) {
            JSONObject()
        }
    }
}
