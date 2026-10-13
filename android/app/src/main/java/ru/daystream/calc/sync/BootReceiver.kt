package ru.daystream.calc.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.daystream.calc.data.SessionStore

/**
 * После перезагрузки поднять связь без открытия приложения.
 */
class BootReceiver : BroadcastReceiver() {
    /**
     * Старт SyncService, если сессия не сброшена.
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!SessionStore(context).hasSession()) return
        context.startForegroundService(Intent(context, SyncService::class.java))
    }
}
