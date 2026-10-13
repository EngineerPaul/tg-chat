package ru.daystream.calc.sync

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import ru.daystream.calc.data.SessionStore
import ru.daystream.calc.data.TokenClient
import ru.daystream.calc.net.openWs

/**
 * Фон: WebSocket и обновление access, без показа UI.
 */
class SyncService : Service() {
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var socket: WebSocket? = null

    /**
     * Поднять соединение, если есть сессия.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notification.Builder(this, "updates")
            .setContentTitle("Обновления")
            .setContentText("Проверка обновлений")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .build()
        startForeground(1, n)
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
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
