package org.panchang.verify.grid

import kotlinx.serialization.Serializable

/**
 * A verification location.
 *
 * Coordinates are the ones we send to USNO and use for our own sunrise computation, so
 * they must be identical on both sides of any comparison: a 0.05 degree disagreement in
 * longitude is ~12 seconds of sunrise, which is the same order as the discrepancies we
 * are trying to detect. Do not "improve" these values without re-harvesting.
 */
@Serializable
data class ReferenceCity(
    val id: String,
    val displayName: String,
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val ianaZone: String,
    /**
     * File stem used by vaisnavacalendar.info's per-city text calendars, if that source
     * publishes one for this location. Null means the community calendar cannot be used
     * as a factor-3 anchor here and only factors 1 and 2 are verifiable.
     */
    val vaisnavaCalendarCity: String? = null,
    val notes: String = "",
)

/**
 * The standard verification grid.
 *
 * Chosen to make each failure mode reachable rather than to be geographically pretty:
 *  - Mayapur and Vrindavan are the tradition's own reference locations, so a
 *    disagreement there is a disagreement about the observance itself, not about
 *    geography.
 *  - Auckland and London are the DST stress cases. Auckland is southern-hemisphere DST
 *    with a transition inside Gaura Purnima season and a UTC offset that crosses the
 *    date line relative to India; London's transition lands near the equinox where
 *    sunrise moves fastest.
 *  - Moscow at 55.8N is the high-latitude case: long twilight, arunodaya computed at a
 *    depression angle that comes close to not occurring at midsummer.
 *  - Sao Paulo and Sydney are southern-hemisphere sanity: a hemisphere sign error in
 *    daylight-fraction arithmetic shows up here and nowhere else.
 *  - New York is the large western-longitude negative-offset case.
 */
object ReferenceCities {

    val MAYAPUR = ReferenceCity(
        id = "mayapur",
        displayName = "Mayapur, India",
        latitudeDeg = 23.4242,
        longitudeDeg = 88.3888,
        ianaZone = "Asia/Kolkata",
        vaisnavaCalendarCity = "Mayapur [India]",
        notes = "Gaudiya Vaishnava reference location; the community calendar's own datum.",
    )

    val VRINDAVAN = ReferenceCity(
        id = "vrindavan",
        displayName = "Vrindavan, India",
        latitudeDeg = 27.5806,
        longitudeDeg = 77.7006,
        ianaZone = "Asia/Kolkata",
        vaisnavaCalendarCity = "Vrindavan [India]",
    )

    val DELHI = ReferenceCity(
        id = "delhi",
        displayName = "Delhi, India",
        latitudeDeg = 28.6139,
        longitudeDeg = 77.2090,
        ianaZone = "Asia/Kolkata",
        vaisnavaCalendarCity = "Delhi [India]",
    )

    val MUMBAI = ReferenceCity(
        id = "mumbai",
        displayName = "Mumbai, India",
        latitudeDeg = 19.0760,
        longitudeDeg = 72.8777,
        ianaZone = "Asia/Kolkata",
        // The community calendar still files Mumbai under its pre-1995 name.
        vaisnavaCalendarCity = "Bombay [India]",
    )

    val LONDON = ReferenceCity(
        id = "london",
        displayName = "London, United Kingdom",
        latitudeDeg = 51.5074,
        longitudeDeg = -0.1278,
        ianaZone = "Europe/London",
        vaisnavaCalendarCity = "London [United Kingdom]",
        notes = "DST stress case: BST transition near the equinox.",
    )

    val NEW_YORK = ReferenceCity(
        id = "new-york",
        displayName = "New York, United States",
        latitudeDeg = 40.7128,
        longitudeDeg = -74.0060,
        ianaZone = "America/New_York",
        vaisnavaCalendarCity = "New York City [United States of America]",
    )

    val AUCKLAND = ReferenceCity(
        id = "auckland",
        displayName = "Auckland, New Zealand",
        latitudeDeg = -36.8485,
        longitudeDeg = 174.7633,
        ianaZone = "Pacific/Auckland",
        vaisnavaCalendarCity = "Auckland [New Zealand]",
        notes = "DST stress case: southern-hemisphere DST, UTC+12/+13, far side of the date line.",
    )

    val MOSCOW = ReferenceCity(
        id = "moscow",
        displayName = "Moscow, Russia",
        latitudeDeg = 55.7558,
        longitudeDeg = 37.6173,
        ianaZone = "Europe/Moscow",
        vaisnavaCalendarCity = "Moskva [Russia]",
        notes = "High-latitude case at 55.8N; long twilight, arunodaya nearly degenerate at midsummer.",
    )

    val SAO_PAULO = ReferenceCity(
        id = "sao-paulo",
        displayName = "Sao Paulo, Brazil",
        latitudeDeg = -23.5505,
        longitudeDeg = -46.6333,
        ianaZone = "America/Sao_Paulo",
        vaisnavaCalendarCity = "Sao Paulo [Brazil]",
        notes = "Southern hemisphere, western longitude. Brazil abolished DST in 2019; " +
            "historical dates before then are a separate stress case.",
    )

    val SYDNEY = ReferenceCity(
        id = "sydney",
        displayName = "Sydney, Australia",
        latitudeDeg = -33.8688,
        longitudeDeg = 151.2093,
        ianaZone = "Australia/Sydney",
        vaisnavaCalendarCity = "Sydney [Australia]",
    )

    val ALL: List<ReferenceCity> = listOf(
        MAYAPUR, VRINDAVAN, DELHI, MUMBAI, LONDON,
        NEW_YORK, AUCKLAND, MOSCOW, SAO_PAULO, SYDNEY,
    )

    fun byId(id: String): ReferenceCity =
        ALL.firstOrNull { it.id == id.lowercase() }
            ?: throw IllegalArgumentException(
                "Unknown reference city '$id'. Known: ${ALL.joinToString(", ") { it.id }}",
            )

    /**
     * Polar probe locations, not part of the standard grid.
     *
     * USNO's "Object continuously above the Horizon" responses are the interesting case
     * for factor 2, and they cannot be provoked from any city in [ALL]. Longyearbyen at
     * 78.2N returns them for roughly a third of the year.
     */
    val POLAR_PROBES: List<ReferenceCity> = listOf(
        ReferenceCity(
            id = "longyearbyen",
            displayName = "Longyearbyen, Svalbard",
            latitudeDeg = 78.2232,
            longitudeDeg = 15.6469,
            ianaZone = "Arctic/Longyearbyen",
            notes = "Polar day and polar night; provokes USNO's no-rise responses.",
        ),
        ReferenceCity(
            id = "mcmurdo",
            displayName = "McMurdo Station, Antarctica",
            latitudeDeg = -77.8419,
            longitudeDeg = 166.6863,
            ianaZone = "Antarctica/McMurdo",
            notes = "Southern polar counterpart; catches hemisphere sign errors in the no-rise path.",
        ),
    )
}
