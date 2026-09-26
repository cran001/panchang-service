package org.panchang.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.panchang.gazetteer.Gazetteer
import org.panchang.wire.MIDNIGHT_SUN_LIMIT_DEG

/**
 * The HTTP surface: what each route answers, and — the part that carries weight — which status
 * code it answers with.
 *
 * A status code is not decoration. It is the only part of a refusal that a client is guaranteed to
 * act on, and getting it wrong makes the difference between "you made a typing mistake" and "there
 * is no answer for where you are standing". Every code asserted here is asserted together with the
 * sentence that must travel with it, because a bare 422 is as useless as a bare 200.
 */
class RoutesTest {

    // ── Health and meta ─────────────────────────────────────────────────────────────────────

    @Test
    fun `health answers with a version it did not invent`() {
        val r = call("/v2/health")
        assertEquals(200, r.status)
        assertEquals("ok", r.json["status"]!!.jsonPrimitive.content)
        assertEquals("panchang-api", r.json["service"]!!.jsonPrimitive.content)
        // Generated from the Gradle project version at build time. If the resource is missing this
        // throws rather than reporting a made-up number.
        assertTrue(
            r.json["engineVersion"]!!.jsonPrimitive.content.isNotBlank(),
            "a version a service reports is only worth reading if it cannot drift",
        )
    }

    @Test
    fun `meta states what is known, how well, and whose data this is`() {
        val r = call("/v2/meta")
        assertEquals(200, r.status)

        // The registry, as :wire's own document root, so a client reads the same status and
        // provenance fields here that ride on every calendar.
        val list = r.json["sampradayas"]!!.jsonObject
        assertEquals(1, list["schemaVersion"]!!.jsonPrimitive.content.toInt())
        val entries = list["sampradayas"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("iskcon"), entries.map { it["id"]!!.jsonPrimitive.content })
        assertTrue(entries.isNotEmpty(), "the registry must not be empty at this front door")
        val iskcon = entries.single { it["id"]!!.jsonPrimitive.content == "iskcon" }
        assertTrue(iskcon["status"]!!.jsonPrimitive.content.isNotBlank())
        assertTrue(
            iskcon["provenanceNote"]!!.jsonPrimitive.content.isNotBlank(),
            "a user cannot weigh a date they were not told the provenance of",
        )

        assertEquals(1, r.json["wireSchemaVersion"]!!.jsonPrimitive.content.toInt())

        val gaz = r.json["gazetteer"]!!.jsonObject
        assertEquals(
            Gazetteer.default.places.size,
            gaz["records"]!!.jsonPrimitive.content.toInt(),
        )
        val byKind = gaz["byKind"]!!.jsonObject
        assertTrue(byKind["CITY"]!!.jsonPrimitive.content.toInt() > 20_000)
        assertTrue(byKind["DISTRICT"]!!.jsonPrimitive.content.toInt() > 500)
        assertEquals(
            Gazetteer.default.places.size,
            byKind.values.sumOf { it.jsonPrimitive.content.toInt() },
            "the kind counts must account for every record",
        )

        // CC BY 4.0 is attribution-only, which is exactly why this source was chosen; the credit
        // is the price of that choice and it is not optional.
        assertEquals(Gazetteer.ATTRIBUTION, gaz["attribution"]!!.jsonPrimitive.content)
        assertTrue(gaz["provenance"]!!.jsonArray.isNotEmpty())
        assertTrue(
            r.json["notes"]!!.jsonArray.any {
                it.jsonPrimitive.content.contains("GeoNames")
            },
        )
        assertTrue(
            r.json["notes"]!!.jsonArray.any {
                it.jsonPrimitive.content.contains("%.2f".format(MIDNIGHT_SUN_LIMIT_DEG))
            },
            "the boundary of the service must be discoverable before a 422 is met",
        )
    }

    // ── Places ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a place search lists every record carrying the name and chooses none`() {
        val r = call("/v2/places?q=Raigarh")
        assertEquals(200, r.status, "a search is not a resolution; finding two is a result")
        val ids = r.json["exactMatches"]!!.jsonArray
            .map { it.jsonObject["geonameId"]!!.jsonPrimitive.content.toInt() }
        assertTrue(7626540 in ids, "Maharashtra's Raigarh district must be listed")
        assertTrue(1259006 in ids, "Chhattisgarh's Raigarh district must be listed")
        assertTrue(
            r.json["note"]!!.jsonPrimitive.content.contains("fuzzy"),
            "the refusal to fuzzy-match is a property of the service and must be stated",
        )
        for (p in r.json["exactMatches"]!!.jsonArray) {
            assertEquals(
                Gazetteer.ATTRIBUTION,
                p.jsonObject["attribution"]!!.jsonPrimitive.content,
                "a client that copies one record out must copy the obligation with it",
            )
        }
    }

    @Test
    fun `a historical name returns no record and the hint that names the modern one`() {
        val r = call("/v2/places?q=Bombay")
        assertEquals(200, r.status)
        assertTrue((r.json["exactMatches"] as JsonArray).isEmpty())
        assertTrue(r.json["hint"]!!.jsonPrimitive.content.contains("renamed Mumbai"))
    }

    @Test
    fun `a place search without q is refused`() {
        val r = call("/v2/places")
        assertEquals(400, r.status)
        assertEquals("MISSING_PARAMETER", r.json["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `nearest names a record and how far away it is, as a label only`() {
        val r = call("/v2/places/nearest?lat=19.0760&lon=72.8777")
        assertEquals(200, r.status)
        val nearest = r.json["nearest"]!!.jsonObject
        assertEquals("Mumbai", nearest["place"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertTrue(nearest["distanceKm"]!!.jsonPrimitive.content.toDouble() < 20.0)
        assertTrue(
            nearest["note"]!!.jsonPrimitive.content.contains("Nothing is computed"),
            "a label must never be mistakable for the point something was computed at",
        )
        assertTrue(r.json["computable"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `nearest in the middle of an ocean says so rather than naming a distant record`() {
        // Point Nemo, the oceanic pole of inaccessibility. Nothing is within 150 km of it, and
        // naming a record 900 km away would be a fabrication.
        val r = call("/v2/places/nearest?lat=-48.8767&lon=-123.3933")
        assertEquals(200, r.status, "absence is a result, not an error")
        assertFalse(r.json.containsKey("nearest"), "a client cannot format what is not there")
        val absent = r.json["absent"]!!.jsonObject
        assertEquals("NO_RECORD_WITHIN_RADIUS", absent["reason"]!!.jsonPrimitive.content)
        assertTrue(absent["reasonText"]!!.jsonPrimitive.content.contains("fabrication"))
    }

    @Test
    fun `nearest above the polar limit still answers, and warns before a calendar is asked for`() {
        // Longyearbyen. The gazetteer knows places up there; the observance rules cannot be
        // evaluated there. Telling a caller that here saves them a 422 they cannot act on.
        val r = call("/v2/places/nearest?lat=78.2232&lon=15.6469")
        assertEquals(200, r.status)
        assertFalse(r.json["computable"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("ABOVE_POLAR_LIMIT", r.json["notComputableCode"]!!.jsonPrimitive.content)
        assertTrue(r.json["notComputableReason"]!!.jsonPrimitive.content.contains("no sunrise"))
    }

    @Test
    fun `nearest with a latitude off the planet is a malformed request`() {
        val r = call("/v2/places/nearest?lat=200&lon=0")
        assertEquals(400, r.status)
        assertEquals(
            "LATITUDE_OUT_OF_RANGE",
            r.json["error"]!!.jsonObject["code"]!!.jsonPrimitive.content,
        )
    }

    // ── The calendar routes: status codes ───────────────────────────────────────────────────

    @Test
    fun `a polar site is 422, not 400, and carries wire's own reason verbatim`() {
        // The distinction this test exists for. The request is well formed and Longyearbyen is a
        // real place with real devotees; there is simply no answer, because the rules are defined
        // against a sunrise that does not occur there every day. A 400 would tell the caller they
        // made a typing mistake.
        val r = call("/v2/calendar/iskcon/2026?lat=78.2232&lon=15.6469&tz=Arctic/Longyearbyen")
        assertEquals(422, r.status)
        val error = r.json["error"]!!.jsonObject
        assertEquals("ABOVE_POLAR_LIMIT", error["code"]!!.jsonPrimitive.content)
        val message = error["message"]!!.jsonPrimitive.content
        assertTrue(message.contains("midnight-sun boundary"), "wire's own sentence, unedited: $message")
        assertTrue(message.contains("no sunrise"), "the reason must explain itself")
        assertTrue(
            message.contains("declines rather than guessing"),
            "declining is the point; a guessed fasting date would look authoritative",
        )
        // The coordinates are echoed so the caller can see which point was judged.
        assertEquals(
            78.2232,
            r.json["requested"]!!.jsonObject["latitude"]!!.jsonPrimitive.content.toDouble(),
        )
        assertTrue(r.json["limitStatedBy"]!!.jsonPrimitive.content.contains("org.panchang.wire"))
    }

    @Test
    fun `a site below the midnight-sun boundary is answered`() {
        // :wire states the boundary as "above", not "at or above", and computes it rather than
        // choosing it. A boundary that moves depending on which door you knock at is worse than
        // either boundary, so this asks with a real sub-boundary site: Reykjavik, whose midsummer
        // night is under three hours long and whose Sun therefore sets every day of the year.
        val r = call("/v2/day/iskcon/2026-06-15?lat=64.1466&lon=-21.9426&tz=Atlantic/Reykjavik")
        assertEquals(200, r.status)
        assertEquals(
            64.1466,
            r.json["location"]!!.jsonObject["coordinatesUsed"]!!.jsonObject["latitude"]!!
                .jsonPrimitive.content.toDouble(),
        )
    }

    @Test
    fun `an unknown sampradaya is 404 naming what is known, never another tradition's dates`() {
        val r = call("/v2/calendar/pushtimarg/2026?${Fixtures.MUMBAI_QUERY}")
        assertEquals(404, r.status)
        val error = r.json["error"]!!.jsonObject
        assertEquals("UNKNOWN_SAMPRADAYA", error["code"]!!.jsonPrimitive.content)
        assertTrue(error["message"]!!.jsonPrimitive.content.contains("iskcon"))
        // Public discovery advertises launch scope, not the internal calculator registry.
        assertEquals(
            listOf("iskcon"),
            r.json["known"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertFalse(
            r.json.containsKey("ekadashiYear"),
            "an unknown tradition must not be answered with a calendar of any kind",
        )
    }

    @Test
    fun `a malformed year is 400`() {
        val r = call("/v2/calendar/iskcon/twentytwentysix?${Fixtures.MUMBAI_QUERY}")
        assertEquals(400, r.status)
        assertEquals("MALFORMED_YEAR", r.json["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a malformed date is 400`() {
        val r = call("/v2/day/iskcon/15-01-2026?${Fixtures.MUMBAI_QUERY}")
        assertEquals(400, r.status)
        assertEquals("MALFORMED_DATE", r.json["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        assertTrue(r.json["error"]!!.jsonObject["message"]!!.jsonPrimitive.content.contains("YYYY-MM-DD"))
    }

    // ── The calendar routes: location precedence ────────────────────────────────────────────

    @Test
    fun `an ambiguous place is 409 with every candidate named and none chosen`() {
        val r = call("/v2/calendar/iskcon/2026?place=Raigarh")
        assertEquals(409, r.status, "several answers exist; choosing between them is not our call")
        assertEquals(
            "PLACE_AMBIGUOUS",
            r.json["error"]!!.jsonObject["code"]!!.jsonPrimitive.content,
        )
        val ids = r.json["candidates"]!!.jsonArray
            .map { it.jsonObject["geonameId"]!!.jsonPrimitive.content.toInt() }
        assertTrue(7626540 in ids && 1259006 in ids)
        assertTrue(r.json["resolveWith"]!!.jsonPrimitive.content.contains("placeId"))
        // `:calc`'s own worked example travels unedited, because rewriting it for HTTP would be a
        // second explanation of one decision.
        assertTrue(
            r.json["error"]!!.jsonObject["message"]!!.jsonPrimitive.content
                .contains("two distinct Raigarh districts"),
        )
        assertFalse(r.json.containsKey("ekadashiYear"))
    }

    @Test
    fun `a placeId settles an ambiguous name`() {
        val r = call("/v2/day/iskcon/2026-01-15?placeId=1259006")
        assertEquals(200, r.status)
        val location = r.json["location"]!!.jsonObject
        assertEquals("GAZETTEER_PLACE_POINT", location["coordinateSource"]!!.jsonPrimitive.content)
        assertEquals(
            1259006,
            location["place"]!!.jsonObject["geonameId"]!!.jsonPrimitive.content.toInt(),
        )
        assertTrue(
            location["notes"]!!.jsonArray.any {
                it.jsonPrimitive.content.contains("not your position")
            },
            "borrowing a record's representative point must be stated, not implied",
        )
    }

    @Test
    fun `a name with no record is 404 and nothing is substituted for it`() {
        val r = call("/v2/calendar/iskcon/2026?place=Bombay")
        assertEquals(404, r.status)
        assertEquals(
            "PLACE_NOT_FOUND",
            r.json["error"]!!.jsonObject["code"]!!.jsonPrimitive.content,
        )
        assertTrue(r.json["hint"]!!.jsonPrimitive.content.contains("renamed Mumbai"))
        assertTrue(
            r.json["error"]!!.jsonObject["message"]!!.jsonPrimitive.content
                .contains("does not fuzzy-match"),
        )
        assertFalse(r.json.containsKey("ekadashiYear"))
    }

    @Test
    fun `an unknown placeId is 404`() {
        val r = call("/v2/calendar/iskcon/2026?placeId=1")
        assertEquals(404, r.status)
        assertEquals(
            "PLACE_ID_UNKNOWN",
            r.json["error"]!!.jsonObject["code"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `coordinates and a place together are refused rather than silently ranked`() {
        val r = call("/v2/calendar/iskcon/2026?place=Nadia&lat=23.0&lon=88.0&tz=Asia/Kolkata")
        assertEquals(400, r.status)
        assertEquals(
            "CONFLICTING_LOCATION",
            r.json["error"]!!.jsonObject["code"]!!.jsonPrimitive.content,
        )
        assertTrue(
            r.json["error"]!!.jsonObject["message"]!!.jsonPrimitive.content
                .contains("keep believing it had been used"),
        )
    }

    @Test
    fun `coordinates without a zone are refused rather than guessed`() {
        val r = call("/v2/calendar/iskcon/2026?lat=19.076&lon=72.8777")
        assertEquals(400, r.status)
        assertEquals(
            "INCOMPLETE_COORDINATES",
            r.json["error"]!!.jsonObject["code"]!!.jsonPrimitive.content,
        )
        assertTrue(
            r.json["error"]!!.jsonObject["message"]!!.jsonPrimitive.content.contains("will not infer"),
            "the refusal must say why guessing a zone is wrong, not merely that it is refused",
        )
    }

    @Test
    fun `an unknown time zone is a malformed request, not a polar one`() {
        val r = call("/v2/calendar/iskcon/2026?lat=19.076&lon=72.8777&tz=Asia/Bombay")
        assertEquals(400, r.status)
        assertEquals(
            "UNKNOWN_TIME_ZONE",
            r.json["error"]!!.jsonObject["code"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `no location at all is refused with instructions`() {
        val r = call("/v2/calendar/iskcon/2026")
        assertEquals(400, r.status)
        assertEquals(
            "MISSING_LOCATION",
            r.json["error"]!!.jsonObject["code"]!!.jsonPrimitive.content,
        )
        assertTrue(r.json["error"]!!.jsonObject["message"]!!.jsonPrimitive.content.contains("place="))
    }

    @Test
    fun `every refusal is a json document with the same two identifying fields`() {
        for (url in listOf(
            "/v2/calendar/iskcon/2026",
            "/v2/calendar/pushtimarg/2026?${Fixtures.MUMBAI_QUERY}",
            "/v2/places",
            "/v2/calendar/iskcon/2026?lat=78.2232&lon=15.6469&tz=Arctic/Longyearbyen",
        )) {
            val r = call(url)
            assertTrue(r.status >= 400, "$url should refuse")
            assertEquals("panchang-api", r.json["service"]!!.jsonPrimitive.content, url)
            assertEquals(1, r.json["schemaVersion"]!!.jsonPrimitive.content.toInt(), url)
            val error = r.json["error"]!!.jsonObject
            assertEquals(r.status, error["status"]!!.jsonPrimitive.content.toInt(), url)
            assertNotNull(error["code"], url)
            assertTrue(error["message"]!!.jsonPrimitive.content.isNotBlank(), url)
        }
    }
}
