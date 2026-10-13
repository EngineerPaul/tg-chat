package ru.daystream.calc.media

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Скрытие вложений: sandbox, .nomedia, XOR и чужое расширение.
 */
class HiddenStore(context: Context) {
    private val dir = File(context.filesDir, "hidden").apply {
        mkdirs()
        File(this, ".nomedia").writeText("")
    }
    private val key = 0x5A.toByte()

    /**
     * Записать байты в обфусцированный файл.
     * @param bytes исходные данные
     */
    fun put(bytes: ByteArray): String {
        val name = UUID.randomUUID().toString().replace("-", "") + ".bin"
        val out = ByteArray(bytes.size + 4)
        out[0] = 0x43; out[1] = 0x48; out[2] = 0x41; out[3] = 0x54
        for (i in bytes.indices) out[i + 4] = (bytes[i].toInt() xor key.toInt()).toByte()
        File(dir, name).writeBytes(out)
        return name
    }

    /**
     * Прочитать и снять обфускацию в память.
     * @param name случайное имя
     */
    fun get(name: String): ByteArray? {
        val file = File(dir, name)
        if (!file.exists()) return null
        val raw = file.readBytes()
        if (raw.size < 4) return null
        val out = ByteArray(raw.size - 4)
        for (i in out.indices) out[i] = (raw[i + 4].toInt() xor key.toInt()).toByte()
        return out
    }

    /**
     * Удалить вытесненный файл.
     * @param name случайное имя
     */
    fun delete(name: String) {
        File(dir, name).delete()
    }
}
