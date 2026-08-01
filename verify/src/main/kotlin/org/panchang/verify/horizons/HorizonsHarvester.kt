package org.panchang.verify.horizons

import org.panchang.verify.harvest.ArtifactStore
import org.panchang.verify.harvest.Harvester
import org.panchang.verify.harvest.ParsedArtifact
import org.panchang.verify.harvest.Provenance
import org.panchang.verify.harvest.RequestSpec
import java.time.Duration

/**
 * JPL Horizons: the primary oracle for factor 1, global tithi instants.
 *
 * Tithi is a function of (moon apparent longitude - sun apparent longitude), which is
 * location-independent. Horizons gives both to far better precision than any calendar
 * question requires, over 1600-2500, with no API key and no redistribution restriction
 * (US Government work). That combination is why it is the anchor rather than a
 * cross-check.
 *
 * Verified reachable from a developer host on 2026-08-01: the Moon's apparent geocentric
 * ecliptic longitude for 2026-04-14 00:00 UT is 338.2914490 degrees. That value is the
 * parser's first regression fixture.
 */
class HorizonsHarvester : Harvester<HorizonsQuery, HorizonsRecord>() {

    override val sourceId = "jpl-horizons"

    /**
     * v1: OBSERVER ephemeris, CENTER='500@399', QUANTITIES='31,20', CSV, degrees.
     * Any change to that set must bump this, or cached artifacts from the old shape will
     * be silently reused to answer a different question.
     */
    override val harvesterVersion = 1

    /**
     * Horizons results for a fixed past epoch are effectively immutable — the underlying
     * DE441 kernel does not change — but Earth-orientation predictions do get revised, so
     * a year is a reasonable staleness bound rather than "never".
     */
    override val cacheTtl: Duration = Duration.ofDays(365)

    override fun requestSpec(query: HorizonsQuery): RequestSpec = RequestSpec(
        sourceId = sourceId,
        baseUrl = ENDPOINT,
        parameters = listOf(
            "format" to "text",
            "COMMAND" to "'${query.body.command}'",
            "OBJ_DATA" to "'NO'",
            "MAKE_EPHEM" to "'YES'",
            "EPHEM_TYPE" to "'OBSERVER'",
            // 500@399 is the geocentre. Anything topocentric would move lunar longitude
            // by up to a degree and quietly invalidate every tithi instant derived here.
            "CENTER" to "'500@399'",
            "START_TIME" to "'${query.start}'",
            "STOP_TIME" to "'${query.stop}'",
            "STEP_SIZE" to "'${query.step}'",
            // 31 = observer ecliptic longitude/latitude; 20 = observer range and range rate.
            "QUANTITIES" to "'31,20'",
            "CAL_FORMAT" to "'CAL'",
            "ANG_FORMAT" to "'DEG'",
            "TIME_DIGITS" to "'SECONDS'",
            "CSV_FORMAT" to "'YES'",
        ),
        headers = mapOf("User-Agent" to org.panchang.verify.harvest.HttpFetcher.PROJECT_USER_AGENT),
        notes = listOf(
            "Source: NASA/JPL Horizons. US Government work, no redistribution restriction.",
        ),
    )

    override fun cacheKey(query: HorizonsQuery): String = ArtifactStore.cacheKey(
        readable = "${query.body.name.lowercase()}-${query.start}_${query.stop}",
        discriminator = "v$harvesterVersion|${query.body.command}|${query.start}|${query.stop}|${query.step}",
    )

    override fun parse(bytes: ByteArray, provenance: Provenance): ParsedArtifact<HorizonsRecord> {
        val query = queryFromProvenance(provenance)
        return HorizonsParser.parse(bytes.decodeToString(), query)
    }

    /**
     * Reconstructs the query from the stamp so a cached artifact is validated against the
     * request that actually produced it, not against whatever the caller is asking for
     * now. Without this, re-parsing a cached Sun table under a Moon query would pass the
     * target assertion by accident.
     */
    private fun queryFromProvenance(provenance: Provenance): HorizonsQuery {
        val p = provenance.requestParameters
        fun unquoted(key: String): String = p[key].orEmpty().trim('\'')
        return HorizonsQuery(
            body = HorizonsBody.parse(unquoted("COMMAND").ifEmpty { "301" }),
            start = unquoted("START_TIME"),
            stop = unquoted("STOP_TIME"),
            step = unquoted("STEP_SIZE"),
        )
    }

    companion object {
        const val ENDPOINT = "https://ssd.jpl.nasa.gov/api/horizons.api"
    }
}
