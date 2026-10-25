package ru.daystream.calc.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import ru.daystream.calc.data.SessionStore
import ru.daystream.calc.data.UiState

/**
 * Тап по «Обновления»: меньше [ChatApp.HIDDEN_WINDOW_MS] — чат, иначе ключ.
 */
class PushOpenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SessionStore(this)
        val openChat = store.hasSession() && UiState.canOpenChatFromPush()
        val next = if (openChat) {
            UiState.closedByArrow = false
            Intent(this, ChatActivity::class.java)
        } else {
            Intent(this, ActivationActivity::class.java)
        }
        next.putExtra("from_push", true)
        next.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(next)
        finish()
    }
}
