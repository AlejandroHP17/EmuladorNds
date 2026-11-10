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
                id = "shiny-wild",
                displayName = "Salvajes variocolor",
                codeWords = ar(
                    0x621D1584, 0x00000000,
                    0x0206DEFC, 0x47004800,
                    0x0206DF00, 0x02000001,
                    0xE2000000, 0x0000003C,
                    0x6800480D, 0x490B6840,
                    0x88011808, 0x9C0D8842,
                    0x40510C24, 0x20074061,
                    0x04244041, 0x940D430C,
                    0x21001C28, 0xF06EAA0D,
                    0x4801FEA3, 0x46C04700,
                    0x0206DF05, 0x000000DC,
                    0x021D1584, 0x00000000,
                    0xD2000000, 0x00000000,
                ),
            ),
        )
        else -> emptyList()
    }
}

fun findPreparedCheat(fileName: String, cheatId: String): PreparedCheat? =
    preparedCheatsForNds(fileName).find { it.id == cheatId }

private fun ar(vararg words: Long): IntArray = IntArray(words.size) { words[it].toInt() }
