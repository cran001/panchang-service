package org.panchang.core

import org.panchang.ephemeris.TimeScale

/**
 * The offset between the tropical and sidereal zodiacs, in degrees.
 *
 *     sidereal longitude = tropical longitude − ayanamsha
 *
 * [org.panchang.ephemeris.Ephemeris] deliberately returns only tropical longitudes: which
 * ayanamsha to subtract is a calendar convention, not astronomy, and different traditions pick
 * different ones. Keeping this an interface rather than a hard-coded constant is not
 * speculative generality — Jyotisha work needs Raman and Krishnamurti, and the Surya Siddhanta
 * reckoning some sampradayas use is a different function altogether.
 *
 * Implementations must be pure and take **TT**, matching the ephemeris.
 */
interface Ayanamsha {

    /** Short stable identifier used in reports and cache keys, e.g. `"lahiri"`. */
    val id: String

    /** Ayanamsha in degrees at [jdTt]. Positive, and increasing by ~50.3″ per year. */
    fun degreesAt(jdTt: Double): Double

    companion object {
        /** The Indian civil calendar standard. See [LahiriAyanamsha]. */
        val LAHIRI: Ayanamsha = LahiriAyanamsha

        /** All ayanamshas this module ships. */
        val builtIn: List<Ayanamsha> = listOf(LAHIRI)

        /** Look up a built-in by [id], or `null` if unknown. Case-insensitive. */
        fun byId(id: String): Ayanamsha? = builtIn.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

/**
 * Lahiri (Chitrapaksha) ayanamsha — the Indian Astronomical Ephemeris standard, and the one
 * DrikPanchang and the ISKCON calendar are computed with.
 *
 * Model: a fixed value at J2000.0 plus IAU 2000 general precession in longitude accumulated
 * from that epoch.
 *
 * ## Accuracy, stated honestly
 *
 * This is a two-term precession polynomial anchored at a single epoch value. It is not the
 * Swiss Ephemeris `SE_SIDM_LAHIRI` model, which additionally applies the full precession
 * theory and a nutation-consistent frame. Near the present epoch the two agree to within a
 * few arcseconds; the difference grows away from J2000.0. A few arcseconds of ayanamsha error
 * moves a *nakshatra* boundary by a few seconds of time and a *tithi* boundary by nothing at
 * all — tithi depends on the Moon−Sun difference, in which the ayanamsha cancels exactly.
 * No conformance test against Swiss Ephemeris values has been run in this phase, so treat the
 * agreement above as a statement about the model, not a measurement.
 */
object LahiriAyanamsha : Ayanamsha {

    override val id: String = "lahiri"

    /**
     * Lahiri ayanamsha at J2000.0: 23° 51′ 11″, the value published in the Indian Astronomical
     * Ephemeris.
     */
    const val AT_J2000_DEGREES: Double = 23.0 + 51.0 / 60.0 + 11.0 / 3600.0

    override fun degreesAt(jdTt: Double): Double {
        val t = TimeScale.centuriesSinceJ2000(jdTt)
        // IAU 2000 general precession in longitude, arcseconds since J2000.0. The rate is
        // ~5028.8″ per *century*; expressing it per year and then using T in centuries is the
        // factor-of-100 error the app engine shipped for a while.
        val precessionArcsec = 5028.796195 * t + 1.1054348 * t * t
        return AT_J2000_DEGREES + precessionArcsec / 3600.0
    }
}
