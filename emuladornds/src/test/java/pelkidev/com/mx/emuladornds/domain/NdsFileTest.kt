package pelkidev.com.mx.emuladornds.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NdsFileTest {

    @Test
    fun nameEndingInNds_isAnNdsFile() {
        assertTrue(isNdsFileName("Pokemon HeartGold.nds"))
    }

    @Test
    fun extension_ignoresCase() {
        assertTrue(isNdsFileName("juego.NDS"))
        assertTrue(isNdsFileName("juego.Nds"))
    }

    @Test
    fun otherExtension_isNotAnNdsFile() {
        assertFalse(isNdsFileName("notas.txt"))
        assertFalse(isNdsFileName("juego.gba"))
        assertFalse(isNdsFileName("juego.nds.txt"))
    }

    @Test
    fun blankName_isNotAnNdsFile() {
        assertFalse(isNdsFileName("   "))
    }
}
