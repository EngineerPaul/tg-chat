package ru.daystream.calc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import ru.daystream.calc.data.TokenClient
import ru.daystream.calc.media.MediaVault

/**
 * Профиль собеседника: фото, логин, ник, вкладки ссылок / фото / файлов с сервера.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PeerProfilePane(
    dialogId: Long,
    peer: Map<*, *>,
    client: TokenClient,
    vault: MediaVault,
    readyTick: MutableState<Int>,
    backEnabled: Boolean,
    onPeer: (Map<String, Any>) -> Unit,
    onBack: () -> Unit,
    onOpenMedia: (List<ViewerItem>, Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val tab = remember(dialogId) { mutableStateOf(0) }
    val nickDraft = remember(dialogId) { mutableStateOf(peer["nick"]?.toString().orEmpty()) }
    val links = remember(dialogId) { mutableStateListOf<Map<String, Any>>() }
    val media = remember(dialogId) { mutableStateListOf<Map<String, Any>>() }
    val files = remember(dialogId) { mutableStateListOf<Map<String, Any>>() }
    val linksMore = remember(dialogId) { mutableStateOf(true) }
    val mediaMore = remember(dialogId) { mutableStateOf(true) }
    val filesMore = remember(dialogId) { mutableStateOf(true) }
    val linksLoad = remember(dialogId) { mutableStateOf(false) }
    val mediaLoad = remember(dialogId) { mutableStateOf(false) }
    val filesLoad = remember(dialogId) { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val login = peer["login"]?.toString().orEmpty()

    fun persistNick(value: String) {
        scope.launch {
            val saved = peer["nick"]?.toString().orEmpty()
            if (value.trim() == saved.trim()) return@launch
            try {
                val user = client.call { auth -> alias(auth, dialogId, mapOf("nick" to value.trim())) }
                onPeer(user)
            } catch (_: Exception) { }
        }
    }

    fun goBack() {
        persistNick(nickDraft.value)
        onBack()
    }

    BackHandler(enabled = backEnabled, onBack = { goBack() })

    LaunchedEffect(nickDraft.value) {
        delay(700)
        persistNick(nickDraft.value)
    }

    suspend fun load(kind: String) {
        val (list, more, busy) = when (kind) {
            "links" -> Triple(links, linksMore, linksLoad)
            "files" -> Triple(files, filesMore, filesLoad)
            else -> Triple(media, mediaMore, mediaLoad)
        }
        if (busy.value || !more.value) return
        busy.value = true
        try {
            val before = if (list.isEmpty()) {
                null
            } else if (kind == "links") {
                list.last()["message_id"].asLong()
            } else {
                list.last()["id"].asLong()
            }
            val page = client.call { auth -> shared(auth, dialogId, kind, before, 40) }
            val rows = asMapList(page["items"])
            list.addAll(rows)
            more.value = page["has_more"] == true
            if (kind != "links") {
                vault.ensureAttachments(rows) { readyTick.value += 1 }
            }
        } catch (_: Exception) {
            more.value = false
        } finally {
            busy.value = false
        }
    }

    LaunchedEffect(tab.value) {
        when (tab.value) {
            0 -> if (links.isEmpty()) load("links")
            1 -> if (media.isEmpty()) load("media")
            2 -> if (files.isEmpty()) load("files")
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Профиль") },
            navigationIcon = {
                IconButton(onClick = { goBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
            },
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircleAvatar(login = login, url = peer["avatar_url"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }, size = 96.dp, textSize = 36.sp)
            Text(login, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(
                value = nickDraft.value,
                onValueChange = { if (it.length <= 60) nickDraft.value = it },
                label = { Text("Ник") },
                placeholder = { Text("пусто — логин") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = readableFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
        }
        TabRow(selectedTabIndex = tab.value) {
            Tab(selected = tab.value == 0, onClick = { tab.value = 0 }, text = { Text("Ссылки") })
            Tab(selected = tab.value == 1, onClick = { tab.value = 1 }, text = { Text("Фото") })
            Tab(selected = tab.value == 2, onClick = { tab.value = 2 }, text = { Text("Файлы") })
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab.value) {
                0 -> ProfileLinks(
                    rows = links,
                    loading = linksLoad.value || (links.isEmpty() && linksMore.value),
                    onNearEnd = { load("links") },
                    onOpen = { href ->
                        if (href.isNotBlank()) {
                            try {
                                uri.openUri(href)
                            } catch (_: Exception) { }
                        }
                    },
                )
                1 -> ProfileMedia(
                    rows = media,
                    vault = vault,
                    readyTick = readyTick.value,
                    loading = mediaLoad.value || (media.isEmpty() && mediaMore.value),
                    onNearEnd = { load("media") },
                    onOpen = onOpenMedia,
                )
                else -> ProfileFiles(
                    rows = files,
                    vault = vault,
                    readyTick = readyTick.value,
                    loading = filesLoad.value || (files.isEmpty() && filesMore.value),
                    onNearEnd = { load("files") },
                )
            }
        }
    }
}

@Composable
private fun ProfileLinks(
    rows: List<Map<String, Any>>,
    loading: Boolean,
    onNearEnd: suspend () -> Unit,
    onOpen: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { idx -> if (idx >= rows.size - 4 && rows.isNotEmpty()) onNearEnd() }
    }
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (loading) "Загрузка…" else "Нет ссылок", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(rows, key = { i, row -> "${row["message_id"]}-$i" }) { _, row ->
            val href = row.str("href")
            Text(
                row.str("text").ifBlank { href },
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().clickable { onOpen(href) }.padding(horizontal = 16.dp, vertical = 12.dp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileMedia(
    rows: List<Map<String, Any>>,
    vault: MediaVault,
    readyTick: Int,
    loading: Boolean,
    onNearEnd: suspend () -> Unit,
    onOpen: (List<ViewerItem>, Int) -> Unit,
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { idx -> if (idx >= rows.size - 6 && rows.isNotEmpty()) onNearEnd() }
    }
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (loading) "Загрузка…" else "Нет фото", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val viewerItems = rows.mapNotNull { attToViewer(it) }
    LazyVerticalGrid(columns = GridCells.Fixed(3), state = gridState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(rows, key = { _, att -> att["id"].asLong() ?: att.hashCode().toLong() }) { index, att ->
            val item = attToViewer(att) ?: return@itemsIndexed
            HiddenThumb(
                vault = vault,
                item = item,
                readyTick = readyTick,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(0.5.dp),
                onClick = { onOpen(viewerItems, index.coerceIn(0, viewerItems.lastIndex)) },
                onLong = {},
            )
        }
    }
}

@Composable
private fun ProfileFiles(
    rows: List<Map<String, Any>>,
    vault: MediaVault,
    readyTick: Int,
    loading: Boolean,
    onNearEnd: suspend () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { idx -> if (idx >= rows.size - 4 && rows.isNotEmpty()) onNearEnd() }
    }
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (loading) "Загрузка…" else "Нет файлов", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(rows, key = { _, att -> att["id"].asLong() ?: att.hashCode().toLong() }) { _, att ->
            val item = attToViewer(att) ?: return@itemsIndexed
            Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                FileRow(vault, item, readyTick, onLong = {})
            }
        }
    }
}

private fun attToViewer(att: Map<String, Any>): ViewerItem? {
    val id = att["id"].asLong()?.toString() ?: return null
    return ViewerItem(
        id = id,
        previewId = att.str("preview_url").takeIf { it.isNotBlank() }?.let { MediaVault.previewKey(id) },
        kind = att.str("kind").ifBlank { "file" },
        name = att.str("orig_name"),
    )
}
