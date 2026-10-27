package pelkidev.com.mx.emuladornds.domain

fun isNdsFileName(fileName: String): Boolean =
    fileName.trim().endsWith(".nds", ignoreCase = true)
