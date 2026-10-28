package ru.daystream.calc.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import ru.daystream.calc.HiddenLock
import ru.daystream.calc.data.SessionStore
import ru.daystream.calc.data.TokenClient
import ru.daystream.calc.data.UiState
import ru.daystream.calc.media.AvatarCache
import ru.daystream.calc.media.MediaVault
import ru.daystream.calc.media.PhotoCompressor
import ru.daystream.calc.net.ReadBody
import ru.daystream.calc.net.TextSend
import ru.daystream.calc.net.openWs
import java.io.ByteArrayOutputStream
import java.text.BreakIterator
import java.util.Locale
import java.util.zip.GZIPOutputStream

/**
 * Скрытый чат: список и переписка.
 *
 * @param store сессия
 * @param themeMode текущая тема
 * @param onThemeChange сохранить тему
 * @param onArrowShort короткое нажатие стрелки
 * @param onLogout пункт «Выйти»
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatRoot(
    store: SessionStore,
    themeMode: MutableState<String>,
    onThemeChange: (String) -> Unit,
    onArrowShort: () -> Unit,
    onLogout: () -> Unit,
) {
    val client = remember { TokenClient(store) }
    val dialogs = remember { mutableStateListOf<Map<String, Any>>() }
    val unreadCounts = remember { mutableStateMapOf<Long, Int>() }
    val messages = remember { mutableStateListOf<Map<String, Any>>() }
    val active = remember { mutableStateOf<Map<String, Any>?>(null) }
    val listMode = remember { mutableStateOf(true) }
    val text = remember { mutableStateOf("") }
    val search = remember { mutableStateOf("") }
    val settings = remember { mutableStateOf(false) }
    val profile = remember { mutableStateOf(false) }
    val replyTo = remember { mutableStateOf<Map<String, Any>?>(null) }
    val menuFor = remember { mutableStateOf<Map<String, Any>?>(null) }
    val editFor = remember { mutableStateOf<Map<String, Any>?>(null) }
    val editText = remember { mutableStateOf("") }
    val typing = remember { mutableStateOf(false) }
    val hasMore = remember { mutableStateOf(false) }
    val loadingOlder = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val meId = remember { mutableStateOf<Long?>(null) }
    val meLogin = remember { mutableStateOf("") }
    val meAvatarUrl = remember { mutableStateOf<String?>(null) }
    val cropBmp = remember { mutableStateOf<Bitmap?>(null) }
    val avatarNote = remember { mutableStateOf("") }
    val avatarBusy = remember { mutableStateOf(false) }
    val socketRef = remember { mutableStateOf<WebSocket?>(null) }
    val listState = rememberLazyListState()
    val dark = when (themeMode.value) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val chatBg = if (dark) ChatBgDark else ChatBgLight
    val vault = remember { MediaVault(context) }
    val readyTick = remember { mutableStateOf(0) }
    val viewer = remember { mutableStateOf<Pair<List<ViewerItem>, Int>?>(null) }
    val stickBottom = remember { mutableStateOf(true) }
    val sendError = remember { mutableStateOf("") }
    val bootDone = remember { mutableStateOf(false) }
    val highlightId = remember { mutableStateOf<Long?>(null) }

    val photos = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val dlg = active.value ?: return@rememberLauncherForActivityResult
        val dialogId = dlg["id"].asLong() ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val local = mutableListOf<ByteArray>()
                val parts = uris.take(10).mapIndexed { i, uri ->
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@mapIndexed null
                    val jpeg = PhotoCompressor.compress(bytes)
                    local.add(jpeg)
                    MultipartBody.Part.createFormData(
                        "files",
                        "p$i.jpg",
                        jpeg.toRequestBody("image/jpeg".toMediaType()),
                    )
                }.filterNotNull()
                if (parts.isNotEmpty()) {
                    val sent = client.call { auth ->
                        sendMedia(auth, dialogId, parts, text.value.toRequestBody(), "1".toRequestBody())
                    }
                    text.value = ""
                    vault.ingestOutgoing(sent, local)
                    upsertMessage(messages, sent)
                    readyTick.value = readyTick.value + 1
                }
            } catch (_: Exception) { }
        }
    }
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        avatarNote.value = ""
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { PhotoCompressor.decodeForCrop(context, uri) }
            if (bmp == null) {
                avatarNote.value = "Не удалось открыть фото"
                return@launch
            }
            cropBmp.value = bmp
        }
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val dlg = active.value ?: return@rememberLauncherForActivityResult
        val dialogId = dlg["id"].asLong() ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                if (bytes.size > 50 * 1024 * 1024) return@launch
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file.bin"
                val (payload, gzip) = maybeGzip(bytes)
                val part = MultipartBody.Part.createFormData(
                    "file",
                    name,
                    payload.toRequestBody("application/octet-stream".toMediaType()),
                )
                val sent = client.call { auth ->
                    sendFile(
                        auth,
                        dialogId,
                        part,
                        text.value.toRequestBody(),
                        (if (gzip) "1" else "0").toRequestBody(),
                        bytes.size.toString().toRequestBody(),
                    )
                }
                text.value = ""
                vault.ingestOutgoing(sent, listOf(bytes))
                upsertMessage(messages, sent)
                readyTick.value = readyTick.value + 1
            } catch (_: Exception) { }
        }
    }

    /**
     * Отправить текст: сразу показать пузырь, потом подтвердить сервером.
     */
    fun sendCurrent() {
        val dlg = active.value ?: return
        val bodyText = text.value.trim()
        if (bodyText.isBlank()) return
        val dialogId = dlg["id"].asLong() ?: return
        val replyId = replyTo.value?.get("id").asLong()
        val localId = -System.currentTimeMillis()
        val pending = mutableMapOf<String, Any>(
            "id" to localId,
            "dialog_id" to dialogId,
            "author_id" to (meId.value ?: 0L),
            "type" to "text",
            "text" to bodyText,
            "ticks" to "sent",
            "created_at" to java.time.Instant.now().toString(),
        )
        replyTo.value?.let { src ->
            pending["reply_to"] = mutableMapOf<String, Any>(
                "id" to (src["id"].asLong() ?: 0L),
                "text" to previewText(src),
                "type" to src.str("type").ifBlank { "text" },
            )
        }
        messages.add(pending)
        text.value = ""
        replyTo.value = null
        sendError.value = ""
        stickBottom.value = true
        scope.launch {
            try {
                scrollToLatest(listState)
            } catch (_: Exception) { }
            try {
                val raw = withContext(Dispatchers.IO) {
                    client.call { auth -> sendText(auth, dialogId, TextSend(bodyText, replyId)) }.string()
                }
                val sent = jsonToMap(org.json.JSONObject(raw))
                messages.removeAll { it["id"].asLong() == localId }
                upsertMessage(messages, sent)
                stickBottom.value = true
                try {
                    scrollToLatest(listState)
                } catch (_: Exception) { }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is retrofit2.HttpException || e is java.io.IOException) {
                    messages.removeAll { it["id"].asLong() == localId }
                    text.value = bodyText
                    sendError.value = "Не отправлено"
                }
            }
        }
    }

    /**
     * Подставить список диалогов и живые счётчики непрочитанных.
     */
    fun applyDialogs(list: List<Map<String, Any>>) {
        dialogs.clear()
        dialogs.addAll(list)
        list.forEach { row ->
            val id = row["id"].asLong() ?: return@forEach
            unreadCounts[id] = row["unread"].asLong()?.toInt() ?: 0
            val peer = row.map("peer") ?: return@forEach
            val uid = peer["id"].asLong() ?: return@forEach
            val url = peer.str("avatar_url").ifBlank { null }
            scope.launch(Dispatchers.IO) { AvatarCache.ensure(uid, url) }
        }
    }

    /**
     * Записать бейдж; отдельная карта, чтобы Compose точно перерисовал строку.
     */
    fun setUnreadCount(dialogId: Long, unread: Int) {
        unreadCounts[dialogId] = unread
        val idx = dialogs.indexOfFirst { it["id"].asLong() == dialogId }
        if (idx < 0) return
        dialogs[idx] = dialogs[idx].toMutableMap().apply { put("unread", unread) }
    }

    /**
     * Прокрутить к оригиналу ответа, подгрузив старше при необходимости.
     */
    fun jumpToReply(id: Long) {
        val dialogId = active.value?.get("id").asLong() ?: return
        stickBottom.value = false
        scope.launch {
            var guard = 0
            while (messages.none { it["id"].asLong() == id } && hasMore.value && guard < 20) {
                guard += 1
                val oldest = messages.minOfOrNull { it["id"].asLong() ?: Long.MAX_VALUE } ?: break
                try {
                    loadOlder(client, dialogId, oldest, messages, hasMore, vault) { readyTick.value = readyTick.value + 1 }
                } catch (_: Exception) {
                    break
                }
            }
            val reversed = messages.sortedBy { it["id"].asLong() ?: 0L }.asReversed()
            val index = reversed.indexOfFirst { it["id"].asLong() == id }
            if (index < 0) return@launch
            highlightId.value = id
            try {
                listState.animateScrollToItem(index)
            } catch (_: Exception) {
                try {
                    listState.scrollToItem(index)
                } catch (_: Exception) { }
            }
            delay(5000)
            if (highlightId.value == id) highlightId.value = null
        }
    }

    LaunchedEffect(Unit) {
        try {
            val profileMe = client.call { auth -> me(auth) }
            meId.value = profileMe["id"].asLong()
            meLogin.value = profileMe.str("login")
            meAvatarUrl.value = profileMe.str("avatar_url").ifBlank { null }
            profileMe["id"].asLong()?.let { id ->
                AvatarCache.ensure(id, meAvatarUrl.value)
            }
            val list = asMapList(client.call { auth -> dialogs(auth) })
            applyDialogs(list)
            if (list.size == 1) {
                val onlyId = list[0]["id"].asLong()
                if (onlyId != null) {
                    active.value = list[0]
                    listMode.value = false
                    loadMsgs(client, onlyId, messages, dialogs, unreadCounts, hasMore, vault) { readyTick.value = readyTick.value + 1 }
                }
            }
        } catch (_: Exception) { }
        bootDone.value = true
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            if (!bootDone.value || listMode.value) return@LifecycleEventObserver
            val id = active.value?.get("id").asLong() ?: return@LifecycleEventObserver
            scope.launch {
                try {
                    loadMsgs(client, id, messages, dialogs, unreadCounts, hasMore, vault, reset = false) {
                        readyTick.value = readyTick.value + 1
                    }
                } catch (_: Exception) { }
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(listMode.value, bootDone.value) {
        if (!bootDone.value || !listMode.value) return@LaunchedEffect
        val leaving = active.value?.get("id").asLong()
        if (leaving != null) {
            try {
                client.call { auth -> read(auth, leaving, ReadBody()) }
            } catch (_: Exception) { }
            setUnreadCount(leaving, 0)
        }
        try {
            val list = asMapList(client.call { auth -> dialogs(auth) })
            applyDialogs(list)
            if (leaving != null) setUnreadCount(leaving, 0)
        } catch (_: Exception) { }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.get("id"), listMode.value) {
        if (listMode.value) return@LaunchedEffect
        try {
            vault.ensureMessages(messages.toList()) { readyTick.value = readyTick.value + 1 }
        } catch (_: Exception) { }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                stickBottom.value = index <= 1
            }
    }

    LaunchedEffect(messages.size, messages.maxOfOrNull { it["id"].asLong() ?: 0L }, listMode.value) {
        if (listMode.value || messages.isEmpty() || !stickBottom.value) return@LaunchedEffect
        scrollToLatest(listState)
    }

    LaunchedEffect(listState, listMode.value) {
        snapshotFlow {
            val info = listState.layoutInfo
            val top = info.visibleItemsInfo.maxOfOrNull { it.index } ?: 0
            Triple(top, info.totalItemsCount, hasMore.value)
        }
            .distinctUntilChanged()
            .collect { (top, total, more) ->
                if (!listMode.value && more && !loadingOlder.value && total > 0 && top >= total - 4) {
                    val oldest = messages.minOfOrNull { it["id"].asLong() ?: Long.MAX_VALUE } ?: return@collect
                    loadingOlder.value = true
                    try {
                        loadOlder(client, active.value?.get("id").asLong() ?: return@collect, oldest, messages, hasMore, vault) { readyTick.value = readyTick.value + 1 }
                    } catch (_: Exception) {
                    } finally {
                        loadingOlder.value = false
                    }
                }
            }
    }

    DisposableEffect(Unit) {
        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, payload: String) {
                val data = try { JSONObject(payload) } catch (_: Exception) { return }
                val event = data.optString("event")
                scope.launch(Dispatchers.Main) {
                    when (event) {
                        "message" -> {
                            val msg = jsonToMap(data.optJSONObject("message") ?: return@launch)
                            val author = msg["author_id"].asLong()
                            val dialogId = msg["dialog_id"].asLong()
                            val activeId = active.value?.get("id").asLong()
                            val looking = dialogId != null && dialogId == activeId && !listMode.value && UiState.chatOpen
                            val mine = author != null && author == meId.value
                            if (dialogId != null && dialogId == activeId && !listMode.value) {
                                upsertMessage(messages, msg)
                            }
                            if (dialogId != null && looking) {
                                if (!mine) {
                                    val upTo = msg["id"].asLong()
                                    if (upTo != null) {
                                        try {
                                            client.call { auth ->
                                                read(auth, dialogId, ReadBody(up_to_id = upTo))
                                            }
                                        } catch (_: Exception) { }
                                    }
                                }
                            }
                            refreshDialogPreview(dialogs, msg)
                            if (dialogId != null && !mine) {
                                if (looking) setUnreadCount(dialogId, 0)
                                else setUnreadCount(dialogId, (unreadCounts[dialogId] ?: 0) + 1)
                            }
                        }
                        "caught_up" -> {
                            val dialogId = data.optLong("dialog_id")
                            if (dialogId != 0L) setUnreadCount(dialogId, 0)
                        }
                        "delivered", "read" -> {
                            val ids = mutableListOf<Long>()
                            if (data.has("ids")) {
                                val arr = data.getJSONArray("ids")
                                for (i in 0 until arr.length()) ids.add(arr.optLong(i))
                            } else if (data.has("id")) {
                                ids.add(data.optLong("id"))
                            }
                            val tick = if (event == "read") "read" else "delivered"
                            for (i in messages.indices) {
                                val id = messages[i]["id"].asLong() ?: continue
                                if (id !in ids) continue
                                if (tick == "delivered" && messages[i].str("ticks") == "read") continue
                                messages[i] = messages[i].toMutableMap().apply { put("ticks", tick) }
                            }
                        }
                        "typing" -> {
                            val dialogId = data.optLong("dialog_id")
                            if (dialogId == active.value?.get("id").asLong() && data.optLong("user_id") != meId.value) {
                                typing.value = true
                            }
                        }
                        "presence" -> {
                            val uid = data.optLong("user_id")
                            val online = data.optBoolean("online")
                            val seen = data.optString("last_seen")
                            updatePeerPresence(dialogs, active, uid, online, seen)
                        }
                    }
                }
            }
        }
        val socket = openWs(store.access, listener)
        socketRef.value = socket
        onDispose {
            socketRef.value = null
            socket.cancel()
        }
    }

    LaunchedEffect(typing.value) {
        if (typing.value) {
            kotlinx.coroutines.delay(3500)
            typing.value = false
        }
    }

    if (settings.value) {
        val crop = cropBmp.value
        if (crop != null) {
            AvatarCropPane(
                bitmap = crop,
                onCancel = { cropBmp.value = null },
                onCrop = { square ->
                    cropBmp.value = null
                    avatarBusy.value = true
                    avatarNote.value = ""
                    scope.launch {
                        try {
                            val jpeg = withContext(Dispatchers.IO) { PhotoCompressor.compressSquare(square) }
                            val part = MultipartBody.Part.createFormData(
                                "file",
                                "avatar.jpg",
                                jpeg.toRequestBody("image/jpeg".toMediaType()),
                            )
                            val user = client.call { auth -> uploadAvatar(auth, part) }
                            val url = user.str("avatar_url").ifBlank { null }
                            meAvatarUrl.value = url
                            val uid = user["id"].asLong() ?: meId.value
                            if (uid != null && url != null) AvatarCache.putLocal(uid, url, jpeg)
                            avatarNote.value = "Фото сохранено"
                        } catch (_: Exception) {
                            avatarNote.value = "Не удалось сохранить фото"
                        } finally {
                            avatarBusy.value = false
                        }
                    }
                },
            )
            return
        }
        SettingsPane(
            themeMode = themeMode.value,
            login = meLogin.value,
            userId = meId.value,
            avatarUrl = meAvatarUrl.value,
            avatarNote = avatarNote.value,
            avatarBusy = avatarBusy.value,
            onChangePhoto = { pickAvatar.launch("image/*") },
            onTheme = onThemeChange,
            onLogout = {
                scope.launch {
                    try {
                        client.call { auth -> logout(auth, mapOf("refresh_token" to store.refresh)) }
                    } catch (_: Exception) { }
                    onLogout()
                }
            },
            onBack = { settings.value = false },
            onChangePassword = { password, done ->
                scope.launch {
                    try {
                        val res = client.call { auth ->
                            changePassword(
                                auth,
                                mapOf("password" to password, "refresh_token" to store.refresh),
                            )
                        }
                        val access = res["access_token"] as? String
                        val refresh = res["refresh_token"] as? String
                        if (access.isNullOrBlank() || refresh.isNullOrBlank()) {
                            done("Не удалось сменить пароль")
                            return@launch
                        }
                        store.access = access
                        store.refresh = refresh
                        store.saveKeyHash(password)
                        done(null)
                    } catch (_: Exception) {
                        done("Не удалось сменить пароль")
                    }
                }
            },
        )
        return
    }

    if (profile.value) {
        val row = active.value
        val peerMap = row?.map("peer")
        val dialogId = row?.get("id").asLong()
        if (row != null && peerMap != null && dialogId != null) {
            PeerProfilePane(
                dialogId = dialogId,
                peer = peerMap,
                client = client,
                vault = vault,
                readyTick = readyTick,
                backEnabled = viewer.value == null,
                onPeer = { user ->
                    active.value = active.value?.toMutableMap()?.apply { put("peer", user) }
                    val idx = dialogs.indexOfFirst { it["id"].asLong() == dialogId }
                    if (idx >= 0) dialogs[idx] = dialogs[idx].toMutableMap().apply { put("peer", user) }
                },
                onBack = { profile.value = false },
                onOpenMedia = { items, start -> viewer.value = items to start },
            )
            viewer.value?.let { (items, start) ->
                MediaViewer(items, start, vault, readyTick.value) { viewer.value = null }
            }
            return
        }
    }

    Column(Modifier.fillMaxSize().background(if (listMode.value) MaterialTheme.colorScheme.background else chatBg).imePadding()) {
        val peer = active.value?.map("peer")
        TopAppBar(
            title = {
                if (!listMode.value && peer != null) {
                    Column(
                        Modifier.clickable { profile.value = true },
                    ) {
                        Text(peerDisplay(peer), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (typing.value) "печатает…"
                            else lastSeenText(peer["last_seen"]?.toString(), peer["online"] == true),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text("Чаты")
                }
            },
            navigationIcon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .combinedClickable(
                            onClick = onArrowShort,
                            onLongClick = {
                                if (!listMode.value) listMode.value = true
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
            },
            actions = {
                IconButton(onClick = { settings.value = true }) { Icon(Icons.Default.MoreVert, null) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        if (listMode.value) {
            OutlinedTextField(
                search.value,
                { search.value = it },
                label = { Text("Поиск по логину") },
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                singleLine = true,
                colors = readableFieldColors(),
                trailingIcon = {
                    TextButton(onClick = {
                        scope.launch {
                            try {
                                val d = client.call { auth -> openDialog(auth, mapOf("login" to search.value.trim())) }
                                val row = d.toMutableMap()
                                dialogs.removeAll { it["id"].asLong() == row["id"].asLong() }
                                dialogs.add(0, row)
                                active.value = row
                                listMode.value = false
                                loadMsgs(client, row["id"].asLong() ?: return@launch, messages, dialogs, unreadCounts, hasMore, vault) { readyTick.value = readyTick.value + 1 }
                            } catch (_: Exception) { }
                        }
                    }) { Text("OK") }
                },
            )
            LazyColumn {
                itemsIndexed(dialogs, key = { _, d -> d["id"].asLong() ?: d.hashCode().toLong() }) { _, d ->
                    val dialogId = d["id"].asLong()
                    val unread = dialogId?.let { unreadCounts[it] } ?: 0
                    DialogRow(d, unread) {
                        active.value = d
                        listMode.value = false
                        if (dialogId != null) unreadCounts[dialogId] = 0
                        scope.launch {
                            try {
                                loadMsgs(client, dialogId ?: return@launch, messages, dialogs, unreadCounts, hasMore, vault) { readyTick.value = readyTick.value + 1 }
                            } catch (_: Exception) { }
                        }
                    }
                }
            }
        } else {
            val chronological = messages.sortedBy { it["id"].asLong() ?: 0L }
            val newestFirst = chronological.asReversed()
            val farFromNow = remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true,
                verticalArrangement = Arrangement.Top,
            ) {
                itemsIndexed(
                    items = newestFirst,
                    key = { _, m -> m["id"].asLong() ?: m.hashCode().toLong() },
                ) { index, m ->
                    val older = newestFirst.getOrNull(index + 1)
                    val showDay = older == null || !sameMessageDay(m["created_at"]?.toString(), older["created_at"]?.toString())
                    Column(Modifier.fillMaxWidth()) {
                        if (showDay) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    dayChipLabel(m["created_at"]?.toString()),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x66000000))
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                        MessageBubble(
                            m = m,
                            mine = m["author_id"].asLong() != null && m["author_id"].asLong() == meId.value,
                            dark = dark,
                            vault = vault,
                            readyTick = readyTick.value,
                            highlighted = highlightId.value == m["id"].asLong(),
                            onLong = { menuFor.value = m },
                            onReplyClick = { id -> jumpToReply(id) },
                            onOpenMedia = { items, start -> viewer.value = items to start },
                        )
                    }
                }
            }
            if (farFromNow.value) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(48.dp)
                        .alpha(0.7f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            stickBottom.value = true
                            scope.launch {
                                try {
                                    scrollToLatest(listState, 3000)
                                } catch (_: Exception) { }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "К текущим",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            }
            menuFor.value?.let { target ->
                val mine = target["author_id"].asLong() == meId.value
                AlertDialog(
                    onDismissRequest = { menuFor.value = null },
                    title = { Text("Сообщение") },
                    text = { Text(previewText(target), maxLines = 3, overflow = TextOverflow.Ellipsis) },
                    confirmButton = {
                        TextButton(onClick = {
                            replyTo.value = target
                            menuFor.value = null
                        }) { Text("Ответить") }
                    },
                    dismissButton = {
                        Row {
                            if (mine && (target.str("type").ifBlank { "text" } == "text" || target.str("text").isNotBlank())) {
                                TextButton(onClick = {
                                    editFor.value = target
                                    editText.value = target.str("text")
                                    menuFor.value = null
                                }) { Text("Изменить") }
                            }
                            TextButton(onClick = {
                                val id = target["id"].asLong()
                                menuFor.value = null
                                if (id != null) {
                                    scope.launch {
                                        try {
                                            client.call { auth -> delete(auth, id) }
                                            messages.removeAll { it["id"].asLong() == id }
                                        } catch (_: Exception) { }
                                    }
                                }
                            }) { Text("Удалить") }
                        }
                    },
                )
            }
            replyTo.value?.let { r ->
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Ответ: ${previewText(r)}", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { replyTo.value = null }) { Text("×") }
                }
            }
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val attachMenu = remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { attachMenu.value = true }) { Icon(Icons.Default.AttachFile, contentDescription = "Вложение") }
                    DropdownMenu(expanded = attachMenu.value, onDismissRequest = { attachMenu.value = false }) {
                        DropdownMenuItem(text = { Text("Галерея") }, onClick = {
                            attachMenu.value = false
                            photos.launch("image/*")
                        })
                        DropdownMenuItem(text = { Text("Файл") }, onClick = {
                            attachMenu.value = false
                            files.launch("*/*")
                        })
                    }
                }
                OutlinedTextField(
                    text.value,
                    {
                        text.value = it
                        if (it.isNotBlank()) sendError.value = ""
                        val id = active.value?.get("id").asLong()
                        if (id != null && it.isNotBlank()) {
                            socketRef.value?.send(JSONObject().put("event", "typing").put("dialog_id", id).toString())
                        }
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Сообщение") },
                    colors = readableFieldColors(),
                    maxLines = 4,
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default,
                    ),
                )
                IconButton(onClick = { sendCurrent() }) { Icon(Icons.Default.Send, contentDescription = "Отправить") }
            }
            if (sendError.value.isNotBlank()) {
                Text(sendError.value, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
            }
        }
    }

    viewer.value?.let { (items, start) ->
        MediaViewer(items, start, vault, readyTick.value) { viewer.value = null }
    }
    editFor.value?.let { target ->
        AlertDialog(
            onDismissRequest = { editFor.value = null },
            title = { Text("Изменить") },
            text = {
                OutlinedTextField(editText.value, { editText.value = it }, colors = readableFieldColors())
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = target["id"].asLong()
                    val next = editText.value.trim()
                    editFor.value = null
                    if (id != null && next.isNotBlank()) {
                        scope.launch {
                            try {
                                val updated = client.call { auth -> edit(auth, id, mapOf("text" to next)) }
                                upsertMessage(messages, updated)
                            } catch (_: Exception) { }
                        }
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editFor.value = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun SettingsPane(
    themeMode: String,
    login: String,
    userId: Long?,
    avatarUrl: String?,
    avatarNote: String,
    avatarBusy: Boolean,
    onChangePhoto: () -> Unit,
    onTheme: (String) -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    onChangePassword: (String, (String?) -> Unit) -> Unit,
) {
    val lockOn = remember { mutableStateOf(HiddenLock.enabled) }
    val minutesText = remember { mutableStateOf(HiddenLock.minutes.toString()) }
    val newPass = remember { mutableStateOf("") }
    val passNote = remember { mutableStateOf("") }
    val passBusy = remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Настройки", style = MaterialTheme.typography.titleLarge)
        Text("Фото", modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Box(Modifier.clickable(enabled = !avatarBusy, onClick = onChangePhoto)) {
                CircleAvatar(login = login, url = avatarUrl, userId = userId, size = 72.dp, textSize = 28.sp)
            }
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(login.ifBlank { "Вы" }, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onChangePhoto, enabled = !avatarBusy) {
                    Text(if (avatarBusy) "Сохранение…" else "Выбрать фото")
                }
            }
        }
        if (avatarNote.isNotEmpty()) {
            Text(
                avatarNote,
                color = if (avatarNote == "Фото сохранено") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text("Тема", modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
        Row {
            listOf("system" to "Система", "light" to "Светлая", "dark" to "Тёмная").forEach { (id, label) ->
                TextButton(onClick = { onTheme(id) }) {
                    Text(if (themeMode == id) "• $label" else label)
                }
            }
        }
        Text("Возврат без ключа", modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = minutesText.value,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }.take(3)
                    minutesText.value = digits
                    digits.toIntOrNull()?.let { HiddenLock.minutes = it }
                },
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                enabled = lockOn.value,
                singleLine = true,
                suffix = { Text("мин") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Text("Вкл", modifier = Modifier.padding(end = 8.dp))
            Switch(
                checked = lockOn.value,
                onCheckedChange = {
                    lockOn.value = it
                    HiddenLock.enabled = it
                },
            )
        }
        Text("Пароль", modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = newPass.value,
            onValueChange = { newPass.value = it; passNote.value = "" },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            label = { Text("Новый пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !passBusy.value,
        )
        if (passNote.value.isNotEmpty()) {
            Text(
                passNote.value,
                color = if (passNote.value == "Пароль изменён") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Button(
            onClick = {
                val value = newPass.value
                if (!value.matches(Regex("^[A-Za-zА-Яа-яЁё0-9 ]{3,30}$"))) {
                    passNote.value = "Пароль: 3–30 букв, цифр и пробелов, без знаков"
                    return@Button
                }
                passBusy.value = true
                onChangePassword(value) { err ->
                    passBusy.value = false
                    if (err == null) {
                        newPass.value = ""
                        passNote.value = "Пароль изменён"
                    } else {
                        passNote.value = err
                    }
                }
            },
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            enabled = !passBusy.value,
        ) { Text("Сменить пароль") }
        Button(onClick = onLogout, modifier = Modifier.padding(top = 16.dp)) { Text("Выйти") }
        TextButton(onClick = onBack) { Text("Назад") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialogRow(d: Map<String, Any>, unread: Int, onOpen: () -> Unit) {
    val peer = d.map("peer")
    val name = peerDisplay(peer)
    val login = peer?.get("login")?.toString().orEmpty()
    val online = peer?.get("online") == true
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp)) {
            CircleAvatar(
                login = login.ifBlank { name },
                url = peer?.get("avatar_url")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
                userId = peer?.get("id").asLong(),
                size = 48.dp,
                textSize = 18.sp,
            )
            if (online) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50)),
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(shortTime(d["last_at"]?.toString()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (d["typing"] == true) "печатает…" else d.str("last_message"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (unread > 0) {
            Box(Modifier.padding(start = 8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(unread.toString(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    m: Map<String, Any>,
    mine: Boolean,
    dark: Boolean,
    vault: MediaVault,
    readyTick: Int,
    highlighted: Boolean,
    onLong: () -> Unit,
    onReplyClick: (Long) -> Unit,
    onOpenMedia: (List<ViewerItem>, Int) -> Unit,
) {
    val ticks = m.str("ticks")
    val ticksColor = if (ticks == "read") TickRead else MaterialTheme.colorScheme.onSurfaceVariant
    val bg = when {
        mine && dark -> BubbleOutDark
        mine -> BubbleOutLight
        dark -> BubbleInDark
        else -> BubbleInLight
    }
    val reply = m.map("reply_to")
    val atts = asMapList(m["attachments"])
    val body = m.str("text").trim()
    val emojiCount = if (atts.isEmpty() && reply == null) emojiOnlyCount(body) else 0
    val emojiSize = when {
        emojiCount == 1 -> 60.sp
        emojiCount > 1 -> 40.sp
        else -> 16.sp
    }
    val emojiLine = when {
        emojiCount == 1 -> 66.sp
        emojiCount > 1 -> 44.sp
        else -> 22.sp
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp)) {
        val bubbleMax = maxWidth * 0.91f
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        ) {
        Column(
            Modifier
                .widthIn(max = bubbleMax)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .then(if (highlighted) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)) else Modifier)
                .combinedClickable(onClick = {}, onLongClick = onLong)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            if (reply != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (dark) Color(0x33000000) else Color(0x1A000000))
                        .clickable { reply["id"].asLong()?.let(onReplyClick) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(2.dp).height(28.dp).background(MaterialTheme.colorScheme.primary))
                    Text(
                        previewText(reply),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            if (atts.isNotEmpty()) {
                AttachmentBlock(atts, vault, readyTick, onLong) { items, start -> onOpenMedia(items, start) }
            }
            if (body.isNotBlank()) {
                LinkedMessageText(
                    body = body,
                    color = if (dark) Color(0xFFF5F5F5) else Color(0xFF111111),
                    fontSize = emojiSize,
                    lineHeight = emojiLine,
                    onLong = onLong,
                )
            } else if (atts.isEmpty()) {
                val fallback = previewText(m).trim()
                if (fallback.isNotBlank()) Text(fallback, color = if (dark) Color(0xFFF5F5F5) else Color(0xFF111111))
            }
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                if (m["edited_at"] != null && m.str("edited_at").isNotBlank()) {
                    Text("изменено  ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(shortTime(m["created_at"]?.toString()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (mine && ticks != "none" && ticks.isNotBlank()) {
                    Text(
                        if (ticks == "sent") "  ✓" else "  ✓✓",
                        style = MaterialTheme.typography.labelSmall,
                        color = ticksColor,
                    )
                }
            }
        }
        }
    }
}

/**
 * Текст пузыря: ссылки открываются в браузере, долгое нажатие — меню.
 */
@Composable
private fun LinkedMessageText(
    body: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    onLong: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(body, linkColor) { linkifiedText(body, linkColor) }
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotated,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        onTextLayout = { layout.value = it },
        modifier = Modifier.pointerInput(annotated) {
            detectTapGestures(
                onLongPress = { onLong() },
                onTap = { pos ->
                    val l = layout.value ?: return@detectTapGestures
                    val off = l.getOffsetForPosition(pos)
                    val href = annotated.getStringAnnotations(UrlTag, off, off).firstOrNull()?.item
                    if (href != null) uriHandler.openUri(href)
                },
            )
        },
    )
}

private fun isEmojiCluster(cluster: String): Boolean {
    if (cluster.isBlank()) return false
    return cluster.any { ch ->
        val type = Character.getType(ch)
        type == Character.OTHER_SYMBOL.toInt() ||
            type == Character.SURROGATE.toInt() ||
            ch == '\u200D' ||
            ch == '\uFE0F' ||
            ch == '\u20E3'
    }
}

/**
 * Сколько эмодзи в тексте, если кроме них ничего нет (пробелы можно). 0 — не только смайлики.
 */
private fun emojiOnlyCount(text: String): Int {
    val t = text.trim()
    if (t.isEmpty()) return 0
    val it = BreakIterator.getCharacterInstance(Locale.ROOT)
    it.setText(t)
    var count = 0
    var start = it.first()
    var end = it.next()
    while (end != BreakIterator.DONE) {
        val cluster = t.substring(start, end)
        if (!cluster.isBlank()) {
            if (!isEmojiCluster(cluster)) return 0
            count += 1
        }
        start = end
        end = it.next()
    }
    return count
}

private fun previewText(m: Map<*, *>): String {
    val kind = m["type"]?.toString() ?: "text"
    val text = m["text"]?.toString().orEmpty()
    return when (kind) {
        "photo" -> text.ifBlank { "Фото" }
        "video" -> text.ifBlank { "Видео" }
        "album" -> text.ifBlank { "Альбом" }
        "file" -> {
            val atts = m["attachments"] as? List<*>
            val name = (atts?.firstOrNull() as? Map<*, *>)?.get("orig_name")?.toString()
            text.ifBlank { name ?: "Файл" }
        }
        else -> text
    }
}

private suspend fun loadMsgs(
    client: TokenClient,
    id: Long,
    messages: MutableList<Map<String, Any>>,
    dialogs: MutableList<Map<String, Any>>,
    unreadCounts: MutableMap<Long, Int>,
    hasMore: MutableState<Boolean>,
    vault: MediaVault,
    reset: Boolean = true,
    onReady: () -> Unit,
) {
    val data = client.call { auth -> messages(auth, id) }
    val list = asMapList(data["messages"])
    if (reset) {
        messages.clear()
        messages.addAll(list)
    } else {
        messages.removeAll { it["dialog_id"].asLong() != id }
        list.forEach { upsertMessage(messages, it) }
    }
    hasMore.value = data["has_more"].asBool() || list.size >= 250
    unreadCounts[id] = 0
    setUnread(dialogs, id, 0)
    try {
        client.call { auth -> read(auth, id, ReadBody()) }
    } catch (_: Exception) { }
    unreadCounts[id] = 0
    setUnread(dialogs, id, 0)
    try {
        vault.ensureMessages(list, onReady)
    } catch (_: Exception) { }
}

private fun setUnread(dialogs: MutableList<Map<String, Any>>, dialogId: Long, unread: Int) {
    val idx = dialogs.indexOfFirst { it["id"].asLong() == dialogId }
    if (idx < 0) return
    dialogs[idx] = dialogs[idx].toMutableMap().apply { put("unread", unread) }
}

private suspend fun loadOlder(
    client: TokenClient,
    dialogId: Long,
    before: Long,
    messages: MutableList<Map<String, Any>>,
    hasMore: MutableState<Boolean>,
    vault: MediaVault,
    onReady: () -> Unit,
) {
    val data = client.call { auth -> messages(auth, dialogId, before = before) }
    val list = asMapList(data["messages"])
    if (list.isEmpty()) {
        hasMore.value = false
        return
    }
    list.forEach { upsertMessage(messages, it) }
    hasMore.value = data["has_more"].asBool() || list.size >= 250
    try {
        vault.ensureMessages(messages.toList(), onReady)
    } catch (_: Exception) { }
}

private fun refreshDialogPreview(dialogs: MutableList<Map<String, Any>>, msg: Map<String, Any>) {
    val dialogId = msg["dialog_id"].asLong() ?: return
    val idx = dialogs.indexOfFirst { it["id"].asLong() == dialogId }
    if (idx < 0) return
    dialogs[idx] = dialogs[idx].toMutableMap().apply {
        put("last_message", previewText(msg))
        put("last_at", msg["created_at"] ?: "")
    }
}

private fun updatePeerPresence(
    dialogs: MutableList<Map<String, Any>>,
    active: MutableState<Map<String, Any>?>,
    userId: Long,
    online: Boolean,
    lastSeen: String,
) {
    fun patch(row: Map<String, Any>): Map<String, Any> {
        val peer = row.map("peer")?.toMutableMap() ?: return row
        if (peer["id"].asLong() != userId) return row
        peer["online"] = online
        if (lastSeen.isNotBlank()) peer["last_seen"] = lastSeen
        return row.toMutableMap().apply { put("peer", peer) }
    }
    for (i in dialogs.indices) dialogs[i] = patch(dialogs[i])
    active.value?.let { active.value = patch(it) }
}

private suspend fun scrollToLatest(state: LazyListState, durationMs: Int = 200) {
    val avg = state.layoutInfo.visibleItemsInfo.maxOfOrNull { it.size } ?: 80
    val px = (state.firstVisibleItemIndex * avg + state.firstVisibleItemScrollOffset).toFloat()
    if (px > 0f) {
        try {
            state.animateScrollBy(-px, tween(durationMs))
        } catch (_: Exception) { }
    }
    if (durationMs < 800) {
        try {
            state.scrollToItem(0)
        } catch (_: Exception) { }
    }
}

private fun maybeGzip(bytes: ByteArray): Pair<ByteArray, Boolean> {
    val out = ByteArrayOutputStream()
    GZIPOutputStream(out).use { it.write(bytes) }
    val gz = out.toByteArray()
    return if (gz.size <= (bytes.size * 0.9).toInt()) gz to true else bytes to false
}
