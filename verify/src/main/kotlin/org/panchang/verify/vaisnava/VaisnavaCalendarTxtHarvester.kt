package org.panchang.verify.vaisnava

import org.panchang.verify.harvest.ArtifactStore
import org.panchang.verify.harvest.Harvester
import org.panchang.verify.harvest.HttpFetcher
import org.panchang.verify.harvest.ParsedArtifact
import org.panchang.verify.harvest.Provenance
import org.panchang.verify.harvest.RequestSpec
import org.panchang.verify.grid.ReferenceCity

/**
 * @param city the file stem used by the source, e.g. "Mayapur [India]". Not our city id:
 *   the source files Mumbai under "Bombay [India]" and Moscow under "Moskva [Russia]",
 *   and mapping that here rather than guessing keeps the mismatch visible.
 */
data class VaisnavaCalendarQuery(val year: Int, val city: String) {
    companion object {
        fun forCity(city: ReferenceCity, year: Int): VaisnavaCalendarQuery? =
            city.vaisnavaCalendarCity?.let { VaisnavaCalendarQuery(year, it) }
    }
}

/**
 * vaisnavacalendar.info per-city text calendars.
 *
 * The primary factor-3 reference anchor. See [VaisnavaCalendarTxtParser] for why the text
 * export is used in preference to any of the HTML calendar pages.
 *
 * This is a small volunteer-run site on shared hosting. The rate limiter gives it a
 * multi-second gap, and a full grid harvest is ten files per year — do not turn this into
 * a per-day fetch loop.
 */
class VaisnavaCalendarTxtHarvester : Harvester<VaisnavaCalendarQuery, VaisnavaDay>() {

    override val sourceId = "vaisnavacalendar-txt"

    override val harvesterVersion = 1

    /**
     * No TTL. A published year's calendar is a fixed artifact; if the publisher revises
     * one, that revision is itself news and should be picked up by deleting the cache
     * entry deliberately, not by silent expiry.
     */
    override val cacheTtl = null

    override fun requestSpec(query: VaisnavaCalendarQuery): RequestSpec = RequestSpec(
        sourceId = sourceId,
        // The site's own index links these through a `wp2020/../calendars/` path; the
        // normalised form below is what the server actually serves.
        baseUrl = "$BASE/${query.year}/${RequestSpec.encode(query.city)}.txt",
        headers = mapOf("User-Agent" to HttpFetcher.PROJECT_USER_AGENT),
        notes = listOf(
            "Source: vaisnavacalendar.info (Gaurabda Calendar export). Community-published " +
                "reference used for comparison; retain attribution if any excerpt is ever shown.",
        ),
    )

    override fun cacheKey(query: VaisnavaCalendarQuery): String = ArtifactStore.cacheKey(
        readable = "${query.city}-${query.year}",
        discriminator = "v$harvesterVersion|${query.year}|${query.city}",
    )

    override fun parse(bytes: ByteArray, provenance: Provenance): ParsedArtifact<VaisnavaDay> =
        VaisnavaCalendarTxtParser.parse(bytes.toString(Charsets.ISO_8859_1))

    /**
     * The header block of a harvested artifact: city, coordinates, offset, generator.
     *
     * Re-parses the raw bytes rather than caching a header on this object during [parse].
     * A harvester that remembers something about the last thing it parsed is a harvester
     * that can hand a caller the previous city's coordinates, and the whole point of the
     * site block is that it belongs to the file it is written into. Re-parsing 40 KB is
     * free next to that risk.
     */
    fun headerOf(bytes: ByteArray): VaisnavaCalendarHeader =
        VaisnavaCalendarTxtParser.parseCalendar(bytes.toString(Charsets.ISO_8859_1)).first.header

    companion object {
        const val BASE = "https://www.vaisnavacalendar.info/calendars"

        /** Index page listing the available city files for a year; useful for discovery. */
        fun indexUrl(year: Int) =
            "https://www.vaisnavacalendar.info/calendar-file-downloads/txt-calendar-files-$year"
    }
}
