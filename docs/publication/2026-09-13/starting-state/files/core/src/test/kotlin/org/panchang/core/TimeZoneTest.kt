package org.panchang.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * Defect #2: the engine read `Calendar.getInstance()` — the JVM default zone — when it needed
 * the weekday for the Rahu Kaal table and the vaar, so a server outside the user's zone got
 * both wrong for the hours of every day on which the two dates disagree.
 *
 * The build pins `user.timezone=UTC` for all tests precisely so that a reintroduction of that
 * bug fails here rather than in production. These tests use zones far from UTC and assert on
 * the site's own local time, so any accidental use of the default zone shows up immediately.
 */
class TimeZoneTest {

    private val calculator = PanchangCalculator(LinearEphemeris())

    private val auckland = GeoLocation(-36.8485, 174.7633, ZoneId.of("Pacific/Auckland"))
    private val london = GeoLocation(51.5074, -0.1278, ZoneId.of("Europe/London"))
    private val delhi = GeoLocation(28.6139, 77.2090, ZoneId.of("Asia/Kolkata"))

    @Test
    fun `equivalent zone ids give identical instants`() {
        val date = LocalDate.of(2026, 6, 15)
        // Asia/Calcutta is a legacy alias for Asia/Kolkata: a different ZoneId object, the same
        // rules. And India keeps a fixed +05:30, so a raw offset is equivalent there too.
        val byAlias = delhi.copy(zone = ZoneId.of("Asia/Calcutta"))
        val byOffset = delhi.copy(zone = ZoneId.of("+05:30"))

        assertNotEquals(delhi.zone, byAlias.zone)
        val reference = calculator.sunTimes(date, delhi)
        for (variant in listOf(byAlias, byOffset)) {
            val times = calculator.sunTimes(date, variant)
            assertEquals(reference.sunrise.jdUtOrNull!!, times.sunrise.jdUtOrNull!!, 1e-9)
            assertEquals(reference.sunset.jdUtOrNull!!, times.sunset.jdUtOrNull!!, 1e-9)
            assertEquals(reference.solarNoonJdUt, times.solarNoonJdUt, 1e-9)
        }
    }

    @Test
    fun `rahu kaal is computed in the site's zone, not in UTC`() {
        // 2026-06-15 is a Monday in Auckland and, before 12:00 NZST, still Sunday in UTC. The
        // Rahu Kaal slot for Monday is the 2nd eighth of the day and for Sunday the 8th, so a
        // default-zone bug is not a subtle shift here — it lands in a completely different part
        // of the day.
        val date = LocalDate.of(2026, 6, 15)
        val panchang = calculator.panchang(date, auckland)
        val rahuKaal = panchang.rahuKaal
        assertNotNull(rahuKaal) { "Auckland has ordinary daylight in June" }
        requireNotNull(rahuKaal)

        assertEquals(Vaar.SOMAVARA, panchang.vaar)

        val sunrise = panchang.sun.sunrise.jdUtOrNull!!
        val sunset = panchang.sun.sunset.jdUtOrNull!!
        assertTrue(rahuKaal.startJdUt >= sunrise) { "Rahu Kaal starts before sunrise" }
        assertTrue(rahuKaal.endJdUt <= sunset + 1e-9) { "Rahu Kaal ends after sunset" }
        assertEquals((sunset - sunrise) / 8.0, rahuKaal.durationDays, 1e-9)

        // The 2nd eighth begins one eighth of the day after sunrise.
        assertEquals(sunrise + (sunset - sunrise) / 8.0, rahuKaal.startJdUt, 1e-9)

        // The same physical site, declared as if it kept UTC. The UTC civil day at 175 deg east
        // straddles the local night, so the answer is not merely shifted — it is a different
        // interval, and may legitimately be absent because that civil day's sunset precedes its
        // sunrise. Either way it is not the answer for Auckland.
        val asIfUtc = auckland.copy(zone = ZoneId.of("UTC"))
        val utcPanchang = calculator.panchang(date, asIfUtc)
        assertNotEquals(panchang.rahuKaal, utcPanchang.rahuKaal)
    }

    @Test
    fun `vaar follows the site's civil date`() {
        // 2026-06-15T00:30 NZST is 2026-06-14T12:30 UTC: Monday locally, Sunday in UTC.
        val date = LocalDate.of(2026, 6, 15)
        assertEquals(Vaar.SOMAVARA, calculator.panchang(date, auckland).vaar)
        assertEquals(Vaar.SOMAVARA, Vaar.ofLocalDate(date))

        // The vaar turns over at sunrise, not at midnight.
        val panchang = calculator.panchang(date, auckland)
        val sunrise = panchang.sun.sunrise.jdUtOrNull!!
        assertEquals(Vaar.RAVIVARA, Vaar.atInstant(sunrise - 0.05, auckland, panchang.sun.sunrise))
        assertEquals(Vaar.SOMAVARA, Vaar.atInstant(sunrise + 0.05, auckland, panchang.sun.sunrise))
    }

    @Test
    fun `sunrise always falls on the requested local date, including across DST`() {
        // Auckland: DST ends 2026-04-05 (a 25-hour civil day) and begins 2026-09-27 (23 hours).
        // London: 2026-03-29 and 2026-10-25.
        val transitions = listOf(
            auckland to LocalDate.of(2026, 4, 5),
            auckland to LocalDate.of(2026, 9, 27),
            london to LocalDate.of(2026, 3, 29),
            london to LocalDate.of(2026, 10, 25),
        )

        for ((location, transitionDate) in transitions) {
            var previousSunrise = Double.NEGATIVE_INFINITY
            for (offset in -3L..3L) {
                val date = transitionDate.plusDays(offset)
                val times = calculator.sunTimes(date, location)
                val sunrise = times.sunrise.jdUtOrNull
                assertNotNull(sunrise) { "no sunrise on $date at ${location.zone}" }

                assertEquals(date, location.localDate(sunrise!!)) {
                    "sunrise for $date at ${location.zone} landed on ${location.localDate(sunrise)}"
                }
                assertTrue(sunrise > previousSunrise) {
                    "sunrise instants are not monotonic across the DST change on $date"
                }
                if (previousSunrise.isFinite()) {
                    val gap = sunrise - previousSunrise
                    assertTrue(gap in 0.9..1.1) {
                        "consecutive sunrises $gap days apart on $date"
                    }
                }
                previousSunrise = sunrise
            }
        }
    }

    @Test
    fun `local clock times move by about an hour at a DST transition and stay ordered`() {
        // The underlying instants shift by minutes a day; the wall clock jumps by an hour. Both
        // must hold at once, and only code that keeps the two notions separate gets this right.
        val transitionDate = LocalDate.of(2026, 4, 5) // NZDT -> NZST, clocks go back
        val before = calculator.sunTimes(transitionDate.minusDays(1), auckland).sunrise.jdUtOrNull!!
        val after = calculator.sunTimes(transitionDate, auckland).sunrise.jdUtOrNull!!

        val instantShiftMinutes = (after - before - 1.0) * 1440.0
        assertTrue(abs(instantShiftMinutes) < 5.0) {
            "the instant of sunrise should drift by a few minutes a day, not $instantShiftMinutes"
        }

        val clockBefore = auckland.zonedDateTime(before).toLocalTime()
        val clockAfter = auckland.zonedDateTime(after).toLocalTime()
        val clockShiftMinutes = (clockAfter.toSecondOfDay() - clockBefore.toSecondOfDay()) / 60.0
        assertTrue(clockShiftMinutes < -50.0 && clockShiftMinutes > -70.0) {
            "wall-clock sunrise should jump back about an hour, jumped $clockShiftMinutes min"
        }

        assertTrue(after > before) { "instants must stay ordered through the transition" }
    }

    @Test
    fun `the length of the civil day is honoured, not assumed to be 24 hours`() {
        val longDay = LocalDate.of(2026, 4, 5) // 25 hours in Auckland
        val shortDay = LocalDate.of(2026, 9, 27) // 23 hours in Auckland

        assertEquals(
            25.0 / 24.0,
            auckland.jdUtAtEndOfDay(longDay) - auckland.jdUtAtStartOfDay(longDay),
            1e-9,
        )
        assertEquals(
            23.0 / 24.0,
            auckland.jdUtAtEndOfDay(shortDay) - auckland.jdUtAtStartOfDay(shortDay),
            1e-9,
        )
    }
}
