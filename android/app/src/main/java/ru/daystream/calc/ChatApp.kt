package ru.daystream.calc

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
        const val CHANNEL_UPDATES = "updates"
        const val CHANNEL_SYNC = "sync_min"

        lateinit var instance: ChatApp
            private set
    }
}
