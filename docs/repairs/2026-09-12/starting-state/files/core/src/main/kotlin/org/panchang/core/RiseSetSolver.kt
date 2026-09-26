package org.panchang.core

import org.panchang.ephemeris.Ephemeris
import org.panchang.ephemeris.TimeScale
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/** Which horizon crossing is wanted. The sign is that of the local hour angle at the event. */
internal enum class RiseSetDirection(val hourAngleSign: Double) {
    RISE(-1.0),
    SET(+1.0),
}

/**
 * Finds meridian transits and horizon crossings for an arbitrary body within a civil-day window.
 *
 * One solver serves the Sun and the Moon. The app engine had two hand-written closed forms
 * instead, and they diverged: sunrise iterated to place the solar position at the event, sunset
 * did not, so sunset carried a systematic error the sunrise comment claimed had been eliminated
 * — and that stale sunset then fed Abhijit, Rahu Kaal and the parana cap. Here [refinementPasses]
 * is a parameter of one shared routine, and the default converges.
 *
 * ## Method
 *
 * The hour angle `H(t) = GAST(t) + λ_east − α(t)` is a nearly linear function of time with a
 * large derivative (360°/day for the Sun, ~348°/day for the Moon), so Newton's method on it is
 * fast and stable. The outer loop is the physical part: the semi-diurnal arc `H₀` depends on
 * the declination and the horizon altitude, both of which must be evaluated *at the event*, not
 * at noon. Each pass recomputes them at the current estimate and re-solves `H(t) = ±H₀`.
 *
 * ## Precision
 *
 * The inner Newton solve is exact to well under a second of the supplied ephemeris's own hour
 * angle; the outer loop stops when a pass moves the answer by less than [toleranceDays]. As
 * everywhere in this module, that is convergence of the solver, not accuracy of the result. The
 * physical accuracy of a computed sunrise is dominated by the atmosphere, not by the ephemeris:
 * the standard −0°50′ horizon assumes a standard refraction that real air departs from by
 * several arcminutes routinely, and by degrees during a polar inversion. Sub-minute sunrise
 * accuracy is not a claim anyone can honestly make.
 */
internal class RiseSetSolver(
    private val ephemeris: Ephemeris,
    private val location: GeoLocation,
    private val equatorialAt: (jdTt: Double) -> Equatorial,
    private val horizonAltitudeAt: (jdTt: Double) -> Double,
) {

    /** Local hour angle in degrees, wrapped to `(-180, +180]`. Negative before upper transit. */
    fun hourAngleDeg(jdUt: Double): Double {
        val jdTt = TimeScale.toTt(jdUt, ephemeris)
        val nutation = ephemeris.nutationAndObliquity(jdTt)
        val gast = Coordinates.gastDegrees(jdUt, nutation.nutationLongitude, nutation.trueObliquity)
        val equatorial = equatorialAt(jdTt)
        return TimeScale.angleDifference(gast + location.longitude, equatorial.rightAscensionDeg)
    }

    /** Geometric altitude of the body's centre in degrees, uncorrected for refraction. */
    fun altitudeDeg(jdUt: Double): Double {
        val jdTt = TimeScale.toTt(jdUt, ephemeris)
        val equatorial = equatorialAt(jdTt)
        return Coordinates.altitudeDeg(
            location.latitude,
            equatorial.declinationDeg,
            hourAngleDeg(jdUt),
        )
    }

    /** How far the body is above its own rise/set horizon at [jdUt]. Zero exactly at the event. */
    fun altitudeAboveHorizonDeg(jdUt: Double): Double =
        altitudeDeg(jdUt) - horizonAltitudeAt(TimeScale.toTt(jdUt, ephemeris))

    /** The upper meridian transit nearest to [seedJdUt]. */
    fun transitNear(seedJdUt: Double): Double =
        AngleCrossing.newtonOnAngle(::hourAngleDeg, targetDeg = 0.0, seedJdUt = seedJdUt)

    /**
     * The upper transit falling closest to the middle of the window. Always defined — a body
     * crosses the meridian every day regardless of whether it rises, which is why this returns
     * a plain `Double` while rise and set do not.
     */
    fun transitInWindow(windowStartJdUt: Double, windowEndJdUt: Double): Double {
        val middle = (windowStartJdUt + windowEndJdUt) / 2.0
        return candidateTransits(windowStartJdUt, windowEndJdUt).minByOrNull { abs(it - middle) }
            ?: transitNear(middle)
    }

    /**
     * The requested horizon crossing within `[windowStartJdUt, windowEndJdUt)`.
     *
     * @param refinementPasses outer iterations. `1` reproduces the single-pass behaviour of the
     *   engine's `calculateSunset` and is retained only so the regression test can demonstrate
     *   the difference; production callers want the default.
     */
    fun solve(
        windowStartJdUt: Double,
        windowEndJdUt: Double,
        direction: RiseSetDirection,
        refinementPasses: Int,
        toleranceDays: Double,
    ): RiseSet {
        require(windowEndJdUt > windowStartJdUt) { "empty window" }
        require(refinementPasses >= 1) { "refinementPasses must be at least 1" }

        for (transit in candidateTransits(windowStartJdUt, windowEndJdUt)) {
            val event = eventFromTransit(transit, direction, refinementPasses, toleranceDays)
            if (event != null && event >= windowStartJdUt && event < windowEndJdUt) {
                return RiseSet.At(event)
            }
        }
        return classifyWindow(windowStartJdUt, windowEndJdUt)
    }

    /**
     * Horizon crossing derived from one transit, or `null` when the body's diurnal circle does
     * not reach the horizon at all on that rotation.
     */
    private fun eventFromTransit(
        transitJdUt: Double,
        direction: RiseSetDirection,
        refinementPasses: Int,
        toleranceDays: Double,
    ): Double? {
        var t = transitJdUt
        var solvedAtLeastOnce = false

        repeat(refinementPasses) { pass ->
            val jdTt = TimeScale.toTt(t, ephemeris)
            val declination = equatorialAt(jdTt).declinationDeg
            val horizon = horizonAltitudeAt(jdTt)

            val cosH0 = (sinDeg(horizon) - sinDeg(location.latitude) * sinDeg(declination)) /
                (cosDeg(location.latitude) * cosDeg(declination))
            if (!cosH0.isFinite() || cosH0 < -1.0 || cosH0 > 1.0) {
                // No crossing on this rotation. If an earlier pass already produced one, keep
                // it — wandering out of range on a later pass only happens within seconds of
                // the circumpolar limit, where the earlier estimate is the better answer.
                return if (solvedAtLeastOnce) t else null
            }

            val targetHourAngle = direction.hourAngleSign * acos(cosH0) / DEG
            val refined = AngleCrossing.newtonOnAngle(::hourAngleDeg, targetHourAngle, seedJdUt = t)
            val movement = refined - t
            t = refined
            solvedAtLeastOnce = true
            if (pass > 0 && abs(movement) < toleranceDays) return t
        }
        return t
    }

    /**
     * Decide what it means that no crossing of the requested kind fell inside the window, by
     * sampling the body's height above its horizon across the window.
     *
     * Sampling rather than inferring from `cos H₀` because the two failure modes are genuinely
     * different: a body that never reaches the horizon, and a body that crossed it only in the
     * other direction. Only the sampled altitude distinguishes them, and only the sealed result
     * can express both.
     */
    private fun classifyWindow(windowStartJdUt: Double, windowEndJdUt: Double): RiseSet {
        var anyAbove = false
        var anyBelow = false
        for (i in 0..SAMPLES) {
            val t = windowStartJdUt + (windowEndJdUt - windowStartJdUt) * i / SAMPLES
            if (altitudeAboveHorizonDeg(t) > 0.0) anyAbove = true else anyBelow = true
            if (anyAbove && anyBelow) return RiseSet.NoEventInWindow
        }
        return if (anyAbove) RiseSet.CircumpolarUp else RiseSet.CircumpolarDown
    }

    /**
     * Transits that could plausibly produce an event inside the window. A crossing lies within
     * half a rotation of its transit, so seeding Newton at the window edges and beyond covers
     * every transit that can matter, including for a site whose civil zone is hours away from
     * its longitude.
     */
    private fun candidateTransits(windowStartJdUt: Double, windowEndJdUt: Double): List<Double> {
        val middle = (windowStartJdUt + windowEndJdUt) / 2.0
        val seeds = listOf(
            windowStartJdUt - 0.5,
            windowStartJdUt,
            middle,
            windowEndJdUt,
            windowEndJdUt + 0.5,
        )
        val found = ArrayList<Double>(seeds.size)
        for (seed in seeds) {
            val transit = transitNear(seed)
            if (!transit.isFinite()) continue
            if (found.none { abs(it - transit) < TRANSIT_DEDUPE_DAYS }) found.add(transit)
        }
        return found.sortedBy { abs(it - middle) }
    }

    private companion object {
        /** Two transits closer than this are the same transit reached from different seeds. */
        const val TRANSIT_DEDUPE_DAYS = 0.05

        /** Window samples for [classifyWindow]: half-hourly over a civil day. */
        const val SAMPLES = 48
    }
}

/**
 * Standard horizon altitudes.
 *
 * These define what "rise" means. They are conventions, not measurements, and every published
 * table is only as right as the convention it assumed.
 */
object Horizon {

    /**
     * Solar rise/set altitude: −0°50′.
     *
     * The Sun's centre sits 50′ below the true horizon when its upper limb appears on it:
     * about 16′ of semidiameter plus about 34′ of mean atmospheric refraction. Real refraction
     * at the horizon varies with temperature and pressure by several arcminutes, and by far
     * more under a polar inversion, so this constant is the largest single source of error in
     * any sunrise time — larger than the ephemeris by an order of magnitude.
     */
    const val SUN_ALTITUDE_DEGREES: Double = -0.8333

    /** Refraction allowance used for the Moon, 34′, matching the solar figure. */
    const val MOON_REFRACTION_DEGREES: Double = 34.0 / 60.0

    /**
     * Fraction of the Moon's equatorial horizontal parallax that applies at the horizon.
     * Meeus, eq. 15.1: `h₀ = 0.7275·π − 34′`.
     */
    const val MOON_PARALLAX_FACTOR: Double = 0.7275

    /**
     * Depression of the visible horizon in degrees for an observer [elevationMeters] above the
     * surface. Roughly `1.75′·√h` geometrically, `2.08′·√h` once refraction along the sight
     * line is included; the latter is used. Negative elevations return zero rather than an
     * imaginary dip.
     */
    fun dipDegrees(elevationMeters: Double): Double =
        if (elevationMeters <= 0.0) 0.0 else 0.0347 * sqrt(elevationMeters)

    /** Effective solar horizon at a site, including the dip from its elevation. */
    fun sunAltitudeDegrees(location: GeoLocation): Double =
        SUN_ALTITUDE_DEGREES - dipDegrees(location.elevationMeters)

    /**
     * Effective lunar horizon. Depends on the Moon's distance through its parallax, which swings
     * the value by about 0.03° between perigee and apogee — small, but free to include.
     *
     * Note this places the Moon's *centre* on the horizon, the usual convention for moonrise,
     * whereas the solar figure places the Sun's upper limb there. That asymmetry is inherited
     * from the standard tables, not an oversight.
     */
    fun moonAltitudeDegrees(distanceKm: Double, elevationMeters: Double = 0.0): Double {
        val parallaxDeg = asinDeg(EARTH_EQUATORIAL_RADIUS_KM / distanceKm)
        return MOON_PARALLAX_FACTOR * parallaxDeg - MOON_REFRACTION_DEGREES - dipDegrees(elevationMeters)
    }

    /** IAU 1976 equatorial radius of the Earth, kilometres. */
    const val EARTH_EQUATORIAL_RADIUS_KM: Double = 6378.14
}
