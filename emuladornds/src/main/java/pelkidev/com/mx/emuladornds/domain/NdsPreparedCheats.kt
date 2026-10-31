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
            experienceCheat("exp-x2", "Experiencia ×2", 2),
            experienceCheat("exp-x4", "Experiencia ×4", 4),
            experienceCheat("exp-x10", "Experiencia ×10", 10),
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
            wildEncounter("wild-mewtwo", "Encuentro: Mewtwo", 0x96),
            wildEncounter("wild-arceus", "Encuentro: Arceus", 0x1ED),
        )
        else -> emptyList()
    }
}

fun findPreparedCheat(fileName: String, cheatId: String): PreparedCheat? =
    preparedCheatsForNds(fileName).find { it.id == cheatId }

/** Experiencia USA: mantener R al cerrar el combate. Solo uno de ×2, ×4 o ×10. */
private fun experienceCheat(id: String, displayName: String, multiplier: Int): PreparedCheat {
    return PreparedCheat(
        id = id,
        displayName = displayName,
        codeWords = ar(
            0xA4000130, 0xFEFF0000,
            0x1206FADA, 0x00000068,
            0x1206FB2E, 0x0000E03C,
            0x0206FB30, 0x07808960,
            0x1206FB44, 0x00000F85,
            0xD2000000, 0x00000000,
            0x94000130, 0xFEFF0000,
            0x1206FADA, 0x0000006C,
            0x1206FB2E, 0x00002000L or multiplier.toLong(),
            0x0206FB30, 0xE03A4345,
            0x1206FB44, 0x00008965,
            0xD2000000, 0x00000000,
        ),
    )
}

/** Especie salvaje USA. El número es el de la Pokédex nacional. Solo un encuentro a la vez. */
private fun wildEncounter(id: String, displayName: String, nationalDex: Int): PreparedCheat {
    val species = nationalDex.toLong() and 0xFFFF
    return PreparedCheat(
        id = id,
        displayName = displayName,
        codeWords = ar(
            0x52247D8C, 0x582000C0,
            0x12247D8A, species,
            0x02247D8C, 0xE0021C39,
            0x02247D90, species,
            0xD2000000, 0x00000000,
        ),
    )
}

private fun ar(vararg words: Long): IntArray = IntArray(words.size) { words[it].toInt() }
