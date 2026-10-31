package pelkidev.com.mx.emuladornds.domain

data class PreparedCheat(
    val id: String,
    val displayName: String,
)

/** Trucos ya preparados por nombre de archivo .nds (sin inventar códigos en runtime). */
fun preparedCheatsForNds(fileName: String): List<PreparedCheat> {
    val key = fileName.trim().lowercase()
    return when {
        key.contains("heartgold") || key.contains("soulsilver") -> listOf(
            PreparedCheat(id = "max-money", displayName = "Dinero máximo"),
            PreparedCheat(id = "infinite-rare-candy", displayName = "Carameloraro infinito"),
        )
        else -> emptyList()
    }
}
