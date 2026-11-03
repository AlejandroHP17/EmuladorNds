package pelkidev.com.mx.emuladornds.domain

data class NdsBootAssets(
    val arm9Bios: ByteArray? = null,
    val arm7Bios: ByteArray? = null,
    val firmware: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NdsBootAssets) return false
        return arm9Bios.contentEquals(other.arm9Bios) &&
            arm7Bios.contentEquals(other.arm7Bios) &&
            firmware.contentEquals(other.firmware)
    }

    override fun hashCode(): Int {
        var result = arm9Bios?.contentHashCode() ?: 0
        result = 31 * result + (arm7Bios?.contentHashCode() ?: 0)
        result = 31 * result + (firmware?.contentHashCode() ?: 0)
        return result
    }
}

const val ARM9_BIOS_SIZE = 4096
const val ARM7_BIOS_SIZE = 16384

private val VALID_FIRMWARE_SIZES = setOf(131_072, 262_144, 524_288)

/** Solo si ambas medidas son las de BIOS de DS; si no, ninguna (FreeBIOS de reserva). */
fun bundledArmBios(arm9: ByteArray?, arm7: ByteArray?): Pair<ByteArray?, ByteArray?> {
    if (arm9?.size == ARM9_BIOS_SIZE && arm7?.size == ARM7_BIOS_SIZE) {
        return arm9 to arm7
    }
    return null to null
}

fun bundledFirmware(data: ByteArray?): ByteArray? {
    if (data == null) return null
    return if (data.size in VALID_FIRMWARE_SIZES) data else null
}

fun loadNdsBootAssets(
    arm9Raw: ByteArray?,
    arm7Raw: ByteArray?,
    firmwareRaw: ByteArray?,
): NdsBootAssets {
    val (arm9, arm7) = bundledArmBios(arm9Raw, arm7Raw)
    val firmware = bundledFirmware(firmwareRaw)
    return NdsBootAssets(arm9, arm7, firmware)
}
