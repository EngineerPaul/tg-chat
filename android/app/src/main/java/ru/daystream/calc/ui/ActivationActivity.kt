package ru.daystream.calc.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.daystream.calc.data.SessionStore
import ru.daystream.calc.data.UiState
import ru.daystream.calc.net.createApi
import ru.daystream.calc.sync.SyncService

/**
 * Страница активации: ключ (и логин при первой привязке).
 */
class ActivationActivity : ComponentActivity() {
    /**
     * Показать поле ключа в духе активации функций калькулятора.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SessionStore(this)
        if (intent.getBooleanExtra("from_push", false) && store.hasSession() && UiState.canOpenChatFromPush()) {
            openChat()
            return
        }
        val theme = ThemeStore(this)
        setContent {
            AppTheme(theme.mode) {
                val login = remember { mutableStateOf(store.login) }
                val key = remember { mutableStateOf("") }
                val err = remember { mutableStateOf("") }
                val scope = rememberCoroutineScope()
                val first = !store.hasSession()
                val colors = readableFieldColors()
                Column(Modifier.padding(24.dp)) {
                    Text("Активация функций", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Введите ключ активации, чтобы открыть расширенные функции.",
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (first) {
                        OutlinedTextField(
                            login.value,
                            { login.value = it },
                            label = { Text("Идентификатор") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = colors,
                        )
                    }
                    OutlinedTextField(
                        key.value,
                        { key.value = it },
                        label = { Text("Ключ") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = colors,
                    )
                    if (err.value.isNotEmpty()) {
                        Text(err.value, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    if (first) {
                                        val api = createApi()
                                        val res = api.login(mapOf("login" to login.value, "password" to key.value))
                                        store.login = login.value
                                        store.access = res["access_token"] as String
                                        store.refresh = res["refresh_token"] as String
                                        store.saveKeyHash(key.value)
                                        startSync()
                                        openChat()
                                    } else if (store.checkKey(key.value)) {
                                        startSync()
                                        openChat()
                                    } else {
                                        err.value = "Неверный ключ"
                                    }
                                } catch (_: Exception) {
                                    err.value = "Неверный ключ"
                                }
                            }
                        },
                        modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                    ) { Text("Активировать") }
                }
            }
        }
    }

    /**
     * Фон после привязки: без всплывающего окна «Обновления».
     */
    private fun startSync() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        SyncService.start(this)
    }

    /**
     * После верного ключа открыть скрытый чат.
     */
    private fun openChat() {
        UiState.enterHidden()
        startActivity(Intent(this, ChatActivity::class.java))
        finish()
    }
}
