package org.panchang.verify

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.panchang.verify.harvest.ResponseShapeException
import org.panchang.verify.horizons.HorizonsBody
import org.panchang.verify.horizons.HorizonsParser
import org.panchang.verify.horizons.HorizonsQuery

class HorizonsParserTest {

    private val moonQuery = HorizonsQuery(HorizonsBody.MOON, "2026-04-14", "2026-04-15", "1 d")
    private val sunQuery = HorizonsQuery(HorizonsBody.SUN, "2026-04-14", "2026-04-16", "1 d")

    private fun moonFixture() = Fixtures.text("horizons_moon_geocentric_2026-04-14.txt")
    private fun sunFixture() = Fixtures.text("horizons_sun_geocentric_2026-04-14.txt")

    /**
     * The regression anchor for this whole module.
     *
     * 338.2914490 degrees is the Moon's apparent geocentric ecliptic longitude at
     * 2026-04-14 00:00 UT as returned by JPL Horizons on 2026-08-01. If this assertion
     * ever fails, the parser is wrong; do not "fix" it by changing the expected value.
     */
    @Test
    fun `recovers the moon apparent longitude regression anchor`() {
        val parsed = HorizonsParser.parse(moonFixture(), moonQuery)
        val first = parsed.records.first()
        assertEquals("2026-04-14T00:00:00Z", first.utc)
        assertEquals(338.2914490, first.apparentEclipticLongitudeDeg, 0.0)
        assertEquals(0.0147343, first.apparentEclipticLatitudeDeg, 0.0)
    }

    @Test
    fun `parses every row in the ephemeris block`() {
        val parsed = HorizonsParser.parse(moonFixture(), moonQuery)
        assertEquals(2, parsed.records.size)
        assertEquals(2, parsed.report.recordCount)
        assertTrue(parsed.report.unparsed.isEmpty(), "unexpected unparsed: ${parsed.report.unparsed}")
        assertEquals(351.8489780, parsed.records[1].apparentEclipticLongitudeDeg, 0.0)
    }

    @Test
    fun `warns when the table carries no distance column`() {
        // The moon fixture was fetched with QUANTITIES='31' only. A caller that expects a
        // distance must be told it is absent rather than silently receive nulls.
        val parsed = HorizonsParser.parse(moonFixture(), moonQuery)
        assertTrue(parsed.report.warnings.any { it.contains("delta") }, parsed.report.warnings.toString())
        assertEquals(null, parsed.records.first().distanceAu)
    }

    @Test
    fun `resolves columns by name so an added quantity does not shift the reading`() {
        // The sun fixture has two extra columns (delta, deldot) inserted after ObsEcLat.
        // A positional parser would read deldot as a distance here.
        val parsed = HorizonsParser.parse(sunFixture(), sunQuery)
        assertEquals(3, parsed.records.size)
        val first = parsed.records.first()
        assertEquals(24.0607132, first.apparentEclipticLongitudeDeg, 0.0)
        assertEquals(1.00284989767471, first.distanceAu!!, 0.0)
        assertEquals(0.4953917, first.rangeRateKmPerSec!!, 0.0)
    }

    @Test
    fun `records the ephemeris kernel and geocentric framing`() {
        val (table, _) = HorizonsParser.parseTable(moonFixture(), moonQuery)
        assertEquals("DE441", table.header.ephemerisSource)
        assertEquals("GEOCENTRIC", table.header.centerSiteName)
        assertTrue(table.header.centerBodyName.startsWith("Earth (399)"))
        assertNotNull(table.header.tableFormat)
    }

    @Test
    fun `rejects a table for the wrong target body`() {
        // Asking for the Moon and being handed the Sun must fail, not silently succeed.
        val e = assertThrows<ResponseShapeException> {
            HorizonsParser.parse(sunFixture(), moonQuery)
        }
        assertTrue(e.message!!.contains("target"), e.message)
    }

    @Test
    fun `rejects a table whose centre is not the geocentre`() {
        val tampered = moonFixture().replace("Center-site name: GEOCENTRIC", "Center-site name: SOMEWHERE")
        val e = assertThrows<ResponseShapeException> { HorizonsParser.parse(tampered, moonQuery) }
        assertTrue(e.message!!.contains("GEOCENTRIC"), e.message)
    }

    /**
     * The frame declaration is the only machine-checkable statement that ObsEcLon means
     * apparent ecliptic-of-date. Comparing a J2000 mean longitude against these values
     * would produce a plausible-looking few-tenths-of-a-degree bias, which is exactly the
     * size of error a tithi boundary is sensitive to.
     */
    @Test
    fun `rejects a response that does not declare the ecliptic-of-date frame`() {
        val tampered = moonFixture().replace("ecliptic-of-date", "ecliptic-of-J2000")
        val e = assertThrows<ResponseShapeException> { HorizonsParser.parse(tampered, moonQuery) }
        assertTrue(e.message!!.contains("frame"), e.message)
    }

    @Test
    fun `fails loudly on a Horizons prose error instead of returning nothing`() {
        val prose = "API VERSION: 1.2\nNo ephemeris for target \"999\" after 2026-Apr-14\n"
        val e = assertThrows<ResponseShapeException> { HorizonsParser.parse(prose, moonQuery) }
        assertTrue(e.message!!.contains("SOE"), e.message)
    }

    @Test
    fun `reports a malformed data row rather than skipping it silently`() {
        val broken = moonFixture().replace(
            " 2026-Apr-15 00:00:00, , , 351.8489780,  1.2229991,",
            " 2026-Apr-15 00:00:00, , , n.a.,  n.a.,",
        )
        val parsed = HorizonsParser.parse(broken, moonQuery)
        assertEquals(1, parsed.records.size)
        assertEquals(1, parsed.report.unparsed.size)
        assertTrue(parsed.report.unparsed.first().reason.contains("numeric"))
    }
}
