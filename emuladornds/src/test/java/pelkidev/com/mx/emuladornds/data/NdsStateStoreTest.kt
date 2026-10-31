package pelkidev.com.mx.emuladornds.data

import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NdsStateStoreTest {

    @Test
    fun slotWrittenBefore_isReadableAfterNewStoreInstance() {
        val directory = createTempDirectory("nds-state").toFile()
        NdsStateStore(directory).writeSlot("juego.nds", 1, byteArrayOf(7, 8))

        val afterUpdate = NdsStateStore(directory)

        assertTrue(afterUpdate.hasSlot("juego.nds", 1))
        assertArrayEquals(byteArrayOf(7, 8), afterUpdate.readSlot("juego.nds", 1))
    }

    @Test
    fun multipleSlots_persistForSameGame() {
        val directory = createTempDirectory("nds-state").toFile()
        val store = NdsStateStore(directory)
        store.writeSlot("juego.nds", 1, byteArrayOf(1))
        store.writeSlot("juego.nds", 2, byteArrayOf(2))
        store.writeSlot("juego.nds", 3, byteArrayOf(3))

        val afterUpdate = NdsStateStore(directory)

        assertTrue(afterUpdate.hasSlot("juego.nds", 1))
        assertTrue(afterUpdate.hasSlot("juego.nds", 2))
        assertTrue(afterUpdate.hasSlot("juego.nds", 3))
    }

    @Test
    fun twoGames_keepSeparateSlots() {
        val directory = createTempDirectory("nds-state").toFile()
        val store = NdsStateStore(directory)
        store.writeSlot("uno.nds", 1, byteArrayOf(10))
        store.writeSlot("dos.nds", 1, byteArrayOf(20))

        val afterUpdate = NdsStateStore(directory)

        assertArrayEquals(byteArrayOf(10), afterUpdate.readSlot("uno.nds", 1))
        assertArrayEquals(byteArrayOf(20), afterUpdate.readSlot("dos.nds", 1))
    }

    @Test
    fun emptyStore_hasNoSlots() {
        val directory = createTempDirectory("nds-state").toFile()

        val store = NdsStateStore(directory)

        assertFalse(store.hasSlot("juego.nds", 1))
        assertNull(store.readSlot("juego.nds", 1))
    }

    @Test
    fun slotReadableFromBackupWhenPrimaryMissing() {
        val primary = createTempDirectory("nds-primary").toFile()
        val backup = createTempDirectory("nds-backup").toFile()
        NdsStateStore(primary, backup).writeSlot("juego.nds", 1, byteArrayOf(4, 5))
        primary.listFiles()?.forEach { it.delete() }

        val afterReinstall = NdsStateStore(primary, backup)

        assertTrue(afterReinstall.hasSlot("juego.nds", 1))
        assertArrayEquals(byteArrayOf(4, 5), afterReinstall.readSlot("juego.nds", 1))
        assertTrue(primary.resolve("juego.nds").isFile)
    }

    @Test
    fun multipleSlots_onBackupWhenPrimaryCleared() {
        val primary = createTempDirectory("nds-primary").toFile()
        val backup = createTempDirectory("nds-backup").toFile()
        val store = NdsStateStore(primary, backup)
        store.writeSlot("juego.nds", 1, byteArrayOf(1))
        store.writeSlot("juego.nds", 2, byteArrayOf(2))
        primary.listFiles()?.forEach { it.delete() }

        val afterReinstall = NdsStateStore(primary, backup)

        assertTrue(afterReinstall.hasSlot("juego.nds", 1))
        assertTrue(afterReinstall.hasSlot("juego.nds", 2))
    }
}
