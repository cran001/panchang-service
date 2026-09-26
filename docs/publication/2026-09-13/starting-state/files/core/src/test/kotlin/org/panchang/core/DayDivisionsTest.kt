package org.panchang.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * The eighth-part and muhurta divisions of daylight.
 *
 * These are pure arithmetic on two instants, so they are tested as such: the properties that
 * matter are that the parts tile the daylight period exactly, that the weekday tables are the
 * ones actually in use, and that nothing here consults a clock or a default zone.
 */
class DayDivisionsTest {

    private val calculator = PanchangCalculator(LinearEphemeris())
    private val delhi = GeoLocation(28.6139, 77.2090, ZoneId.of("Asia/Kolkata"))

    private val sunrise = 2_461_100.25
    private val sunset = sunrise + 0.5

    /**
     * A Julian Day near the present is about 2.46e6, where one double ulp is 2^-31 days — some
     * 40 microseconds. Anything built by adding a fraction of a day to a JD and subtracting it
     * back therefore carries a few ulp of noise, so equality here is asserted to 1e-8 days
     * (0.9 ms): twenty ulp, and five orders of magnitude tighter than the one-second boundary
     * tolerance the rest of the module works to. Asserting exact equality would be testing the
     * representation rather than the arithmetic.
     */
    private val toleranceDays = 1e-8

    @Test
    fun `the eight parts tile daylight exactly`() {
        val parts = (1..8).map { DayDivisions.equalPart(sunrise, sunset, it, DayDivisions.EIGHTHS) }

        assertEquals(sunrise, parts.first().startJdUt, toleranceDays)
        assertEquals(sunset, parts.last().endJdUt, toleranceDays)
        parts.zipWithNext { before, after ->
            assertEquals(before.endJdUt, after.startJdUt, toleranceDays) { "gap between eighth parts" }
        }
        for (part in parts) {
            assertEquals((sunset - sunrise) / 8.0, part.durationDays, toleranceDays)
        }
        assertEquals(sunset - sunrise, parts.sumOf { it.durationDays }, toleranceDays)
    }

    @Test
    fun `the fifteen muhurtas tile daylight exactly`() {
        val muhurtas =
            (1..15).map { DayDivisions.equalPart(sunrise, sunset, it, DayDivisions.DAY_MUHURTAS) }
        assertEquals(sunrise, muhurtas.first().startJdUt, toleranceDays)
        assertEquals(sunset, muhurtas.last().endJdUt, toleranceDays)
        assertEquals(sunset - sunrise, muhurtas.sumOf { it.durationDays }, toleranceDays)
    }

    @Test
    fun `part indices are one-based and out-of-range parts are rejected`() {
        assertEquals(sunrise, DayDivisions.equalPart(sunrise, sunset, 1, 8).startJdUt, toleranceDays)
        assertThrows(IllegalArgumentException::class.java) {
            DayDivisions.equalPart(sunrise, sunset, 0, 8)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DayDivisions.equalPart(sunrise, sunset, 9, 8)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DayDivisions.equalPart(sunrise, sunset, 1, 0)
        }
    }

    @Test
    fun `abhijit is the eighth muhurta and is centred on the middle of daylight`() {
        val abhijit = DayDivisions.abhijit(sunrise, sunset)
        val midpoint = (sunrise + sunset) / 2.0
        val centre = (abhijit.startJdUt + abhijit.endJdUt) / 2.0

        assertEquals(midpoint, centre, toleranceDays)
        assertEquals((sunset - sunrise) / 15.0, abhijit.durationDays, toleranceDays)
        assertEquals(DayDivisions.equalPart(sunrise, sunset, 8, 15), abhijit)
        // 48 minutes for a twelve-hour day: one fifteenth of it.
        assertEquals(48.0, abhijit.durationDays * 1440.0, toleranceDays * 1440.0)
    }

    @Test
    fun `the rahu kaal weekday table is the standard one`() {
        val expected = mapOf(
            DayOfWeek.SUNDAY to 8,
            DayOfWeek.MONDAY to 2,
            DayOfWeek.TUESDAY to 7,
            DayOfWeek.WEDNESDAY to 5,
            DayOfWeek.THURSDAY to 6,
            DayOfWeek.FRIDAY to 4,
            DayOfWeek.SATURDAY to 3,
        )
        assertPartTable(expected) { vaar -> DayDivisions.rahuKaal(vaar, sunrise, sunset) }
    }

    @Test
    fun `the yamaganda weekday table is the standard one`() {
        val expected = mapOf(
            DayOfWeek.SUNDAY to 5,
            DayOfWeek.MONDAY to 4,
            DayOfWeek.TUESDAY to 3,
            DayOfWeek.WEDNESDAY to 2,
            DayOfWeek.THURSDAY to 1,
            DayOfWeek.FRIDAY to 7,
            DayOfWeek.SATURDAY to 6,
        )
        assertPartTable(expected) { vaar -> DayDivisions.yamaganda(vaar, sunrise, sunset) }
    }

    @Test
    fun `the gulika weekday table is the standard one`() {
        val expected = mapOf(
            DayOfWeek.SUNDAY to 7,
            DayOfWeek.MONDAY to 6,
            DayOfWeek.TUESDAY to 5,
            DayOfWeek.WEDNESDAY to 4,
            DayOfWeek.THURSDAY to 3,
            DayOfWeek.FRIDAY to 2,
            DayOfWeek.SATURDAY to 1,
        )
        assertPartTable(expected) { vaar -> DayDivisions.gulika(vaar, sunrise, sunset) }
    }

    @Test
    fun `the three eighth-part periods never coincide on any weekday`() {
        // A weekday on which two of them shared a slot would mean a transcription error in one
        // of the tables, which is otherwise easy to miss.
        for (vaar in Vaar.entries) {
            val periods = listOf(
                DayDivisions.rahuKaal(vaar, sunrise, sunset),
                DayDivisions.yamaganda(vaar, sunrise, sunset),
                DayDivisions.gulika(vaar, sunrise, sunset),
            )
            assertEquals(3, periods.distinct().size) { "two periods coincide on $vaar" }
        }
    }

    @Test
    fun `all seven weekdays are covered by each table`() {
        // getValue throws on a missing weekday; running every Vaar proves none is absent.
        for (vaar in Vaar.entries) {
            DayDivisions.rahuKaal(vaar, sunrise, sunset)
            DayDivisions.yamaganda(vaar, sunrise, sunset)
            DayDivisions.gulika(vaar, sunrise, sunset)
        }
        assertEquals(7, Vaar.entries.size)
        assertEquals(7, Vaar.entries.map { it.dayOfWeek }.distinct().size)
    }

    @Test
    fun `divisions of a real day fall between sunrise and sunset`() {
        val date = LocalDate.of(2026, 7, 4)
        val panchang = calculator.panchang(date, delhi)
        val riseJdUt = panchang.sun.sunrise.jdUtOrNull!!
        val setJdUt = panchang.sun.sunset.jdUtOrNull!!

        for (period in listOfNotNull(
            panchang.rahuKaal,
            panchang.yamaganda,
            panchang.gulika,
            panchang.abhijit,
        )) {
            assertTrue(period.startJdUt >= riseJdUt - toleranceDays) { "$period starts before sunrise" }
            assertTrue(period.endJdUt <= setJdUt + toleranceDays) { "$period ends after sunset" }
            assertTrue(period.durationDays > 0.0)
        }

        // Abhijit brackets local apparent noon, which is the transit, to within the small
        // asymmetry of daylight about it.
        val abhijit = panchang.abhijit!!
        assertTrue(panchang.sun.solarNoonJdUt in abhijit) {
            "abhijit ${delhi.zonedDateTime(abhijit.startJdUt)}..${delhi.zonedDateTime(abhijit.endJdUt)} " +
                "does not contain the transit at ${delhi.zonedDateTime(panchang.sun.solarNoonJdUt)}"
        }
    }

    @Test
    fun `a longer day makes every division proportionally longer`() {
        // The divisions are defined on the interval, so they stretch with it. This is also what
        // makes them correct on a 23- or 25-hour civil day: nothing here reads a clock.
        val shortDay = DayDivisions.rahuKaal(Vaar.SOMAVARA, sunrise, sunrise + 0.4)
        val longDay = DayDivisions.rahuKaal(Vaar.SOMAVARA, sunrise, sunrise + 0.6)

        assertEquals(0.4 / 8.0, shortDay.durationDays, toleranceDays)
        assertEquals(0.6 / 8.0, longDay.durationDays, toleranceDays)
        assertTrue(longDay.durationDays > shortDay.durationDays)
    }

    @Test
    fun `an interval contains its start but not its end`() {
        val interval = JdInterval(sunrise, sunset)
        assertTrue(sunrise in interval)
        assertTrue((sunrise + 0.25) in interval)
        assertTrue(sunset !in interval) { "the interval is half-open" }
        assertTrue((sunrise - 1e-9) !in interval)
    }

    @Test
    fun `an inverted or non-finite interval is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { JdInterval(sunset, sunrise) }
        assertThrows(IllegalArgumentException::class.java) { JdInterval(Double.NaN, sunset) }
        assertThrows(IllegalArgumentException::class.java) {
            JdInterval(sunrise, Double.POSITIVE_INFINITY)
        }
    }

    @Test
    fun `rahu kaal spans one eighth of daylight for a real site`() {
        val date = LocalDate.of(2026, 7, 4)
        val panchang = calculator.panchang(date, delhi)
        val daylight = panchang.sun.daylightDays!!
        val rahuKaal = panchang.rahuKaal!!

        assertEquals(daylight / 8.0, rahuKaal.durationDays, toleranceDays)
        // 2026-07-04 is a Saturday, whose slot is the 3rd eighth.
        assertEquals(Vaar.SHANIVARA, panchang.vaar)
        val riseJdUt = panchang.sun.sunrise.jdUtOrNull!!
        assertEquals(riseJdUt + 2.0 * daylight / 8.0, rahuKaal.startJdUt, toleranceDays)
        assertTrue(abs(rahuKaal.durationDays * 1440.0 - 90.0) < 15.0) {
            "an eighth of a Delhi July day should be about 90 minutes"
        }
    }

    private fun assertPartTable(expected: Map<DayOfWeek, Int>, actual: (Vaar) -> JdInterval) {
        for (vaar in Vaar.entries) {
            val part = expected.getValue(vaar.dayOfWeek)
            assertEquals(DayDivisions.equalPart(sunrise, sunset, part, 8), actual(vaar)) {
                "$vaar should occupy part $part of eight"
            }
        }
    }
}
