package pelkidev.com.mx.emuladornds.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import pelkidev.com.mx.emuladornds.domain.isNdsFileName

data class EmuladorStartUiState(
    val loadedFileName: String? = null,
    val showInvalidFileMessage: Boolean = false,
)

class EmuladorStartViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(EmuladorStartUiState())
    val uiState: StateFlow<EmuladorStartUiState> = _uiState.asStateFlow()

    var loadedRom: ByteArray? = null
        private set

    fun onFileChosen(fileName: String, readBytes: () -> ByteArray?) {
        if (!isNdsFileName(fileName)) {
            _uiState.update { it.copy(showInvalidFileMessage = true) }
            return
        }
        val bytes = readBytes() ?: return
        val trimmed = fileName.trim()
        loadedRom = bytes
        _uiState.update {
            it.copy(
                loadedFileName = trimmed,
                showInvalidFileMessage = false,
            )
        }
    }
}
