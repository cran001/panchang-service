package org.panchang.verify.vaisnava

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The site a golden calendar file describes, as the source itself states it.
 *
 * A golden calendar with no site block is a list of times with no place attached, and a
 * time without a place cannot be checked against anything. Every field here except
 * [ianaZone] and [cityId] is read out of the artifact's own printed header, never out of
 * [org.panchang.verify.grid.ReferenceCities]: the source computed its times at *its*
 * coordinates, and a datum disagreement and a rule disagreement look identical once a time
 * has been rounded to the printed minute. Evaluating the reference at coordinates it was
 * not computed for would silently convert one into the other. The published header puts
 * Delhi at 28N40 77E13; our grid carries 28.6139/77.2090, ~1.3 km apart.
 */
@Serializable
data class VaisnavaSite(
    /** City exactly as the header prints it, e.g. "Delhi [India]". */
    val city: String,
    /** Coordinate string exactly as printed, e.g. "28N40 77E13". */
    val coordinates: String,
    /** UTC offset exactly as printed, e.g. "+5:30". This is a *standard* offset. */
    val utcOffset: String,
    /** Generator identification as printed, e.g. "GCal 11, Build 5". */
    val generator: String?,
    /** Our reference grid id for this location; the file name is built from it. */
    val cityId: String,
    /** [coordinates] in degrees, south and west negative. Derived, not re-sourced. */
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    /**
     * IANA zone, hand-entered — see [VaisnavaSiteZones].
     *
     * The one field that cannot be read from the artifact. The source prints a fixed
     * offset, and an offset is not a zone: "+12:00" does not say whether the 13:00 lines
     * later in the file are a DST rule or a different city.
     */
    val ianaZone: String,
)

/**
 * Hand-entered IANA zones for the cities vaisnavacalendar.info publishes.
 *
 * Keyed by the source's own printed city string rather than by our city id, because the
 * source is the thing being described: it files Mumbai as "Bombay [India]" and Moscow as
 * "Moskva [Russia]", and keying off the printed name means a renamed file cannot be
 * silently paired with the wrong zone.
 *
 * Each entry is checked against the header's printed offset by [siteFor] at generation
 * time — the printed value is a standard offset (the file marks daylight-saving lines
 * separately with a "DST" clock tag), so it must equal the zone's standard offset for the
 * whole calendar year. That check will not catch a zone that has the right offset and the
 * wrong DST rule, so it is a guard against transcription slips, not a proof.
 */
object VaisnavaSiteZones {

    private val ZONES: Map<String, String> = mapOf(
        "Mayapur [India]" to "Asia/Kolkata",
        "Vrindavan [India]" to "Asia/Kolkata",
        "Delhi [India]" to "Asia/Kolkata",
        "Bombay [India]" to "Asia/Kolkata",
        "Madras [India]" to "Asia/Kolkata",
        "Bangalore [India]" to "Asia/Kolkata",
        "Ahmadabad [India]" to "Asia/Kolkata",
        "Guwahati [India]" to "Asia/Kolkata",
        "London [United Kingdom]" to "Europe/London",
        "New York City [United States of America]" to "America/New_York",
        "Auckland [New Zealand]" to "Pacific/Auckland",
        "Moskva [Russia]" to "Europe/Moscow",
        "Sao Paulo [Brazil]" to "America/Sao_Paulo",
        "Sydney [Australia]" to "Australia/Sydney",
    )

    fun zoneFor(printedCity: String): String? = ZONES[printedCity]

    private val COORDINATES =
        Regex("""^(\d{1,3})([NS])(\d{1,2})\s+(\d{1,3})([EW])(\d{1,2})$""")

    private val OFFSET = Regex("""^([+-])?(\d{1,2}):(\d{2})$""")

    /**
     * Builds the site block for one harvested calendar, or throws with the reason.
     *
     * Throws rather than degrading to a partial site: a golden file that names a place it
     * is not sure about is worse than no golden file, because a location test written
     * against it would pass for the wrong reason.
     */
    fun siteFor(cityId: String, header: VaisnavaCalendarHeader, year: Int): VaisnavaSite {
        val zone = zoneFor(header.city)
            ?: error(
                "No IANA zone entered for printed city '${header.city}'. The source states a " +
                    "fixed offset (${header.utcOffset}) and an offset is not a zone; add the " +
                    "zone by hand in VaisnavaSiteZones and check it against that offset.",
            )
        val coords = COORDINATES.matchEntire(header.coordinates.trim())
            ?: error("Unrecognised coordinate string '${header.coordinates}' for '${header.city}'.")
        val lat = (coords.groupValues[1].toInt() + coords.groupValues[3].toInt() / 60.0)
            .let { if (coords.groupValues[2] == "S") -it else it }
        val lon = (coords.groupValues[4].toInt() + coords.groupValues[6].toInt() / 60.0)
            .let { if (coords.groupValues[5] == "W") -it else it }

        val printed = parseOffset(header.utcOffset)
            ?: error("Unrecognised UTC offset '${header.utcOffset}' for '${header.city}'.")
        checkStandardOffset(header.city, zone, printed, year)

        return VaisnavaSite(
            city = header.city,
            coordinates = header.coordinates,
            utcOffset = header.utcOffset,
            generator = header.generator,
            cityId = cityId,
            latitudeDeg = lat,
            longitudeDeg = lon,
            ianaZone = zone,
        )
    }

    fun parseOffset(text: String): ZoneOffset? {
        val m = OFFSET.matchEntire(text.trim()) ?: return null
        val sign = if (m.groupValues[1] == "-") -1 else 1
        return ZoneOffset.ofHoursMinutes(sign * m.groupValues[2].toInt(), sign * m.groupValues[3].toInt())
    }

    /**
     * Verifies the hand-entered zone against the printed standard offset.
     *
     * Sampled at both solstices because a zone whose standard offset changes mid-year
     * (Moscow did in 2014, Sao Paulo's neighbours have done since) would otherwise pass on
     * whichever half we happened to look at.
     */
    private fun checkStandardOffset(city: String, zone: String, printed: ZoneOffset, year: Int) {
        val rules = ZoneId.of(zone).rules
        for (month in listOf(1, 7)) {
            val instant = LocalDate.of(year, month, 15).atStartOfDay(ZoneOffset.UTC).toInstant()
            val actual = rules.getStandardOffset(instant)
            check(actual.totalSeconds == printed.totalSeconds) {
                "Zone '$zone' entered for '$city' has standard offset $actual in ${year}-$month, " +
                    "but the calendar header prints $printed. One of the two is wrong; the header " +
                    "is the artifact, so fix the zone."
            }
        }
    }
}
