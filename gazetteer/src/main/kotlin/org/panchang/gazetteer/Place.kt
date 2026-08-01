package org.panchang.gazetteer

import org.panchang.core.GeoLocation
import java.time.ZoneId

/** What kind of record a [Place] is. The gazetteer carries exactly these two. */
enum class PlaceKind {
    /** A populated place: GeoNames feature class `P`, population 15 000 or more. */
    CITY,

    /** An Indian district: GeoNames feature class `A`, feature code `ADM2`, country `IN`. */
    DISTRICT,
}

/**
 * One row of the gazetteer: a named point with a civil time zone.
 *
 * A `Place` is a *label for a region*, not a substitute for the user's position. Its
 * coordinates are GeoNames' representative point for the record — for a district that is
 * somewhere near the administrative centre, which can sit tens of kilometres from where
 * the user actually is. Anything that computes a sunrise from a `Place`'s coordinates when
 * it was handed the user's own has thrown away accuracy for no reason. See
 * [Gazetteer.nearest] for why that matters here specifically.
 *
 * @param geonameId GeoNames' stable integer id. Unique across the whole table.
 * @param name the record's name in its own script, as GeoNames publishes it.
 * @param asciiName the same name transliterated to ASCII by GeoNames.
 * @param country ISO-3166 alpha-2 country code.
 * @param admin1 first-level division. For `IN` this is the state/UT name resolved from the
 *   country file's own `ADM1` records; for every other country it is GeoNames' raw admin1
 *   *code*, because no code-to-name table was vendored. Treat it as a disambiguator between
 *   like-named places, not as a display string you can rely on being human-readable.
 * @param population GeoNames' population figure; `0` where GeoNames records none.
 * @param latitude degrees north, `[-90, +90]`.
 * @param longitude degrees east, `[-180, +180]`.
 * @param zone the civil time zone GeoNames records for the record.
 */
data class Place(
    val geonameId: Int,
    val name: String,
    val asciiName: String,
    val kind: PlaceKind,
    val country: String,
    val admin1: String,
    val population: Long,
    val latitude: Double,
    val longitude: Double,
    val zone: ZoneId,
) {
    /**
     * This record's representative point as a [GeoLocation].
     *
     * **Elevation is zero and that is deliberate, not a missing feature.** GeoNames ships
     * `elevation` and `dem` columns; the ingest does not read them. The reference calendars
     * this service is validated against are computed at sea level, so feeding a district's
     * true altitude into the horizon-dip correction would move our sunrise away from the
     * reference by a knowable amount and toward — what, exactly? Nothing we can check. On a
     * quantity that decides when a fast begins and ends, an unvalidatable "improvement" is
     * just an unvalidatable change. If a caller has a defensible elevation and a reference
     * to test it against, they can pass one; the default declines to guess.
     *
     * Prefer building a `GeoLocation` from the user's own coordinates when you have them.
     * This exists for the case where a place name is genuinely all the input there is.
     */
    fun toGeoLocation(elevationMeters: Double = 0.0): GeoLocation =
        GeoLocation(latitude, longitude, zone, elevationMeters)
}
