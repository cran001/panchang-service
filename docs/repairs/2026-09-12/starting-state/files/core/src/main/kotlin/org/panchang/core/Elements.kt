package org.panchang.core

/**
 * Common shape of the four angular panchanga elements.
 *
 * The two boundary instants are the point of these types. The engine this replaces reported
 * only "which one is running at sunrise" plus a crude search for the next end; but every sect
 * rule, every vrata qualification and every parana window is stated as a relation between an
 * element boundary and a solar event, so a value that does not carry its own start and end is
 * not usable input for the rule layer.
 *
 * Both instants are Julian Day in **UT**, not TT.
 */
sealed interface PanchangaElement {
    /** Zero-based index within the element's cycle. */
    val index: Int

    /** Traditional Sanskrit name, transliterated without diacritics. */
    val name: String

    /** Instant the element began, Julian Day (UT). Always strictly before [endJdUt]. */
    val startJdUt: Double

    /** Instant the element ends, Julian Day (UT). */
    val endJdUt: Double

    /**
     * How far through the element the reference instant was, as a fraction of the element's
     * *angular* span — in `[0, 1)`. Angular, not temporal: the two differ by up to a few
     * percent because the Moon's speed varies within a single tithi.
     */
    val elapsedFraction: Double
}

/** Lunar fortnight. Shukla is waxing (elongation 0°–180°), Krishna waning (180°–360°). */
enum class Paksha(val displayName: String) {
    SHUKLA("Shukla"),
    KRISHNA("Krishna"),
}

/**
 * A tithi: one thirtieth of the synodic month, the interval in which the Moon−Sun elongation
 * sweeps 12°.
 *
 * Elongation is a *difference* of longitudes, so the ayanamsha cancels exactly and a tithi is
 * the same instant in every sidereal convention. This module therefore computes it tropically
 * and does not subtract an ayanamsha at all — the app engine subtracted it from both terms,
 * which is harmless but obscures the invariant.
 *
 * @param index 0–29. 0–14 are Shukla Pratipada through Purnima, 15–29 Krishna Pratipada
 *   through Amavasya.
 * @param numberInPaksha 1–15, the number by which the tithi is normally cited.
 */
data class Tithi(
    override val index: Int,
    override val name: String,
    val numberInPaksha: Int,
    val paksha: Paksha,
    override val startJdUt: Double,
    override val endJdUt: Double,
    override val elapsedFraction: Double,
) : PanchangaElement {

    init {
        require(index in 0..29) { "tithi index must be in 0..29, was $index" }
        require(endJdUt > startJdUt) { "tithi $index has non-positive duration" }
        require(elapsedFraction in 0.0..1.0) { "elapsedFraction out of range: $elapsedFraction" }
    }

    companion object {
        const val COUNT: Int = 30

        /** Degrees of elongation per tithi. */
        const val SPAN_DEGREES: Double = 360.0 / COUNT

        /**
         * The fifteen names, cycled across both pakshas. Index 29 is *not* Purnima: it is
         * Amavasya, the new moon. `29 % 15 == 14` makes the naive table lookup print "Purnima"
         * for the new moon, which the app engine had to special-case after shipping it.
         */
        val NAMES: List<String> = listOf(
            "Pratipada", "Dvitiya", "Tritiya", "Chaturthi", "Panchami",
            "Shashthi", "Saptami", "Ashtami", "Navami", "Dashami",
            "Ekadashi", "Dvadashi", "Trayodashi", "Chaturdashi", "Purnima",
        )

        fun nameOf(index: Int): String = when (index) {
            29 -> "Amavasya"
            else -> NAMES[index % 15]
        }

        fun pakshaOf(index: Int): Paksha = if (index < 15) Paksha.SHUKLA else Paksha.KRISHNA
    }
}

/**
 * A nakshatra: one of 27 equal 13°20′ sectors of the sidereal zodiac, indexed by the Moon's
 * sidereal longitude.
 *
 * @param pada quarter of the nakshatra, 1–4.
 */
data class Nakshatra(
    override val index: Int,
    override val name: String,
    val pada: Int,
    override val startJdUt: Double,
    override val endJdUt: Double,
    override val elapsedFraction: Double,
) : PanchangaElement {

    init {
        require(index in 0..26) { "nakshatra index must be in 0..26, was $index" }
        require(pada in 1..4) { "pada must be in 1..4, was $pada" }
        require(endJdUt > startJdUt) { "nakshatra $index has non-positive duration" }
    }

    companion object {
        const val COUNT: Int = 27
        const val SPAN_DEGREES: Double = 360.0 / COUNT

        val NAMES: List<String> = listOf(
            "Ashvini", "Bharani", "Krittika", "Rohini", "Mrigashira",
            "Ardra", "Punarvasu", "Pushya", "Ashlesha", "Magha",
            "Purva Phalguni", "Uttara Phalguni", "Hasta", "Chitra", "Svati",
            "Vishakha", "Anuradha", "Jyeshtha", "Mula", "Purva Ashadha",
            "Uttara Ashadha", "Shravana", "Dhanishta", "Shatabhisha", "Purva Bhadrapada",
            "Uttara Bhadrapada", "Revati",
        )
    }
}

/**
 * A yoga: one of 27 equal 13°20′ divisions of the **sum** of the sidereal longitudes of Sun
 * and Moon.
 *
 * Unlike tithi, the sum does not cancel the ayanamsha — it doubles it — so yoga boundaries do
 * depend on which [Ayanamsha] is configured.
 *
 * [isInauspicious] flags the nine yogas conventionally avoided for auspicious work. That is a
 * widely shared muhurta convention rather than a sectarian rule, so it stays here; anything
 * sampradaya-specific belongs in the rules module.
 */
data class Yoga(
    override val index: Int,
    override val name: String,
    val isInauspicious: Boolean,
    override val startJdUt: Double,
    override val endJdUt: Double,
    override val elapsedFraction: Double,
) : PanchangaElement {

    init {
        require(index in 0..26) { "yoga index must be in 0..26, was $index" }
        require(endJdUt > startJdUt) { "yoga $index has non-positive duration" }
    }

    companion object {
        const val COUNT: Int = 27
        const val SPAN_DEGREES: Double = 360.0 / COUNT

        val NAMES: List<String> = listOf(
            "Vishkambha", "Priti", "Ayushman", "Saubhagya", "Shobhana", "Atiganda",
            "Sukarma", "Dhriti", "Shula", "Ganda", "Vriddhi", "Dhruva", "Vyaghata",
            "Harshana", "Vajra", "Siddhi", "Vyatipata", "Variyan", "Parigha", "Shiva",
            "Siddha", "Sadhya", "Shubha", "Shukla", "Brahma", "Indra", "Vaidhriti",
        )

        val INAUSPICIOUS: Set<String> = setOf(
            "Vishkambha", "Atiganda", "Shula", "Ganda", "Vyaghata", "Vajra",
            "Vyatipata", "Parigha", "Vaidhriti",
        )
    }
}

/**
 * A karana: half a tithi, 6° of elongation. Sixty per lunar month.
 *
 * Index 0 is the fixed karana Kimstughna; indices 1–56 cycle the seven movable karanas
 * Bava…Vishti eight times; 57–59 are the fixed Shakuni, Chatushpada and Naga.
 */
data class Karana(
    override val index: Int,
    override val name: String,
    val isInauspicious: Boolean,
    override val startJdUt: Double,
    override val endJdUt: Double,
    override val elapsedFraction: Double,
) : PanchangaElement {

    init {
        require(index in 0..59) { "karana index must be in 0..59, was $index" }
        require(endJdUt > startJdUt) { "karana $index has non-positive duration" }
    }

    companion object {
        const val COUNT: Int = 60
        const val SPAN_DEGREES: Double = 360.0 / COUNT

        /** Seven movable karanas followed by the four fixed ones. */
        val NAMES: List<String> = listOf(
            "Bava", "Balava", "Kaulava", "Taitila", "Garaja", "Vanija", "Vishti",
            "Shakuni", "Chatushpada", "Naga", "Kimstughna",
        )

        val INAUSPICIOUS: Set<String> = setOf("Vishti")

        fun nameOf(index: Int): String = when (index) {
            0 -> "Kimstughna"
            in 1..56 -> NAMES[(index - 1) % 7]
            57 -> "Shakuni"
            58 -> "Chatushpada"
            else -> "Naga"
        }
    }
}

/** The twelve sidereal solar months (rashi), 0 = Mesha. */
object Rashi {
    const val COUNT: Int = 12
    const val SPAN_DEGREES: Double = 360.0 / COUNT

    val NAMES: List<String> = listOf(
        "Mesha", "Vrishabha", "Mithuna", "Karka", "Simha", "Kanya",
        "Tula", "Vrishchika", "Dhanu", "Makara", "Kumbha", "Meena",
    )

    /** Rashi index of a sidereal longitude in `[0, 360)`. */
    fun indexOf(siderealLongitudeDeg: Double): Int =
        (siderealLongitudeDeg / SPAN_DEGREES).toInt().coerceIn(0, COUNT - 1)
}
