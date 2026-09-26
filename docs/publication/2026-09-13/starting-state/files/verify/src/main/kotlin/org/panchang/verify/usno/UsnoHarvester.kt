package org.panchang.verify.usno

import org.panchang.verify.harvest.ArtifactStore
import org.panchang.verify.harvest.Harvester
import org.panchang.verify.harvest.HttpFetcher
import org.panchang.verify.harvest.ParsedArtifact
import org.panchang.verify.harvest.Provenance
import org.panchang.verify.harvest.RequestSpec
import org.panchang.verify.grid.ReferenceCity
import java.time.LocalDate
import java.time.ZoneId

/**
 * USNO Astronomical Applications: the oracle for factor 2, local sunrise and sunset.
 *
 * Factor 2 carries no religion. It is pure geometry plus a refraction convention, which
 * makes it the one factor where an external authority can adjudicate cleanly: if our
 * sunrise disagrees with USNO's by more than the rounding USNO publishes to, we are
 * wrong, with no appeal to sampradaya practice.
 *
 * USNO publishes rise/set to the minute. Any comparison harness must therefore allow a
 * one-minute tolerance and must not read more precision into these values than exists.
 */
class UsnoHarvester : Harvester<UsnoQuery, UsnoDayRecord>() {

    override val sourceId = "usno-rstt-oneday"

    override val harvesterVersion = 1

    /**
     * No TTL. Rise/set for a given date and place is a fixed astronomical fact; USNO will
     * not revise it, so re-fetching only costs their bandwidth.
     */
    override val cacheTtl = null

    override fun requestSpec(query: UsnoQuery): RequestSpec = RequestSpec(
        sourceId = sourceId,
        baseUrl = ENDPOINT,
        parameters = buildList {
            add("date" to query.date)
            // USNO wants "lat,lon" in that order, unlike the GeoJSON it returns.
            add("coords" to "${query.latitudeDeg},${query.longitudeDeg}")
            add("tz" to formatOffset(query.utcOffsetHours))
            query.label?.let { add("label" to it) }
        },
        headers = mapOf("User-Agent" to HttpFetcher.PROJECT_USER_AGENT),
        notes = listOf("Source: US Naval Observatory. US Government work."),
    )

    override fun cacheKey(query: UsnoQuery): String = ArtifactStore.cacheKey(
        readable = "${query.label ?: "coord"}-${query.date}",
        discriminator = "v$harvesterVersion|${query.date}|${query.latitudeDeg}|" +
            "${query.longitudeDeg}|${query.utcOffsetHours}",
    )

    override fun parse(bytes: ByteArray, provenance: Provenance): ParsedArtifact<UsnoDayRecord> {
        val p = provenance.requestParameters
        val coords = p["coords"].orEmpty().split(",")
        val query = UsnoQuery(
            date = p["date"].orEmpty(),
            latitudeDeg = coords.getOrNull(0)?.toDoubleOrNull() ?: 0.0,
            longitudeDeg = coords.getOrNull(1)?.toDoubleOrNull() ?: 0.0,
            utcOffsetHours = p["tz"]?.toDoubleOrNull() ?: 0.0,
            label = p["label"],
        )
        return UsnoParser.parse(bytes.decodeToString(), query)
    }

    companion object {
        const val ENDPOINT = "https://aa.usno.navy.mil/api/rstt/oneday"

        /**
         * USNO takes a fixed numeric UTC offset and knows nothing about IANA zones or DST
         * transitions. Resolving the offset here, from the zone and the date, is where the
         * DST stress cases actually bite: ask for Auckland on 2026-04-05 with the wrong
         * offset and USNO returns a perfectly plausible answer that is an hour out.
         *
         * The offset is taken at local noon rather than midnight so that a day containing
         * a transition resolves to the offset in force for most of its daylight, which is
         * the offset a sunrise time is meaningful in.
         */
        fun offsetHoursFor(zone: ZoneId, date: LocalDate): Double {
            val noon = date.atTime(12, 0).atZone(zone)
            return noon.offset.totalSeconds / 3600.0
        }

        fun queryFor(city: ReferenceCity, date: LocalDate): UsnoQuery = UsnoQuery(
            date = date.toString(),
            latitudeDeg = city.latitudeDeg,
            longitudeDeg = city.longitudeDeg,
            utcOffsetHours = offsetHoursFor(ZoneId.of(city.ianaZone), date),
            label = city.id,
        )

        /** USNO accepts fractional offsets; India is +5.5 and Nepal +5.75. */
        fun formatOffset(hours: Double): String =
            if (hours == hours.toInt().toDouble()) hours.toInt().toString() else hours.toString()
    }
}
