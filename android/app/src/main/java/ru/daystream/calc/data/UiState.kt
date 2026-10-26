package ru.daystream.calc.data

import android.content.Context
import ru.daystream.calc.ChatApp
import ru.daystream.calc.HiddenLock

/**
 * Состояние скрытой части для таймера возврата и push.
 * Момент сворачивания пишется на диск, чтобы пережить смену activity.
 */
object UiState {
    private val prefs
        get() = ChatApp.instance.getSharedPreferences("ui_state", Context.MODE_PRIVATE)

    /** Скрытый чат сейчас на экране. */
    @Volatile var chatOpen: Boolean = false

    /** Момент сворачивания чата, 0 если не сворачивали. */
    var minimizedAt: Long
        get() = prefs.getLong("minimizedAt", 0L)
        set(value) { prefs.edit().putLong("minimizedAt", value).apply() }

    /** Закрыли стрелкой — следующий вход не в чат. */
    var closedByArrow: Boolean
        get() = prefs.getBoolean("closedByArrow", false)
        set(value) { prefs.edit().putBoolean("closedByArrow", value).apply() }

    /** Диалог из последнего push, если тап в окне 5 минут. */
    var pendingDialogId: Long?
        get() = prefs.getLong("pendingDialogId", 0L).takeIf { it != 0L }
        set(value) { prefs.edit().putLong("pendingDialogId", value ?: 0L).apply() }

    /** Момент маскировочного пуша, 0 если не было. */
    var pushAt: Long
        get() = prefs.getLong("pushAt", 0L)
        set(value) { prefs.edit().putLong("pushAt", value).apply() }

    /**
     * Сколько ещё длится окно после сворачивания. 0 — окна нет.
     * Длительность из [ru.daystream.calc.HiddenLock].
     */
    fun remainingHiddenMs(): Long {
        if (!HiddenLock.enabled) return Long.MAX_VALUE
        if (closedByArrow || minimizedAt == 0L) return 0L
        val left = HiddenLock.windowMs() - (System.currentTimeMillis() - minimizedAt)
        return left.coerceAtLeast(0L)
    }

    /**
     * Окно ещё открыто, либо замок в настройках выключен.
     */
    fun withinLockWindow(): Boolean {
        if (!HiddenLock.enabled) return true
        return remainingHiddenMs() > 0L
    }

    /**
     * Тап по «Обновления»: в окне или с выключенным замком — чат, иначе ключ.
     */
    fun canOpenChatFromPush(): Boolean = withinLockWindow()

    /**
     * Запустить окно. Вызывать при Home / недавних / уходе из чата, не при стрелке.
     */
    fun startHiddenWindow() {
        if (closedByArrow) return
        minimizedAt = System.currentTimeMillis()
    }

    /**
     * Человек снова в скрытом чате — окно не нужно, пока снова не свернёт.
     */
    fun enterHidden() {
        closedByArrow = false
        minimizedAt = 0L
        chatOpen = true
    }

    /**
     * Скрытую часть выключить: закрытие, смахивание, иконка, истечение окна.
     */
    fun dismissHidden() {
        chatOpen = false
        closedByArrow = true
        minimizedAt = 0L
    }
}
