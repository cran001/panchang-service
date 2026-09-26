package org.panchang.calc

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.panchang.core.GeoLocation
import org.panchang.sampradaya.IskconRules

class YearCarryoverTest {
    private val site = ResolvedSite(
        GeoLocation.of(23.0 + 25.0 / 60, 88.0 + 23.0 / 60, "Asia/Kolkata"),
        CoordinateSource.CALLER_COORDINATES, "Mayapur audit coordinates",
    )
    private val engine = CalcEngine()
    private val rules = IskconRules()

    @Test
    fun `January 1 retains the December fast with its distinct Parana date`() {
        // Independent calendar: verify/golden/vaisnavacalendar-mayapur-2026.json, Jan 1.
        val day = engine.compute(site, rules, Scope.Day(LocalDate.of(2026, 1, 1)))
        val observance = day.ekadashiYear.observances.single()
        assertEquals(LocalDate.of(2025, 12, 31), observance.date)
        assertEquals(LocalDate.of(2026, 1, 1), observance.parana!!.date)
    }

    @Test
    fun `adjacent years share only the carryover and day selection agrees with each year`() {
        val years = (2025..2027).associateWith { engine.compute(site, rules, Scope.Year(it)).ekadashiYear.observances }
        for ((year, observations) in years) {
            assertEquals(observations.size, observations.map { it.date }.distinct().size)
            assertTrue(observations.all { it.date.year == year || it.parana?.date?.year == year })
        }
        val carry = years.getValue(2026).single { it.date.year == 2025 }
        val previous = years.getValue(2025).single { it.date == carry.date }
        // Different yearly index seeds can round a tithi boundary a second differently.
        // Compare delivery identity and the complete Parana, not unrelated tithi rendering.
        assertEquals(previous.date, carry.date)
        assertEquals(previous.name, carry.name)
        assertEquals(previous.kind, carry.kind)
        assertEquals(previous.mahadvadashiType, carry.mahadvadashiType)
        assertEquals(previous.parana, carry.parana)
        for (date in listOf("2025-12-30", "2025-12-31", "2026-01-01", "2026-01-02",
                            "2026-01-14", "2026-01-15", "2026-12-31", "2027-01-01", "2027-01-02")) {
            val d = LocalDate.parse(date)
            val expected = years.getValue(d.year).filter { it.date == d || it.parana?.date == d }
            assertEquals(expected, engine.compute(site, rules, Scope.Day(d)).ekadashiYear.observances, date)
        }
    }
}
