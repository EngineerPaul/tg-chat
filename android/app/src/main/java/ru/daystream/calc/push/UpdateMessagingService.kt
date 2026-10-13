package ru.daystream.calc.push

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import ru.daystream.calc.data.UiState
import ru.daystream.calc.ui.ActivationActivity
import ru.daystream.calc.ui.ChatActivity
import ru.daystream.calc.ui.ShellActivity

/**
 * FCM: текст только «Доступно новое обновление».
 */
class UpdateMessagingService : FirebaseMessagingService() {
    /**
     * Показать маскировочное уведомление.
     */
    override fun onMessageReceived(message: RemoteMessage) {
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
        val n = NotificationCompat.Builder(this, "updates")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Обновления")
            .setContentText("Доступно новое обновление")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(42, n)
    }
}
