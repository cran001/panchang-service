package org.panchang.core

import org.panchang.ephemeris.TimeScale
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * An observing site: geodetic position plus the civil time zone the site keeps.
 *
 * The zone is a [ZoneId], not a string. Every civil-day boundary in this module — the window
 * a sunrise is searched in, the weekday a Rahu Kaal table is indexed by — is derived from it.
 * The app engine this project replaces carried the zone as a `String` on its location model
 * and then ignored it, reading the JVM default zone instead when it needed a weekday. Making
 * the zone a first-class typed field that every day-scoped computation must be handed removes
 * that whole class of bug: there is no code path here that can reach a default zone.
 *
 * Validation happens at construction, so a `GeoLocation` in hand is always usable. Callers
 * parsing user input should catch [IllegalArgumentException] and [java.time.DateTimeException]
 * at that edge rather than propagating unvalidated doubles inward.
 *
 * @param latitude geodetic latitude in degrees, north positive, `[-90, +90]`.
 * @param longitude geodetic longitude in degrees, **east positive**, `[-180, +180]`.
 * @param zone civil time zone observed at the site.
 * @param elevationMeters height above the reference ellipsoid, used only for the horizon dip
 *   correction in rise/set. Zero is the correct default; a wrong non-zero value is worse than
 *   none because dip grows as the square root of height.
 */
data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
    val zone: ZoneId,
    val elevationMeters: Double = 0.0,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "latitude must be a finite value in [-90, 90], was $latitude"
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "longitude must be a finite value in [-180, 180], was $longitude"
        }
        require(elevationMeters.isFinite() && elevationMeters in -500.0..12_000.0) {
            "elevationMeters must be a finite value in [-500, 12000], was $elevationMeters"
        }
    }

    /**
     * Julian Day (UT) of the first instant of [date] in this site's zone.
     *
     * Uses [LocalDate.atStartOfDay] with the zone, which resolves the case of a DST transition
     * that removes midnight itself (Chile, Cuba, Lebanon and others spring forward at 24:00) by
     * returning the first instant that does exist on that date. A naive `00:00` construction
     * silently lands on the previous day in those zones.
     */
    fun jdUtAtStartOfDay(date: LocalDate): Double =
        TimeScale.jdUt(date.atStartOfDay(zone).toInstant())

    /** Julian Day (UT) of the first instant of the day after [date] in this site's zone. */
    fun jdUtAtEndOfDay(date: LocalDate): Double = jdUtAtStartOfDay(date.plusDays(1))

    /** The instant [jdUt] rendered as civil time at this site. */
    fun zonedDateTime(jdUt: Double): ZonedDateTime = TimeScale.zonedDateTime(jdUt, zone)

    /** The civil calendar date at this site at instant [jdUt]. */
    fun localDate(jdUt: Double): LocalDate = zonedDateTime(jdUt).toLocalDate()

    /**
     * Great-circle distance to [other] in kilometres on a spherical Earth.
     *
     * Carried over from the calibration table that used to live in the app engine, which is
     * otherwise deleted. Accurate to roughly 0.3% against the WGS84 ellipsoid, which is ample
     * for the only thing it is for: deciding whether two sites are near enough to share a
     * cached result.
     */
    fun distanceKmTo(other: GeoLocation): Double {
        val earthRadiusKm = 6371.0088
        val dLat = (other.latitude - latitude) * DEG
        val dLon = (other.longitude - longitude) * DEG
        val a = sin(dLat / 2).let { it * it } +
            cos(latitude * DEG) * cos(other.latitude * DEG) * sin(dLon / 2).let { it * it }
        return 2.0 * earthRadiusKm * asin(sqrt(a).coerceAtMost(1.0))
    }

    companion object {
        /**
         * Convenience factory for callers holding a zone id as text (config files, HTTP query
         * parameters). Throws [java.time.zone.ZoneRulesException] if the id is unknown, which
         * is the correct place for that failure — at the edge, not deep in an integrator.
         */
        fun of(
            latitude: Double,
            longitude: Double,
            zoneId: String,
            elevationMeters: Double = 0.0,
        ): GeoLocation = GeoLocation(latitude, longitude, ZoneId.of(zoneId), elevationMeters)
    }
}
