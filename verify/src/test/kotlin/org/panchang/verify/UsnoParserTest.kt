package org.panchang.verify

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.panchang.verify.harvest.ResponseShapeException
import org.panchang.verify.grid.ReferenceCities
import org.panchang.verify.usno.PolarCondition
import org.panchang.verify.usno.UsnoHarvester
import org.panchang.verify.usno.UsnoParser
import org.panchang.verify.usno.UsnoQuery
import java.time.LocalDate
import java.time.ZoneId

class UsnoParserTest {

    private val mayapurQuery = UsnoQuery("2026-04-14", 23.4242, 88.3888, 5.5)
    private val svalbardQuery = UsnoQuery("2026-06-21", 78.22, 15.65, 1.0)

    @Test
    fun `parses an ordinary day`() {
        val parsed = UsnoParser.parse(Fixtures.text("usno_oneday_mayapur_2026-04-14.json"), mayapurQuery)
        val d = parsed.records.single()
        assertEquals("2026-04-14", d.date)
        assertEquals(listOf("05:17"), d.sunRises)
        assertEquals(listOf("17:57"), d.sunSets)
        assertEquals("04:54", d.beginCivilTwilight)
        assertEquals("18:20", d.endCivilTwilight)
        assertEquals(listOf("02:59"), d.moonRises)
        assertEquals(listOf("14:54"), d.moonSets)
        assertEquals(5.5, d.utcOffsetHours)
        assertNull(d.sunCondition)
        assertTrue(parsed.report.unparsed.isEmpty(), parsed.report.unparsed.toString())
        assertTrue(parsed.report.warnings.isEmpty(), parsed.report.warnings.toString())
    }

    /**
     * The polar response is the case that matters. USNO replaces the Rise and Set entries
     * with a prose phenomenon and a null time; a reader that does `sundata[0].time`
     * either crashes or, worse, reports "Begin Civil Twilight" as sunrise.
     */
    @Test
    fun `represents a polar day as a condition rather than a missing sunrise`() {
        val parsed = UsnoParser.parse(Fixtures.text("usno_oneday_svalbard_2026-06-21.json"), svalbardQuery)
        val d = parsed.records.single()
        assertTrue(d.sunRises.isEmpty())
        assertTrue(d.sunSets.isEmpty())
        assertTrue(d.sunHasNoRiseOrSet)
        assertEquals(PolarCondition.CONTINUOUSLY_ABOVE_HORIZON, d.sunCondition)
        assertEquals(PolarCondition.CONTINUOUSLY_ABOVE_TWILIGHT_LIMIT, d.sunTwilightCondition)
        assertTrue(parsed.report.unparsed.isEmpty(), parsed.report.unparsed.toString())
        // No spurious "this is neither normal nor polar" warning: the condition was understood.
        assertTrue(parsed.report.warnings.isEmpty(), parsed.report.warnings.toString())
    }

    /**
     * Svalbard on 2026-06-21 really does set twice: 00:17 and 23:05. Collapsing moondata
     * to a single moonset would drop one of them, which later looks like a one-day offset.
     */
    @Test
    fun `keeps both moonsets on a day that has two`() {
        val parsed = UsnoParser.parse(Fixtures.text("usno_oneday_svalbard_2026-06-21.json"), svalbardQuery)
        val d = parsed.records.single()
        assertEquals(listOf("00:17", "23:05"), d.moonSets)
        assertEquals(listOf("11:29"), d.moonRises)
    }

    @Test
    fun `reports an unknown phenomenon instead of discarding it`() {
        val tampered = Fixtures.text("usno_oneday_mayapur_2026-04-14.json")
            .replace("\"Begin Civil Twilight\"", "\"Begin Nautical Twilight\"")
        val parsed = UsnoParser.parse(tampered, mayapurQuery)
        assertEquals(1, parsed.report.unparsed.size)
        assertTrue(parsed.report.unparsed.first().text.contains("Begin Nautical Twilight"))
    }

    @Test
    fun `rejects USNO's HTTP 200 error envelope`() {
        val envelope = """{"apiversion":"4.0.1","error":true,"type":"invalid coordinates"}"""
        val e = assertThrows<ResponseShapeException> { UsnoParser.parse(envelope, mayapurQuery) }
        assertTrue(e.message!!.contains("error envelope"), e.message)
    }

    @Test
    fun `rejects a response for a different date`() {
        val e = assertThrows<ResponseShapeException> {
            UsnoParser.parse(
                Fixtures.text("usno_oneday_mayapur_2026-04-14.json"),
                mayapurQuery.copy(date = "2026-04-15"),
            )
        }
        assertTrue(e.message!!.contains("2026-04-15"), e.message)
    }

    @Test
    fun `warns when USNO echoes coordinates we did not send`() {
        val parsed = UsnoParser.parse(
            Fixtures.text("usno_oneday_mayapur_2026-04-14.json"),
            mayapurQuery.copy(latitudeDeg = 20.0),
        )
        assertTrue(parsed.report.warnings.any { it.contains("echoed coordinates") })
    }

    /**
     * USNO takes a fixed numeric offset, so the DST resolution happens on our side. If
     * this arithmetic is wrong, USNO returns an internally consistent answer that is an
     * hour out, and nothing in the response reveals it.
     */
    @Test
    fun `resolves DST offsets for the stress cities`() {
        val auckland = ZoneId.of(ReferenceCities.AUCKLAND.ianaZone)
        assertEquals(13.0, UsnoHarvester.offsetHoursFor(auckland, LocalDate.of(2026, 1, 15)))
        assertEquals(12.0, UsnoHarvester.offsetHoursFor(auckland, LocalDate.of(2026, 6, 15)))

        val london = ZoneId.of(ReferenceCities.LONDON.ianaZone)
        assertEquals(0.0, UsnoHarvester.offsetHoursFor(london, LocalDate.of(2026, 1, 15)))
        assertEquals(1.0, UsnoHarvester.offsetHoursFor(london, LocalDate.of(2026, 6, 15)))

        // The transition day itself resolves to the offset in force at local noon.
        assertEquals(1.0, UsnoHarvester.offsetHoursFor(london, LocalDate.of(2026, 3, 29)))

        val kolkata = ZoneId.of(ReferenceCities.MAYAPUR.ianaZone)
        assertEquals(5.5, UsnoHarvester.offsetHoursFor(kolkata, LocalDate.of(2026, 6, 15)))
    }

    @Test
    fun `formats fractional offsets the way USNO expects`() {
        assertEquals("5.5", UsnoHarvester.formatOffset(5.5))
        assertEquals("-5", UsnoHarvester.formatOffset(-5.0))
        assertEquals("13", UsnoHarvester.formatOffset(13.0))
    }
}
