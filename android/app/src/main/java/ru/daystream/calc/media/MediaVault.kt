package ru.daystream.calc.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.daystream.calc.ui.asLong
import ru.daystream.calc.ui.asMapList
import ru.daystream.calc.ui.str
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * Скачать вложение с сервера в HiddenStore и отдать копию только по явной команде.
 */
class MediaVault(private val context: Context) {
    val store = HiddenStore(context)
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * Догрузить все вложения сообщений в скрытое хранилище.
     */
    suspend fun ensureMessages(messages: List<Map<String, Any>>, onReady: () -> Unit) {
        messages.forEach { ensureMessage(it, onReady) }
    }

    /**
     * Положить локальные байты сразу после отправки, без повторной загрузки.
     * @param message ответ сервера
     * @param payloads байты в том же порядке, что attachments
     */
    fun ingestOutgoing(message: Map<String, Any>, payloads: List<ByteArray>) {
        val atts = asMapList(message["attachments"])
        atts.forEachIndexed { i, att ->
            val id = att["id"].asLong()?.toString() ?: return@forEachIndexed
            val bytes = payloads.getOrNull(i) ?: return@forEachIndexed
            store.put(id, bytes, att.str("orig_name").ifBlank { "file" }, att.str("mime").ifBlank { "application/octet-stream" }, att.str("kind").ifBlank { "file" })
        }
    }

    /**
     * Картинка для пузыря из скрытого файла, не из сети и не из MediaStore.
     */
    fun bitmap(id: String): Bitmap? {
        val bytes = store.get(id) ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /**
     * «Сохранить»: обычная копия в Pictures / Movies / Download. Скрытая не меняется.
     */
    suspend fun savePublic(id: String): Boolean = withContext(Dispatchers.IO) {
        val bytes = store.get(id) ?: return@withContext false
        val meta = store.meta(id) ?: return@withContext false
        val resolver = context.contentResolver
        val isVideo = meta.kind == "video" || meta.mime.startsWith("video/")
        val isImage = meta.kind == "photo" || meta.kind == "preview" || meta.mime.startsWith("image/")
        val collection: Uri
        val values = ContentValues()
        when {
            isVideo -> {
                collection = if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                values.put(MediaStore.Video.Media.DISPLAY_NAME, meta.origName.ifBlank { "video.mp4" })
                values.put(MediaStore.Video.Media.MIME_TYPE, meta.mime.ifBlank { "video/mp4" })
                if (Build.VERSION.SDK_INT >= 29) values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
            }
            isImage -> {
                collection = if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                values.put(MediaStore.Images.Media.DISPLAY_NAME, meta.origName.ifBlank { "photo.jpg" })
                values.put(MediaStore.Images.Media.MIME_TYPE, meta.mime.ifBlank { "image/jpeg" })
                if (Build.VERSION.SDK_INT >= 29) values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
            else -> {
                collection = if (Build.VERSION.SDK_INT >= 29) MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Files.getContentUri("external")
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, meta.origName.ifBlank { "file" })
                values.put(MediaStore.MediaColumns.MIME_TYPE, meta.mime.ifBlank { "application/octet-stream" })
                if (Build.VERSION.SDK_INT >= 29) values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }
        val uri = resolver.insert(collection, values) ?: return@withContext false
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return@withContext false
        true
    }

    /**
     * «Открыть»: временная копия через FileProvider, без MediaStore.
     */
    fun openExternal(id: String): File? {
        val tmp = store.revealTemp(id) ?: return null
        val meta = store.meta(id)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", tmp)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, meta?.mime?.ifBlank { "*/*" } ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            tmp
        } catch (_: Exception) {
            tmp.delete()
            null
        }
    }

    /**
     * Догрузить вложения профиля (медиа и файлы диалога) в скрытое хранилище.
     */
    suspend fun ensureAttachments(atts: List<Map<String, Any>>, onReady: () -> Unit) {
        atts.forEach { ensureAtt(it, onReady) }
    }

    private suspend fun ensureMessage(message: Map<String, Any>, onReady: () -> Unit) {
        asMapList(message["attachments"]).forEach { att -> ensureAtt(att, onReady) }
    }

    private suspend fun ensureAtt(att: Map<String, Any>, onReady: () -> Unit) {
        val id = att["id"].asLong()?.toString() ?: return
        if (!store.has(id)) {
            val url = att.str("url")
            if (url.isNotBlank()) {
                val raw = download(url) ?: return
                val bytes = if (att["gzip"] == true || att.str("gzip") == "true" || att.str("gzip") == "1") gunzip(raw) else raw
                store.put(id, bytes, att.str("orig_name").ifBlank { "file" }, att.str("mime").ifBlank { "application/octet-stream" }, att.str("kind").ifBlank { "file" })
                withContext(Dispatchers.Main) { onReady() }
            }
        }
        val previewUrl = att.str("preview_url")
        val previewId = previewKey(id)
        if (previewUrl.isNotBlank() && !store.has(previewId)) {
            val prev = download(previewUrl) ?: return
            store.put(previewId, prev, "preview.jpg", "image/jpeg", "preview")
            withContext(Dispatchers.Main) { onReady() }
        }
    }

    private suspend fun download(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.bytes()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun gunzip(data: ByteArray): ByteArray {
        return try {
            GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
        } catch (_: Exception) {
            data
        }
    }

    companion object {
        fun previewKey(attId: String) = "${attId}p"
    }
}
