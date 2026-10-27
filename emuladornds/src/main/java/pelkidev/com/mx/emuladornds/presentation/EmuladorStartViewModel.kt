package pelkidev.com.mx.emuladornds.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import pelkidev.com.mx.emuladornds.domain.NdsFrame
import pelkidev.com.mx.emuladornds.domain.isNdsFileName
import pelkidev.com.mx.emuladornds.domain.patternFrame

data class EmuladorStartUiState(
    val loadedFileName: String? = null,
    val showInvalidFileMessage: Boolean = false,
)

class EmuladorStartViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(EmuladorStartUiState())
    val uiState: StateFlow<EmuladorStartUiState> = _uiState.asStateFlow()

    private val _topFrame = MutableStateFlow<NdsFrame?>(null)
    val topFrame: StateFlow<NdsFrame?> = _topFrame.asStateFlow()

    private val _bottomFrame = MutableStateFlow<NdsFrame?>(null)
    val bottomFrame: StateFlow<NdsFrame?> = _bottomFrame.asStateFlow()

    fun onFileChosen(fileName: String) {
        if (!isNdsFileName(fileName)) {
            _uiState.update { it.copy(showInvalidFileMessage = true) }
            return
        }
        val pattern = patternFrame()
        _topFrame.value = pattern
        _bottomFrame.value = pattern
        _uiState.update {
            it.copy(
                loadedFileName = fileName.trim(),
                showInvalidFileMessage = false,
            )
        }
    }
}
