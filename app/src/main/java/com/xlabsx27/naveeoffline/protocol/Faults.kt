package com.xlabsx27.naveeoffline.protocol

/** [number] is the decimal fault number; its text lives in the app's string resources (`fault_<number>`). */
data class Fault(val raw: Int, val number: Int, val code: String?)

/** NAVEE fault codes. The wire byte is BCD (0x21 = 21 = E9). Unknown codes are shown raw, never guessed. */
object NaveeFaults {
    private val TABLE = mapOf(
        10 to "E1", 11 to "E2", 12 to "E3", 14 to "H3", 15 to "H4", 18 to "H5", 21 to "E9", 24 to "F2",
        28 to "E4", 29 to "E5", 39 to "F1", 40 to "E6", 43 to "E7", 45 to "E8", 50 to "F3", 52 to "F4",
        53 to "F5", 54 to "F6", 55 to "F7", 56 to "F8", 57 to "F9", 60 to "H1", 61 to "H2",
    )

    /** Null when [raw] is 0 (no fault). */
    fun decode(raw: Int): Fault? {
        if (raw == 0) return null
        val number = ((raw shr 4) and 0x0F) * 10 + (raw and 0x0F)
        return Fault(raw, number, TABLE[number])
    }

    val all: List<Fault> get() = TABLE.map { (n, code) -> Fault(0, n, code) }
}
