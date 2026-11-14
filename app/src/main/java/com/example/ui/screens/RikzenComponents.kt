package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.util.MediaUtils
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RikzenEmerald
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private const val WEB_SPOKES = 12
private val SPOKE_COS = FloatArray(WEB_SPOKES + 1) { i ->
    cos((i % WEB_SPOKES) * 2.0 * Math.PI / WEB_SPOKES).toFloat()
}
private val SPOKE_SIN = FloatArray(WEB_SPOKES + 1) { i ->
    sin((i % WEB_SPOKES) * 2.0 * Math.PI / WEB_SPOKES).toFloat()
}

@Composable
fun rememberAsyncBase64Image(
    base64: String,
    maxDim: Int = 256
): ImageBitmap? {
    val cached = remember(base64, maxDim) {
        MediaUtils.getCachedImageBitmap(base64, maxDim)
    }
    val state by produceState(initialValue = cached, key1 = base64, key2 = maxDim) {
        if (value == null && base64.isNotBlank()) {
            value = MediaUtils.decodeBase64ToImageBitmapAsync(base64, maxDim)
        }
    }
    return state
}

@Composable
fun RikzenAvatar(
    photoBase64: String,
    name: String,
    size: Dp = 48.dp,
    isOnline: Boolean = false,
    statusRingState: Int = 0, // 0 = none, 1 = unread green ring, 2 = viewed muted ring
    modifier: Modifier = Modifier
) {
    val targetPx = with(LocalDensity.current) { (size.toPx() * 1.25f).toInt().coerceIn(96, 320) }
    val imageBitmap = rememberAsyncBase64Image(photoBase64, maxDim = targetPx)

    val outlineColor = MaterialTheme.colorScheme.outline
    val surfaceHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val primaryColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background

    val avatarGradient = remember(surfaceHigh, primaryContainer) {
        Brush.linearGradient(listOf(surfaceHigh, primaryContainer))
    }

    val ringModifier = when (statusRingState) {
        1 -> Modifier.border(2.5.dp, RikzenEmerald, CircleShape).padding(3.dp)
        2 -> Modifier.border(2.dp, outlineColor, CircleShape).padding(3.dp)
        else -> Modifier
    }
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(ringModifier)
                .clip(CircleShape)
                .background(avatarGradient)
                .border(1.dp, primaryColor.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "$name profile picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val initial = remember(name) {
                    name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "R"
                }
                Text(
                    text = initial,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.4f).sp
                )
            }
        }
        if (isOnline) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size((size.value * 0.28f).coerceAtLeast(10f).dp)
                    .clip(CircleShape)
                    .background(RikzenEmerald)
                    .border(2.dp, bgColor, CircleShape)
            )
        }
    }
}

@Composable
fun SpiderWebPattern(
    modifier: Modifier = Modifier,
    progress: Float = 1f,
    alpha: Float = 0.25f,
    progressProvider: (() -> Float)? = null
) {
    Canvas(modifier = modifier) {
        val p = (progressProvider?.invoke() ?: progress).coerceIn(0f, 1f)
        if (p <= 0f) return@Canvas
        val effectiveAlpha = if (progressProvider != null) alpha * p else alpha
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxR = size.minDimension * 0.55f * p
        val gold = ChampagneGold.copy(alpha = effectiveAlpha)

        for (i in 0 until WEB_SPOKES) {
            val ex = cx + maxR * SPOKE_COS[i]
            val ey = cy + maxR * SPOKE_SIN[i]
            drawLine(gold, Offset(cx, cy), Offset(ex, ey), strokeWidth = 1.4f)
        }
        val rings = 5
        for (r in 1..rings) {
            val radius = (maxR / rings) * r
            for (i in 0 until WEB_SPOKES) {
                val p1 = Offset(cx + radius * SPOKE_COS[i], cy + radius * SPOKE_SIN[i])
                val p2 = Offset(cx + radius * SPOKE_COS[i + 1], cy + radius * SPOKE_SIN[i + 1])
                drawLine(gold, p1, p2, strokeWidth = 1.1f)
            }
        }
        drawCircle(
            color = ChampagneGold.copy(alpha = effectiveAlpha * 0.6f),
            radius = maxR * 0.35f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5f)
        )
    }
}

@Composable
fun ImageCropDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isEncoding by remember { mutableStateOf(false) }
    val previewImageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    Dialog(onDismissRequest = { if (!isEncoding) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Crop & Position Photo",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Drag to reposition • Slide to zoom",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .clip(CircleShape)
                        .border(2.dp, ChampagneGold, CircleShape)
                        .background(ObsidianBlack)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                offsetX = (offsetX + dragAmount.x / 180f).coerceIn(-1f, 1f)
                                offsetY = (offsetY + dragAmount.y / 180f).coerceIn(-1f, 1f)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = previewImageBitmap,
                        contentDescription = "Cropped profile preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                val maxTranslation = size.width * (zoom - 1f) * 0.5f + size.width * 0.2f
                                translationX = offsetX * maxTranslation
                                translationY = offsetY * maxTranslation
                            }
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("Zoom: ${"%.1f".format(zoom)}x", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = zoom,
                    onValueChange = { zoom = it },
                    valueRange = 1f..3f,
                    enabled = !isEncoding
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isEncoding,
                        modifier = Modifier.weight(1f).testTag("crop_cancel_button")
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (isEncoding) return@Button
                            isEncoding = true
                            scope.launch {
                                val encoded = MediaUtils.cropAndEncodeAsync(
                                    bitmap = bitmap,
                                    zoom = zoom,
                                    offsetXFrac = offsetX,
                                    offsetYFrac = offsetY,
                                    outSize = 256,
                                    maxBytes = 85_000
                                )
                                isEncoding = false
                                onConfirm(encoded)
                            }
                        },
                        enabled = !isEncoding,
                        modifier = Modifier.weight(1f).testTag("crop_save_button")
                    ) {
                        if (isEncoding) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Use Photo")
                        }
                    }
                }
            }
        }
    }
}
