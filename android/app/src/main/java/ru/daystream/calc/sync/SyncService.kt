package ru.daystream.calc.sync

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import ru.daystream.calc.ChatApp
import ru.daystream.calc.data.SessionStore
import ru.daystream.calc.data.TokenClient
import ru.daystream.calc.net.openWs

/**
 * Фон: WebSocket и обновление access, без постоянного уведомления.
 */
class SyncService : Service() {
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var socket: WebSocket? = null

    /**
     * После startForegroundService система требует уведомление на секунду — сразу снимаем.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= 26) {
            val n = NotificationCompat.Builder(this, ChatApp.CHANNEL_SYNC)
                .setContentTitle("Обновления")
                .setContentText("Проверка обновлений")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build()
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(1, n)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        getSystemService(NotificationManager::class.java).cancel(1)
        scope.launch { loop() }
        return START_STICKY
    }

    /**
     * Пока сессия жива — refresh и WebSocket.
     */
    private suspend fun loop() {
        val store = SessionStore(this)
        val tokens = TokenClient(store)
        while (store.hasSession()) {
            tokens.refreshIfNeeded()
            var closed = false
            socket = openWs(store.access, object : WebSocketListener() {
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    closed = true
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    closed = true
                }
            })
            while (store.hasSession() && !closed) delay(4000)
            delay(2000)
        }
        stopSelf()
    }

    override fun onDestroy() {
        job.cancel()
        socket?.cancel()
        getSystemService(NotificationManager::class.java).cancel(1)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
