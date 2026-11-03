package pelkidev.com.mx.emuladornds.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NdsBootAssetsTest {

    @Test
    fun bundledArmBios_acceptsExactSizes() {
        val arm9 = ByteArray(ARM9_BIOS_SIZE) { 9 }
        val arm7 = ByteArray(ARM7_BIOS_SIZE) { 7 }

        val (parsed9, parsed7) = bundledArmBios(arm9, arm7)

        assertSame(arm9, parsed9)
        assertSame(arm7, parsed7)
    }

    @Test
    fun bundledArmBios_rejectsPartialPair() {
        val arm9 = ByteArray(ARM9_BIOS_SIZE)

        val (parsed9, parsed7) = bundledArmBios(arm9, null)

        assertNull(parsed9)
        assertNull(parsed7)
    }

    @Test
    fun bundledFirmware_accepts128256512K() {
        assertEquals(131_072, bundledFirmware(ByteArray(131_072))?.size)
        assertEquals(262_144, bundledFirmware(ByteArray(262_144))?.size)
        assertEquals(524_288, bundledFirmware(ByteArray(524_288))?.size)
    }

    @Test
    fun bundledFirmware_rejectsOtherSizes() {
        assertNull(bundledFirmware(ByteArray(100)))
        assertNull(bundledFirmware(null))
    }

    @Test
    fun loadNdsBootAssets_combinesValidPieces() {
        val arm9 = ByteArray(ARM9_BIOS_SIZE) { 1 }
        val arm7 = ByteArray(ARM7_BIOS_SIZE) { 2 }
        val firmware = ByteArray(262_144) { 3 }

        val boot = loadNdsBootAssets(arm9, arm7, firmware)

        assertArrayEquals(arm9, boot.arm9Bios)
        assertArrayEquals(arm7, boot.arm7Bios)
        assertArrayEquals(firmware, boot.firmware)
    }
}
