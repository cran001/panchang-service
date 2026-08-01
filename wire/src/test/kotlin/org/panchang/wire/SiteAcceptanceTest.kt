package org.panchang.wire

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.panchang.core.GeoLocation

/**
 * Where the world ends, agreed once so three front doors cannot disagree about it.
 *
 * Above [POLAR_LIMIT_DEG] the observance rules have no sunrise to test a tithi against and the
 * tradition has no ruling this project has a source for. Declining is the honest answer; a
 * fabricated one would look authoritative.
 */
class SiteAcceptanceTest {

    @Test
    fun `the limit is stated once and is 66 degrees`() {
        assertEquals(66.0, POLAR_LIMIT_DEG)
    }

    @Test
    fun `an ordinary site is accepted`() {
        val result = acceptSite(Fixtures.mayapur)

        assertTrue(result is SiteAcceptance.Accepted, "Mayapur must be computable: $result")
        assertEquals("Asia/Kolkata", (result as SiteAcceptance.Accepted).site.timeZone)
        assertTrue(result.isAccepted)
    }

    @Test
    fun `a site just below the limit is accepted, north and south`() {
        for (latitude in listOf(65.9, -65.9, 66.0, -66.0)) {
            val result = acceptSite(latitude, 25.0, "Europe/Helsinki")
            assertTrue(result.isAccepted, "latitude $latitude is not above the limit: $result")
        }
    }

    @Test
    fun `a site above the limit is rejected with a reason a user can be told`() {
        for (latitude in listOf(66.1, 71.0, -66.1, -78.0)) {
            val result = acceptSite(latitude, 25.0, "Europe/Helsinki")

            assertTrue(result is SiteAcceptance.Rejected, "latitude $latitude must be refused")
            val rejected = result as SiteAcceptance.Rejected
            assertEquals(SiteRejectionCode.ABOVE_POLAR_LIMIT, rejected.code)
            assertTrue(rejected.reason.contains("sunrise"), rejected.reason)
            assertFalse(rejected.isAccepted)
        }
    }

    @Test
    fun `an already-built GeoLocation above the limit is rejected too`() {
        val tromso = GeoLocation.of(69.6496, 18.9560, "Europe/Oslo")
        val result = acceptSite(tromso)

        assertTrue(result is SiteAcceptance.Rejected)
        assertEquals(SiteRejectionCode.ABOVE_POLAR_LIMIT, (result as SiteAcceptance.Rejected).code)
    }

    @Test
    fun `raw input failures each get their own code rather than one blanket refusal`() {
        val cases = listOf(
            Triple(Double.NaN, 0.0, "UTC") to SiteRejectionCode.NON_FINITE_COORDINATE,
            Triple(0.0, Double.POSITIVE_INFINITY, "UTC") to SiteRejectionCode.NON_FINITE_COORDINATE,
            Triple(95.0, 0.0, "UTC") to SiteRejectionCode.LATITUDE_OUT_OF_RANGE,
            Triple(0.0, 200.0, "UTC") to SiteRejectionCode.LONGITUDE_OUT_OF_RANGE,
            Triple(0.0, 0.0, "Middle/Earth") to SiteRejectionCode.UNKNOWN_TIME_ZONE,
            Triple(0.0, 0.0, "not a zone at all") to SiteRejectionCode.UNKNOWN_TIME_ZONE,
        )
        for ((input, expected) in cases) {
            val (lat, lon, zone) = input
            val result = acceptSite(lat, lon, zone)
            assertTrue(result is SiteAcceptance.Rejected, "$input should be refused")
            assertEquals(expected, (result as SiteAcceptance.Rejected).code, "for input $input")
            assertTrue(result.reason.isNotBlank(), "a refusal with nothing to say is useless")
        }
    }

    @Test
    fun `an out-of-range elevation is refused by code rather than by exception`() {
        val result = acceptSite(24.4, 88.4, "Asia/Kolkata", elevationMeters = 40_000.0)

        assertEquals(
            SiteRejectionCode.ELEVATION_OUT_OF_RANGE,
            (result as SiteAcceptance.Rejected).code,
        )
    }

    @Test
    fun `the polar limit is checked before the zone, so the real reason is the one reported`() {
        // A caller who types a valid arctic site with a typo in the zone should still be told the
        // thing they cannot fix, not the thing they can.
        val result = acceptSite(78.0, 15.0, "Europe/Nowhere")
        assertEquals(SiteRejectionCode.ABOVE_POLAR_LIMIT, (result as SiteAcceptance.Rejected).code)
    }

    @Test
    fun `a refusal is serialisable, so the API can return it rather than reword it`() {
        val result = acceptSite(80.0, 15.0, "Europe/Oslo")
        val text = WireJson.compact.encodeToString(SiteAcceptance.serializer(), result)

        assertTrue(text.contains("ABOVE_POLAR_LIMIT"), text)
        assertEquals(result, WireJson.compact.decodeFromString(SiteAcceptance.serializer(), text))
    }
}
