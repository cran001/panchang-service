package org.panchang.core

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.panchang.ephemeris.Vsop87Ephemeris

class DaylightIntervalTest {
    private val calculator = PanchangCalculator(Vsop87Ephemeris())
    private fun times(rise: RiseSet, set: RiseSet) = SunTimes(rise, set, 0.0, rise)

    @Test
    fun `ordinary Indian daylight keeps the exact civil events and avoids next day lookup`() {
        for (site in listOf(GeoLocation.of(23.0 + 25.0 / 60, 88.0 + 23.0 / 60, "Asia/Kolkata"),
                            GeoLocation.of(19.076, 72.8777, "Asia/Kolkata"))) {
            for (date in listOf("2026-01-01", "2026-06-26", "2026-12-31")) {
                val sun = calculator.sunTimes(LocalDate.parse(date), site)
                val interval = sun.daylightInterval { error("ordinary daylight must use same-date sunset") }!!
                assertEquals(sun.sunrise.jdUtOrNull, interval.sunriseJdUt)
                assertEquals(sun.sunset.jdUtOrNull, interval.sunsetJdUt)
                assertEquals(sun.daylightDays, interval.durationDays)
            }
        }
    }

    @Test
    fun `cross-midnight interval uses a distinct next sunset without altering civil semantics`() {
        val site = GeoLocation.of(64.1466, -21.9426, "Atlantic/Reykjavik")
        val date = LocalDate.of(2026, 6, 26)
        val sun = calculator.sunTimes(date, site)
        val next = calculator.sunTimes(date.plusDays(1), site)
        val original = sun.copy()
        assertTrue(sun.daylightDays!! < 0)
        val interval = sun.daylightInterval { next }!!
        assertEquals(next.sunset.jdUtOrNull, interval.sunsetJdUt)
        assertTrue(interval.sunsetJdUt < next.sunrise.jdUtOrNull!!)
        assertTrue(interval.durationDays > 0)
        assertNotEquals(sun.sunset.jdUtOrNull!! + 1, interval.sunsetJdUt)
        assertNotEquals(-sun.daylightDays!!, interval.durationDays)
        assertEquals(original, sun)
    }

    @Test
    fun `daylight across both DST transitions follows actual instants and civil dates`() {
        // Deliberately skew longitude against the zone to put sunset after midnight, including
        // the clock change. These are interval mechanics tests, not accepted observance sites.
        val site = GeoLocation.of(0.0, -150.0, "Europe/London")
        for (transition in listOf("2027-03-28", "2027-10-31")) {
            val date = LocalDate.parse(transition).minusDays(1)
            val sun = calculator.sunTimes(date, site)
            val next = calculator.sunTimes(date.plusDays(1), site)
            val interval = sun.daylightInterval { next }!!
            assertTrue(sun.daylightDays!! < 0)
            assertEquals(next.sunset.jdUtOrNull, interval.sunsetJdUt)
            assertEquals(date, site.localDate(interval.sunriseJdUt))
            assertEquals(date.plusDays(1), site.localDate(interval.sunsetJdUt))
            val civilHours = (site.jdUtAtEndOfDay(date.plusDays(1)) - site.jdUtAtStartOfDay(date.plusDays(1))) * 24
            assertEquals(if (transition.contains("03-")) 23.0 else 25.0, civilHours, 1e-7)
            assertTrue(interval.durationDays * 24 in 12.0..12.3)
        }
    }

    @Test
    fun `missing endpoints and polar conditions do not invent an interval`() {
        val absent = listOf(RiseSet.NoEventInWindow, RiseSet.CircumpolarUp, RiseSet.CircumpolarDown)
        for (missing in absent) {
            assertNull(times(missing, RiseSet.At(1.8)).daylightInterval { error("no sunrise") })
            assertNull(times(RiseSet.At(1.2), missing).daylightInterval { times(missing, missing) })
        }
        // No same-date sunset, but a real following sunset before the next sunrise is usable.
        assertEquals(2.1, times(RiseSet.At(1.2), RiseSet.NoEventInWindow)
            .daylightInterval { times(RiseSet.At(2.2), RiseSet.At(2.1)) }!!.sunsetJdUt)
        // An intervening sunrise means this is not the end of the starting daylight.
        assertNull(times(RiseSet.At(1.2), RiseSet.NoEventInWindow)
            .daylightInterval { times(RiseSet.At(2.2), RiseSet.At(2.8)) })
        for (latitude in listOf(78.2232, -77.8419)) {
            val site = GeoLocation.of(latitude, 15.6469, "UTC")
            for (date in listOf(LocalDate.of(2026, 6, 21), LocalDate.of(2026, 12, 21))) {
                val sun = calculator.sunTimes(date, site)
                assertNull(sun.daylightInterval { calculator.sunTimes(date.plusDays(1), site) })
            }
        }
    }
}
