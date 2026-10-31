package pelkidev.com.mx.emuladornds.data

/**
 * Enlace Bluetooth entre dos teléfonos con este emulador. No transfiere la ROM;
 * cada lado usa su copia local del mismo juego ([matchingGameId]).
 */
interface NdsBluetoothLink {
    val linked: Boolean

    fun connect(matchingGameId: String)

    fun disconnect()
}

object NoOpNdsBluetoothLink : NdsBluetoothLink {
    override val linked: Boolean = false

    override fun connect(matchingGameId: String) = Unit

    override fun disconnect() = Unit
}

/**
 * Enlace en memoria para tests: dos instancias con el mismo [matchingGameId] quedan enlazadas.
 */
class PairingNdsBluetoothLink : NdsBluetoothLink {
    private val linkedFlag = java.util.concurrent.atomic.AtomicBoolean(false)
    override val linked: Boolean
        get() = linkedFlag.get()

    override fun connect(matchingGameId: String) {
        val gameId = matchingGameId.trim()
        if (gameId.isEmpty()) return
        synchronized(waitQueue) {
            val waiting = waitQueue[gameId]
            if (waiting == null) {
                waitQueue[gameId] = this
                return
            }
            waitQueue.remove(gameId)
            waiting.linkedFlag.set(true)
            linkedFlag.set(true)
        }
    }

    override fun disconnect() {
        synchronized(waitQueue) {
            waitQueue.entries.removeIf { it.value === this }
            linkedFlag.set(false)
        }
    }

    companion object {
        private val waitQueue = mutableMapOf<String, PairingNdsBluetoothLink>()
    }
}
