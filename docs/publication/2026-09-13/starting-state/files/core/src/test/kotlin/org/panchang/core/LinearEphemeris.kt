package org.panchang.core

import org.panchang.ephemeris.Ephemeris
import org.panchang.ephemeris.NutationObliquity
import org.panchang.ephemeris.TimeScale

/**
 * A deterministic stand-in for a real ephemeris: Sun and Moon move at constant angular rates
 * along a fixed ecliptic, ΔT is constant, and there is no nutation.
 *
 * This is not an approximation of the sky and is not used to check accuracy. It is used because
 * every quantity `core` computes from it has a closed form, so a boundary solver can be checked
 * against arithmetic rather than against another implementation of the same idea. Testing the
 * solver against the real Meeus series would only establish that two pieces of code agree.
 *
 * The defaults are chosen so that seasons, lunations and rise/set behaviour land in roughly the
 * right place — the Sun near 280° at J2000.0, a sidereal month of 27.32 days — which keeps the
 * property tests physically meaningful without making any of them depend on real values.
 */
class LinearEphemeris(
    val epochJdTt: Double = TimeScale.J2000,
    val sunLongitudeAtEpochDeg: Double = 280.46,
    val sunDegreesPerDay: Double = 360.0 / 365.25,
    val moonLongitudeAtEpochDeg: Double = 218.32,
    val moonDegreesPerDay: Double = 360.0 / 27.321582,
    val moonLatitudeDeg: Double = 0.0,
    val moonDistanceKmValue: Double = 385_000.56,
    val obliquityDeg: Double = 23.4392911,
    val deltaTSecondsValue: Double = 69.0,
) : Ephemeris {

    override val id: String = "linear-test"

    /**
     * Infinite, because this ephemeris makes no claim about the real sky at all. Anything
     * downstream that reasons from this number should refuse to promise a tolerance, which is
     * the correct behaviour.
     */
    override val claimedAccuracyArcsec: Double = Double.POSITIVE_INFINITY

    override fun sunLongitude(jdTt: Double): Double =
        TimeScale.normalizeDegrees(sunLongitudeAtEpochDeg + sunDegreesPerDay * (jdTt - epochJdTt))

    override fun moonLongitude(jdTt: Double): Double =
        TimeScale.normalizeDegrees(moonLongitudeAtEpochDeg + moonDegreesPerDay * (jdTt - epochJdTt))

    override fun moonLatitude(jdTt: Double): Double = moonLatitudeDeg

    override fun moonDistanceKm(jdTt: Double): Double = moonDistanceKmValue

    override fun sunDistanceKm(jdTt: Double): Double = 1.495_978_707e8

    override fun nutationAndObliquity(jdTt: Double): NutationObliquity =
        NutationObliquity(nutationLongitude = 0.0, trueObliquity = obliquityDeg)

    override fun deltaT(jdUt: Double): Double = deltaTSecondsValue

    // ── Closed forms, for tests to assert against ───────────────────────────────────────────

    /** ΔT is constant here, so the two scales differ by a fixed shift and both inverses exact. */
    fun jdTt(jdUt: Double): Double = jdUt + deltaTSecondsValue / 86_400.0

    fun jdUt(jdTt: Double): Double = jdTt - deltaTSecondsValue / 86_400.0

    val elongationDegreesPerDay: Double get() = moonDegreesPerDay - sunDegreesPerDay

    val yogaSumDegreesPerDay: Double get() = moonDegreesPerDay + sunDegreesPerDay

    fun elongationDeg(jdUt: Double): Double {
        val tt = jdTt(jdUt)
        return TimeScale.normalizeDegrees(moonLongitude(tt) - sunLongitude(tt))
    }

    /** First instant at or after [afterJdUt] at which the Moon−Sun elongation equals [targetDeg]. */
    fun jdUtWhenElongation(targetDeg: Double, afterJdUt: Double): Double =
        afterJdUt + TimeScale.normalizeDegrees(targetDeg - elongationDeg(afterJdUt)) / elongationDegreesPerDay

    /** First instant at or after [afterJdUt] at which the Sun's tropical longitude equals [targetDeg]. */
    fun jdUtWhenSunLongitude(targetDeg: Double, afterJdUt: Double): Double =
        afterJdUt + TimeScale.normalizeDegrees(targetDeg - sunLongitude(jdTt(afterJdUt))) / sunDegreesPerDay

    /** First instant at or after [afterJdUt] at which the Moon's tropical longitude equals [targetDeg]. */
    fun jdUtWhenMoonLongitude(targetDeg: Double, afterJdUt: Double): Double =
        afterJdUt + TimeScale.normalizeDegrees(targetDeg - moonLongitude(jdTt(afterJdUt))) / moonDegreesPerDay
}
