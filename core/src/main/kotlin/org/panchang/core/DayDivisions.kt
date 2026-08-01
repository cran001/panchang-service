package org.panchang.core

import java.time.DayOfWeek
import java.time.LocalDate

/** A half-open interval of time, both bounds Julian Day (UT). */
data class JdInterval(val startJdUt: Double, val endJdUt: Double) {
    init {
        require(startJdUt.isFinite() && endJdUt.isFinite()) { "interval bounds must be finite" }
        require(endJdUt >= startJdUt) { "interval end precedes start" }
    }

    val durationDays: Double get() = endJdUt - startJdUt

    operator fun contains(jdUt: Double): Boolean = jdUt >= startJdUt && jdUt < endJdUt
}

/**
 * The seven weekdays of the Vedic week.
 *
 * The vaar is derived from the civil date **in the site's own zone**. The app engine read
 * `Calendar.getInstance()`, which is the JVM default zone — so a server in UTC computing a
 * panchang for Auckland got the wrong weekday for the eleven hours of every day when the two
 * dates disagree, and therefore the wrong Rahu Kaal slot. Nothing in this module can reach a
 * default zone: the zone arrives inside [GeoLocation] and there is no zero-argument path.
 *
 * A vaar strictly begins at sunrise, not at midnight, so an instant between midnight and
 * sunrise still belongs to the previous vaar. [atInstant] applies that; [ofLocalDate] does not
 * and is the right choice when naming the weekday of a whole civil day.
 */
enum class Vaar(val displayName: String, val dayOfWeek: DayOfWeek) {
    RAVIVARA("Ravivara", DayOfWeek.SUNDAY),
    SOMAVARA("Somavara", DayOfWeek.MONDAY),
    MANGALAVARA("Mangalavara", DayOfWeek.TUESDAY),
    BUDHAVARA("Budhavara", DayOfWeek.WEDNESDAY),
    GURUVARA("Guruvara", DayOfWeek.THURSDAY),
    SHUKRAVARA("Shukravara", DayOfWeek.FRIDAY),
    SHANIVARA("Shanivara", DayOfWeek.SATURDAY);

    companion object {
        private val byDayOfWeek = entries.associateBy { it.dayOfWeek }

        fun ofLocalDate(date: LocalDate): Vaar = byDayOfWeek.getValue(date.dayOfWeek)

        /**
         * The vaar in force at [jdUt] at [location], given that day's [sunrise]. Before sunrise
         * the previous vaar is still running. When there is no sunrise — polar day or night —
         * the civil date's vaar is returned, because there is no better answer and the
         * alternative is a null nobody can act on.
         */
        fun atInstant(jdUt: Double, location: GeoLocation, sunrise: RiseSet): Vaar {
            val date = location.localDate(jdUt)
            val sunriseJdUt = sunrise.jdUtOrNull ?: return ofLocalDate(date)
            return if (jdUt < sunriseJdUt) ofLocalDate(date.minusDays(1)) else ofLocalDate(date)
        }
    }
}

/**
 * Divisions of the daylight period.
 *
 * All of these are defined as equal parts of the interval from sunrise to sunset, so all of
 * them inherit the accuracy of both endpoints. That is why the engine's un-iterated sunset
 * mattered so much more than it looked: a six-minute error in sunset is a three-minute error in
 * every Rahu Kaal boundary and in Abhijit, and it moves the one-third-of-daylight cap that
 * bounds an Ekadashi parana window.
 *
 * The eighth-part tables below are the standard ones. Only Rahu Kaal existed in the engine.
 */
object DayDivisions {

    /** Daylight is divided into eight equal parts for Rahu Kaal, Yamaganda and Gulika. */
    const val EIGHTHS: Int = 8

    /** Daylight is divided into fifteen muhurtas; Abhijit is the eighth. */
    const val DAY_MUHURTAS: Int = 15

    /** Index (1-based) of the eighth part occupied by Rahu Kaal, by weekday. */
    private val RAHU_KAAL_PART: Map<DayOfWeek, Int> = mapOf(
        DayOfWeek.SUNDAY to 8,
        DayOfWeek.MONDAY to 2,
        DayOfWeek.TUESDAY to 7,
        DayOfWeek.WEDNESDAY to 5,
        DayOfWeek.THURSDAY to 6,
        DayOfWeek.FRIDAY to 4,
        DayOfWeek.SATURDAY to 3,
    )

    /** Index (1-based) of the eighth part occupied by Yamaganda, by weekday. */
    private val YAMAGANDA_PART: Map<DayOfWeek, Int> = mapOf(
        DayOfWeek.SUNDAY to 5,
        DayOfWeek.MONDAY to 4,
        DayOfWeek.TUESDAY to 3,
        DayOfWeek.WEDNESDAY to 2,
        DayOfWeek.THURSDAY to 1,
        DayOfWeek.FRIDAY to 7,
        DayOfWeek.SATURDAY to 6,
    )

    /** Index (1-based) of the eighth part occupied by Gulika (Kuligai), by weekday. */
    private val GULIKA_PART: Map<DayOfWeek, Int> = mapOf(
        DayOfWeek.SUNDAY to 7,
        DayOfWeek.MONDAY to 6,
        DayOfWeek.TUESDAY to 5,
        DayOfWeek.WEDNESDAY to 4,
        DayOfWeek.THURSDAY to 3,
        DayOfWeek.FRIDAY to 2,
        DayOfWeek.SATURDAY to 1,
    )

    /**
     * The [part]-th of [divisions] equal parts of the daylight period. [part] is 1-based.
     *
     * Works correctly for a "day" of any length, including the 25-hour and 23-hour civil days
     * either side of a DST transition, because it divides the interval between two instants and
     * never touches a clock.
     */
    fun equalPart(
        sunriseJdUt: Double,
        sunsetJdUt: Double,
        part: Int,
        divisions: Int,
    ): JdInterval {
        require(divisions > 0) { "divisions must be positive" }
        require(part in 1..divisions) { "part must be in 1..$divisions, was $part" }
        val length = (sunsetJdUt - sunriseJdUt) / divisions
        val start = sunriseJdUt + (part - 1) * length
        return JdInterval(start, start + length)
    }

    fun rahuKaal(vaar: Vaar, sunriseJdUt: Double, sunsetJdUt: Double): JdInterval =
        equalPart(sunriseJdUt, sunsetJdUt, RAHU_KAAL_PART.getValue(vaar.dayOfWeek), EIGHTHS)

    fun yamaganda(vaar: Vaar, sunriseJdUt: Double, sunsetJdUt: Double): JdInterval =
        equalPart(sunriseJdUt, sunsetJdUt, YAMAGANDA_PART.getValue(vaar.dayOfWeek), EIGHTHS)

    fun gulika(vaar: Vaar, sunriseJdUt: Double, sunsetJdUt: Double): JdInterval =
        equalPart(sunriseJdUt, sunsetJdUt, GULIKA_PART.getValue(vaar.dayOfWeek), EIGHTHS)

    /**
     * Abhijit muhurta: the eighth of the fifteen daytime muhurtas, hence the one centred on the
     * midpoint of daylight.
     *
     * Some authorities centre it on the Sun's meridian transit instead. The two differ by the
     * small asymmetry of daylight about transit — under two minutes at temperate latitudes,
     * more near the solstices at high latitude. The equal-division definition is used here
     * because it is the one the fifteen-muhurta scheme actually states.
     */
    fun abhijit(sunriseJdUt: Double, sunsetJdUt: Double): JdInterval =
        equalPart(sunriseJdUt, sunsetJdUt, 8, DAY_MUHURTAS)
}
