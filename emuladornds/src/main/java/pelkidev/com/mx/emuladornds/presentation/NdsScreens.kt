package pelkidev.com.mx.emuladornds.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import pelkidev.com.mx.emuladornds.domain.NdsFrame

@Composable
fun NdsScreens(
    topFrame: () -> NdsFrame?,
    bottomFrame: () -> NdsFrame?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NdsScreen(frame = topFrame, modifier = Modifier.fillMaxWidth())
        NdsScreen(frame = bottomFrame, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun NdsScreen(
    frame: () -> NdsFrame?,
    modifier: Modifier = Modifier,
) {
    val ratio = NdsFrame.WIDTH.toFloat() / NdsFrame.HEIGHT
    val bitmap = remember {
        Bitmap.createBitmap(NdsFrame.WIDTH, NdsFrame.HEIGHT, Bitmap.Config.ARGB_8888)
    }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Canvas(
        modifier = modifier.aspectRatio(ratio),
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
