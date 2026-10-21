package ru.daystream.calc.ui

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import ru.daystream.calc.data.SessionStore
import ru.daystream.calc.data.UiState
import ru.daystream.calc.shell.CalculatorScreen
import ru.daystream.calc.sync.SyncService

/**
 * Лаунчер: калькулятор. Не чат и не поле ключа.
 */
class ShellActivity : ComponentActivity() {
    /**
     * Показать калькулятор или сразу чат, если окно 5 минут.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSystemService(NotificationManager::class.java).cancel(1)
        if (SessionStore(this).hasSession()) {
            val sync = Intent(this, SyncService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(sync) else startService(sync)
        }
        openChatIfWindow()
        val store = getSharedPreferences("calc_hist", MODE_PRIVATE)
        val theme = ThemeStore(this)
        setContent {
            AppTheme(theme.mode) {
                val hist = remember { mutableStateOf(store.getString("tape", "")?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()) }
                CalculatorScreen(
                    history = hist.value,
                    onHistoryChange = {
                        hist.value = it
                        store.edit().putString("tape", it.joinToString("\n")).apply()
                    },
                    onActivate = { startActivity(Intent(this, ActivationActivity::class.java)) },
                )
            }
        }
    }

    /**
     * Иконка при живом процессе: вернуть чат только в окне 5 минут.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openChatIfWindow()
    }

    /**
     * После 5 минут не восстанавливать чат.
     */
    override fun onResume() {
        super.onResume()
        if (UiState.minimizedAt != 0L && !UiState.withinFiveMinutes()) {
            UiState.minimizedAt = 0L
        }
    }

    private fun openChatIfWindow() {
        if (UiState.withinFiveMinutes()) {
            startActivity(Intent(this, ChatActivity::class.java))
        }
    }
}
