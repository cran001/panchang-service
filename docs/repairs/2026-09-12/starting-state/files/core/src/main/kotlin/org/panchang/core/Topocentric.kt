package org.panchang.core

import org.panchang.ephemeris.Ephemeris
import org.panchang.ephemeris.TimeScale
import kotlin.math.atan

/**
 * Correction of the Moon's apparent position for the observer's displacement from the centre of
 * the Earth. Meeus, *Astronomical Algorithms*, chapter 40.
 *
 * ## Why this is here but not wired into the panchanga elements
 *
 * Lunar parallax shifts the Moon's apparent ecliptic longitude by up to about a degree — nearly
 * two hours of tithi — so it looks like it ought to matter. It does not, for these elements:
 * tithi, nakshatra, yoga and karana are defined on **geocentric** longitudes, which is what
 * Swiss Ephemeris returns by default and what DrikPanchang and the printed panchangas are
 * computed from. Applying a topocentric correction would make this service disagree with every
 * published table by minutes to hours, which is a far larger error than the one it corrects.
 *
 * The app engine applied it to nakshatra, yoga and karana but not to tithi, so its elements did
 * not even agree with each other about which Moon they were describing.
 *
 * It is retained as a standalone utility because it is genuinely needed elsewhere: anything
 * concerning the Moon's *visibility* — first crescent sighting, an occultation, the moonrise
 * altitude at second order — is a topocentric question. Rise and set in this module use the
 * standard parallax term in the horizon altitude ([Horizon.moonAltitudeDegrees]) rather than a
 * full topocentric position, which is the conventional and adequate treatment.
 *
 * Untested against reference values in this phase. The geometry follows Meeus directly, but no
 * conformance check has been run, so it should be verified before anything depends on it.
 */
object Topocentric {

    /** Flattening factor of the WGS84 ellipsoid, `1 − f = b/a`. */
    private const val POLAR_RADIUS_RATIO = 0.99664719

    /** Equatorial radius in metres, for the elevation term. */
    private const val EARTH_EQUATORIAL_RADIUS_M = 6_378_140.0

    /**
     * Apparent **topocentric** ecliptic longitude of the Moon in degrees, `[0, 360)`.
     *
     * Takes UT and converts internally, so callers do not have to remember which scale the
     * sidereal time and which the ephemeris want — they want different ones.
     */
    fun moonLongitudeDeg(ephemeris: Ephemeris, jdUt: Double, location: GeoLocation): Double {
        val jdTt = TimeScale.toTt(jdUt, ephemeris)
        val moonLongitude = ephemeris.moonLongitude(jdTt)
        val moonLatitude = ephemeris.moonLatitude(jdTt)
        val distanceKm = ephemeris.moonDistanceKm(jdTt)
        val nutation = ephemeris.nutationAndObliquity(jdTt)
        val obliquity = nutation.trueObliquity

        // Equatorial horizontal parallax, Meeus eq. 40.1.
        val sinParallax = Horizon.EARTH_EQUATORIAL_RADIUS_KM / distanceKm

        // Observer's geocentric position: reduced latitude plus the elevation term.
        val reducedLatitude = atan(POLAR_RADIUS_RATIO * tanDeg(location.latitude)) / DEG
        val elevationRatio = location.elevationMeters / EARTH_EQUATORIAL_RADIUS_M
        val rhoSinPhi = POLAR_RADIUS_RATIO * sinDeg(reducedLatitude) +
            elevationRatio * sinDeg(location.latitude)
        val rhoCosPhi = cosDeg(reducedLatitude) + elevationRatio * cosDeg(location.latitude)

        val geocentric = Coordinates.eclipticToEquatorial(moonLongitude, moonLatitude, obliquity)
        val gast = Coordinates.gastDegrees(jdUt, nutation.nutationLongitude, obliquity)
        val hourAngle = TimeScale.angleDifference(gast + location.longitude, geocentric.rightAscensionDeg)

        // Meeus eqs. 40.2 and 40.3.
        val declination = geocentric.declinationDeg
        val denominator = cosDeg(declination) - rhoCosPhi * sinParallax * cosDeg(hourAngle)
        val deltaRa = atan2Deg(-rhoCosPhi * sinParallax * sinDeg(hourAngle), denominator)
        val topocentricDeclination = atan2Deg(
            (sinDeg(declination) - rhoSinPhi * sinParallax) * cosDeg(deltaRa),
            denominator,
        )
        val topocentricRa = geocentric.rightAscensionDeg + deltaRa

        // Back to ecliptic longitude.
        val longitude = atan2Deg(
            sinDeg(topocentricRa) * cosDeg(obliquity) + tanDeg(topocentricDeclination) * sinDeg(obliquity),
            cosDeg(topocentricRa),
        )
        return TimeScale.normalizeDegrees(longitude)
    }
}
