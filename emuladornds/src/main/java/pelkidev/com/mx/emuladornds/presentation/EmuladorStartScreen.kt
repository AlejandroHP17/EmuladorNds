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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
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
import pelkidev.com.mx.emuladornds.data.NdsStateStore

@Composable
private fun rememberEmuladorStartViewModel(): EmuladorStartViewModel {
    val context = LocalContext.current
    val app = context.applicationContext
    return viewModel {
        EmuladorStartViewModel(
            stateStore = NdsStateStore.open(
                app.filesDir,
                app.getExternalFilesDir(null),
            ),
        )
    }
}

@Composable
fun EmuladorStartScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EmuladorStartViewModel = rememberEmuladorStartViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val topFrame by viewModel.topFrame.collectAsStateWithLifecycle()
    val bottomFrame by viewModel.bottomFrame.collectAsStateWithLifecycle()
    val context = LocalContext.current
    BackHandler {
        if (viewModel.onLeave()) onExit()
    }
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
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (uiState.loadedFileName == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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
                if (uiState.showInvalidFileMessage) {
                    Text(
                        text = "Elige un archivo .nds",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = uiState.loadedFileName.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = viewModel::onMenuToggled) {
                    Text(text = "Menú")
                }
            }
            if (uiState.menuOpen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                ) {
                    Text(
                        text = "Partida",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (slot in 1..3) {
                            val selected = uiState.selectedSlot == slot
                            Button(
                                onClick = { viewModel.onSlotSelected(slot) },
                                modifier = Modifier.weight(1f),
                                enabled = !selected,
                            ) {
                                Text(text = slot.toString())
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = viewModel::onSaveState,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = "Guardar")
                        }
                        Button(
                            onClick = viewModel::onLoadState,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = "Cargar")
                        }
                    }
                    Button(
                        onClick = viewModel::onFastForwardToggled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        Text(
                            text = when (uiState.fastForward) {
                                FastForwardLevel.Off -> "Rápido"
                                FastForwardLevel.Fast -> "Rápido ×2"
                                FastForwardLevel.Faster -> "Rápido ×4"
                            },
                        )
                    }
                    Text(
                        text = "Trucos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    if (uiState.cheatMenuItems.isEmpty()) {
                        Text(
                            text = "Este juego no tiene trucos",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        uiState.cheatMenuItems.forEach { cheat ->
                            Button(
                                onClick = {
                                    if (cheat.active) {
                                        viewModel.onCheatDeactivated(cheat.id)
                                    } else {
                                        viewModel.onCheatActivated(cheat.id)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            ) {
                                Text(
                                    text = if (cheat.active) {
                                        "${cheat.label} · activo (apagar)"
                                    } else {
                                        cheat.label
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (uiState.showInvalidFileMessage) {
                Text(
                    text = "Elige un archivo .nds",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds(),
            ) {
                NdsScreens(
                    topFrame = { topFrame },
                    bottomFrame = { bottomFrame },
                    onStylusMoved = viewModel::onStylusMoved,
                    onStylusReleased = viewModel::onStylusReleased,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(),
                )
            }
            NdsControls(
                onChanged = viewModel::onControlChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
                }
                if (uiState.replaceAsk != null) {
                    ReplaceQuestion(
                        label = uiState.replaceLabel,
                        onConfirm = { if (viewModel.onReplaceConfirmed()) onExit() },
                        onReject = { if (viewModel.onReplaceRejected()) onExit() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplaceQuestion(
    label: String,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "¿Sustituir $label?",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onConfirm,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(text = "Sí, sustituir")
            }
            Button(
                onClick = onReject,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(text = "No")
            }
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
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            NdsControl(
                "L",
                NdsButton.L,
                onChanged,
                style = ControlStyle.Shoulder,
                fill = ShoulderFill,
            )
            NdsControl(
                "R",
                NdsButton.R,
                onChanged,
                style = ControlStyle.Shoulder,
                fill = ShoulderFill,
            )
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
            Spacer(Modifier.width(PadGap))
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
    ControlStyle.Face -> ControlLook(58.dp, 58.dp, CircleShape, 18.sp)
    ControlStyle.Menu -> ControlLook(88.dp, 40.dp, RoundedCornerShape(percent = 50), 13.sp)
    ControlStyle.Pad -> ControlLook(54.dp, 54.dp, RoundedCornerShape(10.dp), 16.sp)
    ControlStyle.Shoulder -> ControlLook(88.dp, 40.dp, RoundedCornerShape(10.dp), 15.sp)
}

private val PadGap = 28.dp
private val PadFill = Color(0xE65A6478)
private val ShoulderFill = Color(0x995A6478)
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
