package ru.daystream.calc.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import ru.daystream.calc.data.SessionStore
import ru.daystream.calc.data.UiState
import ru.daystream.calc.sync.SyncService

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
        val theme = ThemeStore(this)
        setContent {
            val themeMode = remember { mutableStateOf(theme.mode) }
            AppTheme(themeMode.value) {
                ChatRoot(
                    store = store,
                    themeMode = themeMode,
                    onThemeChange = {
                        theme.mode = it
                        themeMode.value = it
                    },
                    onArrowShort = { closeLikeArrow() },
                    onLogout = {
                        store.clear()
                        UiState.chatOpen = false
                        stopService(Intent(this, SyncService::class.java))
                        startActivity(Intent(this, ShellActivity::class.java))
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
