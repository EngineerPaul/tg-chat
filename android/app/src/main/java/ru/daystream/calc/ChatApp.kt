package ru.daystream.calc

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import ru.daystream.calc.sync.SyncService

/**
 * Процесс приложения. Канал «Обновления» без намёка на мессенджер.
 */
class ChatApp : Application() {
    /**
     * Создать канал уведомлений при старте процесса.
     */
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("updates", "Обновления", NotificationManager.IMPORTANCE_DEFAULT),
        )
        instance = this
    }

    companion object {
        lateinit var instance: ChatApp
            private set
    }
}
