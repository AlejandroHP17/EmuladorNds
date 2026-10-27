package pelkidev.com.mx.emuladornds.presentation

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import pelkidev.com.mx.emuladornds.data.NdsButton

@Composable
fun EmuladorStartScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EmuladorStartViewModel = viewModel { EmuladorStartViewModel() },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val topFrame by viewModel.topFrame.collectAsStateWithLifecycle()
    val bottomFrame by viewModel.bottomFrame.collectAsStateWithLifecycle()
    val context = LocalContext.current
    BackHandler(onBack = onExit)
    val pickNdsFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = displayName(context, uri)
        viewModel.onFileChosen(name) {
            copyRomToCache(context, uri)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Emulador Nintendo DS",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = { pickNdsFile.launch(arrayOf("*/*")) },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(text = "Elegir .nds")
        }
        if (uiState.loadedFileName == null) {
            if (uiState.showInvalidFileMessage) {
                Text(
                    text = "Elige un archivo .nds",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        } else {
            Text(
                text = uiState.loadedFileName.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (uiState.showInvalidFileMessage) {
                Text(
                    text = "Elige un archivo .nds",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            NdsScreens(
                topFrame = { topFrame },
                bottomFrame = { bottomFrame },
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp),
            )
            NdsControls(
                onChanged = viewModel::onControlChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun NdsControls(
    onChanged: (NdsButton, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            NdsControl("L", NdsButton.L, onChanged, style = ControlStyle.Shoulder)
            NdsControl("R", NdsButton.R, onChanged, style = ControlStyle.Shoulder)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NdsDpad(onChanged)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NdsControl("Select", NdsButton.SELECT, onChanged, style = ControlStyle.Menu)
                Spacer(Modifier.height(6.dp))
                NdsControl("Start", NdsButton.START, onChanged, style = ControlStyle.Menu)
            }
            NdsFace(onChanged)
        }
    }
}

@Composable
private fun NdsDpad(onChanged: (NdsButton, Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        NdsControl("▲", NdsButton.UP, onChanged, style = ControlStyle.Pad)
        Row {
            NdsControl("◀", NdsButton.LEFT, onChanged, style = ControlStyle.Pad)
            Spacer(Modifier.width(PadGap))
            NdsControl("▶", NdsButton.RIGHT, onChanged, style = ControlStyle.Pad)
        }
        NdsControl("▼", NdsButton.DOWN, onChanged, style = ControlStyle.Pad)
    }
}

@Composable
private fun NdsFace(onChanged: (NdsButton, Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        NdsControl("X", NdsButton.X, onChanged, style = ControlStyle.Face, fill = FaceX)
        Row(verticalAlignment = Alignment.CenterVertically) {
            NdsControl("Y", NdsButton.Y, onChanged, style = ControlStyle.Face, fill = FaceY)
            Spacer(Modifier.width(FaceGap))
            NdsControl("A", NdsButton.A, onChanged, style = ControlStyle.Face, fill = FaceA)
        }
        NdsControl("B", NdsButton.B, onChanged, style = ControlStyle.Face, fill = FaceB)
    }
}

@Composable
private fun NdsControl(
    label: String,
    button: NdsButton,
    onChanged: (NdsButton, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    style: ControlStyle = ControlStyle.Face,
    fill: Color = PadFill,
) {
    var fingerDown by remember(button) { mutableStateOf(false) }
    val sink = with(LocalDensity.current) { 2.dp.toPx() }
    DisposableEffect(button) {
        onDispose { onChanged(button, false) }
    }
    val look = style.look()
    Box(
        modifier = modifier
            .size(look.width, look.height)
            .graphicsLayer {
                val scale = if (fingerDown) 0.94f else 1f
                scaleX = scale
                scaleY = scale
                translationY = if (fingerDown) sink else 0f
            }
            .clip(look.shape)
            .background(if (fingerDown) fill.pressed() else fill)
            .pointerInput(button) {
                awaitEachGesture {
                    awaitFirstDown()
                    fingerDown = true
                    onChanged(button, true)
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        onChanged(button, false)
                        fingerDown = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = look.fontSize,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

private enum class ControlStyle {
    Face,
    Menu,
    Pad,
    Shoulder,
}

private data class ControlLook(
    val width: Dp,
    val height: Dp,
    val shape: Shape,
    val fontSize: TextUnit,
)

private fun ControlStyle.look(): ControlLook = when (this) {
    ControlStyle.Face -> ControlLook(44.dp, 44.dp, CircleShape, 16.sp)
    ControlStyle.Menu -> ControlLook(72.dp, 32.dp, RoundedCornerShape(percent = 50), 11.sp)
    ControlStyle.Pad -> ControlLook(40.dp, 40.dp, RoundedCornerShape(10.dp), 14.sp)
    ControlStyle.Shoulder -> ControlLook(72.dp, 32.dp, RoundedCornerShape(10.dp), 13.sp)
}

private val PadGap = 4.dp
private val FaceGap = 8.dp
private val PadFill = Color(0xE65A6478)
private val FaceA = Color(0xCC3D8BFF)
private val FaceB = Color(0xCCFF5A5A)
private val FaceX = Color(0xCC6E7A90)
private val FaceY = Color(0xCC4CAF50)

private fun Color.pressed(): Color = Color(
    red = red * 0.62f,
    green = green * 0.62f,
    blue = blue * 0.62f,
    alpha = alpha,
)

private fun copyRomToCache(context: Context, uri: Uri): String? {
    val dest = File(context.cacheDir, "session.nds")
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
            }
        } ?: return null
        dest.absolutePath
    } catch (_: Exception) {
        null
    }
}

private fun displayName(context: Context, uri: Uri): String {
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) {
                return cursor.getString(index).orEmpty()
            }
        }
    }
    return uri.lastPathSegment.orEmpty()
}
