package ru.daystream.calc.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import ru.daystream.calc.data.SessionStore
import ru.daystream.calc.data.UiState

/**
 * Скрытая часть. FLAG_SECURE, чтобы в недавних не было переписки.
 */
class ChatActivity : ComponentActivity() {
    /**
     * Включить защиту снимка и UI чата.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        UiState.chatOpen = true
        UiState.closedByArrow = false
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                closeLikeArrow()
            }
        })
        val store = SessionStore(this)
        setContent {
            MaterialTheme {
                ChatRoot(
                    store = store,
                    onArrowShort = { closeLikeArrow() },
                    onLogout = {
                        store.clear()
                        UiState.chatOpen = false
                        startActivity(Intent(this, ru.daystream.calc.ui.ShellActivity::class.java))
                        finish()
                    },
                )
            }
        }
    }

    /**
     * Свернули — таймер 5 минут, в недавних калькулятор (этот экран не снимается).
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (UiState.chatOpen && !UiState.closedByArrow) {
            UiState.minimizedAt = System.currentTimeMillis()
        }
    }

    /**
     * Если 5 минут вышли, пока были в фоне — закрыть чат до кадра.
     */
    override fun onStart() {
        super.onStart()
        if (UiState.minimizedAt != 0L && !UiState.withinFiveMinutes() && !UiState.chatOpen) {
            finish()
        }
        if (UiState.minimizedAt != 0L && !UiState.withinFiveMinutes()) {
            UiState.chatOpen = false
            finish()
        }
    }

    /**
     * Короткое «назад»: предыдущее приложение, чат закрыть, процесс жив.
     */
    fun closeLikeArrow() {
        UiState.closedByArrow = true
        UiState.chatOpen = false
        UiState.minimizedAt = 0L
        moveTaskToBack(true)
        finish()
    }
}
