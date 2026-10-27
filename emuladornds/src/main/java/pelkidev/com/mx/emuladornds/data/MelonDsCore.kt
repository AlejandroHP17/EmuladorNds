package pelkidev.com.mx.emuladornds.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import pelkidev.com.mx.emuladornds.domain.NdsFrame

interface NdsCore {
    fun step(): Pair<NdsFrame, NdsFrame>
    fun close()
}

class MelonDsCore(romPath: String) : NdsCore {
    private val topPixels = IntArray(NdsFrame.WIDTH * NdsFrame.HEIGHT)
    private val bottomPixels = IntArray(NdsFrame.WIDTH * NdsFrame.HEIGHT)
    private val pcm = ShortArray(AUDIO_BUFFER_SAMPLES * 2)
    private var handle: Long = nativeCreate(romPath)
    private var track = openTrack()
    private var produced = 0

    init {
        check(handle != 0L) { "melonDS no pudo cargar el ROM" }
    }

    override fun step(): Pair<NdsFrame, NdsFrame> {
        check(handle != 0L) { "melonDS ya está cerrado" }
        produced = nativeRunFrame(handle, topPixels, bottomPixels, pcm)
        playProduced()
        return NdsFrame(topPixels) to NdsFrame(bottomPixels)
    }

    private fun playProduced() {
        val track = track ?: return
        if (produced <= 0) return
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
        track.write(pcm, 0, produced * 2, AudioTrack.WRITE_BLOCKING)
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
        audioOut: ShortArray,
    ): Int

    private external fun nativeDestroy(handle: Long)

    private companion object {
        const val AUDIO_BUFFER_SAMPLES = 1608
        const val SAMPLE_RATE = 48000
        const val BYTES_PER_STEREO_FRAME = 4

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
