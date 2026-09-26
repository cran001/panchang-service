package org.panchang.ephemeris

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Conversions between civil time, Julian Day (UT), and Julian Day (TT).
 *
 * The app engine this project replaces scattered the magic constant `2440587.5` across
 * ten call sites and had no UT→TT conversion at all. Everything to do with the time
 * scale lives here instead.
 */
object TimeScale {

    /** Julian Day number of the Unix epoch, 1970-01-01T00:00:00Z. */
    const val UNIX_EPOCH_JD: Double = 2440587.5

    /** Julian Day number of J2000.0, 2000-01-01T12:00:00 TT. */
    const val J2000: Double = 2451545.0

    /** Days per Julian century. */
    const val JULIAN_CENTURY: Double = 36525.0

    /** Julian Day (UT) at 00:00 UTC of the given Gregorian calendar date. */
    fun jdUtAtMidnight(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = Math.floorDiv(y, 100)
        val b = 2 - a + Math.floorDiv(a, 4)
        return Math.floor(365.25 * (y + 4716)) +
            Math.floor(30.6001 * (m + 1)) +
            day + b - 1524.5
    }

    /** Julian Day (UT) at 00:00 UTC of the given date. */
    fun jdUtAtMidnight(date: LocalDate): Double =
        jdUtAtMidnight(date.year, date.monthValue, date.dayOfMonth)

    /** Julian Day (UT) of an absolute instant. */
    fun jdUt(instant: Instant): Double =
        UNIX_EPOCH_JD + instant.toEpochMilli() / 86_400_000.0

    /** The absolute instant corresponding to a Julian Day (UT). */
    fun instant(jdUt: Double): Instant =
        Instant.ofEpochMilli(Math.round((jdUt - UNIX_EPOCH_JD) * 86_400_000.0))

    /** Epoch milliseconds corresponding to a Julian Day (UT). */
    fun epochMillis(jdUt: Double): Long =
        Math.round((jdUt - UNIX_EPOCH_JD) * 86_400_000.0)

    /** The Julian Day (UT) as civil date-time in [zone]. */
    fun zonedDateTime(jdUt: Double, zone: ZoneId): ZonedDateTime =
        instant(jdUt).atZone(zone)

    /** Julian Day (UT) of a civil date-time in [zone]. */
    fun jdUt(dateTime: LocalDateTime, zone: ZoneId): Double =
        jdUt(dateTime.atZone(zone).toInstant())

    /** Julian centuries of TT since J2000.0. The independent variable of most series. */
    fun centuriesSinceJ2000(jdTt: Double): Double = (jdTt - J2000) / JULIAN_CENTURY

    /** Convert a UT-based Julian Day to a TT-based one using [ephemeris]'s ΔT. */
    fun toTt(jdUt: Double, ephemeris: Ephemeris): Double =
        jdUt + ephemeris.deltaT(jdUt) / 86_400.0

    /** Convert a TT-based Julian Day back to UT. */
    fun toUt(jdTt: Double, ephemeris: Ephemeris): Double {
        // deltaT varies so slowly (~1 s/century) that evaluating it at jdTt instead of
        // the unknown jdUt is exact to well under a millisecond. No iteration needed.
        return jdTt - ephemeris.deltaT(jdTt) / 86_400.0
    }

    /** Normalise an angle in degrees to `[0, 360)`. */
    fun normalizeDegrees(degrees: Double): Double {
        val r = degrees % 360.0
        return if (r < 0) r + 360.0 else r
    }

    /**
     * Signed difference `a - b` in degrees, wrapped to `(-180, +180]`.
     *
     * Needed wherever a boundary is being bisected: comparing raw normalised angles
     * across the 360°/0° seam is the classic way to make a bisection silently diverge.
     */
    fun angleDifference(a: Double, b: Double): Double {
        var d = (a - b) % 360.0
        if (d > 180.0) d -= 360.0
        if (d <= -180.0) d += 360.0
        return d
    }
}
