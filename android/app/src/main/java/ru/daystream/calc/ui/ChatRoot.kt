package ru.daystream.calc.ui

import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import ru.daystream.calc.media.PhotoCompressor
import ru.daystream.calc.data.SessionStore
import ru.daystream.calc.data.TokenClient

/**
 * Скрытый чат: список и переписка.
 *
 * @param store сессия
 * @param onArrowShort короткое нажатие стрелки
 * @param onLogout пункт «Выйти»
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun ChatRoot(store: SessionStore, onArrowShort: () -> Unit, onLogout: () -> Unit) {
    val client = remember { TokenClient(store) }
    val dialogs = remember { mutableStateListOf<Map<String, Any>>() }
    val messages = remember { mutableStateListOf<Map<String, Any>>() }
    val active = remember { mutableStateOf<Map<String, Any>?>(null) }
    val listMode = remember { mutableStateOf(true) }
    val text = remember { mutableStateOf("") }
    val search = remember { mutableStateOf("") }
    val settings = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val dlg = active.value ?: return@rememberLauncherForActivityResult
        scope.launch {
            val parts = uris.take(10).mapIndexed { i, uri ->
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@mapIndexed null
                val jpeg = PhotoCompressor.compress(bytes)
                MultipartBody.Part.createFormData(
                    "files",
                    "p$i.jpg",
                    jpeg.toRequestBody("image/jpeg".toMediaType()),
                )
            }.filterNotNull()
            if (parts.isNotEmpty()) {
                client.call { auth ->
                    sendMedia(
                        auth,
                        dlg["id"].toString().toDouble().toLong(),
                        parts,
                        text.value.toRequestBody(),
                        "1".toRequestBody(),
                    )
                }
                loadMsgs(client, dlg["id"].toString().toDouble().toLong(), messages)
            }
        }
    }
    val downAt = remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        try {
            val list = client.call { auth -> dialogs(auth) }
            dialogs.clear(); dialogs.addAll(list)
            if (list.size == 1) {
                active.value = list[0]
                listMode.value = false
                loadMsgs(client, list[0]["id"].toString().toDouble().toLong(), messages)
            }
        } catch (_: Exception) { }
    }

    if (settings.value) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Настройки", style = MaterialTheme.typography.titleLarge)
            Text("Тема следует системной. Аватар загружается на сайте.")
            androidx.compose.material3.Button(onClick = onLogout, modifier = Modifier.padding(top = 16.dp)) { Text("Выйти") }
            androidx.compose.material3.TextButton(onClick = { settings.value = false }) { Text("Назад") }
        }
        return
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = {
                val peer = (active.value?.get("peer") as? Map<*, *>)
                Text(
                    if (!listMode.value && peer != null) (peer["display_name"] ?: peer["login"]).toString()
                    else "Чаты",
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = { },
                    modifier = Modifier.pointerInteropFilter {
                        when (it.action) {
                            MotionEvent.ACTION_DOWN -> downAt.value = System.currentTimeMillis()
                            MotionEvent.ACTION_UP -> {
                                if (System.currentTimeMillis() - downAt.value > 450 && !listMode.value) {
                                    listMode.value = true
                                } else {
                                    onArrowShort()
                                }
                            }
                        }
                        true
                    },
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            },
            actions = {
                IconButton(onClick = { settings.value = true }) { Icon(Icons.Default.MoreVert, null) }
            },
        )
        if (listMode.value) {
            OutlinedTextField(
                search.value,
                { search.value = it },
                label = { Text("Поиск по логину") },
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                trailingIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            val d = client.call { auth -> openDialog(auth, mapOf("login" to search.value)) }
                            dialogs.removeAll { it["id"] == d["id"] }
                            dialogs.add(0, d)
                            active.value = d
                            listMode.value = false
                            loadMsgs(client, d["id"].toString().toDouble().toLong(), messages)
                        }
                    }) { Text("OK") }
                },
            )
            LazyColumn {
                items(dialogs) { d ->
                    val peer = d["peer"] as Map<*, *>
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp).combinedClickable(onClick = {
                            active.value = d
                            listMode.value = false
                            scope.launch { loadMsgs(client, d["id"].toString().toDouble().toLong(), messages) }
                        }),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text((peer["display_name"] ?: peer["login"]).toString(), style = MaterialTheme.typography.titleMedium)
                            Text((d["last_message"] ?: "").toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val unread = (d["unread"] as? Number)?.toInt() ?: 0
                        if (unread > 0) Text(unread.toString(), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), reverseLayout = true) {
                items(messages.reversed()) { m ->
                    val mine = (m["author_id"] as Number).toLong()
                    val ticks = m["ticks"] as? String ?: ""
                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                        Text((m["text"] ?: "").toString())
                        Text(
                            (if (ticks == "read") "✓✓" else if (ticks == "delivered") "✓✓" else if (ticks == "sent") "✓" else "") +
                                if (m["edited_at"] != null) " изменено" else "",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Row(Modifier.padding(8.dp)) {
                IconButton(onClick = { photos.launch("image/*") }) { Icon(Icons.Default.AttachFile, null) }
                OutlinedTextField(text.value, { text.value = it }, modifier = Modifier.weight(1f), placeholder = { Text("Сообщение") })
                IconButton(onClick = {
                    val dlg = active.value ?: return@IconButton
                    scope.launch {
                        client.call { auth ->
                            sendText(auth, dlg["id"].toString().toDouble().toLong(), mapOf("text" to text.value, "reply_to_id" to null))
                        }
                        text.value = ""
                        loadMsgs(client, dlg["id"].toString().toDouble().toLong(), messages)
                    }
                }) { Icon(Icons.Default.Send, null) }
            }
        }
    }
}

/**
 * Загрузить хвост истории и пометить прочитанным.
 */
private suspend fun loadMsgs(client: TokenClient, id: Long, messages: MutableList<Map<String, Any>>) {
    val data = client.call { auth -> messages(auth, id) }
    @Suppress("UNCHECKED_CAST")
    val list = data["messages"] as List<Map<String, Any>>
    messages.clear()
    messages.addAll(list)
    client.call { auth -> read(auth, id, emptyMap()) }
}
