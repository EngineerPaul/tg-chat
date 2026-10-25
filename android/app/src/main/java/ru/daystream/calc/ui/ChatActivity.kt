package ru.daystream.calc.ui

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private val lockHandler = Handler(Looper.getMainLooper())
    private val expireRunnable = Runnable { expireToPublic() }

    /**
     * Включить защиту снимка и UI чата. После закрытия / 5 минут — не рисовать чат.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideChatSurface()
        getSystemService(NotificationManager::class.java).cancel(1)
        if (!allowShowChat(intent)) return
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
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
                        UiState.dismissHidden()
                        stopService(Intent(this, SyncService::class.java))
                        startActivity(Intent(this, ShellActivity::class.java))
                        finish()
                    },
                )
            }
        }
    }

    /**
     * Тот же ChatActivity ещё жив: тап по «Обновления» снова проверяет окно.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!allowShowChat(intent)) return
    }

    /**
     * Свернули — убрать кадр чата сразу, чтобы при возврате его не было видно.
     */
    override fun onUserLeaveHint() {
        hideChatSurface()
        super.onUserLeaveHint()
        markMinimized()
    }

    override fun onPause() {
        hideChatSurface()
        UiState.chatOpen = false
        super.onPause()
    }

    override fun onStop() {
        hideChatSurface()
        super.onStop()
        if (isFinishing) {
            lockHandler.removeCallbacks(expireRunnable)
            UiState.dismissHidden()
            return
        }
        if (UiState.closedByArrow) {
            finishWholeTask()
            return
        }
        markMinimized()
    }

    /**
     * Если 5 минут вышли — закрыть чат до кадра, останется калькулятор.
     */
    override fun onRestart() {
        if (UiState.closedByArrow || expiredLock()) hideChatSurface()
        super.onRestart()
    }

    override fun onStart() {
        if (UiState.closedByArrow || expiredLock()) {
            hideChatSurface()
            super.onStart()
            finish()
            overridePendingTransition(0, 0)
            return
        }
        super.onStart()
        lockHandler.removeCallbacks(expireRunnable)
    }

    override fun onResume() {
        super.onResume()
        if (UiState.closedByArrow || expiredLock() || isFinishing) {
            hideChatSurface()
            finish()
            overridePendingTransition(0, 0)
            return
        }
        showChatSurface()
        UiState.enterHidden()
    }

    override fun onDestroy() {
        lockHandler.removeCallbacks(expireRunnable)
        super.onDestroy()
    }

    /**
     * Короткое «назад»: предыдущее приложение, калькулятор закрыть уже из фона.
     */
    fun closeLikeArrow() {
        lockHandler.removeCallbacks(expireRunnable)
        UiState.dismissHidden()
        moveTaskToBack(true)
        overridePendingTransition(0, 0)
    }

    /**
     * Снять задачу целиком, включая калькулятор.
     */
    private fun finishWholeTask() {
        getSystemService(ActivityManager::class.java).appTasks.forEach { it.finishAndRemoveTask() }
    }

    /**
     * Тап по пушу и обычный вход: меньше окна — чат, иначе активация / закрыть.
     */
    private fun allowShowChat(incoming: Intent): Boolean {
        val fromPush = incoming.getBooleanExtra("from_push", false)
        if (fromPush) {
            if (UiState.canOpenChatFromPush()) {
                UiState.closedByArrow = false
                return true
            }
            startActivity(Intent(this, ActivationActivity::class.java).putExtra("from_push", true))
            finish()
            overridePendingTransition(0, 0)
            return false
        }
        if (UiState.closedByArrow || expiredLock()) {
            finish()
            overridePendingTransition(0, 0)
            return false
        }
        return true
    }

    private fun markMinimized() {
        if (isFinishing || isChangingConfigurations) return
        if (UiState.closedByArrow) return
        UiState.startHiddenWindow()
        val left = UiState.remainingHiddenMs()
        if (left <= 0L) return
        lockHandler.removeCallbacks(expireRunnable)
        lockHandler.postDelayed(expireRunnable, left)
    }

    private fun expireToPublic() {
        if (UiState.withinFiveMinutes()) return
        if (UiState.minimizedAt == 0L && !UiState.closedByArrow) return
        UiState.dismissHidden()
        hideChatSurface()
        finish()
        overridePendingTransition(0, 0)
    }

    private fun expiredLock(): Boolean {
        if (UiState.closedByArrow) return true
        if (UiState.minimizedAt == 0L) return false
        if (UiState.withinFiveMinutes()) return false
        UiState.dismissHidden()
        return true
    }

    private fun hideChatSurface() {
        window.decorView.alpha = 0f
        val lp = window.attributes
        lp.alpha = 0f
        window.attributes = lp
    }

    private fun showChatSurface() {
        val lp = window.attributes
        lp.alpha = 1f
        window.attributes = lp
        window.decorView.alpha = 1f
    }
}
