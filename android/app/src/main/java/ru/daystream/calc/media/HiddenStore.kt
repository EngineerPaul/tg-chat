package ru.daystream.calc.media

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Скрытие вложений: sandbox, .nomedia, XOR и чужое расширение.
 * Исходное имя только в индексе (и в пузыре), не в имени файла на диске.
 */
class HiddenStore(context: Context) {
    private val dir = File(context.filesDir, "hidden").apply {
        mkdirs()
        File(this, ".nomedia").writeText("")
    }
    private val cache = File(context.cacheDir, "reveal").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")
    private val key = 0x5A.toByte()

    /**
     * Записать байты в обфусцированный файл и запомнить метаданные.
     * @param id ключ вложения (id с сервера или preview-{id})
     * @param bytes исходные данные уже разжатые
     * @param origName имя для пузыря и «Сохранить»
     * @param mime тип
     * @param kind photo / video / file / preview
     */
    @Synchronized
    fun put(id: String, bytes: ByteArray, origName: String, mime: String, kind: String) {
        val existing = meta(id)
        if (existing != null) File(dir, existing.file).delete()
        val name = UUID.randomUUID().toString().replace("-", "") + ".dat"
        File(dir, name).writeBytes(obfuscate(bytes))
        val index = readIndex()
        index.put(
            id,
            JSONObject()
                .put("file", name)
                .put("name", origName)
                .put("mime", mime)
                .put("kind", kind)
                .put("size", bytes.size),
        )
        writeIndex(index)
    }

    /**
     * Снять обфускацию в память. На диск в открытом виде не пишет.
     */
    @Synchronized
    fun get(id: String): ByteArray? {
        val row = meta(id) ?: return null
        val file = File(dir, row.file)
        if (!file.exists()) return null
        return deobfuscate(file.readBytes())
    }

    /** Есть ли скрытая копия. */
    @Synchronized
    fun has(id: String): Boolean {
        val row = meta(id) ?: return false
        return File(dir, row.file).exists()
    }

    /** Метаданные для пузыря. */
    @Synchronized
    fun meta(id: String): Meta? {
        val obj = readIndex().optJSONObject(id) ?: return null
        return Meta(
            file = obj.optString("file"),
            origName = obj.optString("name"),
            mime = obj.optString("mime"),
            kind = obj.optString("kind"),
            size = obj.optInt("size"),
        )
    }

    /**
     * Временная копия в cache для «Открыть» / проигрывателя. Не MediaStore.
     * Вызывающий обязан удалить через [deleteTemp].
     */
    fun revealTemp(id: String): File? {
        val bytes = get(id) ?: return null
        val row = meta(id)
        val ext = row?.origName?.substringAfterLast('.', "")?.takeIf { it.length in 1..8 } ?: "bin"
        val tmp = File(cache, "t${UUID.randomUUID().toString().replace("-", "")}.$ext")
        tmp.writeBytes(bytes)
        return tmp
    }

    /** Удалить временную раскрытую копию. */
    fun deleteTemp(file: File?) {
        file?.delete()
    }

    /**
     * Оставить только вложения текущего окна истории.
     * @param keep ids, которые ещё в ленте
     */
    @Synchronized
    fun retain(keep: Set<String>) {
        val index = readIndex()
        val keys = index.keys().asSequence().toList()
        keys.forEach { id ->
            if (id in keep) return@forEach
            val file = index.optJSONObject(id)?.optString("file").orEmpty()
            if (file.isNotBlank()) File(dir, file).delete()
            index.remove(id)
        }
        writeIndex(index)
    }

    private fun obfuscate(bytes: ByteArray): ByteArray {
        val out = ByteArray(bytes.size + 4)
        out[0] = 0x43; out[1] = 0x48; out[2] = 0x41; out[3] = 0x54
        for (i in bytes.indices) out[i + 4] = (bytes[i].toInt() xor key.toInt()).toByte()
        return out
    }

    private fun deobfuscate(raw: ByteArray): ByteArray? {
        if (raw.size < 4 || raw[0] != 0x43.toByte() || raw[1] != 0x48.toByte()) return null
        val out = ByteArray(raw.size - 4)
        for (i in out.indices) out[i] = (raw[i + 4].toInt() xor key.toInt()).toByte()
        return out
    }

    private fun readIndex(): JSONObject {
        if (!indexFile.exists()) return JSONObject()
        return try {
            JSONObject(indexFile.readText())
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun writeIndex(obj: JSONObject) {
        indexFile.writeText(obj.toString())
    }

    data class Meta(
        val file: String,
        val origName: String,
        val mime: String,
        val kind: String,
        val size: Int,
    )
}
