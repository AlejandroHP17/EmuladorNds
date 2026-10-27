package pelkidev.com.mx.emuladornds.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NdsPaceTest {

    @Test
    fun withoutRom_doesNotStep() {
        val pace = NdsPace()

        assertNull(pace.step(loaded = false))
        assertEquals(0L, pace.consoleTimeNanos)
    }

    @Test
    fun oneStep_changesBothScreensTogether() {
        val pace = NdsPace()
        val pattern = patternFrame()

        val moment = pace.step(loaded = true)!!

        assertEquals(NdsPace.NANOS_PER_STEP, pace.consoleTimeNanos)
        assertEquals(moment.top.pixel(0, 0), moment.bottom.pixel(0, 0))
        assertEquals(moment.top.pixel(0, NdsFrame.HEIGHT - 1), moment.bottom.pixel(0, NdsFrame.HEIGHT - 1))
        assertNotEquals(pattern.pixel(0, 0), moment.top.pixel(0, 0))
    }

    @Test
    fun nextStep_movesBothScreensAndSkipsIdleTime() {
        val pace = NdsPace()
        val first = pace.step(loaded = true)!!

        assertNull(pace.step(loaded = false))
        val second = pace.step(loaded = true)!!

        assertEquals(2 * NdsPace.NANOS_PER_STEP, pace.consoleTimeNanos)
        assertNotEquals(first.top.pixel(0, 10), second.top.pixel(0, 10))
        assertEquals(second.top.pixel(1, 10), second.bottom.pixel(1, 10))
        assertEquals(second.top.pixel(0, 10), second.bottom.pixel(0, 10))
    }
}
