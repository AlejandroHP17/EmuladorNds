package pelkidev.com.mx.emuladornds.domain

data class NdsMoment(
    val top: NdsFrame,
    val bottom: NdsFrame,
)

class NdsPace {
    private var steps = 0

    val consoleTimeNanos: Long
        get() = steps * NANOS_PER_STEP

    fun step(loaded: Boolean): NdsMoment? {
        if (!loaded) return null
        steps += 1
        val top = frameAt(steps)
        return NdsMoment(top = top, bottom = NdsFrame(top.pixels))
    }

    companion object {
        const val STEPS_PER_SECOND = 60
        const val NANOS_PER_STEP = 1_000_000_000L / STEPS_PER_SECOND
    }
}

private const val MARK = 0xFFFF3300.toInt()

private fun frameAt(step: Int): NdsFrame {
    val pixels = IntArray(NdsFrame.WIDTH * NdsFrame.HEIGHT)
    val column = (step - 1) % NdsFrame.WIDTH
    for (y in 0 until NdsFrame.HEIGHT) {
        pixels[y * NdsFrame.WIDTH + column] = MARK
    }
    return NdsFrame(pixels)
}
