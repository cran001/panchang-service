package org.panchang.api

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.panchang.wire.WireJson

/**
 * Pins the one piece of duplication in this module.
 *
 * `CalcJson.place` is private, so [placeJson] is a second copy of the same field list. Two copies
 * of a field list drift — someone adds `admin2` to one, or reorders two fields, and the two doors
 * start describing the same district differently while both look correct in isolation. There is no
 * compiler check for that, so there is a test.
 *
 * Both sides here are real output: `location.place` lifted out of an actual `:calc --format json`
 * document, and `exactMatches[0]` from an actual `GET /v2/places`. Nothing is constructed by hand.
 *
 * This one comparison is on the parsed objects rather than the raw text, and for a reason that is
 * not a compromise: the two renderings sit at different depths in their documents, so their
 * indentation cannot match by construction. Key *order* is still compared, because `JsonObject`
 * preserves insertion and parse order, and so are the value literals as they appear — which is
 * what catches `19.0760` against `19.076`.
 */
class PlaceRenderingTest {

    @Test
    fun `both doors describe a gazetteer record with the same fields in the same order`() {
        val fromCalc = calcPlace()
        val fromApi = apiPlace()

        assertEquals(
            fromCalc.keys.toList(),
            fromApi.keys.toList(),
            "the place field lists have drifted apart. :api's placeJson is a hand copy of the " +
                "private CalcJson.place; bring them back into line rather than editing this test",
        )
        for (key in fromCalc.keys) {
            assertEquals(
                fromCalc.getValue(key).toString(),
                fromApi.getValue(key).toString(),
                "the two doors render `$key` differently for the same record",
            )
        }
    }

    @Test
    fun `the fields a district is identified by are all present`() {
        // Named individually so that a future deletion is a failure rather than a silently shorter
        // list on both sides. A record without its admin1 or its country cannot be told apart from
        // a same-named record elsewhere, which is the exact confusion the 409 path exists for.
        val place = apiPlace()
        assertEquals(
            listOf(
                "geonameId", "name", "asciiName", "kind", "country", "admin1",
                "population", "latitude", "longitude", "timeZone", "attribution",
            ),
            place.keys.toList(),
        )
        assertEquals("Nadia", place["name"]!!.jsonPrimitive.content)
        assertEquals("IN", place["country"]!!.jsonPrimitive.content)
        assertEquals("DISTRICT", place["kind"]!!.jsonPrimitive.content)
        assertEquals("Asia/Kolkata", place["timeZone"]!!.jsonPrimitive.content)
        assertTrue(place["attribution"]!!.jsonPrimitive.content.contains("CC BY 4.0"))
    }

    private fun calcPlace(): JsonObject = WireJson.pretty
        .parseToJsonElement(jsonMemberText(Fixtures.calcNadiaYear, "location").substringAfter(':'))
        .jsonObject["place"]!!
        .jsonObject

    private fun apiPlace(): JsonObject {
        val r = call("/v2/places?q=Nadia")
        check(r.status == 200) { "expected 200, got ${r.status}: ${r.body}" }
        val matches = r.json["exactMatches"]!!.jsonArray
        check(matches.size == 1) { "expected exactly one Nadia record, found ${matches.size}" }
        return matches.single().jsonObject
    }
}
