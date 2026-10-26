package ru.daystream.calc.data

import ru.daystream.calc.net.ChatApi
import ru.daystream.calc.net.createApi

/**
 * Обёртка API: подставляет Bearer и сама обновляет access.
 */
class TokenClient(private val store: SessionStore) {
    private val api: ChatApi = createApi()

    /**
     * Заголовок Authorization.
     */
    fun auth(): String = "Bearer ${store.access}"

    /**
     * Обновить access refresh-токеном, без поля ключа.
     */
    suspend fun refreshIfNeeded() {
        if (store.refresh.isBlank()) return
        try {
            val res = api.refresh(mapOf("refresh_token" to store.refresh))
            store.access = res["access_token"] ?: store.access
        } catch (_: Exception) {
            // сеть; следующая попытка при следующем запросе
        }
    }

    /**
     * Выполнить запрос, при 401 обновить access и повторить.
     */
    suspend fun <T> call(block: suspend ChatApi.(String) -> T): T {
        return try {
            api.block(auth())
        } catch (e: retrofit2.HttpException) {
            if (e.code() != 401) throw e
            refreshIfNeeded()
            api.block(auth())
        }
    }

    /**
     * Сырой клиент для multipart.
     */
    fun raw(): ChatApi = api
}
