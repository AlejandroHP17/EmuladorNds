package pelkidev.com.mx.emuladornds.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class NdsFrameTest {

    @Test
    fun frame_measures256By192() {
        val frame = NdsFrame(IntArray(NdsFrame.WIDTH * NdsFrame.HEIGHT))

        assertEquals(256, NdsFrame.WIDTH)
        assertEquals(192, NdsFrame.HEIGHT)
        assertEquals(256 * 192, frame.pixels.size)
    }

    @Test
    fun pixel_isTheOneStoredAtThatPosition() {
        val pixels = IntArray(NdsFrame.WIDTH * NdsFrame.HEIGHT)
        val color = 0xFF112233.toInt()
        pixels[1 * NdsFrame.WIDTH + 2] = color

        val frame = NdsFrame(pixels)

        assertEquals(color, frame.pixel(2, 1))
    }

    @Test
    fun pattern_alternatesEveryEightRows() {
        val frame = patternFrame()

        assertEquals(0xFF000000.toInt(), frame.pixel(0, 0))
        assertEquals(0xFF000000.toInt(), frame.pixel(NdsFrame.WIDTH - 1, 7))
        assertEquals(0xFFFFFFFF.toInt(), frame.pixel(0, 8))
        assertEquals(0xFFFFFFFF.toInt(), frame.pixel(128, 15))
        assertEquals(0xFF000000.toInt(), frame.pixel(0, 16))
    }

    @Test
    fun otherSize_isNotAFrame() {
        try {
            NdsFrame(IntArray(10))
            fail("Un cuadro de otro tamaño no es válido")
        } catch (_: IllegalArgumentException) {
        }
    }
}
