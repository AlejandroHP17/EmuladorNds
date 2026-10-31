package pelkidev.com.mx.emuladornds.data

import java.io.File

/**
 * Huecos Guardar/Cargar en el almacenamiento privado de la app ([filesDir]).
 * Sobreviven a instalar una versión nueva encima y a volver a compilar e instalar
 * (mismo [applicationId], sustitución sin desinstalar).
 *
 * Si la instalación vacía [filesDir], se leen (y se restauran) desde el espejo en
 * [getExternalFilesDir], cuando existe.
 */
class NdsStateStore internal constructor(
    val directory: File,
    private val backupDirectory: File? = null,
) {
    fun writeSlot(ndsFileName: String, slot: Int, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        writeSlotTo(directory, ndsFileName, slot, bytes)
        backupDirectory?.let { writeSlotTo(it, ndsFileName, slot, bytes) }
    }

    fun readSlot(ndsFileName: String, slot: Int): ByteArray? {
        readSlotFrom(directory, ndsFileName, slot)?.let { return it }
        val backup = backupDirectory ?: return null
        val fromBackup = readSlotFrom(backup, ndsFileName, slot) ?: return null
        writeSlotTo(directory, ndsFileName, slot, fromBackup)
        return fromBackup
    }

    fun hasSlot(ndsFileName: String, slot: Int): Boolean {
        if (slotFileIn(directory, ndsFileName, slot).isFile) return true
        val backup = backupDirectory ?: return false
        return slotFileIn(backup, ndsFileName, slot).isFile
    }

    internal fun slotFile(ndsFileName: String, slot: Int): File =
        slotFileIn(directory, ndsFileName, slot)

    private fun writeSlotTo(dir: File, ndsFileName: String, slot: Int, bytes: ByteArray) {
        dir.mkdirs()
        slotFileIn(dir, ndsFileName, slot).writeBytes(bytes)
    }

    private fun readSlotFrom(dir: File, ndsFileName: String, slot: Int): ByteArray? {
        val file = slotFileIn(dir, ndsFileName, slot)
        if (!file.isFile) return null
        return file.readBytes()
    }

    private fun slotFileIn(dir: File, ndsFileName: String, slot: Int): File {
        val safe = ndsFileName.trim().replace("/", "_").replace("\\", "_")
        val fileName = if (slot == 1) safe else "$safe.$slot"
        return File(dir, fileName)
    }

    companion object {
        const val DIRECTORY_NAME = "emuladornds-states"

        fun open(filesDir: File, externalFilesDir: File? = null): NdsStateStore {
            val primary = File(filesDir, DIRECTORY_NAME)
            val backup = externalFilesDir?.let { File(it, DIRECTORY_NAME) }
            return NdsStateStore(primary, backup)
        }
    }
}
