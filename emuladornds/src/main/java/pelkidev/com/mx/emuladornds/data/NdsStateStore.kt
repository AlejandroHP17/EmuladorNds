package pelkidev.com.mx.emuladornds.data

import java.io.File

/**
 * Huecos Guardar/Cargar en el almacenamiento privado de la app ([filesDir]).
 * Sobreviven a instalar una versión nueva encima (mismo applicationId).
 */
class NdsStateStore internal constructor(
    val directory: File,
) {
    fun writeSlot(ndsFileName: String, slot: Int, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        directory.mkdirs()
        slotFile(ndsFileName, slot).writeBytes(bytes)
    }

    fun readSlot(ndsFileName: String, slot: Int): ByteArray? {
        val file = slotFile(ndsFileName, slot)
        if (!file.isFile) return null
        return file.readBytes()
    }

    fun hasSlot(ndsFileName: String, slot: Int): Boolean =
        slotFile(ndsFileName, slot).isFile

    internal fun slotFile(ndsFileName: String, slot: Int): File {
        val safe = ndsFileName.trim().replace("/", "_").replace("\\", "_")
        val fileName = if (slot == 1) safe else "$safe.$slot"
        return File(directory, fileName)
    }

    companion object {
        const val DIRECTORY_NAME = "emuladornds-states"

        fun open(filesDir: File): NdsStateStore =
            NdsStateStore(File(filesDir, DIRECTORY_NAME))
    }
}
