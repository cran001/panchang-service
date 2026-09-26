package org.panchang.api

import java.nio.file.Path
import java.time.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.panchang.publish.FeedPublisher
import org.panchang.publish.LegacyDay
import org.panchang.publish.LegacyFeed
import org.panchang.publish.SiteList
import org.panchang.wire.WireJson

class DeliveryFrontDoorTest {
    @TempDir lateinit var output: Path

    @Test
    fun `January carryover agrees through HTTP day year and written v1 and legacy files`() {
        val query = "lat=23.416666666666668&lon=88.38333333333334&tz=Asia/Kolkata"
        val yearly = getYear(query)
        val observations = yearly["observances"]!!.jsonArray
        val carry = observations.single { it.jsonObject["date"]!!.jsonPrimitive.content == "2025-12-31" }
        assertEquals("2026-01-01", carry.jsonObject["parana"]!!.jsonObject["date"]!!.jsonPrimitive.content)
        assertEquals(listOf(carry), getDay("2026-01-01", query))
        assertEquals(emptyList<JsonElement>(), getDay("2026-01-02", query))
        val previousDay = getDay("2025-12-31", query).single().jsonObject
        assertEquals(carry.jsonObject["date"], previousDay["date"])
        assertEquals(carry.jsonObject["parana"], previousDay["parana"])
        assertEquals(observations.size, observations.map { it.jsonObject["date"] }.distinct().size)
        assertTrue(observations.all {
            val o = it.jsonObject
            o["date"]!!.jsonPrimitive.content.startsWith("2026-") ||
                o["parana"]?.jsonObject?.get("date")?.jsonPrimitive?.content == "2026-01-01"
        })
        val legacy = publishAndCompare("mayapur", "23.416666666666668 88.38333333333334 Asia/Kolkata", "P0530", yearly)
        val jan1 = legacy.single { it.date == "2026-01-01" }
        assertEquals(1, jan1.events.count { LegacyFeed.APP_BREAK_FAST_REGEX.containsMatchIn(it.title) })
        assertTrue(jan1.events.none { LegacyFeed.appWouldTreatAsFastingTitle(it.title) && it.title.startsWith("+") })
    }

    @Test
    fun `Reykjavik daylight cap and metadata agree through HTTP and written files`() {
        val query = "lat=64.1466&lon=-21.9426&tz=Atlantic/Reykjavik"
        val yearly = getYear(query)
        val day = getDay("2026-06-26", query).single()
        assertEquals(yearly["observances"]!!.jsonArray.single {
            it.jsonObject["date"]!!.jsonPrimitive.content == "2026-06-25"
        }, day)
        val parana = day.jsonObject["parana"]!!.jsonObject
        assertEquals("ONE_THIRD_DAYLIGHT", parana["endReason"]!!.jsonPrimitive.content)
        val legacy = publishAndCompare("reykjavik", "64.1466 -21.9426 Atlantic/Reykjavik", "P0000", yearly)
        val title = legacy.single { it.date == "2026-06-26" }.events.single {
            LegacyFeed.APP_BREAK_FAST_REGEX.containsMatchIn(it.title)
        }.title
        // The saved USNO interval's third is 09:59:20 UTC (+/-30s). Legacy rounds the end down.
        assertTrue(title.endsWith(" - 09:59"), title)
    }

    private fun getYear(query: String): JsonObject {
        val response = call("/v1/calendar/iskcon/2026?$query")
        assertEquals(200, response.status, response.body)
        return response.json["ekadashiYear"]!!.jsonObject
    }

    private fun getDay(date: String, query: String): List<JsonElement> {
        val response = call("/v1/day/iskcon/$date?$query")
        assertEquals(200, response.status, response.body)
        return response.json["ekadashiYear"]!!.jsonObject["observances"]!!.jsonArray
    }

    private fun publishAndCompare(key: String, coordinates: String, zone: String, expected: JsonObject): List<LegacyDay> {
        val publisher = FeedPublisher()
        val result = publisher.run(SiteList.parse("coords $key $coordinates $key"), 2026)
        assertEquals(1, result.manifest.published)
        publisher.write(result, output)
        val v1 = output.resolve("v1/$key/ekadashi-year.json").toFile().readText()
        assertEquals(expected, WireJson.pretty.parseToJsonElement(v1))
        val legacy = WireJson.pretty.decodeFromString(ListSerializer(LegacyDay.serializer()),
            output.resolve("legacy/$zone/$key.json").toFile().readText())
        assertEquals(365, legacy.count { it.date.startsWith("2026-") })
        assertEquals(legacy.size, legacy.map { it.date }.distinct().size)
        val carries = expected["observances"]!!.jsonArray.map { it.jsonObject }
            .filter { it["date"]!!.jsonPrimitive.content.startsWith("2025-") }
        val contextDates = carries.map { it["date"]!!.jsonPrimitive.content }
        assertEquals(contextDates, legacy.filterNot { it.date.startsWith("2026-") }.map { it.date })
        for (carry in carries) {
            val fastDay = legacy.single { it.date == carry["date"]!!.jsonPrimitive.content }
            assertTrue(fastDay.events.any { it.title.startsWith("+") && LegacyFeed.appWouldTreatAsFastingTitle(it.title) })
        }
        return legacy
    }
}
