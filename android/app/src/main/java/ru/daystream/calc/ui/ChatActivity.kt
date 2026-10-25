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
        val fromPush = intent.getBooleanExtra("from_push", false)
        if (fromPush && UiState.canOpenChatFromPush()) {
            UiState.closedByArrow = false
        } else if (UiState.closedByArrow || expiredLock()) {
            finish()
            overridePendingTransition(0, 0)
            return
        }
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        UiState.chatOpen = true
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
     * Свернули — убрать кадр чата сразу, чтобы при возврате его не было видно.
     */
    override fun onUserLeaveHint() {
        hideChatSurface()
        super.onUserLeaveHint()
        markMinimized()
    }

    override fun onPause() {
        hideChatSurface()
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
        UiState.chatOpen = true
        UiState.minimizedAt = 0L
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

    private fun markMinimized() {
        if (isFinishing || isChangingConfigurations) return
        if (UiState.closedByArrow) return
        if (UiState.chatOpen) {
            UiState.minimizedAt = System.currentTimeMillis()
            lockHandler.removeCallbacks(expireRunnable)
            lockHandler.postDelayed(expireRunnable, 5 * 60 * 1000L)
        }
    }

    private fun expireToPublic() {
        if (UiState.withinFiveMinutes() || UiState.minimizedAt == 0L) return
        UiState.dismissHidden()
        hideChatSurface()
        finish()
        overridePendingTransition(0, 0)
    }

    private fun expiredLock(): Boolean {
        if (UiState.closedByArrow) return true
        if (UiState.minimizedAt == 0L) return false
        if (UiState.withinFiveMinutes()) return false
        UiState.chatOpen = false
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
