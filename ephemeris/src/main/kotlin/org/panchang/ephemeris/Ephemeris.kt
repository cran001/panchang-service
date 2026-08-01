package org.panchang.ephemeris

/**
 * Positions of the Sun and Moon. Nothing in this module knows what a tithi is.
 *
 * This interface exists so the server can run a high-precision ephemeris while the
 * Android app keeps a small, self-contained one, and so future Jyotisha work (which
 * needs the other grahas) extends rather than rewrites this layer.
 *
 * ## Time scale — read this before implementing
 *
 * Every position method takes **jdTt**: a Julian Day in *Terrestrial Time*. The
 * underlying theories (VSOP87, ELP2000, Meeus' series) are all defined in TT, so
 * feeding them a UT-based Julian Day is simply wrong. The app engine this project
 * replaces did exactly that — it has no ΔT anywhere — which is a systematic error of
 * ~69 s in 2026, growing quadratically as you move away from the present.
 *
 * Callers holding civil time must convert:
 *
 *     val jdTt = jdUt + ephemeris.deltaT(jdUt) / 86400.0
 *
 * [TimeScale] does this for you. Prefer it over hand-rolling the arithmetic.
 *
 * ## Angle conventions
 *
 * - All longitudes/latitudes are in **degrees**.
 * - Longitudes are **apparent geocentric ecliptic longitude of date** — i.e. referred
 *   to the true equinox of date, with nutation in longitude and aberration applied.
 *   They are *tropical*; subtracting ayanamsha to get sidereal is a calendar concern
 *   and belongs in `:core`, not here.
 * - Longitudes are normalised to `[0, 360)`. Latitudes are signed, roughly `[-6, +6]`
 *   for the Moon.
 *
 * ## Purity
 *
 * Implementations must be deterministic, thread-safe, and free of I/O. Given the same
 * `jdTt` they return the same value forever. This is what makes the whole verification
 * strategy — differential testing against JPL Horizons, frozen regression snapshots —
 * possible.
 */
interface Ephemeris {

    /** Short stable identifier used in reports, ETags and snapshot files, e.g. `"meeus"`. */
    val id: String

    /**
     * Worst-case longitude error this implementation claims, in arcseconds.
     *
     * Honesty matters here: this number is what downstream code uses to decide the
     * tolerance it may legitimately promise on tithi boundary times. A tithi advances
     * ~0.508°/hour, so 1″ of longitude error ≈ 2 seconds of timing error, and
     * conversely 1 minute of timing error ≈ 30″. Do not put an aspirational number
     * here — put the one the differential tests actually measured.
     */
    val claimedAccuracyArcsec: Double

    /** Apparent geocentric ecliptic longitude of the Sun, degrees in `[0, 360)`. */
    fun sunLongitude(jdTt: Double): Double

    /** Apparent geocentric ecliptic longitude of the Moon, degrees in `[0, 360)`. */
    fun moonLongitude(jdTt: Double): Double

    /** Apparent geocentric ecliptic latitude of the Moon, degrees, signed. */
    fun moonLatitude(jdTt: Double): Double

    /** Distance from the Earth's centre to the Moon's centre, kilometres. */
    fun moonDistanceKm(jdTt: Double): Double

    /** Distance from the Earth's centre to the Sun's centre, kilometres. */
    fun sunDistanceKm(jdTt: Double): Double

    /**
     * Nutation in longitude (Δψ) in degrees, and the true obliquity of the ecliptic (ε)
     * in degrees, at `jdTt`.
     *
     * Exposed because sunrise/sunset and the topocentric correction in `:core` both need
     * obliquity, and the app engine got into trouble by computing it inconsistently in
     * three different places — the topocentric path omitted the nutation term that the
     * sunrise and sunset paths included. One source of truth avoids that.
     */
    fun nutationAndObliquity(jdTt: Double): NutationObliquity

    /**
     * ΔT = TT − UT1 in **seconds** at the given UT-based Julian Day.
     *
     * Note the argument is UT, not TT — this is the one method that takes UT, because
     * it is the bridge between the two scales. The difference is immaterial to the
     * result (ΔT changes by ~1 s per century) but the parameter name should not lie.
     *
     * For dates beyond the current year this is necessarily an extrapolation, and the
     * uncertainty grows: roughly ±10 s by 2100. That is fine for calendar purposes —
     * 10 s of ΔT moves a tithi boundary by 10 s — but it is a real reason the service
     * should never claim sub-second accuracy for far-future dates.
     */
    fun deltaT(jdUt: Double): Double
}

/** Nutation in longitude and true obliquity, both in degrees. */
data class NutationObliquity(
    val nutationLongitude: Double,
    val trueObliquity: Double,
)
