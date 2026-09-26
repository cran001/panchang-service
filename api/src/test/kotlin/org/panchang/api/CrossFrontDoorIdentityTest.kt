package org.panchang.api

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.panchang.wire.WireJson

/** Publication-v2 intentionally changes the roots. Compare all approved calculation bytes after
 * removing only the new publication metadata and restoring the diagnostic schema version.
 * The explicit signed TEST ONLY fixture authorizes these calculations; production defaults are
 * covered separately by PublicationControlsApiTest. No timing or reference value is changed.
 */
class CrossFrontDoorIdentityTest {

    @Test
    fun `a year at the same coordinates is byte-identical through both front doors`() {
        val calc = Fixtures.calcMumbaiYear
        val api = Fixtures.apiMumbaiYearPretty
        assertEquals(200, api.status)

        for (root in ROOTS) {
            val fromCalc = calculationText(calc, root)
            val fromApi = calculationText(api.body, root)
            assertEquals(
                fromCalc,
                fromApi,
                "the $root document root differs between :calc --format json and :api. Two front " +
                    "doors onto one engine must not be able to disagree about a fasting time; " +
                    "make both go through :wire rather than relaxing this comparison",
            )
            assertArrayEquals(
                fromCalc.toByteArray(Charsets.UTF_8),
                fromApi.toByteArray(Charsets.UTF_8),
                "$root differs in its UTF-8 encoding even though the decoded strings match",
            )
        }

        // A guard on the guard: the regions compared must actually be the payload, not an empty
        // object that would make the assertion above vacuously true.
        val observances = WireJson.pretty
            .parseToJsonElement(jsonMemberText(api.body, "ekadashiYear").substringAfter(':'))
            .jsonObject["observances"]!!
        assertEquals(
            25,
            observances.let { it as kotlinx.serialization.json.JsonArray }.size,
            "Mumbai 2026 carries 24 fasts plus the December fast's January 1 Parana; " +
                "the byte comparison must include that carryover",
        )
    }

    @Test
    fun `a single day at the same coordinates is byte-identical through both front doors`() {
        val calc = Fixtures.calcMumbaiDay
        val api = Fixtures.apiMumbaiDayPretty
        assertEquals(200, api.status)

        for (root in ROOTS) {
            assertEquals(
                calculationText(calc, root),
                calculationText(api.body, root),
                "the $root root differs between the two doors on a day query",
            )
        }

        // The day scope is the case most likely to drift, because it is the one where each door
        // decides what to filter. Both must keep the Ekadashi whose *parana* falls on the
        // requested day even though its fast was the day before.
        val obs = api.json["ekadashiYear"]!!.jsonObject["observances"]!!
            .let { it as kotlinx.serialization.json.JsonArray }
            .map { it.jsonObject }
        assertEquals(1, obs.size)
        assertEquals("2026-01-14", obs.single()["date"]!!.jsonPrimitive.content)
        assertEquals(
            "2026-01-15",
            obs.single()["parana"]!!.jsonObject["date"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `a place-resolved year is byte-identical through both front doors`() {
        // The coordinate path and the gazetteer path reach the engine differently — one uses the
        // caller's point verbatim, the other a record's representative point — so identity on one
        // does not imply identity on the other.
        val calc = Fixtures.calcNadiaYear
        val api = Fixtures.apiNadiaYearPretty
        assertEquals(200, api.status)
        for (root in ROOTS) {
            assertEquals(calculationText(calc, root), calculationText(api.body, root))
        }
    }

    @Test
    fun `the compact form carries the same document as the file form`() {
        val pretty = Fixtures.apiMumbaiYearPretty.json
        val compact = Fixtures.apiMumbaiYearCompact.json
        assertEquals(200, Fixtures.apiMumbaiYearCompact.status)

        for (root in ROOTS) {
            assertEquals(
                pretty[root],
                compact[root],
                "?pretty=1 must change whitespace and nothing else; both configurations are " +
                    ":wire's own and this module defines no third",
            )
        }
        assertTrue(
            Fixtures.apiMumbaiYearCompact.body.length < Fixtures.apiMumbaiYearPretty.body.length,
            "the compact form should actually be compact",
        )
        assertTrue(
            !Fixtures.apiMumbaiYearCompact.body.contains('\n'),
            "the transport form must be a single line",
        )
    }

    @Test
    fun `both doors resolve the same request to the same site, and say so the same way`() {
        // Everything in the location block except the echo of the request itself: which point was
        // used, where it came from, which record labels it and how far away that record is. A user
        // who believes they were given their own district's sunrise checks it here, so the two
        // doors saying different things here would be as bad as differing about a time.
        for ((calcText, apiBody) in listOf(
            Fixtures.calcMumbaiYear to Fixtures.apiMumbaiYearPretty.body,
            Fixtures.calcNadiaYear to Fixtures.apiNadiaYearPretty.body,
        )) {
            val fromCalc = locationWithoutQuery(calcText)
            val fromApi = locationWithoutQuery(apiBody)
            assertEquals(fromCalc, fromApi, "the two doors describe the resolved site differently")
        }

        // `query` is the one field that must differ: it echoes what the caller actually sent, and
        // a CLI caller did not send a query string.
        assertEquals(
            "--lat 19.076 --lon 72.8777 --tz Asia/Kolkata",
            queryEcho(Fixtures.calcMumbaiYear),
        )
        assertEquals(Fixtures.MUMBAI_QUERY, queryEcho(Fixtures.apiMumbaiYearPretty.body))
        assertNotEquals(
            queryEcho(Fixtures.calcMumbaiYear),
            queryEcho(Fixtures.apiMumbaiYearPretty.body),
        )
    }

    @Test
    fun `publication metadata is explicit in both self describing roots`() {
        val api = Fixtures.apiMumbaiYearPretty.json
        assertEquals(
            listOf("service", "schemaVersion", "publication", "request", "location", "ekadashiYear", "yearResolution"),
            api.keys.toList(),
            "anything :api wants to add sits around the wire roots, never inside them",
        )
        assertEquals("panchang-api", api["service"]!!.jsonPrimitive.content)
        // Both roots are self-describing v1 payloads that can be lifted out of the envelope.
        for (root in ROOTS) {
            assertEquals(2, api[root]!!.jsonObject["schemaVersion"]!!.jsonPrimitive.content.toInt())
        }
    }

    private fun calculationText(document: String, key: String): String {
        val root = WireJson.pretty.parseToJsonElement(document).jsonObject.getValue(key).jsonObject
        val diagnostic = kotlinx.serialization.json.buildJsonObject {
            put("schemaVersion", kotlinx.serialization.json.JsonPrimitive(1))
            for (field in listOf("year", "site", "sampradaya", "observances", "events", "unresolved")) {
                root[field]?.let { put(field, it) }
            }
        }
        return WireJson.pretty.encodeToString(JsonObject.serializer(), diagnostic)
    }

    private fun locationWithoutQuery(document: String): JsonObject {
        val location = WireJson.pretty
            .parseToJsonElement(jsonMemberText(document, "location").substringAfter(':'))
            .jsonObject
        return JsonObject(location.filterKeys { it != "query" })
    }

    private fun queryEcho(document: String): String = WireJson.pretty
        .parseToJsonElement(jsonMemberText(document, "location").substringAfter(':'))
        .jsonObject["query"]!!.jsonPrimitive.content

    private companion object {
        /** The two `:wire` document roots. Everything else in either envelope is that door's own. */
        val ROOTS = listOf("ekadashiYear", "yearResolution")
    }
}
