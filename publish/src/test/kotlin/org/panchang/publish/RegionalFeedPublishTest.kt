package org.panchang.publish

import java.time.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.panchang.wire.WireJson

/**
 * A regional tradition through the whole publish pipeline — the content-hub compatibility
 * contract for the sampradaya expansion.
 *
 * The hub consumes two shapes from this module: `v1/` (`:wire`'s document roots) and `legacy/`
 * (the shipped Android app's feed). Both were built around ISKCON, whose payload carries
 * Ekadashi fasts and parana windows; a tradition with **no** ekadashi timing must flow through
 * the same pipeline and come out well-formed — festivals present, fasting titles absent,
 * nothing borrowed from the one tradition that has them.
 */
class RegionalFeedPublishTest {

    private companion object {

        const val YEAR = 2026

        val SITE_LIST = """
            coords    mumbai  19.0760  72.8777  Asia/Kolkata  Mumbai
        """.trimIndent()

        val result: PublishResult by lazy {
            ReviewFeedPublisher().run(SiteList.parse(SITE_LIST), YEAR, sampradayaId = "marathi")
        }
    }

    @Test
    fun `the run publishes one site and refuses nothing`() {
        assertEquals(1, result.manifest.requested)
        assertEquals(1, result.manifest.published)
        assertEquals(0, result.skipped.sites.size, "Mumbai is an ordinary, computable site")
    }

    @Test
    fun `the v1 year resolution keeps its unresolved record and the new year`() {
        val yearResolution = result.files.getValue("v1/mumbai/year-resolution.json")
        assertTrue(
            yearResolution.contains("\"unresolved\""),
            "the hub's audit path is the unresolved map; publishing without it would hide " +
                "whatever the catalog could not date",
        )
        assertTrue(
            yearResolution.contains("gudi_padwa"),
            "the Marathi new year must be in the published events",
        )
        assertTrue(
            yearResolution.contains("\"status\": \"UNVERIFIED\""),
            "an unverified tradition must reach the hub saying so",
        )
    }

    @Test
    fun `the v1 ekadashi year is schema-shaped and honestly empty`() {
        val ekadashiYear = WireJson.pretty.parseToJsonElement(
            result.files.getValue("v1/mumbai/ekadashi-year.json"),
        ).jsonObject
        assertEquals(
            1,
            ekadashiYear["schemaVersion"]!!.jsonPrimitive.content.toInt(),
            "the document root must stay at the schema version the hub parses",
        )
        assertEquals(
            "marathi",
            ekadashiYear["sampradaya"]!!.jsonObject["id"]!!.jsonPrimitive.content,
        )
        assertEquals(
            0,
            ekadashiYear["observances"]!!.jsonArray.size,
            "no tradition but ISKCON computes Ekadashi timing; the hub must see absence, " +
                "not borrowed Gaudiya windows",
        )
    }

    @Test
    fun `the legacy day files carry festivals and no fasting or parana items`() {
        val days = WireJson.pretty.decodeFromString(
            ListSerializer(LegacyDay.serializer()),
            result.files.getValue("legacy/P0530/mumbai.json"),
        )

        val titles = days.flatMap { day -> day.events.map { it.title } }
        assertTrue(
            titles.isNotEmpty(),
            "the Marathi catalog must surface *some* festival titles in the legacy feed",
        )
        assertTrue(
            titles.none { it.contains("Ekadashi", ignoreCase = true) },
            "a tradition with no ekadashi timing must not carry ekadashi titles the app " +
                "would treat as fasting days",
        )
        assertTrue(
            titles.none { it.contains("Break fast", ignoreCase = true) },
            "parana items belong to ISKCON's feed only; the app files them by fast date and " +
                "would mis-anchor any borrowed ones",
        )

        // The app's day-file contract: every date in the year, in order, each with a tithi
        // string it renders verbatim.
        assertEquals(365, days.size, "2026 is not a leap year")
        days.zipWithNext().forEach { (earlier, later) ->
            assertEquals(
                1L,
                LocalDate.parse(later.date).toEpochDay() - LocalDate.parse(earlier.date).toEpochDay(),
                "the day files must be consecutive; the app walks them with a fixed stride",
            )
        }
        assertTrue(days.all { it.tithi.isNotBlank() }, "a blank tithi renders as nothing")
    }

    @Test
    fun `the legacy index files are unchanged in shape`() {
        val zones = WireJson.pretty.parseToJsonElement(result.files.getValue("legacy/zones.json"))
        val zoneDirs = zones.jsonArray.map { it.jsonObject["dir"]!!.jsonPrimitive.content }
        assertTrue(zoneDirs.isNotEmpty(), "the app cannot download anything without zones.json")
        // Review rendering keeps only the requested point; no synthetic UTC substitute.
        assertTrue("P0530" in zoneDirs, "Mumbai publishes into the +05:30 directory: $zoneDirs")
        assertTrue("P0000" !in zoneDirs, "an unrequested UTC fallback must never appear: $zoneDirs")
        val zone = zones.jsonArray.first().jsonObject
        assertTrue(zone.containsKey("title"))
        assertTrue(zone.containsKey("dir"))

        val locations = WireJson.pretty.parseToJsonElement(
            result.files.getValue("legacy/P0530/locations.json"),
        ).jsonArray
        val mumbai = locations.single().jsonObject
        assertEquals("Mumbai", mumbai["title"]!!.jsonPrimitive.content)
        assertTrue(mumbai.containsKey("coordinates"))
        assertTrue(mumbai.containsKey("timezone"))
        assertTrue(mumbai.containsKey("file"))
    }
}
