package org.panchang.verify.horizons

import kotlinx.serialization.Serializable

/** Which body we are asking Horizons about. */
enum class HorizonsBody(val command: String, val expectedTargetPrefix: String) {
    /** Earth's Moon. */
    MOON("301", "Moon"),

    /** The Sun. Horizons' major-body code, not the barycentre. */
    SUN("10", "Sun"),
    ;

    companion object {
        fun parse(s: String): HorizonsBody = when (s.lowercase()) {
            "moon", "301" -> MOON
            "sun", "10" -> SUN
            else -> throw IllegalArgumentException("Unknown body '$s'. Use 'moon' or 'sun'.")
        }
    }
}

/**
 * A Horizons query.
 *
 * [start] and [stop] are calendar dates interpreted by Horizons as UT instants at 00:00.
 * Horizons' interval is half-open at the top only when a step divides it exactly, so the
 * returned row count is generally `(stop - start) / step + 1`.
 */
data class HorizonsQuery(
    val body: HorizonsBody,
    val start: String,
    val stop: String,
    /** Horizons step syntax, e.g. "1 d", "6 h", "30 m". */
    val step: String = "1 d",
)

/** The header block Horizons echoes back, retained so the parser can assert on it. */
@Serializable
data class HorizonsHeader(
    val targetBodyName: String,
    val centerBodyName: String,
    val centerSiteName: String,
    /** Ephemeris kernel, e.g. "DE441". Recorded because it is the thing being trusted. */
    val ephemerisSource: String?,
    val startTime: String,
    val stopTime: String,
    val stepSize: String,
    val atmosphericRefraction: String?,
    val tableFormat: String?,
    /** The column names exactly as Horizons emitted them, in order. */
    val columnNames: List<String>,
)

/**
 * One ephemeris row.
 *
 * Longitude and latitude are Horizons' `ObsEcLon`/`ObsEcLat`: observer-centred
 * IAU76/80 ecliptic-*of-date* apparent position, including light-time, gravitational
 * deflection and stellar aberration. That is the "apparent geocentric longitude"
 * classical panchang arithmetic assumes, and it is not the same as a J2000 mean
 * longitude — comparing our engine's J2000 output against these would produce a bogus
 * ~0.3 degree offset from precession alone. The parser asserts the frame text is present
 * precisely so that substitution cannot happen unnoticed.
 */
@Serializable
data class HorizonsRecord(
    /** ISO-8601 UTC instant of the row. Horizons emits UTC for dates after 1962. */
    val utc: String,
    val apparentEclipticLongitudeDeg: Double,
    val apparentEclipticLatitudeDeg: Double,
    /** Observer range in au (`delta`), when quantity 20 was requested. */
    val distanceAu: Double? = null,
    /** Observer range rate in km/s (`deldot`), when quantity 20 was requested. */
    val rangeRateKmPerSec: Double? = null,
)

/** A parsed Horizons table: what was asked, what came back. */
@Serializable
data class HorizonsTable(
    val header: HorizonsHeader,
    val records: List<HorizonsRecord>,
)
