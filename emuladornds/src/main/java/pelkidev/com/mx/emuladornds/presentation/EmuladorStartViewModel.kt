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
import java.io.File

data class EmuladorStartUiState(
    val loadedFileName: String? = null,
    val showInvalidFileMessage: Boolean = false,
    val menuOpen: Boolean = false,
    val selectedSlot: Int = 1,
    val fastForward: Boolean = false,
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
        _uiState.update {
            it.copy(
                loadedFileName = fileName.trim(),
                showInvalidFileMessage = false,
                menuOpen = false,
                selectedSlot = 1,
                fastForward = false,
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
        _uiState.update { it.copy(selectedSlot = slot) }
    }

    fun onFastForwardToggled() {
        if (_uiState.value.loadedFileName == null) return
        _uiState.update { it.copy(fastForward = !it.fastForward) }
    }

    fun onSaveState() {
        val name = _uiState.value.loadedFileName ?: return
        val directory = stateDirectory ?: return
        val bytes = synchronized(coreLock) {
            core?.exportState()
        } ?: return
        if (bytes.isEmpty()) return
        directory.mkdirs()
        stateFile(directory, name, _uiState.value.selectedSlot).writeBytes(bytes)
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
        val fast = _uiState.value.fastForward
        val frames = synchronized(coreLock) {
            val running = core ?: return
            if (fast) {
                running.fastStep()
                running.fastStep()
            } else {
                running.step()
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
                val fast = _uiState.value.fastForward
                onConsoleTick()
                if (core?.audioPaced == true && !fast) {
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
