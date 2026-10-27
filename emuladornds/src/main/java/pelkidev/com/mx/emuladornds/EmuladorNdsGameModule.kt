package pelkidev.com.mx.emuladornds

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import pelkidev.com.mx.emuladornds.presentation.EmuladorStartScreen
import pelkidev.com.mx.minijuegos.sdk.GameModule

object EmuladorNdsGameModule : GameModule {
    override val id: String = "emuladornds"
    override val title: String = "Emulador Nintendo DS"
    override val description: String = "Juega un archivo .nds en el dispositivo"

    @Composable
    override fun Entry(onExit: () -> Unit) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                EmuladorStartScreen(onExit = onExit)
            }
        }
    }
}
