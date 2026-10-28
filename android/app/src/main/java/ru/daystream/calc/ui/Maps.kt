package ru.daystream.calc.ui

import org.json.JSONArray
import org.json.JSONObject

/**
 * Безопасно привести JSON-число (Gson Double / JSONObject Integer) к Long.
 */
fun Any?.asLong(): Long? = when (this) {
    is Number -> toLong()
    is String -> toDoubleOrNull()?.toLong()
    else -> null
}

/**
 * JSON-булево из Gson Map (Boolean / 1.0 / "true").
 */
fun Any?.asBool(): Boolean = when (this) {
    is Boolean -> this
    is Number -> toInt() != 0
    is String -> equals("true", ignoreCase = true) || this == "1"
    else -> false
}

/**
 * Строка из словаря.
 */
fun Map<*, *>.str(key: String): String {
    val v = this[key] ?: return ""
    return if (v == JSONObject.NULL) "" else v.toString()
}

/**
 * Вложенный объект.
 */
fun Map<*, *>.map(key: String): Map<*, *>? = this[key] as? Map<*, *>

/**
 * Разобрать список словарей из ответа Gson.
 */
fun asMapList(raw: Any?): List<Map<String, Any>> {
    val list = raw as? List<*> ?: return emptyList()
    return list.mapNotNull { item ->
        val src = item as? Map<*, *> ?: return@mapNotNull null
        src.entries.mapNotNull { (k, v) ->
            if (k == null || v == null) null else k.toString() to v
        }.toMap()
    }
}

/**
 * Разобрать кадр WebSocket в словарь.
 */
fun jsonToMap(obj: JSONObject): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    val keys = obj.keys()
    while (keys.hasNext()) {
        val k = keys.next()
        val v = obj.opt(k)
        if (v == null || v == JSONObject.NULL) continue
        map[k] = when (v) {
            is JSONObject -> jsonToMap(v)
            is JSONArray -> (0 until v.length()).mapNotNull { i ->
                when (val item = v.opt(i)) {
                    null, JSONObject.NULL -> null
                    is JSONObject -> jsonToMap(item)
                    else -> item
                }
            }
            else -> v
        }
    }
    return map
}

/**
 * Имя в чате: ник, если задан, иначе логин.
 */
fun peerDisplay(peer: Map<*, *>?): String {
    val nick = peer?.get("nick")?.toString()?.trim().orEmpty()
    if (nick.isNotEmpty()) return nick
    val display = peer?.get("display_name")?.toString()?.trim().orEmpty()
    if (display.isNotEmpty()) return display
    return peer?.get("login")?.toString().orEmpty()
}

/**
 * Вставить или заменить сообщение по id.
 */
fun upsertMessage(messages: MutableList<Map<String, Any>>, msg: Map<String, Any>) {
    val id = msg["id"].asLong() ?: return
    val idx = messages.indexOfFirst { it["id"].asLong() == id }
    if (idx >= 0) messages[idx] = msg else messages.add(msg)
}
