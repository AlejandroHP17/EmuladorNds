package pelkidev.com.mx.emuladornds.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pelkidev.com.mx.emuladornds.data.NdsButton
import pelkidev.com.mx.emuladornds.data.NdsCore
import pelkidev.com.mx.emuladornds.domain.NdsFrame

class EmuladorStartViewModelTest {

    @Test
    fun step_putsKnownPixelOnBothScreens() {
        val cores = mutableListOf<FakeCore>()
        val viewModel = EmuladorStartViewModel(
            startCore = { FakeCore().also(cores::add) },
            autoAdvance = false,
        )

        viewModel.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        viewModel.onConsoleTick()

        assertEquals(1, cores.size)
        assertEquals(KNOWN_PIXEL, viewModel.topFrame.value!!.pixel(0, 0))
        assertEquals(KNOWN_PIXEL, viewModel.bottomFrame.value!!.pixel(0, 0))
        assertEquals(
            viewModel.topFrame.value!!.pixel(0, 0),
            viewModel.bottomFrame.value!!.pixel(0, 0),
        )
    }

    @Test
    fun withoutNds_doesNotStartCore() {
        val cores = mutableListOf<FakeCore>()
        val viewModel = EmuladorStartViewModel(
            startCore = { FakeCore().also(cores::add) },
            autoAdvance = false,
        )

        viewModel.onFileChosen("nota.txt") { "/tmp/nota.txt" }

        assertTrue(cores.isEmpty())
        assertNull(viewModel.topFrame.value)
        assertTrue(viewModel.uiState.value.showInvalidFileMessage)
    }

    @Test
    fun changingRom_closesPreviousCore() {
        val cores = mutableListOf<FakeCore>()
        val viewModel = EmuladorStartViewModel(
            startCore = { FakeCore().also(cores::add) },
            autoAdvance = false,
        )

        viewModel.onFileChosen("uno.nds") { "/tmp/uno.nds" }
        viewModel.onFileChosen("dos.nds") { "/tmp/dos.nds" }

        assertEquals(2, cores.size)
        assertTrue(cores[0].closed)
        assertFalse(cores[1].closed)
    }

    @Test
    fun clearingViewModel_closesCore() {
        val cores = mutableListOf<FakeCore>()
        val store = ViewModelStore()
        val viewModel = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return EmuladorStartViewModel(
                        startCore = { FakeCore().also(cores::add) },
                        autoAdvance = false,
                    ) as T
                }
            },
        )[EmuladorStartViewModel::class.java]

        viewModel.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        store.clear()

        assertTrue(cores.single().closed)
    }

    @Test
    fun twoControls_heldTogether() {
        val cores = mutableListOf<FakeCore>()
        val viewModel = EmuladorStartViewModel(
            startCore = { FakeCore().also(cores::add) },
            autoAdvance = false,
        )

        viewModel.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        viewModel.onControlChanged(NdsButton.A, true)
        viewModel.onControlChanged(NdsButton.UP, true)

        assertEquals(setOf(NdsButton.A, NdsButton.UP), cores.single().held)
    }

    @Test
    fun releasingControl_clearsThatButton() {
        val cores = mutableListOf<FakeCore>()
        val viewModel = EmuladorStartViewModel(
            startCore = { FakeCore().also(cores::add) },
            autoAdvance = false,
        )

        viewModel.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        viewModel.onControlChanged(NdsButton.START, true)
        viewModel.onControlChanged(NdsButton.LEFT, true)
        viewModel.onControlChanged(NdsButton.START, false)

        assertEquals(setOf(NdsButton.LEFT), cores.single().held)
    }

    @Test
    fun menu_startsClosed_togglesAndResetsOnNewRom() {
        val viewModel = EmuladorStartViewModel(
            startCore = { FakeCore() },
            autoAdvance = false,
        )

        assertFalse(viewModel.uiState.value.menuOpen)

        viewModel.onFileChosen("uno.nds") { "/tmp/uno.nds" }
        assertFalse(viewModel.uiState.value.menuOpen)

        viewModel.onMenuToggled()
        assertTrue(viewModel.uiState.value.menuOpen)

        viewModel.onMenuToggled()
        assertFalse(viewModel.uiState.value.menuOpen)

        viewModel.onMenuToggled()
        viewModel.onFileChosen("dos.nds") { "/tmp/dos.nds" }
        assertFalse(viewModel.uiState.value.menuOpen)
    }

    @Test
    fun menuToggle_ignoredWithoutLoadedRom() {
        val viewModel = EmuladorStartViewModel(autoAdvance = false)

        viewModel.onMenuToggled()

        assertFalse(viewModel.uiState.value.menuOpen)
    }

    @Test
    fun newRom_startsWithEmptyHold() {
        val cores = mutableListOf<FakeCore>()
        val viewModel = EmuladorStartViewModel(
            startCore = { FakeCore().also(cores::add) },
            autoAdvance = false,
        )

        viewModel.onFileChosen("uno.nds") { "/tmp/uno.nds" }
        viewModel.onControlChanged(NdsButton.A, true)
        viewModel.onFileChosen("dos.nds") { "/tmp/dos.nds" }

        assertEquals(emptySet<NdsButton>(), cores[1].held)
    }

    private class FakeCore : NdsCore {
        var closed = false
            private set
        var held: Set<NdsButton> = emptySet()
            private set

        override fun step(): Pair<NdsFrame, NdsFrame> {
            val pixels = IntArray(NdsFrame.WIDTH * NdsFrame.HEIGHT) { KNOWN_PIXEL }
            val frame = NdsFrame(pixels)
            return frame to NdsFrame(pixels)
        }

        override fun hold(buttons: Set<NdsButton>) {
            held = buttons
        }

        override fun close() {
            closed = true
        }
    }

    companion object {
        private const val KNOWN_PIXEL = 0xFF00FF00.toInt()
    }
}
