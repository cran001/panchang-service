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

/**
 * The reason `:wire` exists, demonstrated instead of assumed.
 *
 * `:calc` and `:api` are two front doors onto one engine. If they disagree about a fasting time,
 * two users comparing notes see two different answers from what is nominally the same service and
 * neither has any way to tell which is wrong. `:wire` was created so that cannot happen — but
 * until this file existed, that was a structural argument and not an observation. T6 said as much.
 *
 * ## What is compared
 *
 * The **raw characters** of the two document roots, `ekadashiYear` and `yearResolution`, as each
 * door actually emits them. Not parsed trees: a tree comparison would pass while one door emitted
 * its keys in a different order, or `19.076` where the other wrote `19.0760`, or a differently
 * escaped devotee's name — all of which are real ways for two payloads to disagree in front of a
 * user, and none of which a `JsonObject` equality check can see.
 *
 * Both doors are driven for real. One side is [org.panchang.calc.CalcCommand] parsed from argument
 * strings, writing to its own stdout; the other is an HTTP GET served in-process by Ktor. The only
 * thing shared between them is the engine underneath, which is the point.
 *
 * ## Why the request carries `pretty=1`
 *
 * `:wire` defines two `Json` configurations and says which is for what: `compact` for transport,
 * `pretty` for files. `:calc --format json` writes a file. Asking `:api` for the file form is what
 * makes this a byte comparison rather than a comparison of two shapes that would be the same if
 * you reformatted them — and `the compact form carries the same document` below closes the loop by
 * showing the default transport form is the same document, differently spaced.
 *
 * The two envelopes are permitted to differ, and only in their outermost keys: `:calc` writes
 * `"tool"`, `:api` writes `"service"` and `"schemaVersion"`. Both sit at the same depth as the
 * roots, so even the indentation inside the compared regions is identical.
 *
 * **If a future change makes these fail, the fix is to route both doors through `:wire`. It is
 * never to loosen what is compared here.**
 */
class CrossFrontDoorIdentityTest {

    @Test
    fun `a year at the same coordinates is byte-identical through both front doors`() {
        val calc = Fixtures.calcMumbaiYear
        val api = Fixtures.apiMumbaiYearPretty
        assertEquals(200, api.status)

        for (root in ROOTS) {
            val fromCalc = jsonMemberText(calc, root)
            val fromApi = jsonMemberText(api.body, root)
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
                jsonMemberText(calc, root),
                jsonMemberText(api.body, root),
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
            assertEquals(jsonMemberText(calc, root), jsonMemberText(api.body, root))
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
    fun `the api envelope adds nothing inside the wire roots`() {
        val api = Fixtures.apiMumbaiYearPretty.json
        assertEquals(
            listOf("service", "schemaVersion", "request", "location", "ekadashiYear", "yearResolution"),
            api.keys.toList(),
            "anything :api wants to add sits around the wire roots, never inside them",
        )
        assertEquals("panchang-api", api["service"]!!.jsonPrimitive.content)
        // Both roots are self-describing v1 payloads that can be lifted out of the envelope.
        for (root in ROOTS) {
            assertEquals(1, api[root]!!.jsonObject["schemaVersion"]!!.jsonPrimitive.content.toInt())
        }
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
