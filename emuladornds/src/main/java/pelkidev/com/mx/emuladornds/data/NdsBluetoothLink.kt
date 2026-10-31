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
