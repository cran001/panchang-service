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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/**
 * A single Ekadashi notice as an ISKCON temple publishes it for its own city.
 *
 * Narrower than [VaisnavaDay] on purpose. This source states one observance at a time,
 * in prose, for one location; pretending it yields a full calendar would overstate it.
 */
@Serializable
data class IskconEkadasiNotice(
    val ekadasiName: String?,
    /** ISO date of the parana, which falls on dvadasi, i.e. the day after the fast. */
    val paranaDate: String?,
    /** Local clock times exactly as printed, normalised to HH:MM 24-hour. */
    val paranaStart: String?,
    val paranaEnd: String?,
    val city: String?,
    /** The sentence the values were taken from, kept for audit. */
    val sourceSentence: String,
)

enum class IskconMumbaiPage(val path: String) {
    /**
     * Static prose page carrying the current Ekadashi name and its parana window.
     * This is the only part of the site that yields data without executing JavaScript.
     */
    EKADASI("/ekadasi"),

    /**
     * The month calendar. Server-renders an empty `<div id="calendar"></div>` and fills it
     * client-side, so a static fetch yields no events. Kept in the enum so the CLI can
     * demonstrate the failure explicitly rather than have a developer rediscover it.
     */
    CALENDAR("/vaishnava-calendar/"),
}

data class IskconMumbaiQuery(val page: IskconMumbaiPage = IskconMumbaiPage.EKADASI)

/**
 * iskconmumbai.com.
 *
 * A secondary factor-3 cross-check: it tells us what one large ISKCON temple actually
 * announces to its congregation, which is the practice a calendar is ultimately judged
 * against. It is not a substitute for [VaisnavaCalendarTxtHarvester]: it covers one city
 * and, at any moment, one observance.
 */
class IskconMumbaiHarvester : Harvester<IskconMumbaiQuery, IskconEkadasiNotice>() {

    override val sourceId = "iskcon-mumbai"

    override val harvesterVersion = 1

    /**
     * The Ekadashi page is rewritten roughly fortnightly, so a cached copy goes stale
     * quickly — unlike the astronomical sources, this one describes a moving present.
     */
    override val cacheTtl: java.time.Duration = java.time.Duration.ofDays(3)

    override fun requestSpec(query: IskconMumbaiQuery): RequestSpec = RequestSpec(
        sourceId = sourceId,
        baseUrl = "$BASE${query.page.path}",
        headers = mapOf("User-Agent" to HttpFetcher.PROJECT_USER_AGENT),
        notes = listOf("Source: iskconmumbai.com. Comparison reference for Mumbai observance."),
    )

    override fun cacheKey(query: IskconMumbaiQuery): String = ArtifactStore.cacheKey(
        readable = query.page.name.lowercase(),
        discriminator = "v$harvesterVersion|${query.page.path}",
    )

    override fun parse(bytes: ByteArray, provenance: Provenance): ParsedArtifact<IskconEkadasiNotice> {
        val html = bytes.toString(Charsets.UTF_8)
        return if (provenance.url.endsWith(IskconMumbaiPage.CALENDAR.path)) {
            IskconMumbaiParser.parseCalendarPage(html)
        } else {
            IskconMumbaiParser.parseEkadasiPage(html)
        }
    }

    companion object {
        const val BASE = "https://www.iskconmumbai.com"
    }
}

object IskconMumbaiParser {

    private const val SOURCE = "iskcon-mumbai"

    /**
     * Matches, e.g.:
     * "Fast breaking parana time on dvadasi: 10 Aug 2026, 6:18 AM to 08:02 AM for Mumbai"
     *
     * Hours are written inconsistently (`6:18 AM` and `08:02 AM` in the same sentence), so
     * the hour group allows one or two digits and the times are normalised afterwards.
     */
    private val PARANA_SENTENCE = Regex(
        """(?i)Fast\s+breaking\s+parana\s+time\s+on\s+(\w+)\s*:\s*""" +
            """(\d{1,2}\s+\w{3,9}\s+\d{4})\s*,\s*""" +
            """(\d{1,2}:\d{2}\s*[AP]\.?M\.?)\s*(?:to|-|–)\s*(\d{1,2}:\d{2}\s*[AP]\.?M\.?)""" +
            """\s*(?:for\s+([A-Za-z .]+))?""",
    )

    private val EKADASI_HEADING = Regex("""(?i)^(.{0,60}?Ekadas(?:h)?i)\s*$""")

    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("d MMM yyyy")
        .toFormatter(Locale.ENGLISH)

    private val LONG_DATE_FORMAT: DateTimeFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("d MMMM yyyy")
        .toFormatter(Locale.ENGLISH)

    fun parseEkadasiPage(html: String): ParsedArtifact<IskconEkadasiNotice> {
        val text = HtmlText.toText(html)
        val unparsed = mutableListOf<Unparsed>()
        val warnings = mutableListOf<String>()

        val match = PARANA_SENTENCE.find(text)
            ?: throw ResponseShapeException(
                SOURCE,
                "no 'Fast breaking parana time on ...' sentence found. The page layout has " +
                    "changed, or the fetch returned a placeholder. Refusing to report an empty " +
                    "result as a successful harvest.",
            )

        val dateText = match.groupValues[2]
        val date = parseDate(dateText)
        if (date == null) {
            unparsed += Unparsed("parana sentence", dateText, "unrecognised date format")
        }

        val name = EKADASI_HEADING.find(text.lines().firstOrNull { it.contains("Ekadasi", true) }.orEmpty())
            ?.groupValues?.get(1)?.trim()
            ?: text.lines().firstOrNull { it.contains("Ekadasi", true) }?.trim()
        if (name == null) {
            warnings += "Parana window found but no Ekadashi name heading; the name is unverified."
        }

        val notice = IskconEkadasiNotice(
            ekadasiName = name,
            paranaDate = date?.toString(),
            paranaStart = normaliseTime(match.groupValues[3]),
            paranaEnd = normaliseTime(match.groupValues[4]),
            city = match.groupValues[5].trim().ifEmpty { null },
            sourceSentence = match.value.trim(),
        )
        return ParsedArtifact(listOf(notice), ParseReport(1, unparsed, warnings))
    }

    /**
     * The month calendar page. Always fails, by design.
     *
     * The page ships `<div id="calendar"></div>` and populates it from JavaScript, so a
     * static fetch has nothing to parse. Returning an empty list here would look like "no
     * events this month" to every caller. Failing tells the truth: this page needs a
     * headless browser or an undiscovered data endpoint, and neither exists in this module.
     */
    fun parseCalendarPage(html: String): ParsedArtifact<IskconEkadasiNotice> {
        val placeholder = Regex("""<div\s+id=["']calendar["']\s*>\s*</div>""").containsMatchIn(html)
        throw ResponseShapeException(
            SOURCE,
            if (placeholder) {
                "the /vaishnava-calendar/ page renders its events client-side (empty " +
                    "<div id=\"calendar\"></div> in the served HTML). No events can be harvested " +
                    "from static HTML. Use the vaisnavacalendar.info text export instead."
            } else {
                "the /vaishnava-calendar/ page did not contain the expected client-side calendar " +
                    "placeholder and no server-rendered events were recognised either."
            },
        )
    }

    private fun parseDate(s: String): LocalDate? {
        val cleaned = s.replace(Regex("\\s+"), " ").trim()
        return runCatching { LocalDate.parse(cleaned, DATE_FORMAT) }
            .recoverCatching { LocalDate.parse(cleaned, LONG_DATE_FORMAT) }
            .getOrNull()
    }

    /** "6:18 AM" and "08:02 AM" both become "06:18" / "08:02". */
    internal fun normaliseTime(s: String): String? {
        val m = Regex("""(?i)(\d{1,2}):(\d{2})\s*([AP])""").find(s) ?: return null
        var hour = m.groupValues[1].toInt() % 12
        if (m.groupValues[3].uppercase() == "P") hour += 12
        return "%02d:%s".format(hour, m.groupValues[2])
    }
}
