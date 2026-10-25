package ru.daystream.calc.sync

import android.app.NotificationManager
import android.app.Service
import android.content.Context
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
import ru.daystream.calc.ChatApp
import ru.daystream.calc.data.SessionStore
import ru.daystream.calc.data.TokenClient
import ru.daystream.calc.data.UiState
import ru.daystream.calc.net.openWs
import ru.daystream.calc.push.registerFcm

/**
 * Фон: WebSocket и refresh. Без foreground — иначе точка в шторке и «приложение остановлено».
 */
class SyncService : Service() {
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var socket: WebSocket? = null
    @Volatile private var looping = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!looping) {
            looping = true
            scope.launch { loop() }
        }
        return START_STICKY
    }

    /**
     * Пока сессия жива — refresh и WebSocket.
     */
    private suspend fun loop() {
        val store = SessionStore(this)
        val tokens = TokenClient(store)
        registerFcm(store)
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

    /**
     * Смахнули карточку в недавних — скрытая часть выключается, фон остаётся.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        UiState.dismissHidden()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        job.cancel()
        socket?.cancel()
        getSystemService(NotificationManager::class.java).cancel(ChatApp.NOTIFY_SYNC)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        /**
         * Старт из экрана (приложение на переднем плане). Не startForegroundService.
         */
        fun start(context: Context) {
            try {
                context.startService(Intent(context, SyncService::class.java))
            } catch (_: IllegalStateException) { }
        }
    }
}
