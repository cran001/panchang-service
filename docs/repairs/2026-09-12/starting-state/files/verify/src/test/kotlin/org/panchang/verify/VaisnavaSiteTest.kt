package org.panchang.verify

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.panchang.verify.grid.ReferenceCities
import org.panchang.verify.vaisnava.VaisnavaCalendarHeader
import org.panchang.verify.vaisnava.VaisnavaSiteZones

/**
 * The site block is what makes a golden calendar checkable against anything: without it
 * the file is a list of clock times with no place attached.
 */
class VaisnavaSiteTest {

    private fun header(city: String, coordinates: String, offset: String) =
        VaisnavaCalendarHeader(city, coordinates, offset, "GCal 11, Build 5")

    /** Every city header printed in the 2026 grid files, verbatim. */
    private val printedHeaders = listOf(
        Triple("mayapur", "Mayapur [India]", "23N25 88E23" to "+5:30"),
        Triple("vrindavan", "Vrindavan [India]", "27N35 77E42" to "+5:30"),
        Triple("delhi", "Delhi [India]", "28N40 77E13" to "+5:30"),
        Triple("mumbai", "Bombay [India]", "18N59 72E50" to "+5:30"),
        Triple("london", "London [United Kingdom]", "51N31 0W08" to "+0:00"),
        Triple("new-york", "New York City [United States of America]", "40N43 74W00" to "-5:00"),
        Triple("auckland", "Auckland [New Zealand]", "36S52 174E46" to "+12:00"),
        Triple("moscow", "Moskva [Russia]", "55N45 37E37" to "+3:00"),
        Triple("sao-paulo", "Sao Paulo [Brazil]", "23S33 46W38" to "-3:00"),
        Triple("sydney", "Sydney [Australia]", "33S52 151E12" to "+10:00"),
    )

    /**
     * The hand-entered zone is the one field not readable from the artifact, so the only
     * available cross-check is the printed standard offset. This asserts that check runs
     * and passes for all ten published cities.
     */
    @Test
    fun `every published city has a zone whose standard offset matches the printed one`() {
        for ((cityId, city, coords) in printedHeaders) {
            val site = VaisnavaSiteZones.siteFor(cityId, header(city, coords.first, coords.second), 2026)
            assertEquals(city, site.city)
            assertEquals(coords.first, site.coordinates)
            assertEquals(coords.second, site.utcOffset)
            assertTrue(site.ianaZone.contains("/"), "not an IANA zone id: ${site.ianaZone}")
        }
    }

    @Test
    fun `assigns the expected zones`() {
        fun zoneOf(city: String) = VaisnavaSiteZones.zoneFor(city)
        assertEquals("Asia/Kolkata", zoneOf("Mayapur [India]"))
        assertEquals("Europe/London", zoneOf("London [United Kingdom]"))
        assertEquals("America/New_York", zoneOf("New York City [United States of America]"))
        assertEquals("Pacific/Auckland", zoneOf("Auckland [New Zealand]"))
        assertEquals("Europe/Moscow", zoneOf("Moskva [Russia]"))
        assertEquals("America/Sao_Paulo", zoneOf("Sao Paulo [Brazil]"))
        assertEquals("Australia/Sydney", zoneOf("Sydney [Australia]"))
    }

    @Test
    fun `reads degrees from the printed coordinates, south and west negative`() {
        val sydney = VaisnavaSiteZones.siteFor("sydney", header("Sydney [Australia]", "33S52 151E12", "+10:00"), 2026)
        assertEquals(-(33 + 52 / 60.0), sydney.latitudeDeg, 1e-12)
        assertEquals(151 + 12 / 60.0, sydney.longitudeDeg, 1e-12)

        val london = VaisnavaSiteZones.siteFor("london", header("London [United Kingdom]", "51N31 0W08", "+0:00"), 2026)
        assertEquals(51 + 31 / 60.0, london.latitudeDeg, 1e-12)
        assertEquals(-(8 / 60.0), london.longitudeDeg, 1e-12)
    }

    /**
     * The reason coordinates are taken from the artifact and not from the grid. Delhi is
     * published at 28N40 77E13 and carried in the grid at 28.6139/77.2090 — about 1.3 km
     * apart. Once a sunrise has been rounded to the printed minute, a datum disagreement
     * and a rule disagreement are indistinguishable, so the reference has to be evaluated
     * at the coordinates it was computed for.
     */
    @Test
    fun `site coordinates come from the artifact, not from the reference grid`() {
        val site = VaisnavaSiteZones.siteFor("delhi", header("Delhi [India]", "28N40 77E13", "+5:30"), 2026)
        assertNotEquals(ReferenceCities.DELHI.latitudeDeg, site.latitudeDeg)
        assertNotEquals(ReferenceCities.DELHI.longitudeDeg, site.longitudeDeg)
        assertEquals(28.0 + 40.0 / 60.0, site.latitudeDeg, 1e-12)
    }

    @Test
    fun `refuses a zone whose standard offset contradicts the printed offset`() {
        val e = assertThrows<IllegalStateException> {
            VaisnavaSiteZones.siteFor("delhi", header("Delhi [India]", "28N40 77E13", "+8:00"), 2026)
        }
        assertTrue(e.message!!.contains("standard offset"), e.message)
    }

    @Test
    fun `refuses a city with no hand-entered zone rather than guessing one from the offset`() {
        val e = assertThrows<IllegalStateException> {
            VaisnavaSiteZones.siteFor("kyiv", header("Kiev [Ukraine]", "50N27 30E31", "+2:00"), 2026)
        }
        assertTrue(e.message!!.contains("No IANA zone"), e.message)
    }

    @Test
    fun `refuses an unreadable coordinate string`() {
        val e = assertThrows<IllegalStateException> {
            VaisnavaSiteZones.siteFor("delhi", header("Delhi [India]", "28.6139N 77.2090E", "+5:30"), 2026)
        }
        assertTrue(e.message!!.contains("coordinate"), e.message)
    }
}
