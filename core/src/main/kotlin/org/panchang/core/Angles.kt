package org.panchang.core

import org.panchang.ephemeris.TimeScale
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Radians per degree. Every angle in this module is in degrees; this is the only conversion. */
internal const val DEG: Double = PI / 180.0

internal fun sinDeg(degrees: Double): Double = sin(degrees * DEG)

internal fun cosDeg(degrees: Double): Double = cos(degrees * DEG)

internal fun tanDeg(degrees: Double): Double = tan(degrees * DEG)

/** [asin] in degrees, with the argument clamped so rounding at the poles cannot produce NaN. */
internal fun asinDeg(x: Double): Double = asin(x.coerceIn(-1.0, 1.0)) / DEG

internal fun atan2Deg(y: Double, x: Double): Double = atan2(y, x) / DEG

/** Right ascension and declination of date, both in degrees. */
data class Equatorial(val rightAscensionDeg: Double, val declinationDeg: Double)

/**
 * Coordinate and sidereal-time conversions.
 *
 * Obliquity is never computed here — it is taken from [org.panchang.ephemeris.Ephemeris.nutationAndObliquity]
 * so that there is exactly one source of truth for it. The app engine computed obliquity in
 * three places with three different term sets, and the topocentric path was the one that
 * omitted nutation.
 */
object Coordinates {

    /**
     * Ecliptic (of date) to equatorial (of date).
     *
     * @param obliquityDeg the **true** obliquity, i.e. mean obliquity plus nutation in obliquity.
     */
    fun eclipticToEquatorial(
        longitudeDeg: Double,
        latitudeDeg: Double,
        obliquityDeg: Double,
    ): Equatorial {
        val sinE = sinDeg(obliquityDeg)
        val cosE = cosDeg(obliquityDeg)
        val ra = atan2Deg(
            sinDeg(longitudeDeg) * cosE - tanDeg(latitudeDeg) * sinE,
            cosDeg(longitudeDeg),
        )
        val dec = asinDeg(
            sinDeg(latitudeDeg) * cosE + cosDeg(latitudeDeg) * sinE * sinDeg(longitudeDeg),
        )
        return Equatorial(TimeScale.normalizeDegrees(ra), dec)
    }

    /**
     * Greenwich **mean** sidereal time in degrees. Meeus, *Astronomical Algorithms*, eq. 12.4.
     *
     * The argument is UT, not TT: sidereal time tracks the Earth's rotation, which is what UT
     * measures. Feeding this a TT-based Julian Day is a ~69 s error in 2026, and that error is
     * an angle of ~0.29°, which moves a computed sunrise by about a minute.
     */
    fun gmstDegrees(jdUt: Double): Double {
        val d = jdUt - TimeScale.J2000
        val t = d / TimeScale.JULIAN_CENTURY
        return TimeScale.normalizeDegrees(
            280.46061837 + 360.98564736629 * d + 0.000387933 * t * t - t * t * t / 38_710_000.0,
        )
    }

    /**
     * Greenwich **apparent** sidereal time in degrees: GMST plus the equation of the equinoxes,
     * `Δψ · cos ε`. Apparent sidereal time is the one that pairs with an apparent right
     * ascension, and [org.panchang.ephemeris.Ephemeris] returns apparent longitudes.
     */
    fun gastDegrees(jdUt: Double, nutationLongitudeDeg: Double, trueObliquityDeg: Double): Double =
        TimeScale.normalizeDegrees(
            gmstDegrees(jdUt) + nutationLongitudeDeg * cosDeg(trueObliquityDeg),
        )

    /** Geometric altitude above the horizon in degrees, from local hour angle and declination. */
    fun altitudeDeg(latitudeDeg: Double, declinationDeg: Double, hourAngleDeg: Double): Double =
        asinDeg(
            sinDeg(latitudeDeg) * sinDeg(declinationDeg) +
                cosDeg(latitudeDeg) * cosDeg(declinationDeg) * cosDeg(hourAngleDeg),
        )
}
