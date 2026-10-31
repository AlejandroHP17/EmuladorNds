package pelkidev.com.mx.emuladornds.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Enlace Bluetooth entre dos teléfonos con este emulador. No transfiere la ROM;
 * cada lado usa su copia local del mismo juego ([matchingGameId]).
 * Tras enlazar, [sendMp] / [recvMp] transportan los paquetes del multijugador
 * que melonDS ya genera (no el archivo .nds).
 */
interface NdsBluetoothLink {
    val linked: Boolean

    fun connect(matchingGameId: String)

    fun disconnect()

    fun sendMp(frame: ByteArray): Boolean = false

    fun recvMp(): ByteArray? = null
}

object NoOpNdsBluetoothLink : NdsBluetoothLink {
    override val linked: Boolean = false

    override fun connect(matchingGameId: String) = Unit

    override fun disconnect() = Unit
}

data class NdsMpFrame(
    val senderId: Int,
    val type: Int,
    val timestamp: Long,
    val payload: ByteArray,
)

object NdsMpCodec {
    const val MAGIC = 0x4946494E
    const val HEADER_SIZE = 24

    fun encode(frame: NdsMpFrame): ByteArray {
        val payload = frame.payload
        val buffer = ByteBuffer.allocate(HEADER_SIZE + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(MAGIC)
        buffer.putInt(frame.senderId)
        buffer.putInt(frame.type)
        buffer.putInt(payload.size)
        buffer.putLong(frame.timestamp)
        buffer.put(payload)
        return buffer.array()
    }

    fun decode(bytes: ByteArray): NdsMpFrame? {
        if (bytes.size < HEADER_SIZE) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.int != MAGIC) return null
        val senderId = buffer.int
        val type = buffer.int
        val length = buffer.int.coerceAtLeast(0)
        val timestamp = buffer.long
        if (bytes.size < HEADER_SIZE + length) return null
        val payload = ByteArray(length)
        if (length > 0) {
            buffer.get(payload)
        }
        return NdsMpFrame(senderId, type, timestamp, payload)
    }
}

/**
 * Enlace en memoria para tests: dos instancias con el mismo [matchingGameId] quedan
 * enlazadas y se intercambian paquetes MP (nunca la ROM).
 */
class PairingNdsBluetoothLink : NdsBluetoothLink {
    private val linkedFlag = java.util.concurrent.atomic.AtomicBoolean(false)
    private val inbound = ArrayDeque<ByteArray>()
    private var peer: PairingNdsBluetoothLink? = null

    override val linked: Boolean
        get() = linkedFlag.get()

    override fun connect(matchingGameId: String) {
        val gameId = matchingGameId.trim()
        if (gameId.isEmpty()) return
        disconnect()
        synchronized(waitQueue) {
            val waiting = waitQueue[gameId]
            if (waiting == null) {
                waitQueue[gameId] = this
                return
            }
            waitQueue.remove(gameId)
            waiting.peer = this
            peer = waiting
            waiting.linkedFlag.set(true)
            linkedFlag.set(true)
        }
    }

    override fun disconnect() {
        synchronized(waitQueue) {
            waitQueue.entries.removeIf { it.value === this }
            peer?.let { other ->
                if (other.peer === this) other.peer = null
                other.linkedFlag.set(false)
            }
            peer = null
            linkedFlag.set(false)
            synchronized(inbound) { inbound.clear() }
        }
    }

    override fun sendMp(frame: ByteArray): Boolean {
        if (frame.isEmpty() || !linked) return false
        val other = peer ?: return false
        synchronized(other.inbound) { other.inbound.addLast(frame.copyOf()) }
        return true
    }

    override fun recvMp(): ByteArray? {
        synchronized(inbound) {
            return if (inbound.isEmpty()) null else inbound.removeFirst()
        }
    }

    companion object {
        private val waitQueue = mutableMapOf<String, PairingNdsBluetoothLink>()
    }
}
