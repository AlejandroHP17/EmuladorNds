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

data class EmuladorStartUiState(
    val loadedFileName: String? = null,
    val showInvalidFileMessage: Boolean = false,
)

class EmuladorStartViewModel(
    private val startCore: (String) -> NdsCore = ::MelonDsCore,
    private val autoAdvance: Boolean = true,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EmuladorStartUiState())
    val uiState: StateFlow<EmuladorStartUiState> = _uiState.asStateFlow()

    private val _topFrame = MutableStateFlow<NdsFrame?>(null)
    val topFrame: StateFlow<NdsFrame?> = _topFrame.asStateFlow()

    private val _bottomFrame = MutableStateFlow<NdsFrame?>(null)
    val bottomFrame: StateFlow<NdsFrame?> = _bottomFrame.asStateFlow()

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
            )
        }
        val opened = try {
            startCore(romPath)
        } catch (_: RuntimeException) {
            null
        } ?: return
        opened.hold(emptySet())
        opened.releaseTouch()
        core = opened
        if (autoAdvance) {
            startPace()
        }
    }

    fun onControlChanged(button: NdsButton, held: Boolean) {
        val running = core ?: return
        heldButtons = if (held) heldButtons + button else heldButtons - button
        running.hold(heldButtons)
    }

    /** Punto en coordenadas DS (256×192) mientras el dedo está en la pantalla de abajo. */
    fun onStylusMoved(x: Int, y: Int) {
        val running = core ?: return
        running.touch(
            x.coerceIn(0, NdsFrame.WIDTH - 1),
            y.coerceIn(0, NdsFrame.HEIGHT - 1),
        )
    }

    fun onStylusReleased() {
        core?.releaseTouch()
    }

    fun onConsoleTick() {
        val running = core ?: return
        val (top, bottom) = running.step()
        _topFrame.value = top
        _bottomFrame.value = bottom
    }

    private fun startPace() {
        paceJob?.cancel()
        paceJob = viewModelScope.launch(Dispatchers.Default) {
            var deadline = System.nanoTime()
            while (isActive) {
                onConsoleTick()
                if (core?.audioPaced == true) {
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
        core?.close()
        core = null
    }

    override fun onCleared() {
        stopCore()
        super.onCleared()
    }
}
