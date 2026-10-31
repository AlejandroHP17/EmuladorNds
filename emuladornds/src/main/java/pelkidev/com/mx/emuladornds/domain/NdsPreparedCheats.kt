package pelkidev.com.mx.emuladornds.domain

data class PreparedCheat(
    val id: String,
    val displayName: String,
    /** Palabras Action Replay (u32) listas para melonDS AREngine. */
    val codeWords: IntArray,
)

/** Trucos ya preparados por nombre de archivo .nds (sin inventar códigos en runtime). */
fun preparedCheatsForNds(fileName: String): List<PreparedCheat> {
    val key = fileName.trim().lowercase()
    return when {
        key.contains("heartgold") || key.contains("soulsilver") -> listOf(
            PreparedCheat(
                id = "max-money",
                displayName = "Dinero máximo",
                codeWords = intArrayOf(
                    0x94000130.toInt(),
                    0xFCFF0000.toInt(),
                    0xB2108868.toInt(),
                    0x00000000,
                    0x00009090,
                    0x00090000,
                    0xD2000000.toInt(),
                    0x00000000,
                ),
            ),
            PreparedCheat(
                id = "infinite-rare-candy",
                displayName = "Carameloraro infinito",
                codeWords = intArrayOf(
                    0x94000130.toInt(),
                    0xFCFF0000.toInt(),
                    0xB2108868.toInt(),
                    0x00000000,
                    0x00009098,
                    0x00000063,
                    0xD2000000.toInt(),
                    0x00000000,
                ),
            ),
        )
        else -> emptyList()
    }
}

fun findPreparedCheat(fileName: String, cheatId: String): PreparedCheat? =
    preparedCheatsForNds(fileName).find { it.id == cheatId }
