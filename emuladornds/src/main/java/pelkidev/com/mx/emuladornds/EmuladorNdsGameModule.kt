package pelkidev.com.mx.emuladornds

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pelkidev.com.mx.minijuegos.sdk.GameModule

object EmuladorNdsGameModule : GameModule {
    override val id: String = "emuladornds"
    override val title: String = "Emulador Nintendo DS"
    override val description: String = "Juega un archivo .nds en el dispositivo"

    @Composable
    override fun Entry(onExit: () -> Unit) {
        MaterialTheme {
            BackHandler(onBack = onExit)
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
