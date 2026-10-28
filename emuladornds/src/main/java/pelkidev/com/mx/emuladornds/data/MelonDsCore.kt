package pelkidev.com.mx.emuladornds.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import pelkidev.com.mx.emuladornds.domain.NdsFrame

/** Orden de bits de melonDS 1.0: A, B, Select, Start, Right, Left, Up, Down, R, L, X, Y. */
enum class NdsButton {
    A,
    B,
    SELECT,
    START,
    RIGHT,
    LEFT,
    UP,
    DOWN,
    R,
    L,
    X,
    Y,
}

interface NdsCore {
    fun step(): Pair<NdsFrame, NdsFrame>

    fun fastStep(): Pair<NdsFrame, NdsFrame> = step()
    fun hold(buttons: Set<NdsButton>) {}
    fun touch(x: Int, y: Int) {}
    fun releaseTouch() {}
    fun close()

    fun exportState(): ByteArray = byteArrayOf()

    fun importState(state: ByteArray): Boolean = false

    /** Si es true, `step()` ya espera al audio y el ViewModel no añade otra pausa. */
    val audioPaced: Boolean get() = false
}

class MelonDsCore(romPath: String) : NdsCore {
    private val topPixels = IntArray(NdsFrame.WIDTH * NdsFrame.HEIGHT)
    private val bottomPixels = IntArray(NdsFrame.WIDTH * NdsFrame.HEIGHT)
    private val pcm = ShortArray(AUDIO_BUFFER_SAMPLES * 2)
    private var handle: Long = nativeCreate(romPath)
    private var track = openTrack()
    private var produced = 0

    @Volatile
    private var keys = KEYS_RELEASED

    @Volatile
    private var touching = false

    @Volatile
    private var touchX = 0

    @Volatile
    private var touchY = 0

    override val audioPaced: Boolean
        get() = track != null

    init {
        check(handle != 0L) { "melonDS no pudo cargar el ROM" }
    }

    override fun hold(buttons: Set<NdsButton>) {
        var mask = KEYS_RELEASED
        for (button in buttons) {
            mask = mask and (1 shl button.ordinal).inv()
        }
        keys = mask and KEYS_RELEASED
    }

    override fun touch(x: Int, y: Int) {
        touchX = x.coerceIn(0, NdsFrame.WIDTH - 1)
        touchY = y.coerceIn(0, NdsFrame.HEIGHT - 1)
        touching = true
    }

    override fun releaseTouch() {
        touching = false
    }

    override fun step(): Pair<NdsFrame, NdsFrame> = runFrame(blocking = true)

    override fun fastStep(): Pair<NdsFrame, NdsFrame> = runFrame(blocking = false)

    private fun runFrame(blocking: Boolean): Pair<NdsFrame, NdsFrame> {
        check(handle != 0L) { "melonDS ya está cerrado" }
        produced = nativeRunFrame(
            handle,
            topPixels,
            bottomPixels,
            keys,
            touching,
            touchX,
            touchY,
            pcm,
        )
        playProduced(blocking)
        return NdsFrame(topPixels) to NdsFrame(bottomPixels)
    }

    private fun playProduced(blocking: Boolean) {
        val track = track ?: return
        if (produced <= 0) return
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
        val mode = if (blocking) AudioTrack.WRITE_BLOCKING else AudioTrack.WRITE_NON_BLOCKING
        track.write(pcm, 0, produced * 2, mode)
    }

    override fun exportState(): ByteArray {
        if (handle == 0L) return byteArrayOf()
        return nativeExportState(handle) ?: byteArrayOf()
    }

    override fun importState(state: ByteArray): Boolean {
        if (handle == 0L || state.isEmpty()) return false
        return nativeImportState(handle, state)
    }

    override fun close() {
        track?.let { playing ->
            if (playing.playState == AudioTrack.PLAYSTATE_PLAYING) playing.pause()
            playing.flush()
            playing.release()
        }
        track = null
        if (handle == 0L) return
        nativeDestroy(handle)
        handle = 0L
    }

    private external fun nativeCreate(romPath: String): Long

    private external fun nativeRunFrame(
        handle: Long,
        top: IntArray,
        bottom: IntArray,
        keys: Int,
        touching: Boolean,
        touchX: Int,
        touchY: Int,
        audioOut: ShortArray,
    ): Int

    private external fun nativeExportState(handle: Long): ByteArray?

    private external fun nativeImportState(handle: Long, state: ByteArray): Boolean

    private external fun nativeDestroy(handle: Long)

    private companion object {
        const val AUDIO_BUFFER_SAMPLES = 1608
        const val SAMPLE_RATE = 48000
        const val BYTES_PER_STEREO_FRAME = 4
        /** 12 bits a 1 = ningún control pulsado (SetKeyMask active-low). */
        const val KEYS_RELEASED = 0xFFF

        init {
            System.loadLibrary("emuladornds_melonds")
        }

        fun openTrack(): AudioTrack? {
            val min = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (min <= 0) return null
            val size = maxOf(min, AUDIO_BUFFER_SAMPLES * BYTES_PER_STEREO_FRAME)
            return try {
                val created = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(size)
                    .build()
                if (created.state == AudioTrack.STATE_INITIALIZED) {
                    created
                } else {
                    created.release()
                    null
                }
            } catch (_: RuntimeException) {
                null
            }
        }
    }
}
