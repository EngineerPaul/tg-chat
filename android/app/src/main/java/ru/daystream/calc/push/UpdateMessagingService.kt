package ru.daystream.calc.push

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.daystream.calc.data.SessionStore
import ru.daystream.calc.data.UiState
import ru.daystream.calc.ui.ActivationActivity
import ru.daystream.calc.ui.ChatActivity

/**
 * FCM: текст только «Доступно новое обновление».
 */
class UpdateMessagingService : FirebaseMessagingService() {
    /**
     * Google сменил токен — обновить на сервере.
     */
    override fun onNewToken(token: String) {
        val store = SessionStore(this)
        CoroutineScope(Dispatchers.IO).launch { registerFcm(store) }
    }

    /**
     * Показать маскировочное уведомление.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        if (UiState.chatOpen) return
        val chatNow = UiState.withinFiveMinutes()
        val target = when {
            chatNow -> ChatActivity::class.java
            else -> ActivationActivity::class.java
        }
        val pi = PendingIntent.getActivity(
            this,
            1,
            Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, ru.daystream.calc.ChatApp.CHANNEL_UPDATES)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Обновления")
            .setContentText("Доступно новое обновление")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(42, n)
    }
}
