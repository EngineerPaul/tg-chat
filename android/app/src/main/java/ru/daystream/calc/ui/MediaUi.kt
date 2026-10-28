package ru.daystream.calc.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.daystream.calc.media.MediaVault

data class ViewerItem(
    val id: String,
    val previewId: String?,
    val kind: String,
    val name: String,
    val bytes: Long = 0,
)

/**
 * Сетка вложений в пузыре. Байты только из HiddenStore.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttachmentBlock(
    attachments: List<Map<String, Any>>,
    vault: MediaVault,
    readyTick: Int,
    onLong: () -> Unit,
    onOpen: (List<ViewerItem>, Int) -> Unit,
) {
    val items = attachments.mapNotNull { att ->
        val id = att["id"].asLong()?.toString() ?: return@mapNotNull null
        ViewerItem(
            id = id,
            previewId = att.str("preview_url").takeIf { it.isNotBlank() }?.let { MediaVault.previewKey(id) },
            kind = att.str("kind").ifBlank { "file" },
            name = att.str("orig_name"),
            bytes = att["size"].asLong() ?: 0L,
        )
    }
    if (items.isEmpty()) return
    val media = items.filter { it.kind == "photo" || it.kind == "video" }
    val files = items.filter { it.kind == "file" }
    Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (media.size == 1) {
            HiddenThumb(
                vault = vault,
                item = media[0],
                readyTick = readyTick,
                modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(10.dp)),
                onClick = { onOpen(media, 0) },
                onLong = onLong,
            )
        } else if (media.isNotEmpty()) {
            media.chunked(2).forEachIndexed { row, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    pair.forEachIndexed { col, item ->
                        val index = row * 2 + col
                        HiddenThumb(
                            vault = vault,
                            item = item,
                            readyTick = readyTick,
                            modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)),
                            onClick = { onOpen(media, index) },
                            onLong = onLong,
                        )
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
        files.forEach { item ->
            FileRow(vault, item, readyTick, onLong)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HiddenThumb(
    vault: MediaVault,
    item: ViewerItem,
    readyTick: Int,
    modifier: Modifier,
    onClick: () -> Unit,
    onLong: () -> Unit,
) {
    val bmp = remember(item.id, item.previewId, readyTick) {
        when {
            item.kind == "photo" -> vault.bitmap(item.id)
            item.previewId != null -> vault.bitmap(item.previewId)
            else -> vault.bitmap(item.id)
        }
    }
    Box(
        modifier.background(Color(0x33000000)).combinedClickable(onClick = onClick, onLongClick = onLong),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(bmp.asImageBitmap(), contentDescription = item.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (item.kind == "video") {
            Text("▶", color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileRow(vault: MediaVault, item: ViewerItem, readyTick: Int, onLong: () -> Unit) {
    val scope = rememberCoroutineScope()
    val note = remember { mutableStateOf("") }
    val ready = remember(item.id, readyTick) { vault.store.has(item.id) }
    val meta = remember(item.id, readyTick) { vault.store.meta(item.id) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0x22000000)).padding(8.dp).combinedClickable(
            onClick = {},
            onLongClick = onLong,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.name.ifBlank { "Файл" }, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (ready) formatBytes((meta?.size ?: 0).toLong()) else if (item.bytes > 0) formatBytes(item.bytes) else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (note.value.isNotBlank()) Text(note.value, style = MaterialTheme.typography.labelSmall)
        }
        if (ready) {
            TextButton(onClick = {
                scope.launch {
                    note.value = if (vault.savePublic(item.id)) "сохранено" else "ошибка"
                }
            }) { Text("Сохранить") }
            TextButton(onClick = {
                val tmp = vault.openExternal(item.id)
                if (tmp != null) {
                    scope.launch {
                        delay(60_000)
                        vault.store.deleteTemp(tmp)
                    }
                }
            }) { Text("Открыть") }
        }
    }
}

/**
 * Полноэкранный просмотр на тёмном фоне. Видео играет здесь, в пузыре — нет.
 */
@Composable
fun MediaViewer(items: List<ViewerItem>, start: Int, vault: MediaVault, readyTick: Int, onClose: () -> Unit) {
    val index = remember { mutableStateOf(start.coerceIn(0, items.lastIndex)) }
    val item = items.getOrNull(index.value) ?: return
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(index.value) {
            detectTapGestures(onDoubleTap = {
                if (index.value < items.lastIndex) index.value += 1 else onClose()
            })
        }) {
            if (item.kind == "video") {
                HiddenVideo(vault, item.id)
            } else {
                val bmp = remember(item.id, readyTick) { vault.bitmap(item.id) }
                if (bmp != null) {
                    Image(bmp.asImageBitmap(), item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                }
            }
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                val scope = rememberCoroutineScope()
                TextButton(onClick = {
                    scope.launch { vault.savePublic(item.id) }
                }) { Text("Сохранить", color = Color.White) }
                TextButton(onClick = onClose) { Text("Закрыть", color = Color.White) }
            }
            if (items.size > 1) {
                Text(
                    "${index.value + 1} / ${items.size}",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun HiddenVideo(vault: MediaVault, id: String) {
    val context = LocalContext.current
    val tmp = remember(id) { vault.store.revealTemp(id) }
    val player = remember(id) {
        ExoPlayer.Builder(context).build().also { exo ->
            if (tmp != null) {
                exo.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(tmp)))
                exo.prepare()
                exo.playWhenReady = true
            }
        }
    }
    DisposableEffect(player) {
        onDispose {
            player.release()
            vault.store.deleteTemp(tmp)
        }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                useController = true
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

