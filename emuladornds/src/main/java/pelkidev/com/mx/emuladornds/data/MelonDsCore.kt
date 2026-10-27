package pelkidev.com.mx.emuladornds.data

import pelkidev.com.mx.emuladornds.domain.NdsFrame

interface NdsCore {
    fun step(): Pair<NdsFrame, NdsFrame>
    fun close()
}

class MelonDsCore(rom: ByteArray) : NdsCore {
    private val topPixels = IntArray(NdsFrame.WIDTH * NdsFrame.HEIGHT)
    private val bottomPixels = IntArray(NdsFrame.WIDTH * NdsFrame.HEIGHT)
    private var handle: Long = nativeCreate(rom)

    init {
        check(handle != 0L) { "melonDS no pudo cargar el ROM" }
    }

    override fun step(): Pair<NdsFrame, NdsFrame> {
        check(handle != 0L) { "melonDS ya está cerrado" }
        nativeRunFrame(handle, topPixels, bottomPixels)
        return NdsFrame(topPixels) to NdsFrame(bottomPixels)
    }

    override fun close() {
        if (handle == 0L) return
        nativeDestroy(handle)
        handle = 0L
    }

    private external fun nativeCreate(rom: ByteArray): Long

    private external fun nativeRunFrame(handle: Long, top: IntArray, bottom: IntArray)

    private external fun nativeDestroy(handle: Long)

    companion object {
        init {
            System.loadLibrary("emuladornds_melonds")
        }
    }
}
