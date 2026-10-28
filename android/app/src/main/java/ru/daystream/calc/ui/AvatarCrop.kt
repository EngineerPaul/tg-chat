package ru.daystream.calc.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.daystream.calc.media.AvatarCache
import ru.daystream.calc.media.PhotoCompressor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Круглый аватар: локальный кэш, сеть только если URL сменился.
 */
@Composable
fun CircleAvatar(login: String, url: String?, userId: Long? = null, size: Dp = 72.dp, textSize: TextUnit = 28.sp) {
    val bmp = remember(userId) { mutableStateOf(userId?.let { AvatarCache.peek(it) }) }
    LaunchedEffect(userId, url) {
        if (userId == null) {
            bmp.value = null
            return@LaunchedEffect
        }
        val cached = AvatarCache.peek(userId)
        if (cached != null) bmp.value = cached
        bmp.value = withContext(Dispatchers.IO) { AvatarCache.ensure(userId, url) }
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(avatarColor(login.ifBlank { "?" })),
        contentAlignment = Alignment.Center,
    ) {
        val image = bmp.value
        if (image != null) {
            Image(image.asImageBitmap(), contentDescription = login, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(login.take(1).uppercase(), color = Color.White, fontSize = textSize)
        }
    }
}

/**
 * Выбрать квадратную зону на фото: перетаскивание и масштаб.
 */
@Composable
fun AvatarCropPane(
    bitmap: Bitmap,
    onCancel: () -> Unit,
    onCrop: (Bitmap) -> Unit,
) {
    BackHandler(onBack = onCancel)
    val zoom = remember { mutableStateOf(1f) }
    val offset = remember { mutableStateOf(Offset.Zero) }
    val viewPx = remember { mutableStateOf(0f) }
    val img = remember(bitmap) { bitmap.asImageBitmap() }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Text(
            "Выберите область",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val side = minOf(maxWidth, maxHeight) - 32.dp
            val sidePx = with(LocalDensity.current) { side.toPx() }
            SideEffect { viewPx.value = sidePx }
            val imgW = bitmap.width.toFloat()
            val imgH = bitmap.height.toFloat()
            Box(
                Modifier
                    .size(side)
                    .pointerInput(bitmap, sidePx) {
                        detectTransformGestures { _, pan, zoomChange, _ ->
                            val nextZoom = (zoom.value * zoomChange).coerceIn(1f, 5f)
                            zoom.value = nextZoom
                            offset.value = clampCropOffset(nextZoom, offset.value + pan, imgW, imgH, sidePx)
                        }
                    },
            ) {
                val z = zoom.value
                val off = clampCropOffset(z, offset.value, imgW, imgH, sidePx)
                val minScale = sidePx / min(imgW, imgH).coerceAtLeast(1f)
                val scale = minScale * z
                val drawW = imgW * scale
                val drawH = imgH * scale
                val left = (sidePx - drawW) / 2f + off.x
                val top = (sidePx - drawH) / 2f + off.y
                Canvas(Modifier.fillMaxSize()) {
                    drawImage(
                        image = img,
                        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                        dstSize = IntSize(drawW.roundToInt().coerceAtLeast(1), drawH.roundToInt().coerceAtLeast(1)),
                    )
                }
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
                ) {
                    drawRect(Color(0x99000000))
                    val r = size.minDimension / 2f
                    drawCircle(Color.Transparent, radius = r, blendMode = BlendMode.Clear)
                    drawCircle(Color.White, radius = r - 1f, style = Stroke(width = 2.dp.toPx()))
                }
            }
        }
        Text(
            "Двигайте и масштабируйте двумя пальцами",
            color = Color(0xCCFFFFFF),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onCancel) { Text("Отмена") }
            Button(onClick = {
                val side = viewPx.value
                if (side <= 0f) return@Button
                val off = clampCropOffset(zoom.value, offset.value, bitmap.width.toFloat(), bitmap.height.toFloat(), side)
                onCrop(PhotoCompressor.cropVisibleSquare(bitmap, zoom.value, off.x, off.y, side))
            }) { Text("Сохранить") }
        }
    }
}

private fun clampCropOffset(zoom: Float, offset: Offset, imgW: Float, imgH: Float, sidePx: Float): Offset {
    val scale = (sidePx / min(imgW, imgH).coerceAtLeast(1f)) * zoom.coerceAtLeast(1f)
    val maxX = ((imgW * scale - sidePx) / 2f).coerceAtLeast(0f)
    val maxY = ((imgH * scale - sidePx) / 2f).coerceAtLeast(0f)
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}
