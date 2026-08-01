package org.panchang.publish

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.panchang.wire.SiteRejectionCode
import org.panchang.wire.WireJson

/**
 * One real publish run, asserted end to end.
 *
 * A full year for two sites is computed once and shared, because the cost is real and every
 * assertion below is about the same artifacts an operator would ship.
 */
class FeedPublisherTest {

    private companion object {

        const val YEAR = 2026

        /**
         * Five requested sites, chosen so that every outcome this module can produce occurs once:
         * one legacy-publishable site, one withheld for daylight saving, one refused by `:wire`,
         * one input error the gazetteer rejects, and one line that cannot be read at all.
         */
        val SITE_LIST = """
            coords    mayapur   23.4249  88.3883   Asia/Kolkata        Sri Mayapur Dham
            coords    new-york  40.7128  -74.0060  America/New_York    New York
            coords    svalbard  78.2232  15.6469   Arctic/Longyearbyen Longyearbyen
            place-id  nowhere   999999999
            coords    broken    not-a-latitude
        """.trimIndent()

        val specs: List<SiteSpec> by lazy { SiteList.parse(SITE_LIST) }

        val result: PublishResult by lazy { FeedPublisher().run(specs, YEAR) }

        val legacyDays: List<LegacyDay> by lazy {
            WireJson.pretty.decodeFromString(
                ListSerializer(LegacyDay.serializer()),
                result.files.getValue("legacy/P0530/mayapur.json"),
            )
        }
    }

    // ── the counts ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `published plus skipped equals requested`() {
        val m = result.manifest
        assertEquals(5, m.requested, "every line of the site list is a request, including the bad ones")
        assertEquals(m.requested, m.published + m.skipped)
        assertEquals(m.skipped, result.skipped.sites.size)
        assertEquals(2, m.published)
        assertEquals(3, m.skipped)
        // And the file an operator actually reads says the same thing.
        assertEquals(m.requested, result.skipped.requested)
        assertEquals(m.published, result.skipped.published)
    }

    @Test
    fun `every skipped site names a code, and a wire refusal is reported as wire's`() {
        val bySite = result.skipped.sites.associateBy { it.key }
        assertEquals(setOf("svalbard", "nowhere", "line-5"), bySite.keys)

        assertEquals(SiteRejectionCode.ABOVE_POLAR_LIMIT, bySite.getValue("svalbard").siteRejectionCode)
        assertEquals(null, bySite.getValue("svalbard").inputRejectionCode)

        // Not a SiteRejectionCode: there is no wire code for "you typed an id that does not
        // exist", and borrowing the nearest one would report a typo as a property of the Earth.
        assertEquals(InputRejectionCode.UNKNOWN_PLACE_ID, bySite.getValue("nowhere").inputRejectionCode)
        assertEquals(null, bySite.getValue("nowhere").siteRejectionCode)

        assertEquals(InputRejectionCode.MALFORMED_SITE_LINE, bySite.getValue("line-5").inputRejectionCode)

        result.skipped.sites.forEach {
            assertTrue(it.reason.isNotBlank(), "${it.key} was skipped with no reason to show anyone")
            assertTrue(it.query.isNotBlank(), "${it.key} does not echo what was asked for")
        }
    }

    @Test
    fun `legacy published plus withheld equals published`() {
        val m = result.manifest
        assertEquals(m.published, m.legacyPublished + m.legacyWithheld)
        assertEquals(1, m.legacyPublished)
        assertEquals(1, m.legacyWithheld)
        val withheld = m.legacyWithheldSites.single()
        assertEquals("new-york", withheld.key)
        assertEquals("America/New_York", withheld.timeZone)
        assertEquals(LegacyWithholdingCode.ZONE_NOT_FIXED_OFFSET, withheld.code)
    }

    // ── the v1 shape ────────────────────────────────────────────────────────────────────────

    @Test
    fun `every accepted site gets both v1 documents, including the one with no legacy file`() {
        listOf("mayapur", "new-york").forEach { key ->
            assertTrue(
                result.files.containsKey("v1/$key/year-resolution.json"),
                "no v1 year resolution for $key",
            )
            assertTrue(
                result.files.containsKey("v1/$key/ekadashi-year.json"),
                "no v1 ekadashi year for $key",
            )
        }
        // Withholding a legacy file must not withhold the correct data. The DST site's v1 payload
        // is complete, and its instants carry the real zone offset.
        assertTrue(result.files.getValue("v1/new-york/year-resolution.json").contains("America/New_York"))
        assertFalse(
            result.files.keys.any { it.startsWith("legacy/") && it.contains("new-york") },
            "a legacy file was published for a zone that changes offset",
        )
    }

    @Test
    fun `the published v1 year resolution carries unresolved`() {
        // The sect seam's record of what it could NOT place. A payload that dropped it would look
        // complete while being short a festival, with no artifact anywhere saying so.
        listOf("mayapur", "new-york").forEach { key ->
            assertTrue(
                result.files.getValue("v1/$key/year-resolution.json").contains("\"unresolved\""),
                "v1/$key/year-resolution.json has no unresolved map",
            )
        }
    }

    // ── the legacy feed ─────────────────────────────────────────────────────────────────────

    @Test
    fun `zones and locations are the shape CalendarSyncRepository navigates`() {
        val zones = WireJson.pretty.decodeFromString(
            ListSerializer(LegacyZone.serializer()),
            result.files.getValue("legacy/zones.json"),
        )
        assertEquals(listOf("P0530"), zones.map { it.dir })

        val locations = WireJson.pretty.decodeFromString(
            ListSerializer(LegacyLocation.serializer()),
            result.files.getValue("legacy/P0530/locations.json"),
        )
        val mayapur = locations.single { it.file == "mayapur" }
        assertEquals("+05:30", mayapur.timezone)
        assertEquals("Sri Mayapur Dham", mayapur.title)
        assertTrue(LegacyFeed.COORDINATES.matches(mayapur.coordinates), mayapur.coordinates)
        // The path the app builds: /{dir}/{file}.json
        assertTrue(result.files.containsKey("legacy/P0530/${mayapur.file}.json"))
    }

    @Test
    fun `the legacy day file covers the whole year with no gaps`() {
        assertEquals(365, legacyDays.size)
        var expected = LocalDate.of(YEAR, 1, 1)
        legacyDays.forEach { day ->
            assertEquals(expected.toString(), day.date)
            assertTrue(day.tithi.isNotBlank(), "${day.date} has no tithi")
            expected = expected.plusDays(1)
        }
        assertEquals(LocalDate.of(YEAR + 1, 1, 1), expected)
    }

    @Test
    fun `every parana in the legacy feed is recovered by the app's own pipeline`() {
        // This reproduces LiveCalendarDataSource.processAllDays: find break-fast items with the
        // app's regex, subtract one calendar day, and require an event on that day that the app's
        // fasting predicate matches. Anything that fails here is a parana the user never sees,
        // dropped with a log line and no error.
        val byDate = legacyDays.associateBy { it.date }
        var recovered = 0

        legacyDays.forEach { day ->
            day.events.forEach { event ->
                val display = event.title.trimStart('+').trim()
                val match = LegacyFeed.APP_BREAK_FAST_REGEX.find(display) ?: return@forEach

                val start = match.groupValues[1]
                val end = match.groupValues[2]
                assertTrue(start < end || start.length != end.length, "$day: window $start-$end")
                listOf(start, end).forEach {
                    val (h, m) = it.split(":").map(String::toInt)
                    assertTrue(h in 0..23, "$display: hour $h")
                    assertTrue(m in 0..59, "$display: minute $m")
                }

                val fastDay = byDate[LocalDate.parse(day.date).minusDays(1).toString()]
                assertNotNull(fastDay, "${day.date} carries a parana with no preceding day in the file")
                val attachesTo = fastDay!!.events.any {
                    LegacyFeed.appWouldTreatAsFastingTitle(it.title.trimStart('+').trim())
                } || fastDay.events.any { it.title.startsWith("+") }
                assertTrue(
                    attachesTo,
                    "the parana on ${day.date} would be dropped: nothing on ${fastDay.date} " +
                        "matches the app's fasting predicate or carries the major-event prefix. " +
                        "Events there: ${fastDay.events.map { it.title }}",
                )
                recovered++
            }
        }

        // 2026 has 24 or 25 Ekadashis; a run that recovered a handful would pass every assertion
        // above and still be broken.
        assertTrue(recovered >= 20, "only $recovered paranas survive the app's pipeline")
        assertEquals(
            emptyList<LegacyParanaOmission>(),
            result.manifest.legacyParanaOmissions,
            "a parana was computed but could not be expressed in the legacy shape",
        )
    }

    @Test
    fun `each fast day carries an explicitly fasting title`() {
        // The predicate is on the title the user sees. Relying on the current catalog naming would
        // let a future rename delete a fasting time with nothing to show for it.
        //
        // Break-fast items are excluded before the predicate is applied, and that is not a
        // convenience: "Break fast 06:21 - 09:57" contains the substring "fast", so the app's own
        // predicate matches it. The app never sees that, because convertToVaishnavaEvents removes
        // break-fast items from the displayed list before processAllDays looks for something to
        // attach a window to. This test has to remove them at the same point or it is asking a
        // question the app never asks.
        val fastDays = legacyDays.filter { day ->
            day.events.any {
                val display = it.title.trimStart('+').trim()
                !LegacyFeed.APP_BREAK_FAST_REGEX.containsMatchIn(display) &&
                    LegacyFeed.appWouldTreatAsFastingTitle(display)
            }
        }
        assertTrue(fastDays.size >= 20, "only ${fastDays.size} days carry a fasting title")
        fastDays.forEach { day ->
            assertTrue(
                day.events.any { it.title.startsWith("+") },
                "${day.date} has a fasting title but no major-event prefix",
            )
        }
        // Every fast day is followed by exactly one break-fast item, and nothing else in the file
        // carries one. 24 Ekadashis in 2026 at this site; a run that emitted 19 would still have
        // satisfied every other assertion here.
        val paranaDays = legacyDays.filter { day ->
            day.events.any { LegacyFeed.APP_BREAK_FAST_REGEX.containsMatchIn(it.title.trimStart('+').trim()) }
        }
        assertEquals(fastDays.size, paranaDays.size, "fast days and parana days do not correspond")
        assertEquals(
            fastDays.map { LocalDate.parse(it.date).plusDays(1).toString() },
            paranaDays.map { it.date },
        )
    }

    @Test
    fun `the manifest warns that no UTC zone was published`() {
        // CalendarSyncRepository falls back to P0000 for any offset it cannot match, and returns
        // failure when that directory is absent — a user who keeps a stale cache and is told
        // nothing. Whether to always publish a UTC site is a product decision, not this module's.
        assertTrue(
            result.manifest.warnings.any { it.contains("P0000") },
            "no warning about the missing UTC fallback: ${result.manifest.warnings}",
        )
    }

    // ── determinism and writing ─────────────────────────────────────────────────────────────

    @Test
    fun `two runs of the same request produce the same bytes`() {
        // No timestamp, no host name, no run id. A manifest with a generatedAt would make this
        // impossible to assert, and would make every republication a diff.
        val again = FeedPublisher().run(
            SiteList.parse("coords new-york 40.7128 -74.0060 America/New_York New York"),
            YEAR,
        )
        assertEquals(
            result.files.getValue("v1/new-york/year-resolution.json"),
            again.files.getValue("v1/new-york/year-resolution.json"),
        )
        assertEquals(
            result.files.getValue("v1/new-york/ekadashi-year.json"),
            again.files.getValue("v1/new-york/ekadashi-year.json"),
        )
    }

    @Test
    fun `write puts every computed file on disk and nothing else`(@TempDir dir: Path) {
        FeedPublisher().write(result, dir)
        val onDisk = Files.walk(dir).use { stream ->
            stream.filter(Files::isRegularFile)
                .map { dir.relativize(it).toString().replace('\\', '/') }
                .sorted()
                .toList()
        }
        assertEquals(result.files.keys.sorted(), onDisk)
        onDisk.forEach {
            assertTrue(Files.size(dir.resolve(it)) > 0, "$it was written empty")
        }
    }
}
