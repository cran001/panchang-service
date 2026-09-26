package org.panchang.verify.drik

import kotlinx.serialization.Serializable
import org.panchang.verify.harvest.ArtifactStore
import org.panchang.verify.harvest.Harvester
import org.panchang.verify.harvest.ParseReport
import org.panchang.verify.harvest.ParsedArtifact
import org.panchang.verify.harvest.Provenance
import org.panchang.verify.harvest.RequestSpec
import org.panchang.verify.harvest.ResponseShapeException
import org.panchang.verify.harvest.Unparsed
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * A day panchang as drikpanchang.com states it.
 *
 * ---------------------------------------------------------------------------
 * REDISTRIBUTION CONSTRAINT — read before using anything in this file.
 *
 * drikpanchang.com is a commercial site. We have no licence and no agreement with them.
 * This harvester exists so we can *measure ourselves against* their published values and
 * find out where we differ. Their data must never be:
 *   - committed to this repository (including as a test fixture),
 *   - served by the API module, published by the publish module, or embedded in any
 *     artifact we ship,
 *   - included in any dataset we release.
 *
 * Harvested Drik artifacts live only in `verify/cache/`, which is gitignored. If you find
 * yourself wanting to promote one into `verify/golden/`, the answer is no; use JPL
 * Horizons for astronomy and the community calendars for observance instead.
 *
 * Rate limiting: the largest gap of any source in this module (see
 * HostRateLimiter.DEFAULT_PER_HOST). A comparison run should sample dates, not sweep them.
 * ---------------------------------------------------------------------------
 */
@Serializable
data class DrikDayPanchang(
    val date: String,
    /** Key/value rows exactly as the page labels them, in page order. */
    val rows: List<DrikRow>,
) {
    fun value(key: String): String? =
        rows.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
}

@Serializable
data class DrikRow(val key: String, val value: String)

/**
 * @param geonameId drikpanchang's own location identifier. Left null for the site default
 *   (New Delhi). We do not guess these: an unverified geoname id silently answers for the
 *   wrong city, which is the exact failure this module exists to catch.
 */
data class DrikQuery(
    val date: LocalDate,
    val geonameId: Int? = null,
)

class DrikPanchangHarvester : Harvester<DrikQuery, DrikDayPanchang>() {

    override val sourceId = "drikpanchang"

    override val harvesterVersion = 1

    override val cacheTtl: Duration = Duration.ofDays(365)

    override fun requestSpec(query: DrikQuery): RequestSpec = RequestSpec(
        sourceId = sourceId,
        baseUrl = "$BASE/panchang/day-panchang.html",
        parameters = buildList {
            add("date" to DATE_PARAM.format(query.date))
            query.geonameId?.let { add("geoname-id" to it.toString()) }
        },
        // The site returns 403 to a request without a browser User-Agent. We send one,
        // and we compensate for the impoliteness of doing so with a 10-second per-request
        // gap and a policy of sampling rather than sweeping.
        headers = mapOf(
            "User-Agent" to BROWSER_USER_AGENT,
            "Accept" to "text/html,application/xhtml+xml",
            "Accept-Language" to "en-US,en;q=0.9",
        ),
        notes = listOf(
            "COMPARISON TARGET ONLY. drikpanchang.com data must never be committed, " +
                "redistributed, or served. Cache only, under verify/cache/ (gitignored).",
        ),
    )

    override fun cacheKey(query: DrikQuery): String = ArtifactStore.cacheKey(
        readable = "day-${query.date}${query.geonameId?.let { "-g$it" } ?: ""}",
        discriminator = "v$harvesterVersion|${query.date}|${query.geonameId}",
    )

    override fun parse(bytes: ByteArray, provenance: Provenance): ParsedArtifact<DrikDayPanchang> {
        val date = provenance.requestParameters["date"]
            ?.let { runCatching { LocalDate.parse(it, DATE_PARAM) }.getOrNull() }
            ?: throw ResponseShapeException(sourceId, "provenance has no parsable 'date' parameter")
        return DrikPanchangParser.parse(bytes.toString(Charsets.UTF_8), date)
    }

    companion object {
        const val BASE = "https://www.drikpanchang.com"
        val DATE_PARAM: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        /**
         * A plain library User-Agent gets 403 here. This string is the minimum needed to
         * be served; it is not an attempt to disguise the client, and every request still
         * goes through the 10-second-per-host turnstile.
         */
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}

/**
 * Extracts the `dpTableCell` key/value pairs from a day-panchang page.
 *
 * The page nests anchors, spans, images and popup divs inside both cells, so cell content
 * is taken by counting `<div>` depth rather than by a non-greedy regex: a regex that
 * stops at the first `</div>` truncates every value, and one that runs to the next cell
 * marker swallows the intervening card headings. Neither error is visible in the output,
 * which is why the extraction is done properly instead.
 *
 * Continuation rows (a second tithi starting later the same day) have an empty key cell.
 * Those are attached to the key *above them in the same column* with a `" (cont)"` suffix
 * rather than dropped: "Dwadashi upto 12:12 AM, then Trayodashi" is one fact, and losing
 * its tail is how a tithi-boundary comparison silently passes.
 *
 * The column matters. The card is a two-column grid, so the cells arrive in document order
 * as key1 value1 key2 value2 per row; a continuation in column one belongs to the key two
 * cells above it, not to the most recent key seen anywhere. Attaching it to the most recent
 * key files the second tithi under "Nakshatra", which is a wrong answer that still looks
 * like a successful parse.
 */
object DrikPanchangParser {

    private const val SOURCE = "drikpanchang"

    private val ROW_OPEN = Regex("""<div class="dpTableRow"\s*>""")
    private val CELL_OPEN = Regex("""<div class="dpTableCell (dpTableKey|dpTableValue)"\s*>""")

    fun parse(html: String, date: LocalDate): ParsedArtifact<DrikDayPanchang> {
        if (!html.contains("dpTableCell")) {
            throw ResponseShapeException(
                SOURCE,
                "no dpTableCell markup in the response. A 403 interstitial or a redesigned " +
                    "page will land here; either way there is nothing to compare against.",
            )
        }

        val rows = mutableListOf<DrikRow>()
        val unparsed = mutableListOf<Unparsed>()
        val lastKeyByColumn = mutableMapOf<Int, String>()

        // If the row wrapper ever disappears, fall back to treating the document as one
        // long row: the column pairing degrades but no cell is silently dropped.
        val rowBodies = ROW_OPEN.findAll(html).mapNotNull { balancedDivContent(html, it.range.last + 1) }.toList()
        val bodies = rowBodies.ifEmpty { listOf(html) }

        for ((rowIndex, body) in bodies.withIndex()) {
            var column = -1
            var pendingKey: String? = null
            for ((cellIndex, m) in CELL_OPEN.findAll(body).withIndex()) {
                val where = "dpTableRow[$rowIndex].dpTableCell[$cellIndex]"
                val inner = balancedDivContent(body, m.range.last + 1)
                if (inner == null) {
                    unparsed += Unparsed(where, m.value, "unbalanced <div> nesting; cell not closed")
                    continue
                }
                val text = stripMarkup(inner)
                if (m.groupValues[1] == "dpTableKey") {
                    if (pendingKey != null) {
                        unparsed += Unparsed(
                            where,
                            pendingKey.orEmpty(),
                            "key cell followed by another key cell; the first has no value",
                        )
                    }
                    column++
                    pendingKey = text.ifEmpty { lastKeyByColumn[column]?.let { "$it (cont)" }.orEmpty() }
                } else {
                    val key = pendingKey
                    if (key.isNullOrEmpty()) {
                        unparsed += Unparsed(where, text.take(200), "value cell with no key")
                    } else {
                        rows += DrikRow(key, text)
                        if (!key.endsWith("(cont)")) lastKeyByColumn[column] = key
                    }
                    pendingKey = null
                }
            }
            if (pendingKey != null) {
                unparsed += Unparsed("dpTableRow[$rowIndex]", pendingKey.orEmpty(), "key cell with no value")
            }
        }

        if (rows.isEmpty()) {
            throw ResponseShapeException(SOURCE, "dpTableCell markup present but no key/value pairs recovered")
        }
        return ParsedArtifact(
            listOf(DrikDayPanchang(date.toString(), rows)),
            ParseReport(rows.size, unparsed),
        )
    }

    /** Content of a `<div>` already opened at [from], honouring nested divs. */
    internal fun balancedDivContent(html: String, from: Int): String? {
        var depth = 1
        var i = from
        while (i < html.length) {
            val nextOpen = html.indexOf("<div", i).let { if (it < 0) Int.MAX_VALUE else it }
            val nextClose = html.indexOf("</div", i).let { if (it < 0) Int.MAX_VALUE else it }
            if (nextOpen == Int.MAX_VALUE && nextClose == Int.MAX_VALUE) return null
            if (nextOpen < nextClose) {
                depth++
                i = nextOpen + 4
            } else {
                depth--
                if (depth == 0) return html.substring(from, nextClose)
                i = nextClose + 5
            }
        }
        return null
    }

    private val TAG = Regex("(?s)<[^>]*>")

    private fun stripMarkup(fragment: String): String =
        fragment.replace(TAG, " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()
}
