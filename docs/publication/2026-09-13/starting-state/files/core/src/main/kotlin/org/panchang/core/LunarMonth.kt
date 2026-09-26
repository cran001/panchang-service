package org.panchang.core

/**
 * Which new-or-full moon starts a named lunar month.
 *
 * Not an implementation detail and not a default anyone should be allowed to assume: the two
 * conventions disagree about the *name* of every Krishna paksha in the year, so a rule written
 * as "Krishna Ekadashi of Margashirsha" picks out a different fortnight depending on which is
 * in force. Gujarat and most of the south reckon amanta; the Hindi belt reckons purnimanta. The
 * acharya-day recovery work downstream depends on getting this explicit and right, so it is a
 * required parameter rather than a constant.
 */
enum class MonthReckoning {
    /** Month runs new moon to new moon. Shukla paksha first, then Krishna. */
    AMANTA,

    /** Month runs full moon to full moon. Krishna paksha first, then Shukla. */
    PURNIMANTA,
}

/**
 * A named lunar month, with the paksha running at the reference instant.
 *
 * @param index 0 = Chaitra … 11 = Phalguna.
 * @param isAdhika true when this is an intercalary (adhika / mala) month. Adhika status is a
 *   property of the *amanta* cycle — it is defined by whether the Sun changes rashi between two
 *   new moons — and is reported unchanged under either reckoning.
 * @param startJdUt start of the month under [reckoning]: the new moon for amanta, the full moon
 *   for purnimanta.
 */
data class LunarMonth(
    val index: Int,
    val name: String,
    val reckoning: MonthReckoning,
    val paksha: Paksha,
    val isAdhika: Boolean,
    val startJdUt: Double,
    val endJdUt: Double,
) {
    init {
        require(index in 0..11) { "lunar month index must be in 0..11, was $index" }
        require(endJdUt > startJdUt) { "lunar month has non-positive duration" }
    }

    companion object {
        val NAMES: List<String> = listOf(
            "Chaitra", "Vaishakha", "Jyeshtha", "Ashadha", "Shravana", "Bhadrapada",
            "Ashvina", "Kartika", "Margashirsha", "Pausha", "Magha", "Phalguna",
        )

        /**
         * Display name, prefixed for an intercalary month. "Adhika" rather than "Adhik" or
         * "Mala"; all three are current, and the rules module should match on [index] and
         * [isAdhika] rather than on this string.
         */
        fun displayName(index: Int, isAdhika: Boolean): String =
            if (isAdhika) "Adhika ${NAMES[index]}" else NAMES[index]
    }
}
