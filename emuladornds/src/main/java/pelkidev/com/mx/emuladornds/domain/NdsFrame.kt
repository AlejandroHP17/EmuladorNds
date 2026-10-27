package pelkidev.com.mx.emuladornds.domain

class NdsFrame(pixels: IntArray) {
    val pixels: IntArray = pixels.copyOf()

    init {
        require(pixels.size == WIDTH * HEIGHT) {
            "Un cuadro mide $WIDTH×$HEIGHT"
        }
    }

    fun pixel(x: Int, y: Int): Int = pixels[y * WIDTH + x]

    companion object {
        const val WIDTH = 256
        const val HEIGHT = 192
    }
}

private const val STRIPE_HEIGHT = 8
private const val STRIPE_DARK = 0xFF000000.toInt()
private const val STRIPE_LIGHT = 0xFFFFFFFF.toInt()

fun patternFrame(): NdsFrame {
    val pixels = IntArray(NdsFrame.WIDTH * NdsFrame.HEIGHT)
    for (y in 0 until NdsFrame.HEIGHT) {
        val color = if ((y / STRIPE_HEIGHT) % 2 == 0) STRIPE_DARK else STRIPE_LIGHT
        pixels.fill(color, y * NdsFrame.WIDTH, (y + 1) * NdsFrame.WIDTH)
    }
    return NdsFrame(pixels)
}
