package org.panchang.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.panchang.ephemeris.TimeScale
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * Moonrise and moonset, which the app engine did not compute at all. The "fast until moonrise"
 * rules need them, and they need the awkward cases handled rather than smoothed over.
 */
class MoonTimesTest {

    private val ephemeris = LinearEphemeris()
    private val calculator = PanchangCalculator(ephemeris)
    private val delhi = GeoLocation(28.6139, 77.2090, ZoneId.of("Asia/Kolkata"))
    private val startDate = LocalDate.of(2026, 3, 1)

    @Test
    fun `moon events sit on the lunar horizon, which is not the solar one`() {
        var date = startDate
        var checked = 0
        repeat(30) {
            val times = calculator.moonTimes(date, delhi)
            for (event in listOf(times.moonrise, times.moonset)) {
                val jdUt = event.jdUtOrNull ?: continue
                val horizon = calculator.moonHorizonAltitudeDeg(jdUt, delhi)
                val residual = abs(calculator.moonAltitudeDeg(jdUt, delhi) - horizon)
                // Looser than the solar bound (1e-5 deg) because the Moon's own motion is
                // thirteen times faster, so the outer refinement's fixed pass count leaves a
                // slightly larger residual. 1e-4 deg is still about 30 ms of time — well inside
                // the one-second tolerance the solver contracts to.
                assertTrue(residual < 1e-4) { "moon altitude residual $residual deg on $date" }
                checked++
            }
            date = date.plusDays(1)
        }
        assertTrue(checked > 50) { "expected most days to have both events, checked only $checked" }
    }

    @Test
    fun `the lunar horizon is above the true horizon because parallax beats refraction`() {
        // h0 = 0.7275*pi - 34'. At mean distance the parallax term is about 0.69 deg and the
        // refraction allowance 0.567, so the Moon's centre is slightly *above* the horizon at
        // moonrise, unlike the Sun's, which is well below it.
        val horizon = calculator.moonHorizonAltitudeDeg(TimeScale.jdUtAtMidnight(2026, 3, 1), delhi)
        assertTrue(horizon > 0.0) { "lunar horizon altitude $horizon should be positive" }
        assertTrue(horizon < 0.3) { "lunar horizon altitude $horizon is implausibly high" }
        assertTrue(horizon > calculator.sunHorizonAltitudeDeg(delhi))
    }

    @Test
    fun `moon events fall inside the civil day they are reported for`() {
        var date = startDate
        repeat(35) {
            val windowStart = delhi.jdUtAtStartOfDay(date)
            val windowEnd = delhi.jdUtAtEndOfDay(date)
            val times = calculator.moonTimes(date, delhi)
            for (event in listOf(times.moonrise, times.moonset)) {
                val jdUt = event.jdUtOrNull ?: continue
                assertTrue(jdUt >= windowStart && jdUt < windowEnd) {
                    "event at ${delhi.zonedDateTime(jdUt)} is outside the civil day $date"
                }
            }
            date = date.plusDays(1)
        }
    }

    @Test
    fun `about once a lunar month a civil day has no moonrise`() {
        // Moonrise slips roughly 50 minutes later each day, so it must eventually skip a civil
        // day. That day is not polar and not an error, and collapsing it into either would be
        // wrong. This is the case the sealed type exists for.
        var date = startDate
        var missingRise = 0
        var missingSet = 0
        repeat(40) {
            val times = calculator.moonTimes(date, delhi)
            if (times.moonrise == RiseSet.NoEventInWindow) missingRise++
            if (times.moonset == RiseSet.NoEventInWindow) missingSet++
            date = date.plusDays(1)
        }
        assertTrue(missingRise in 1..2) { "expected one skipped moonrise in 40 days, got $missingRise" }
        assertTrue(missingSet in 1..2) { "expected one skipped moonset in 40 days, got $missingSet" }
    }

    @Test
    fun `moonrise runs later each day by roughly fifty minutes`() {
        var date = startDate
        var previous: Double? = null
        val deltas = mutableListOf<Double>()
        repeat(20) {
            val rise = calculator.moonTimes(date, delhi).moonrise.jdUtOrNull
            if (rise != null && previous != null) {
                val delta = (rise - previous!!) * 1440.0
                if (delta in 1000.0..1600.0) deltas += delta - 1440.0
            }
            if (rise != null) previous = rise
            date = date.plusDays(1)
        }
        assertTrue(deltas.size > 10) { "not enough consecutive moonrises to measure the drift" }
        assertEquals(50.0, deltas.average(), 15.0)
    }

    @Test
    fun `the full moon rises near sunset`() {
        // A geometric consequence of opposition, and a cheap end-to-end check that the lunar
        // and solar paths share a consistent coordinate frame.
        val fullMoonJdUt = ephemeris.jdUtWhenElongation(180.0, TimeScale.jdUtAtMidnight(2026, 3, 1))
        val date = delhi.localDate(fullMoonJdUt)
        val sunset = calculator.sunTimes(date, delhi).sunset.jdUtOrNull!!
        val moonrise = calculator.moonTimes(date, delhi).moonrise.jdUtOrNull
            ?: calculator.moonTimes(date.plusDays(1), delhi).moonrise.jdUtOrNull!!

        val differenceMinutes = abs(moonrise - sunset) * 1440.0
        assertTrue(differenceMinutes < 90.0) {
            "full moon rose $differenceMinutes minutes from sunset, which is too far from opposition"
        }
    }
}
