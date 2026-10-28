package pelkidev.com.mx.emuladornds.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import pelkidev.com.mx.emuladornds.domain.NdsFrame

@Composable
fun NdsScreens(
    topFrame: () -> NdsFrame?,
    bottomFrame: () -> NdsFrame?,
    modifier: Modifier = Modifier,
    onStylusMoved: ((Int, Int) -> Unit)? = null,
    onStylusReleased: (() -> Unit)? = null,
) {
    val screenRatio = NdsFrame.WIDTH.toFloat() / NdsFrame.HEIGHT
    val gap = ScreenGap
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
    ) {
        val density = LocalDensity.current
        val gapPx = with(density) { gap.toPx() }
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val scale = TopScreenWidthScale
        fun stackHeight(bottomWidth: Float): Float {
            val topWidth = minOf(maxW, bottomWidth * scale)
            return topWidth / screenRatio + bottomWidth / screenRatio + gapPx
        }
        val bottomWidthPx = if (maxH <= 0f || stackHeight(maxW / scale) <= maxH) {
            maxW / scale
        } else {
            (maxH - gapPx) * screenRatio / (scale + 1f)
        }
        val topWidthPx = minOf(maxW, bottomWidthPx * scale)
        val topWidth = with(density) { topWidthPx.toDp() }
        val bottomWidth = with(density) { bottomWidthPx.toDp() }
        Column(
            modifier = Modifier.align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap, Alignment.Top),
        ) {
            NdsScreen(
                frame = topFrame,
                modifier = Modifier.width(topWidth),
            )
            NdsScreen(
                frame = bottomFrame,
                modifier = Modifier.width(bottomWidth),
                onStylusMoved = onStylusMoved,
                onStylusReleased = onStylusReleased,
            )
        }
    }
}

private val ScreenGap = 4.dp
private const val TopScreenWidthScale = 1.28f

@Composable
private fun NdsScreen(
    frame: () -> NdsFrame?,
    modifier: Modifier = Modifier,
    onStylusMoved: ((Int, Int) -> Unit)? = null,
    onStylusReleased: (() -> Unit)? = null,
) {
    val ratio = NdsFrame.WIDTH.toFloat() / NdsFrame.HEIGHT
    val bitmap = remember {
        Bitmap.createBitmap(NdsFrame.WIDTH, NdsFrame.HEIGHT, Bitmap.Config.ARGB_8888)
    }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    if (onStylusReleased != null) {
        DisposableEffect(onStylusReleased) {
            onDispose { onStylusReleased() }
        }
    }
    val stylusModifier = if (onStylusMoved != null && onStylusReleased != null) {
        Modifier.pointerInput(onStylusMoved, onStylusReleased) {
            awaitEachGesture {
                val down = awaitFirstDown()
                fun report(position: Offset): Boolean {
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()
                    if (width <= 0f || height <= 0f) return false
                    if (position.x < 0f || position.y < 0f ||
                        position.x > width || position.y > height
                    ) {
                        return false
                    }
                    val x = (position.x / width * NdsFrame.WIDTH)
                        .toInt()
                        .coerceIn(0, NdsFrame.WIDTH - 1)
                    val y = (position.y / height * NdsFrame.HEIGHT)
                        .toInt()
                        .coerceIn(0, NdsFrame.HEIGHT - 1)
                    onStylusMoved(x, y)
                    return true
                }
                if (!report(down.position)) {
                    onStylusReleased()
                    return@awaitEachGesture
                }
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                            ?: break
                        if (!change.pressed) break
                        if (!report(change.position)) break
                        change.consume()
                    }
                } finally {
                    onStylusReleased()
                }
            }
        }
    } else {
        Modifier
    }
    Canvas(
        modifier = modifier
            .aspectRatio(ratio)
            .clip(RectangleShape)
            .then(stylusModifier),
    ) {
        val current = frame() ?: return@Canvas
        bitmap.setPixels(
            current.pixels,
            0,
            NdsFrame.WIDTH,
            0,
            0,
            NdsFrame.WIDTH,
            NdsFrame.HEIGHT,
        )
        drawImage(
            image = image,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        )
    }
}
