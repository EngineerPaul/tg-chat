package ru.daystream.calc.data

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

/**
 * Защищённое хранение JWT, логина и хеша ключа.
 */
class SessionStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** Логин, который помнит устройство. */
    var login: String
        get() = prefs.getString("login", "") ?: ""
        set(value) { prefs.edit().putString("login", value).apply() }

    /** Короткий access JWT. */
    var access: String
        get() = prefs.getString("access", "") ?: ""
        set(value) { prefs.edit().putString("access", value).apply() }

    /** Refresh без истечения, пока нет «Выйти». */
    var refresh: String
        get() = prefs.getString("refresh", "") ?: ""
        set(value) { prefs.edit().putString("refresh", value).apply() }

    /** Есть ли привязка устройства. */
    fun hasSession(): Boolean = refresh.isNotBlank()

    /**
     * Сохранить хеш ключа для локальной активации.
     * @param key пароль аккаунта
     */
    fun saveKeyHash(key: String) {
        prefs.edit().putString("key_hash", sha256(key)).apply()
    }

    /**
     * Сверить ключ с сохранённым хешем.
     * @param key введённый ключ
     */
    fun checkKey(key: String): Boolean {
        val stored = prefs.getString("key_hash", "") ?: return false
        return stored.isNotEmpty() && stored == sha256(key)
    }

    /**
     * Стереть сессию при «Выйти».
     */
    fun clear() {
        prefs.edit().clear().apply()
    }

    /**
     * SHA-256 ключа, не хранить пароль открытым текстом.
     * @param value ключ
     */
    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
