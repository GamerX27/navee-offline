package com.xlabsx27.naveeoffline.protocol

/** Model detection from the product id embedded in the serial number. */
object NaveeModels {
    private val NAMES = mapOf(
        "2213" to "N65i", "2314" to "V25/V25i", "2322" to "S40", "2326" to "V3 Pro",
        "2327" to "V25 Pro/V25i Pro", "2328" to "V40i/V40i Pro", "2329" to "V50i Pro", "2334" to "S60",
        "2345" to "ST3 Pro", "2353" to "P50", "2401" to "ST3", "2402" to "GT3", "2403" to "GT3 Pro",
        "2416" to "XT5 Pro", "2417" to "E20", "2418" to "GT3 Max", "2422" to "E25", "2435" to "Birdie 3",
        "2436" to "V25i Pro II", "2437" to "V40i Pro II", "2438" to "V50i Pro II", "2441" to "ST5 Pro",
        "2442" to "G5", "2443" to "XT5 Ultra", "2449" to "NT5 Ultra X", "2504" to "K100",
        "2505" to "K100 Pro", "2506" to "K100 Max", "2509" to "N65i II", "2515" to "Birdie 3x",
        "2517" to "ST5 Max", "2518" to "G5 Pro", "2519" to "G5 Max", "2529" to "XT5 Max", "2536" to "S2",
        "2538" to "UT5 Max", "2543" to "NT5 Max", "2545" to "GT5 Pro", "2546" to "GT5 Max",
        "2547" to "UT5 Ultra", "2573" to "E25 Go", "2585" to "UT5 Ultra X", "2611" to "E45 Pro",
        "2612" to "E60 Pro", "2614" to "S2", "2619" to "UT3 Pro", "2620" to "UT3 Max", "2623" to "V45i",
        "2634" to "E20 Lite", "2643" to "E60 Pro", "2646" to "UT3", "2657" to "NT5 Max+",
        "2658" to "NT5 Ultra", "2701" to "NT3 Pro", "2704" to "GT3 Pro", "2707" to "NT5 Turbo",
        "2714" to "ST3 Pro", "2736" to "KG05", "2739" to "WOLF X", "2745" to "NT3 Max",
        "2753" to "E45 Pro", "2754" to "E60 Pro", "2768" to "EXO S Pro", "2782" to "XT5",
    )

    /** Models whose firmware is known to lack cruise control or the kick-start setting. */
    private val NO_CRUISE = setOf("2329", "2435", "2436", "2438", "2515", "2768")
    private val NO_ZERO_START = setOf(
        "2322", "2329", "2334", "2417", "2422", "2435", "2436", "2437", "2438", "2515", "2768",
    )

    fun name(pid: String?): String? = pid?.let(NAMES::get)

    /** Unknown models get every control: the scooter simply ignores what it does not support. */
    fun hasCruise(pid: String?) = pid !in NO_CRUISE
    fun hasZeroStart(pid: String?) = pid !in NO_ZERO_START

    /**
     * Allowed kick-start levels. US-region meters accept 0..5; everywhere else the meter clamps
     * anything below 3, so only 3..5 are offered.
     */
    fun zeroStartLevels(region: String?): IntRange =
        if (region?.uppercase() in setOf("US", "CN", "RU")) 0..5 else 3..5
}
