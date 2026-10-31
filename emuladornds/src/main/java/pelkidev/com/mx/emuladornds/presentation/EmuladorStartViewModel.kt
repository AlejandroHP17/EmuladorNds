package pelkidev.com.mx.emuladornds.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pelkidev.com.mx.emuladornds.data.MelonDsCore
import pelkidev.com.mx.emuladornds.data.NdsButton
import pelkidev.com.mx.emuladornds.data.NdsCore
import pelkidev.com.mx.emuladornds.domain.NdsFrame
import pelkidev.com.mx.emuladornds.domain.NdsPace
import pelkidev.com.mx.emuladornds.domain.isNdsFileName
import pelkidev.com.mx.emuladornds.domain.patternFrame
import pelkidev.com.mx.emuladornds.domain.preparedCheatsForNds
import java.io.File

enum class ReplaceAsk {
    Save,
    Leave,
}

enum class FastForwardLevel {
    Off,
    Fast,
    Faster,
}

data class CheatMenuItem(
    val id: String,
    val label: String,
    val active: Boolean = false,
)

data class EmuladorStartUiState(
    val loadedFileName: String? = null,
    val showInvalidFileMessage: Boolean = false,
    val menuOpen: Boolean = false,
    val selectedSlot: Int = 1,
    val fastForward: FastForwardLevel = FastForwardLevel.Off,
    val cheatMenuItems: List<CheatMenuItem> = emptyList(),
    val replaceAsk: ReplaceAsk? = null,
    val replaceLabel: String = "",
)

class EmuladorStartViewModel(
    private val startCore: (String) -> NdsCore = ::MelonDsCore,
    private val autoAdvance: Boolean = true,
    private val stateDirectory: File? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EmuladorStartUiState())
    val uiState: StateFlow<EmuladorStartUiState> = _uiState.asStateFlow()

    private val _topFrame = MutableStateFlow<NdsFrame?>(null)
    val topFrame: StateFlow<NdsFrame?> = _topFrame.asStateFlow()

    private val _bottomFrame = MutableStateFlow<NdsFrame?>(null)
    val bottomFrame: StateFlow<NdsFrame?> = _bottomFrame.asStateFlow()

    private val coreLock = Any()
    private var core: NdsCore? = null
    private var paceJob: Job? = null
    private var heldButtons = emptySet<NdsButton>()

    fun onFileChosen(fileName: String, prepareRomPath: () -> String?) {
        if (!isNdsFileName(fileName)) {
            _uiState.update { it.copy(showInvalidFileMessage = true) }
            return
        }
        val romPath = prepareRomPath() ?: return
        stopCore()
        heldButtons = emptySet()
        val pattern = patternFrame()
        _topFrame.value = pattern
        _bottomFrame.value = pattern
        val trimmedName = fileName.trim()
        _uiState.update {
            it.copy(
                loadedFileName = trimmedName,
                showInvalidFileMessage = false,
                menuOpen = false,
                selectedSlot = 1,
                fastForward = FastForwardLevel.Off,
                cheatMenuItems = preparedCheatsForNds(trimmedName).map { cheat ->
                    CheatMenuItem(id = cheat.id, label = cheat.displayName)
                },
            )
        }
        val opened = try {
            startCore(romPath)
        } catch (_: RuntimeException) {
            null
        } ?: return
        synchronized(coreLock) {
            opened.hold(emptySet())
            opened.releaseTouch()
            core = opened
        }
        if (autoAdvance) {
            startPace()
        }
    }

    fun onMenuToggled() {
        if (_uiState.value.loadedFileName == null) return
        _uiState.update { it.copy(menuOpen = !it.menuOpen) }
    }

    fun onSlotSelected(slot: Int) {
        if (slot !in 1..3) return
        _uiState.update {
            it.copy(
                selectedSlot = slot,
                replaceAsk = null,
                replaceLabel = "",
            )
        }
    }

    fun onFastForwardToggled() {
        if (_uiState.value.loadedFileName == null) return
        _uiState.update { state ->
            val next = when (state.fastForward) {
                FastForwardLevel.Off -> FastForwardLevel.Fast
                FastForwardLevel.Fast -> FastForwardLevel.Faster
                FastForwardLevel.Faster -> FastForwardLevel.Off
            }
            state.copy(fastForward = next)
        }
    }

    fun onSaveState() {
        if (askIfReplacing(ReplaceAsk.Save)) return
        writeSelectedState()
    }

    fun onReplaceConfirmed(): Boolean {
        val ask = _uiState.value.replaceAsk ?: return false
        _uiState.update { it.copy(replaceAsk = null, replaceLabel = "") }
        when (ask) {
            ReplaceAsk.Save -> writeSelectedState()
            ReplaceAsk.Leave -> finishLeave(writeState = true)
        }
        return ask == ReplaceAsk.Leave
    }

    fun onReplaceRejected(): Boolean {
        val ask = _uiState.value.replaceAsk ?: return false
        _uiState.update { it.copy(replaceAsk = null, replaceLabel = "") }
        if (ask == ReplaceAsk.Leave) {
            finishLeave(writeState = false)
            return true
        }
        return false
    }

    /** Atrás del sistema: guarda en el hueco elegido y sale al hub si procede. */
    fun onLeave(): Boolean {
        if (_uiState.value.loadedFileName == null) {
            stopCore()
            return true
        }
        if (askIfReplacing(ReplaceAsk.Leave)) return false
        finishLeave(writeState = true)
        return true
    }

    private fun finishLeave(writeState: Boolean) {
        if (writeState) writeSelectedState()
        stopCore()
        heldButtons = emptySet()
        _topFrame.value = null
        _bottomFrame.value = null
        _uiState.value = EmuladorStartUiState()
    }

    private fun writeSelectedState() {
        val name = _uiState.value.loadedFileName ?: return
        val directory = stateDirectory ?: return
        val slot = _uiState.value.selectedSlot
        val bytes = synchronized(coreLock) {
            core?.exportState()
        } ?: return
        if (bytes.isEmpty()) return
        directory.mkdirs()
        stateFile(directory, name, slot).writeBytes(bytes)
    }

    private fun askIfReplacing(ask: ReplaceAsk): Boolean {
        if (_uiState.value.replaceAsk != null) return true
        if (!slotHasState()) return false
        val slot = _uiState.value.selectedSlot
        _uiState.update {
            it.copy(replaceAsk = ask, replaceLabel = slot.toString())
        }
        return true
    }

    private fun slotHasState(): Boolean {
        val name = _uiState.value.loadedFileName ?: return false
        val directory = stateDirectory ?: return false
        return stateFile(directory, name, _uiState.value.selectedSlot).isFile
    }

    fun onLoadState() {
        val name = _uiState.value.loadedFileName ?: return
        val directory = stateDirectory ?: return
        val file = stateFile(directory, name, _uiState.value.selectedSlot)
        if (!file.isFile) return
        val payload = file.readBytes()
        val frames = synchronized(coreLock) {
            val running = core ?: return
            if (!running.importState(payload)) return
            running.step()
        }
        _topFrame.value = frames.first
        _bottomFrame.value = frames.second
    }

    fun onControlChanged(button: NdsButton, held: Boolean) {
        heldButtons = if (held) heldButtons + button else heldButtons - button
        synchronized(coreLock) {
            core?.hold(heldButtons)
        }
    }

    /** Punto en coordenadas DS (256×192) mientras el dedo está en la pantalla de abajo. */
    fun onStylusMoved(x: Int, y: Int) {
        synchronized(coreLock) {
            core?.touch(
                x.coerceIn(0, NdsFrame.WIDTH - 1),
                y.coerceIn(0, NdsFrame.HEIGHT - 1),
            )
        }
    }

    fun onStylusReleased() {
        synchronized(coreLock) {
            core?.releaseTouch()
        }
    }

    fun onConsoleTick() {
        val speed = _uiState.value.fastForward
        val frames = synchronized(coreLock) {
            val running = core ?: return
            when (speed) {
                FastForwardLevel.Off -> running.step()
                FastForwardLevel.Fast -> {
                    running.fastStep()
                    running.fastStep()
                }
                FastForwardLevel.Faster -> {
                    var last = running.fastStep()
                    repeat(3) {
                        last = running.fastStep()
                    }
                    last
                }
            }
        }
        _topFrame.value = frames.first
        _bottomFrame.value = frames.second
    }

    private fun startPace() {
        paceJob?.cancel()
        paceJob = viewModelScope.launch(Dispatchers.Default) {
            var deadline = System.nanoTime()
            while (isActive) {
                val speed = _uiState.value.fastForward
                onConsoleTick()
                if (core?.audioPaced == true && speed == FastForwardLevel.Off) {
                    deadline = System.nanoTime()
                    continue
                }
                deadline += NdsPace.NANOS_PER_STEP
                val wait = deadline - System.nanoTime()
                if (wait > 0) {
                    delay(wait / 1_000_000)
                } else {
                    deadline = System.nanoTime()
                }
            }
        }
    }

    private fun stopCore() {
        paceJob?.cancel()
        paceJob = null
        synchronized(coreLock) {
            core?.close()
            core = null
        }
    }

    override fun onCleared() {
        stopCore()
        super.onCleared()
    }

    private companion object {
        fun stateFile(directory: File, name: String, slot: Int = 1): File {
            val safe = name.replace("/", "_").replace("\\", "_")
            val fileName = if (slot == 1) safe else "$safe.$slot"
            return File(directory, fileName)
        }
    }
}
