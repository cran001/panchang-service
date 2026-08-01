package org.panchang.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.panchang.ephemeris.TimeScale
import java.time.ZoneId

/**
 * Defect #3: the engine signalled polar conditions with sentinel hours, and used a different
 * set of them for sunrise (0.0 / 24.0) than for sunset (20.0 / 4.0). The classifier downstream
 * then tested the *sunrise* sentinels against the sunset value, so its `POLAR_DAY` branch could
 * never be reached at any latitude on any date.
 *
 * The point of these tests is not only that the right case comes back, but that no sentinel
 * exists to be confused with a real answer: [RiseSet.At] is the only case carrying a number.
 */
class PolarTest {

    private val ephemeris = LinearEphemeris()
    private val calculator = PanchangCalculator(ephemeris)

    /** Longyearbyen, Svalbard, at 78 degrees north. */
    private val svalbard = GeoLocation(78.22, 15.63, ZoneId.of("Arctic/Longyearbyen"))
    private val antarctic = GeoLocation(-78.22, 15.63, ZoneId.of("UTC"))

    /** The date the Sun reaches 90 deg of tropical longitude — the northern summer solstice. */
    private val juneDate =
        svalbard.localDate(ephemeris.jdUtWhenSunLongitude(90.0, TimeScale.jdUtAtMidnight(2026, 1, 1)))

    /** The northern winter solstice. */
    private val decemberDate =
        svalbard.localDate(ephemeris.jdUtWhenSunLongitude(270.0, TimeScale.jdUtAtMidnight(2026, 1, 1)))

    @Test
    fun `midnight sun at 78 north in June`() {
        val times = calculator.sunTimes(juneDate, svalbard)
        assertSame(RiseSet.CircumpolarUp, times.sunrise)
        assertSame(RiseSet.CircumpolarUp, times.sunset)
        assertSame(RiseSet.CircumpolarUp, times.arunodaya)
        assertTrue(times.solarNoonJdUt.isFinite()) { "the Sun still transits during polar day" }
        assertNull(times.daylightDays)
    }

    @Test
    fun `polar night at 78 north in December`() {
        val times = calculator.sunTimes(decemberDate, svalbard)
        assertSame(RiseSet.CircumpolarDown, times.sunrise)
        assertSame(RiseSet.CircumpolarDown, times.sunset)
        assertSame(RiseSet.CircumpolarDown, times.arunodaya)
        assertTrue(times.solarNoonJdUt.isFinite()) { "the Sun still transits during polar night" }
        assertNull(times.daylightDays)
    }

    @Test
    fun `the southern hemisphere is the mirror image`() {
        assertSame(RiseSet.CircumpolarDown, calculator.sunTimes(juneDate, antarctic).sunrise)
        assertSame(RiseSet.CircumpolarUp, calculator.sunTimes(decemberDate, antarctic).sunrise)
    }

    @Test
    fun `polar days produce no numbers at all, let alone sentinel ones`() {
        for (date in listOf(juneDate, decemberDate)) {
            val times = calculator.sunTimes(date, svalbard)
            for (event in listOf(times.sunrise, times.sunset, times.arunodaya)) {
                assertNull(event.jdUtOrNull) { "$event should not carry an instant" }
                assertTrue(event !is RiseSet.At)
            }
            // The engine's sentinels, expressed as UTC hours-into-the-day. None of them can be
            // produced here, because the only case that carries a Double is a real event.
            for (sentinel in listOf(0.0, 4.0, 20.0, 24.0)) {
                val asJdUt = svalbard.jdUtAtStartOfDay(date) + sentinel / 24.0
                assertTrue(times.sunrise.jdUtOrNull != asJdUt)
                assertTrue(times.sunset.jdUtOrNull != asJdUt)
            }
        }
    }

    @Test
    fun `a polar panchang still has elements but no day divisions`() {
        val panchang = calculator.panchang(juneDate, svalbard)

        assertNull(panchang.rahuKaal)
        assertNull(panchang.yamaganda)
        assertNull(panchang.gulika)
        assertNull(panchang.abhijit)

        // The angular elements do not depend on sunrise existing; only the instant they are
        // sampled at does, and that falls back to the middle of the civil day.
        assertTrue(panchang.tithi.index in 0..29)
        assertTrue(panchang.nakshatra.index in 0..26)
        assertTrue(panchang.referenceJdUt.isFinite())
        assertEquals(panchang.referenceJdUt, panchang.referenceJdUt) // not NaN
    }

    @Test
    fun `the transition into and out of polar day is continuous`() {
        // Walking a whole year at 78 N, every day must be exactly one of the four cases and
        // none may produce a non-finite number. The engine's classifier silently reported
        // NORMAL for every polar day; here an unhandled case would not compile.
        var rising = 0
        var up = 0
        var down = 0
        var other = 0
        var date = svalbard.localDate(TimeScale.jdUtAtMidnight(2026, 1, 1))
        repeat(365) {
            when (val sunrise = calculator.sunTimes(date, svalbard).sunrise) {
                is RiseSet.At -> {
                    assertTrue(sunrise.jdUt.isFinite())
                    rising++
                }

                RiseSet.CircumpolarUp -> up++
                RiseSet.CircumpolarDown -> down++
                RiseSet.NoEventInWindow -> other++
            }
            date = date.plusDays(1)
        }

        assertTrue(up > 80) { "expected a long midnight-sun season at 78 N, got $up days" }
        assertTrue(down > 80) { "expected a long polar night at 78 N, got $down days" }
        assertTrue(rising > 100) { "expected an ordinary spring and autumn, got $rising days" }
        assertEquals(365, rising + up + down + other)
    }

    @Test
    fun `moonrise at high latitude is also expressed as cases, never as sentinels`() {
        var date = svalbard.localDate(TimeScale.jdUtAtMidnight(2026, 1, 1))
        repeat(60) {
            val moon = calculator.moonTimes(date, svalbard)
            for (event in listOf(moon.moonrise, moon.moonset)) {
                val jdUt = event.jdUtOrNull
                if (jdUt != null) assertTrue(jdUt.isFinite()) { "non-finite moon event on $date" }
            }
            date = date.plusDays(1)
        }
    }
}
