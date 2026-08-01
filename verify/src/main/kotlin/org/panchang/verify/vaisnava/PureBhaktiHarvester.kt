package org.panchang.verify.vaisnava

import kotlinx.serialization.Serializable
import org.panchang.verify.harvest.ArtifactStore
import org.panchang.verify.harvest.Harvester
import org.panchang.verify.harvest.HttpFetcher
import org.panchang.verify.harvest.ParseReport
import org.panchang.verify.harvest.ParsedArtifact
import org.panchang.verify.harvest.Provenance
import org.panchang.verify.harvest.RequestSpec
import org.panchang.verify.harvest.ResponseShapeException
import org.panchang.verify.harvest.Unparsed

/**
 * One entry from a purebhakti.com panjika listing.
 *
 * Kept as loose text plus an optional date because this source's own vocabulary differs
 * from ISKCON's — it follows a different Gaudiya line, which is precisely why it is worth
 * harvesting for factor 3: where two sampradayas disagree, we need to know it is a rule
 * difference and not our bug.
 */
@Serializable
data class PureBhaktiEntry(
    val date: String?,
    val text: String,
    val category: String?,
)

data class PureBhaktiQuery(
    val year: Int,
    val month: Int? = null,
    /** Panjika calendar id on the site. 1 is the default listing. */
    val calendarId: Int = 1,
)

/**
 * purebhakti.com Vaishnava calendar (Joomla `com_panjika` component).
 *
 * STATUS, observed 2026-08-01: the endpoint answers HTTP 200 but the served HTML contains
 * no calendar rows. `/component/vaisnava-calendar/` is 404; `/resources/vaisnava-calendar`
 * with `view=panjika&id=1` returns the site chrome with an empty system-message container,
 * and adding `date`, `month` or `year` parameters did not change that. Either the listing
 * is rendered client-side or it needs a parameter combination we have not found.
 *
 * The harvester and parser are therefore written and wired but **unverified against real
 * calendar content**. The parser fails loudly on a page with no entries rather than
 * returning an empty list, so this gap cannot be mistaken for "purebhakti has no events".
 * Do not treat this source as contributing to coverage until that failure stops firing.
 */
class PureBhaktiHarvester : Harvester<PureBhaktiQuery, PureBhaktiEntry>() {

    override val sourceId = "purebhakti"

    override val harvesterVersion = 1

    override val cacheTtl: java.time.Duration = java.time.Duration.ofDays(30)

    override fun requestSpec(query: PureBhaktiQuery): RequestSpec = RequestSpec(
        sourceId = sourceId,
        baseUrl = "$BASE/resources/vaisnava-calendar",
        parameters = buildList {
            add("view" to "panjika")
            add("id" to query.calendarId.toString())
            add("year" to query.year.toString())
            query.month?.let { add("month" to it.toString()) }
        },
        headers = mapOf("User-Agent" to HttpFetcher.PROJECT_USER_AGENT),
        notes = listOf("Source: purebhakti.com. Non-ISKCON Gaudiya reference; sect-rule cross-check."),
    )

    override fun cacheKey(query: PureBhaktiQuery): String = ArtifactStore.cacheKey(
        readable = "panjika-${query.year}${query.month?.let { "-%02d".format(it) } ?: ""}",
        discriminator = "v$harvesterVersion|${query.calendarId}|${query.year}|${query.month}",
    )

    override fun parse(bytes: ByteArray, provenance: Provenance): ParsedArtifact<PureBhaktiEntry> =
        PureBhaktiParser.parse(bytes.toString(Charsets.UTF_8))

    companion object {
        const val BASE = "https://www.purebhakti.com"
    }
}

object PureBhaktiParser {

    private const val SOURCE = "purebhakti"

    private val DATE_PREFIX = Regex(
        """^(\d{1,2})\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s*(\d{4})?\s*[-–:]?\s*(.*)$""",
        RegexOption.IGNORE_CASE,
    )

    private val CATEGORY_KEYWORDS = listOf(
        "ekadasi" to "Ekadasi",
        "ekadashi" to "Ekadasi",
        "parana" to "Parana",
        "appearance" to "Appearance",
        "disappearance" to "Disappearance",
        "purnima" to "Purnima",
        "amavasya" to "Amavasya",
    )

    fun parse(html: String): ParsedArtifact<PureBhaktiEntry> {
        val text = HtmlText.toText(html)
        val entries = mutableListOf<PureBhaktiEntry>()
        val unparsed = mutableListOf<Unparsed>()

        // Only lines that both start with a date and mention a calendar term are taken.
        // Site chrome is full of dates (article bylines, copyright years); a looser rule
        // would inflate the record count with noise, which is its own kind of lie.
        for ((i, line) in text.lines().withIndex()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val m = DATE_PREFIX.matchEntire(trimmed) ?: continue
            val body = m.groupValues[4].trim()
            if (body.isEmpty()) continue
            val category = CATEGORY_KEYWORDS.firstOrNull { body.contains(it.first, true) }?.second
                ?: continue
            val year = m.groupValues[3].ifEmpty { null }
            entries += PureBhaktiEntry(
                date = year?.let { "%s-%s-%02d".format(it, monthNumber(m.groupValues[2]), m.groupValues[1].toInt()) },
                text = body,
                category = category,
            )
            if (year == null) {
                unparsed += Unparsed("line ${i + 1}", trimmed, "entry has no year; date left null")
            }
        }

        if (entries.isEmpty()) {
            throw ResponseShapeException(
                SOURCE,
                "no calendar entries recognised in the served HTML. As of 2026-08-01 this " +
                    "endpoint returns HTTP 200 with site chrome and no panjika rows, so this " +
                    "failure is expected until the correct request shape is found. Reporting it " +
                    "as a failure rather than an empty calendar is deliberate.",
            )
        }
        return ParsedArtifact(entries, ParseReport(entries.size, unparsed))
    }

    private fun monthNumber(abbrev: String): String {
        val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        return "%02d".format(months.indexOf(abbrev.lowercase().take(3)) + 1)
    }
}
