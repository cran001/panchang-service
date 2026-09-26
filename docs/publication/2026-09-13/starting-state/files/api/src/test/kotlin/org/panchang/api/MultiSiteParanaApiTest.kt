package org.panchang.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Two cities, one time zone, different answers — served over HTTP.
 *
 * Mumbai and Delhi are both `Asia/Kolkata`, so a service that resolved location to a time zone and
 * stopped there would hand both cities the same parana window. That would be wrong by more than
 * half an hour in June, which is more than enough to break a fast early. This file exists to make
 * that failure mode impossible to reintroduce without a red test, *through the HTTP front door* —
 * `:calc` proving it in-process does not prove that the route passes the caller's coordinates
 * through rather than, say, snapping them to a zone centroid.
 *
 * ## Where these numbers come from
 *
 * **Measured in this session, on this code, through this API.** A scratch test computed the whole
 * 2026 Ekadashi year for both cities and printed the Mumbai-minus-Delhi difference in parana start
 * for every observance; the four rows below were taken from that output and the scratch test was
 * deleted. They are not copied from a reference implementation, not derived from a formula, and not
 * rounded to look tidy. If the engine changes and these move, the correct response is to re-measure
 * and to explain the movement — never to widen the tolerance until it passes.
 *
 * The sign convention is **Mumbai minus Delhi**. Positive means Mumbai's fast may be broken later.
 * Delhi is 4.33 degrees east of Mumbai, worth about 17 minutes of earlier sunrise on its own, and
 * 9.5 degrees further north, which in June adds to that and in December works against it. The
 * December figure being small and negative is that cancellation, not a bug.
 *
 * ## The zero rows are the control
 *
 * A parana window's start is whichever bound binds: usually sunrise, which is local, but sometimes
 * the end of Hari Vasara, which is a *tithi* instant and therefore the same moment everywhere on
 * Earth. On those dates the two cities must agree to the last digit the wire carries. Those rows
 * are kept deliberately. Without them a test that reported differences everywhere would look
 * healthy while actually measuring nothing but noise; with them, the suite has to be right about
 * *when* geography matters and not merely that it sometimes does.
 */
class MultiSiteParanaApiTest {

    @Test
    fun `mid-June is the widest separation, and it is over half an hour`() {
        assertRow("2026-06-11", expectedMinutes = 37.6272)
        assertTrue(
            differenceMinutes("2026-06-11") > 30.0,
            "a difference this size is the whole reason coordinates are carried through the API " +
                "instead of a time zone",
        )
    }

    @Test
    fun `at the equinox the separation is about a quarter of an hour`() {
        assertRow("2026-03-15", expectedMinutes = 16.1424)
    }

    @Test
    fun `in December the separation is small and reversed`() {
        // Latitude and longitude nearly cancel here. The sign matters: Delhi breaks its fast very
        // slightly later than Mumbai, the opposite of June.
        assertRow("2026-12-20", expectedMinutes = -2.5344)
    }

    @Test
    fun `a parana bounded by the end of Hari Vasara is identical in both cities`() {
        val date = "2026-04-13"
        assertEquals(
            "HARI_VASARA_END",
            mumbai.getValue(date).startReason,
            "this row is the control; if its bound is no longer the tithi instant, pick a date " +
                "whose bound is, do not delete the control",
        )
        assertEquals("HARI_VASARA_END", delhi.getValue(date).startReason)
        assertEquals(
            0.0,
            differenceMinutes(date),
            0.0,
            "a tithi instant is a position of the moon relative to the sun; it happens at one " +
                "moment for the whole planet, so two cities must not be given different ones",
        )
        // Same instant, same zone, therefore the same wall clock too.
        assertEquals(mumbai.getValue(date).local, delhi.getValue(date).local)
    }

    @Test
    fun `every tithi-bounded parana agrees and every sunrise-bounded one does not`() {
        // The property behind the four rows above, asserted over the whole year rather than at
        // four hand-picked dates, so that a change which happened to preserve those four but broke
        // everything else still fails.
        // 24 observances a year, of which a small number carry no computed parana window at one
        // site or the other; `paranaStarts` already fails loudly if the observance count itself is
        // not 24, so this only guards against the window map quietly emptying out.
        val shared = mumbai.keys.intersect(delhi.keys)
        assertTrue(shared.size >= 22, "expected a full year of parana windows, found ${shared.size}")

        var tithiBounded = 0
        var sunriseBounded = 0
        for (date in shared) {
            val diff = differenceMinutes(date)
            if (mumbai.getValue(date).startReason == "HARI_VASARA_END" &&
                delhi.getValue(date).startReason == "HARI_VASARA_END"
            ) {
                tithiBounded++
                assertEquals(0.0, diff, 0.0, "global bound differed between cities on $date")
            } else if (mumbai.getValue(date).startReason == "SUNRISE" &&
                delhi.getValue(date).startReason == "SUNRISE"
            ) {
                sunriseBounded++
                assertTrue(
                    diff != 0.0,
                    "sunrise at 19.08N and at 28.61N cannot be the same instant, yet $date " +
                        "reports no difference — the API is probably not passing the caller's " +
                        "coordinates to the engine",
                )
            }
        }
        assertTrue(tithiBounded >= 1, "no tithi-bounded control rows were found in 2026")
        assertTrue(sunriseBounded >= 10, "only $sunriseBounded sunrise-bounded rows were compared")
    }

    @Test
    fun `each response reports the coordinates it was actually given`() {
        // The other half of the claim. A difference in the numbers is only meaningful if each
        // answer says which point produced it.
        for ((response, lat, lon) in listOf(
            Triple(Fixtures.apiMumbaiYearCompact, 19.0760, 72.8777),
            Triple(Fixtures.apiDelhiYearCompact, 28.6139, 77.2090),
        )) {
            assertEquals(200, response.status)
            val location = response.json["location"]!!.jsonObject
            assertEquals(
                "CALLER_COORDINATES",
                location["coordinateSource"]!!.jsonPrimitive.content,
                "a caller who sent a point must be told their point was used, not a district's",
            )
            val used = location["coordinatesUsed"]!!.jsonObject
            assertEquals(lat, used["latitude"]!!.jsonPrimitive.content.toDouble())
            assertEquals(lon, used["longitude"]!!.jsonPrimitive.content.toDouble())
            assertEquals("Asia/Kolkata", used["timeZone"]!!.jsonPrimitive.content)
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────

    /**
     * Mumbai minus Delhi, in minutes, at the start of the parana window belonging to the Ekadashi
     * *observed* on [date].
     *
     * Keyed by the observance date and not the parana date, deliberately. The parana falls on the
     * following morning, and which morning that is can itself differ between two cities — so
     * keying by parana date would silently line up two different observances and compare them.
     */
    private fun differenceMinutes(date: String): Double {
        val m = mumbai[date] ?: error("no parana window for the $date fast at Mumbai; rows: ${mumbai.keys}")
        val d = delhi[date] ?: error("no parana window for the $date fast at Delhi; rows: ${delhi.keys}")
        return (m.jdUt - d.jdUt) * MINUTES_PER_DAY
    }

    private fun assertRow(date: String, expectedMinutes: Double) {
        assertEquals(
            expectedMinutes,
            differenceMinutes(date),
            TOLERANCE_MINUTES,
            "the Mumbai-minus-Delhi parana start on $date moved away from the value measured " +
                "against this engine. Re-measure and explain the movement; do not widen this band",
        )
    }

    private data class ParanaStart(val jdUt: Double, val local: String, val startReason: String)

    private val mumbai: Map<String, ParanaStart> by lazy {
        paranaStarts(Fixtures.apiMumbaiYearCompact)
    }
    private val delhi: Map<String, ParanaStart> by lazy {
        paranaStarts(Fixtures.apiDelhiYearCompact)
    }

    private fun paranaStarts(response: ApiResponse): Map<String, ParanaStart> {
        check(response.status == 200) { "expected 200, got ${response.status}: ${response.body}" }
        val observances: JsonArray = response.json["ekadashiYear"]!!.jsonObject["observances"]!!
            .jsonArray
        check(observances.count { it.jsonObject["date"]!!.jsonPrimitive.content.startsWith("2026-") } == 24) {
            "these sites have 24 fasts in 2026; yearly delivery may also include December carryover"
        }
        return observances.mapNotNull { element ->
            val parana = element.jsonObject["parana"]?.jsonObject ?: return@mapNotNull null
            val start = parana["start"]!!.jsonObject
            element.jsonObject["date"]!!.jsonPrimitive.content to ParanaStart(
                jdUt = start["jdUt"]!!.jsonPrimitive.content.toDouble(),
                local = start["local"]!!.jsonPrimitive.content,
                startReason = parana["startReason"]!!.jsonPrimitive.content,
            )
        }.toMap()
    }

    private companion object {
        const val MINUTES_PER_DAY = 1440.0

        /**
         * `jdUt` is carried to five decimals, so a difference of two such values is a multiple of
         * 0.0144 min and can be off by at most 0.0144 min from the underlying quantity. This band
         * is one such step, which is a rounding allowance and not a fudge factor — it is roughly
         * one second, and no fast is broken to the second.
         */
        const val TOLERANCE_MINUTES = 0.0144
    }
}
