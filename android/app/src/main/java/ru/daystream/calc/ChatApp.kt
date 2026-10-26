package ru.daystream.calc

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
/**
 * Процесс приложения. Каналы без намёка на мессенджер.
 */
class ChatApp : Application() {
    /**
     * FCM — слышный канал «Обновления». Фон — тихий канал, без всплывающего окна.
     */
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATES, "Обновления", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val sync = NotificationChannel(CHANNEL_SYNC, "Служба", NotificationManager.IMPORTANCE_MIN).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        nm.createNotificationChannel(sync)
        instance = this
    }

    companion object {
        /** Значение по умолчанию, если в настройках ещё не задавали. */
        const val HIDDEN_WINDOW_DEFAULT_MIN = 5
        const val HIDDEN_WINDOW_MAX_MIN = 999
        const val CHANNEL_UPDATES = "updates"
        const val CHANNEL_SYNC = "sync_min"
        const val NOTIFY_SYNC = 3
        const val NOTIFY_UPDATE = 42

        lateinit var instance: ChatApp
            private set
    }
}

/**
 * Окно возврата в чат без ключа. Хранится в настройках приложения.
 * Выкл — сразу чат, без таймера.
 */
object HiddenLock {
    private val prefs
        get() = ChatApp.instance.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("lock_enabled", true)
        set(value) { prefs.edit().putBoolean("lock_enabled", value).apply() }

    var minutes: Int
        get() = prefs.getInt("lock_minutes", ChatApp.HIDDEN_WINDOW_DEFAULT_MIN)
            .coerceIn(0, ChatApp.HIDDEN_WINDOW_MAX_MIN)
        set(value) {
            prefs.edit().putInt("lock_minutes", value.coerceIn(0, ChatApp.HIDDEN_WINDOW_MAX_MIN)).apply()
        }

    fun windowMs(): Long {
        if (!enabled) return Long.MAX_VALUE
        return minutes * 60_000L
    }
}
