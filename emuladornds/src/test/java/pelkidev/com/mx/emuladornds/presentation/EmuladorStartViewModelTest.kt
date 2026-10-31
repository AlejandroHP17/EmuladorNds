package pelkidev.com.mx.emuladornds.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pelkidev.com.mx.emuladornds.data.NdsButton
import pelkidev.com.mx.emuladornds.data.NdsCore
import pelkidev.com.mx.emuladornds.data.NdsStateStore
import pelkidev.com.mx.emuladornds.data.PairingNdsBluetoothLink
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
    fun slots_keepSeparateStatesAndReplaceOnlyTheChosenOne() {
        val directory = createTempDirectory("nds-state").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        assertEquals(1, viewModel.uiState.value.selectedSlot)
        cores[0].token = 9
        viewModel.onSaveState()
        viewModel.onSlotSelected(2)
        cores[0].token = 4
        viewModel.onSaveState()
        cores[0].token = 7
        viewModel.onSaveState()

        assertEquals(ReplaceAsk.Save, viewModel.uiState.value.replaceAsk)
        assertArrayEquals(byteArrayOf(9), File(directory, "juego.nds").readBytes())
        assertArrayEquals(byteArrayOf(4), File(directory, "juego.nds.2").readBytes())

        assertFalse(viewModel.onReplaceConfirmed())

        assertArrayEquals(byteArrayOf(7), File(directory, "juego.nds.2").readBytes())

        viewModel.onSlotSelected(1)
        viewModel.onLoadState()

        assertArrayEquals(byteArrayOf(9), cores[0].imported)
        assertEquals(9, viewModel.topFrame.value!!.pixel(0, 0))
        assertArrayEquals(byteArrayOf(7), File(directory, "juego.nds.2").readBytes())
    }

    @Test
    fun load_emptySlot_keepsTheFrame() {
        val directory = createTempDirectory("nds-state").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        cores[0].token = 9
        viewModel.onSaveState()
        val top = viewModel.topFrame.value

        viewModel.onSlotSelected(3)
        viewModel.onLoadState()

        assertTrue(viewModel.topFrame.value === top)
        assertNull(cores[0].imported)
    }

    @Test
    fun load_doesNotApplyAnotherFilesSlot() {
        val directory = createTempDirectory("nds-state").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        viewModel.onSlotSelected(2)
        cores[0].token = 7
        viewModel.onSaveState()

        viewModel.onFileChosen("otro.nds") { "/tmp/otro.nds" }
        assertEquals(1, viewModel.uiState.value.selectedSlot)
        viewModel.onSlotSelected(2)
        val top = viewModel.topFrame.value
        viewModel.onLoadState()

        assertTrue(viewModel.topFrame.value === top)
        assertNull(cores[1].imported)
    }

    @Test
    fun leave_emptySlot_writesAndClears() {
        val directory = createTempDirectory("nds-leave").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        cores[0].token = 5

        assertTrue(viewModel.onLeave())

        assertNull(viewModel.uiState.value.loadedFileName)
        assertArrayEquals(byteArrayOf(5), File(directory, "juego.nds").readBytes())
        assertTrue(cores[0].closed)
    }

    @Test
    fun leave_occupiedSlot_asksBeforeExit() {
        val directory = createTempDirectory("nds-leave").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        cores[0].token = 9
        viewModel.onSaveState()
        cores[0].token = 4

        assertFalse(viewModel.onLeave())

        assertEquals(ReplaceAsk.Leave, viewModel.uiState.value.replaceAsk)
        assertArrayEquals(byteArrayOf(9), File(directory, "juego.nds").readBytes())
        assertEquals("juego.nds", viewModel.uiState.value.loadedFileName)
    }

    @Test
    fun leave_confirm_replacesAndClears() {
        val directory = createTempDirectory("nds-leave").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        cores[0].token = 9
        viewModel.onSaveState()
        cores[0].token = 4
        assertFalse(viewModel.onLeave())

        assertTrue(viewModel.onReplaceConfirmed())

        assertNull(viewModel.uiState.value.loadedFileName)
        assertArrayEquals(byteArrayOf(4), File(directory, "juego.nds").readBytes())
    }

    @Test
    fun leave_reject_keepsStateAndClears() {
        val directory = createTempDirectory("nds-leave").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        cores[0].token = 9
        viewModel.onSaveState()
        cores[0].token = 4
        assertFalse(viewModel.onLeave())

        assertTrue(viewModel.onReplaceRejected())

        assertNull(viewModel.uiState.value.loadedFileName)
        assertArrayEquals(byteArrayOf(9), File(directory, "juego.nds").readBytes())
    }

    @Test
    fun leave_withoutRom_doesNotCreateState() {
        val directory = createTempDirectory("nds-leave").toFile()
        File(directory, "juego.nds").writeBytes(byteArrayOf(3))
        val viewModel = viewModelWithStates(directory, mutableListOf())

        assertTrue(viewModel.onLeave())

        assertArrayEquals(byteArrayOf(3), File(directory, "juego.nds").readBytes())
    }

    @Test
    fun emptySlot_savesWithoutAsking() {
        val directory = createTempDirectory("nds-replace").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        cores[0].token = 2
        viewModel.onSaveState()

        assertNull(viewModel.uiState.value.replaceAsk)
        assertArrayEquals(byteArrayOf(2), File(directory, "juego.nds").readBytes())
    }

    @Test
    fun occupiedSlot_asksBeforeReplace() {
        val directory = createTempDirectory("nds-replace").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        cores[0].token = 9
        viewModel.onSaveState()
        cores[0].token = 4
        viewModel.onSaveState()

        assertEquals(ReplaceAsk.Save, viewModel.uiState.value.replaceAsk)
        assertEquals("1", viewModel.uiState.value.replaceLabel)
        assertArrayEquals(byteArrayOf(9), File(directory, "juego.nds").readBytes())
    }

    @Test
    fun confirm_replacesOnlyTheChosenSlot() {
        val directory = createTempDirectory("nds-replace").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        cores[0].token = 9
        viewModel.onSaveState()
        viewModel.onSlotSelected(2)
        cores[0].token = 4
        viewModel.onSaveState()
        cores[0].token = 7
        viewModel.onSaveState()

        assertFalse(viewModel.onReplaceConfirmed())

        assertNull(viewModel.uiState.value.replaceAsk)
        assertArrayEquals(byteArrayOf(7), File(directory, "juego.nds.2").readBytes())
        assertArrayEquals(byteArrayOf(9), File(directory, "juego.nds").readBytes())
    }

    @Test
    fun reject_keepsPreviousState() {
        val directory = createTempDirectory("nds-replace").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        cores[0].token = 9
        viewModel.onSaveState()
        cores[0].token = 4
        viewModel.onSaveState()

        assertFalse(viewModel.onReplaceRejected())

        assertNull(viewModel.uiState.value.replaceAsk)
        assertArrayEquals(byteArrayOf(9), File(directory, "juego.nds").readBytes())
    }

    @Test
    fun changingSlot_clearsReplaceAsk() {
        val directory = createTempDirectory("nds-replace").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        cores[0].token = 9
        viewModel.onSaveState()
        cores[0].token = 4
        viewModel.onSaveState()
        viewModel.onSlotSelected(2)

        assertNull(viewModel.uiState.value.replaceAsk)
        assertArrayEquals(byteArrayOf(9), File(directory, "juego.nds").readBytes())
    }

    @Test
    fun fastForward_cyclesTwoFourAndNormal() {
        val cores = mutableListOf<RememberingCore>()
        val viewModel = EmuladorStartViewModel(
            startCore = { RememberingCore().also(cores::add) },
            autoAdvance = false,
        )
        viewModel.onFileChosen("juego.nds") { "/tmp/juego.nds" }

        viewModel.onConsoleTick()
        assertEquals(1, cores[0].steps)

        viewModel.onFastForwardToggled()
        viewModel.onControlChanged(NdsButton.A, true)
        viewModel.onConsoleTick()

        assertEquals(3, cores[0].steps)
        assertEquals(setOf(NdsButton.A), cores[0].held)
        assertEquals(FastForwardLevel.Fast, viewModel.uiState.value.fastForward)

        viewModel.onFastForwardToggled()
        viewModel.onConsoleTick()

        assertEquals(7, cores[0].steps)
        assertEquals(FastForwardLevel.Faster, viewModel.uiState.value.fastForward)

        viewModel.onFastForwardToggled()
        viewModel.onConsoleTick()

        assertEquals(8, cores[0].steps)
        assertEquals(FastForwardLevel.Off, viewModel.uiState.value.fastForward)

        viewModel.onFileChosen("otro.nds") { "/tmp/otro.nds" }
        viewModel.onConsoleTick()

        assertEquals(FastForwardLevel.Off, viewModel.uiState.value.fastForward)
        assertEquals(1, cores[1].steps)
    }

    @Test
    fun fastForward_staysOnWhenMenuCloses() {
        val cores = mutableListOf<RememberingCore>()
        val viewModel = EmuladorStartViewModel(
            startCore = { RememberingCore().also(cores::add) },
            autoAdvance = false,
        )
        viewModel.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        viewModel.onMenuToggled()
        viewModel.onFastForwardToggled()
        viewModel.onMenuToggled()

        assertFalse(viewModel.uiState.value.menuOpen)
        assertEquals(FastForwardLevel.Fast, viewModel.uiState.value.fastForward)

        viewModel.onConsoleTick()

        assertEquals(2, cores[0].steps)
    }

    @Test
    fun newSession_loadsSavedSlotAfterChoosingSameNds() {
        val directory = createTempDirectory("nds-state").toFile()
        val firstCores = mutableListOf<RememberingCore>()
        val first = viewModelWithStates(directory, firstCores)
        first.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        firstCores[0].token = 6
        first.onSaveState()

        val secondCores = mutableListOf<RememberingCore>()
        val second = viewModelWithStates(directory, secondCores)
        assertNull(second.uiState.value.loadedFileName)
        second.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        second.onLoadState()

        assertArrayEquals(byteArrayOf(6), secondCores[0].imported)
        assertEquals(6, second.topFrame.value!!.pixel(0, 0))
    }

    @Test
    fun newSession_multipleSlotsStillAvailable() {
        val directory = createTempDirectory("nds-state").toFile()
        val firstCores = mutableListOf<RememberingCore>()
        val first = viewModelWithStates(directory, firstCores)
        first.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        firstCores[0].token = 1
        first.onSaveState()
        first.onSlotSelected(2)
        firstCores[0].token = 2
        first.onSaveState()

        val store = NdsStateStore(directory)
        assertTrue(store.hasSlot("juego.nds", 1))
        assertTrue(store.hasSlot("juego.nds", 2))
    }

    @Test
    fun newSession_twoGamesKeepTheirOwnSlots() {
        val directory = createTempDirectory("nds-state").toFile()
        val firstCores = mutableListOf<RememberingCore>()
        val first = viewModelWithStates(directory, firstCores)
        first.onFileChosen("uno.nds") { "/tmp/uno.nds" }
        firstCores[0].token = 3
        first.onSaveState()
        first.onFileChosen("dos.nds") { "/tmp/dos.nds" }
        firstCores[1].token = 5
        first.onSaveState()

        val second = viewModelWithStates(directory, mutableListOf())
        second.onFileChosen("uno.nds") { "/tmp/uno.nds" }
        assertTrue(NdsStateStore(directory).hasSlot("uno.nds", 1))
        second.onFileChosen("dos.nds") { "/tmp/dos.nds" }
        assertTrue(NdsStateStore(directory).hasSlot("dos.nds", 1))
    }

    @Test
    fun newSession_withoutSave_loadDoesNothing() {
        val directory = createTempDirectory("nds-state").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        viewModel.onLoadState()

        assertNull(cores.single().imported)
    }

    @Test
    fun afterReinstallFromBuild_loadsSavedSlotAfterChoosingSameNds() {
        val directory = createTempDirectory("nds-state").toFile()
        val firstCores = mutableListOf<RememberingCore>()
        val first = viewModelWithStates(directory, firstCores)
        first.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        firstCores[0].token = 6
        first.onSaveState()

        val secondCores = mutableListOf<RememberingCore>()
        val second = viewModelWithStates(directory, secondCores)
        assertNull(second.uiState.value.loadedFileName)
        second.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        assertTrue(NdsStateStore(directory).hasSlot("juego.nds", 1))
        second.onLoadState()

        assertArrayEquals(byteArrayOf(6), secondCores[0].imported)
        assertEquals(6, second.topFrame.value!!.pixel(0, 0))
    }

    @Test
    fun afterReinstallFromBuild_multipleSlotsStillAvailable() {
        val directory = createTempDirectory("nds-state").toFile()
        val firstCores = mutableListOf<RememberingCore>()
        val first = viewModelWithStates(directory, firstCores)
        first.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        firstCores[0].token = 1
        first.onSaveState()
        first.onSlotSelected(2)
        firstCores[0].token = 2
        first.onSaveState()

        val store = NdsStateStore(directory)
        assertTrue(store.hasSlot("juego.nds", 1))
        assertTrue(store.hasSlot("juego.nds", 2))
    }

    @Test
    fun afterReinstallFromBuild_twoGamesKeepTheirOwnSlots() {
        val directory = createTempDirectory("nds-state").toFile()
        val firstCores = mutableListOf<RememberingCore>()
        val first = viewModelWithStates(directory, firstCores)
        first.onFileChosen("uno.nds") { "/tmp/uno.nds" }
        firstCores[0].token = 3
        first.onSaveState()
        first.onFileChosen("dos.nds") { "/tmp/dos.nds" }
        firstCores[1].token = 5
        first.onSaveState()

        val second = viewModelWithStates(directory, mutableListOf())
        second.onFileChosen("uno.nds") { "/tmp/uno.nds" }
        assertTrue(NdsStateStore(directory).hasSlot("uno.nds", 1))
        second.onFileChosen("dos.nds") { "/tmp/dos.nds" }
        assertTrue(NdsStateStore(directory).hasSlot("dos.nds", 1))
    }

    @Test
    fun afterReinstallFromBuild_withoutSave_loadDoesNothing() {
        val directory = createTempDirectory("nds-state").toFile()
        val cores = mutableListOf<RememberingCore>()
        val viewModel = viewModelWithStates(directory, cores)
        viewModel.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        viewModel.onLoadState()

        assertNull(cores.single().imported)
        assertFalse(NdsStateStore(directory).hasSlot("juego.nds", 1))
    }

    @Test
    fun withoutLoadedNds_bluetoothConnectNotOffered() {
        val viewModel = EmuladorStartViewModel(autoAdvance = false)

        assertFalse(viewModel.uiState.value.bluetoothConnectVisible)
        viewModel.onBluetoothConnect()

        assertFalse(viewModel.uiState.value.bluetoothLinked)
    }

    @Test
    fun withLoadedNds_bluetoothConnectOfferedInMenu() {
        val cores = mutableListOf<FakeCore>()
        val viewModel = EmuladorStartViewModel(
            startCore = { FakeCore().also(cores::add) },
            autoAdvance = false,
        )
        viewModel.onFileChosen("juego.nds") { "/tmp/juego.nds" }

        assertTrue(viewModel.uiState.value.bluetoothConnectVisible)
        assertFalse(viewModel.uiState.value.bluetoothLinked)
    }

    @Test
    fun pairingLink_sameNdsFile_linksBothPeers() {
        val first = PairingNdsBluetoothLink()
        val second = PairingNdsBluetoothLink()

        first.connect("juego.nds")
        assertFalse(first.linked)
        second.connect("juego.nds")

        assertTrue(first.linked)
        assertTrue(second.linked)
    }

    @Test
    fun viewModel_showsLinkedWhenPeerJoinsSameNds() {
        val link = PairingNdsBluetoothLink()
        val peer = PairingNdsBluetoothLink()
        val cores = mutableListOf<FakeCore>()
        val viewModel = EmuladorStartViewModel(
            startCore = { FakeCore().also(cores::add) },
            autoAdvance = false,
            bluetoothLink = link,
        )
        viewModel.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        link.connect("juego.nds")
        peer.connect("juego.nds")
        viewModel.refreshBluetoothLinkState()

        assertTrue(viewModel.uiState.value.bluetoothLinked)
    }

    @Test
    fun afterReinstallFromBuild_loadsFromBackupWhenInternalStorageCleared() {
        val primary = createTempDirectory("nds-primary").toFile()
        val backup = createTempDirectory("nds-backup").toFile()
        val firstCores = mutableListOf<RememberingCore>()
        val first = viewModelWithStates(primary, firstCores, backup)
        first.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        firstCores[0].token = 9
        first.onSaveState()
        primary.listFiles()?.forEach { it.delete() }

        val secondCores = mutableListOf<RememberingCore>()
        val second = viewModelWithStates(primary, secondCores, backup)
        second.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        second.onLoadState()

        assertArrayEquals(byteArrayOf(9), secondCores[0].imported)
    }

    @Test
    fun reopenedEmulator_loadsTheStateOfTheSameFile() {
        val directory = createTempDirectory("nds-state").toFile()
        val firstCores = mutableListOf<RememberingCore>()
        val first = viewModelWithStates(directory, firstCores)
        first.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        firstCores[0].token = 6
        first.onSaveState()

        val secondCores = mutableListOf<RememberingCore>()
        val second = viewModelWithStates(directory, secondCores)
        second.onFileChosen("juego.nds") { "/tmp/juego.nds" }
        second.onLoadState()

        assertArrayEquals(byteArrayOf(6), secondCores[0].imported)
        assertEquals(6, second.topFrame.value!!.pixel(0, 0))
    }

    @Test
    fun withoutNds_cheatMenuIsEmpty() {
        val viewModel = EmuladorStartViewModel(autoAdvance = false)

        assertNull(viewModel.uiState.value.loadedFileName)
        assertTrue(viewModel.uiState.value.cheatMenuItems.isEmpty())
    }

    @Test
    fun genericNds_hasNoPreparedCheats() {
        val viewModel = EmuladorStartViewModel(
            startCore = { FakeCore() },
            autoAdvance = false,
        )

        viewModel.onFileChosen("juego.nds") { "/tmp/juego.nds" }

        assertTrue(viewModel.uiState.value.cheatMenuItems.isEmpty())
    }

    @Test
    fun heartGold_listsPreparedCheatsInactive() {
        val viewModel = EmuladorStartViewModel(
            startCore = { FakeCore() },
            autoAdvance = false,
        )

        viewModel.onFileChosen("Pokemon HeartGold.nds") { "/tmp/hg.nds" }

        val items = viewModel.uiState.value.cheatMenuItems
        assertEquals(5, items.size)
        assertTrue(items.none { it.active })
        assertEquals(
            listOf("max-money", "infinite-rare-candy", "exp-x2", "exp-x4", "shiny-wild"),
            items.map { it.id },
        )
        assertTrue(items.none { it.id == "exp-x10" || it.id == "wild-mewtwo" || it.id == "wild-arceus" })
        assertTrue(items.none { it.label.contains("Mewtwo") || it.label.contains("Arceus") || it.label.contains("×10") })
    }

    @Test
    fun activatingCheat_appliesToCoreAndMarksActive() {
        val cores = mutableListOf<FakeCore>()
        val viewModel = EmuladorStartViewModel(
            startCore = { FakeCore().also(cores::add) },
            autoAdvance = false,
        )
        viewModel.onFileChosen("Pokemon HeartGold.nds") { "/tmp/hg.nds" }

        viewModel.onCheatActivated("max-money")

        assertTrue(viewModel.uiState.value.cheatMenuItems.single { it.id == "max-money" }.active)
        assertEquals(1, cores.single().activeCheats.size)
    }

    @Test
    fun secondCheat_staysActiveWithFirst() {
        val cores = mutableListOf<FakeCore>()
        val viewModel = EmuladorStartViewModel(
            startCore = { FakeCore().also(cores::add) },
            autoAdvance = false,
        )
        viewModel.onFileChosen("Pokemon HeartGold.nds") { "/tmp/hg.nds" }
        viewModel.onCheatActivated("max-money")

        viewModel.onCheatActivated("infinite-rare-candy")

        assertTrue(viewModel.uiState.value.cheatMenuItems.single { it.id == "max-money" }.active)
        assertTrue(viewModel.uiState.value.cheatMenuItems.single { it.id == "infinite-rare-candy" }.active)
        assertEquals(2, cores.single().activeCheats.size)
    }

    @Test
    fun experienceMultipliers_replaceEachOther() {
        val viewModel = EmuladorStartViewModel(
            startCore = { FakeCore() },
            autoAdvance = false,
        )
        viewModel.onFileChosen("Pokemon HeartGold.nds") { "/tmp/hg.nds" }
        viewModel.onCheatActivated("exp-x2")
        viewModel.onCheatActivated("exp-x4")

        val items = viewModel.uiState.value.cheatMenuItems
        assertFalse(items.single { it.id == "exp-x2" }.active)
        assertTrue(items.single { it.id == "exp-x4" }.active)
    }

    @Test
    fun removedCheats_doNotAppearOrActivate() {
        val cores = mutableListOf<FakeCore>()
        val viewModel = EmuladorStartViewModel(
            startCore = { FakeCore().also(cores::add) },
            autoAdvance = false,
        )
        viewModel.onFileChosen("Pokemon HeartGold.nds") { "/tmp/hg.nds" }

        viewModel.onCheatActivated("exp-x10")
        viewModel.onCheatActivated("wild-mewtwo")
        viewModel.onCheatActivated("wild-arceus")

        val items = viewModel.uiState.value.cheatMenuItems
        assertTrue(items.none { it.id in listOf("exp-x10", "wild-mewtwo", "wild-arceus") })
        assertTrue(items.none { it.active })
        assertTrue(cores.single().activeCheats.isEmpty())
    }

    @Test
    fun deactivatingCheat_clearsCoreAndMenu() {
        val cores = mutableListOf<FakeCore>()
        val viewModel = EmuladorStartViewModel(
            startCore = { FakeCore().also(cores::add) },
            autoAdvance = false,
        )
        viewModel.onFileChosen("Pokemon HeartGold.nds") { "/tmp/hg.nds" }
        viewModel.onCheatActivated("max-money")

        viewModel.onCheatDeactivated("max-money")

        assertTrue(viewModel.uiState.value.cheatMenuItems.none { it.active })
        assertTrue(cores.single().activeCheats.isEmpty())
    }

    @Test
    fun deactivatedCheat_staysOffWhilePlaying() {
        val cores = mutableListOf<FakeCore>()
        val viewModel = EmuladorStartViewModel(
            startCore = { FakeCore().also(cores::add) },
            autoAdvance = false,
        )
        viewModel.onFileChosen("Pokemon HeartGold.nds") { "/tmp/hg.nds" }
        viewModel.onCheatActivated("max-money")
        viewModel.onCheatDeactivated("max-money")

        viewModel.onConsoleTick()
        viewModel.onConsoleTick()

        assertTrue(viewModel.uiState.value.cheatMenuItems.none { it.active })
        assertTrue(cores.single().activeCheats.isEmpty())
    }

    @Test
    fun deactivatingOne_leavesOtherCheatsActive() {
        val cores = mutableListOf<FakeCore>()
        val viewModel = EmuladorStartViewModel(
            startCore = { FakeCore().also(cores::add) },
            autoAdvance = false,
        )
        viewModel.onFileChosen("Pokemon HeartGold.nds") { "/tmp/hg.nds" }
        viewModel.onCheatActivated("max-money")
        viewModel.onCheatActivated("infinite-rare-candy")

        viewModel.onCheatDeactivated("max-money")

        assertFalse(viewModel.uiState.value.cheatMenuItems.single { it.id == "max-money" }.active)
        assertTrue(viewModel.uiState.value.cheatMenuItems.single { it.id == "infinite-rare-candy" }.active)
        assertEquals(1, cores.single().activeCheats.size)
    }

    @Test
    fun deactivatingInactiveCheat_isIgnored() {
        val cores = mutableListOf<FakeCore>()
        val viewModel = EmuladorStartViewModel(
            startCore = { FakeCore().also(cores::add) },
            autoAdvance = false,
        )
        viewModel.onFileChosen("Pokemon HeartGold.nds") { "/tmp/hg.nds" }

        viewModel.onCheatDeactivated("max-money")

        assertTrue(viewModel.uiState.value.cheatMenuItems.none { it.active })
        assertTrue(cores.single().activeCheats.isEmpty())
    }

    @Test
    fun twoActive_onlyThoseMarkedOnInMenu() {
        val viewModel = EmuladorStartViewModel(
            startCore = { FakeCore() },
            autoAdvance = false,
        )
        viewModel.onFileChosen("Pokemon HeartGold.nds") { "/tmp/hg.nds" }
        viewModel.onCheatActivated("max-money")
        viewModel.onCheatActivated("infinite-rare-candy")

        val items = viewModel.uiState.value.cheatMenuItems
        assertEquals(5, items.size)
        assertEquals(2, items.count { it.active })
    }

    @Test
    fun unknownCheatId_doesNotActivate() {
        val cores = mutableListOf<FakeCore>()
        val viewModel = EmuladorStartViewModel(
            startCore = { FakeCore().also(cores::add) },
            autoAdvance = false,
        )
        viewModel.onFileChosen("Pokemon HeartGold.nds") { "/tmp/hg.nds" }
        viewModel.onCheatActivated("max-money")

        viewModel.onCheatActivated("truco-inventado")

        assertEquals(1, viewModel.uiState.value.cheatMenuItems.count { it.active })
        assertEquals(1, cores.single().activeCheats.size)
    }

    @Test
    fun newRom_clearsMultipleActiveCheatsAndReloadsList() {
        val cores = mutableListOf<FakeCore>()
        val viewModel = EmuladorStartViewModel(
            startCore = { FakeCore().also(cores::add) },
            autoAdvance = false,
        )
        viewModel.onFileChosen("Pokemon HeartGold.nds") { "/tmp/hg.nds" }
        viewModel.onCheatActivated("max-money")
        viewModel.onCheatActivated("infinite-rare-candy")

        viewModel.onFileChosen("juego.nds") { "/tmp/juego.nds" }

        assertTrue(viewModel.uiState.value.cheatMenuItems.isEmpty())
        assertTrue(viewModel.uiState.value.cheatMenuItems.none { it.active })
        assertEquals(2, cores.size)
        assertTrue(cores[0].closed)
        assertTrue(cores[1].activeCheats.isEmpty())
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

    private fun viewModelWithStates(
        directory: File,
        cores: MutableList<RememberingCore>,
        backupDirectory: File? = null,
    ): EmuladorStartViewModel =
        EmuladorStartViewModel(
            startCore = { RememberingCore().also(cores::add) },
            autoAdvance = false,
            stateStore = NdsStateStore(directory, backupDirectory),
        )

    private class RememberingCore : NdsCore {
        var token: Byte = 1
        var imported: ByteArray? = null
            private set
        var steps = 0
            private set
        var held: Set<NdsButton> = emptySet()
            private set
        private var shown: Byte = 1

        override fun step(): Pair<NdsFrame, NdsFrame> {
            steps += 1
            val pixels = IntArray(NdsFrame.WIDTH * NdsFrame.HEIGHT)
            pixels[0] = shown.toInt() and 0xFF
            val frame = NdsFrame(pixels)
            return frame to NdsFrame(pixels)
        }

        override fun hold(buttons: Set<NdsButton>) {
            held = buttons
        }

        override fun exportState(): ByteArray = byteArrayOf(token)

        override fun importState(state: ByteArray): Boolean {
            if (state.isEmpty()) return false
            imported = state.copyOf()
            shown = state[0]
            return true
        }

        var closed = false
            private set

        override fun close() {
            closed = true
        }
    }

    private class FakeCore : NdsCore {
        var closed = false
            private set
        var held: Set<NdsButton> = emptySet()
            private set
        var activeCheats: List<IntArray> = emptyList()
            private set

        override fun setActiveCheats(cheatCodes: List<IntArray>) {
            activeCheats = cheatCodes.map { it.copyOf() }
        }

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
