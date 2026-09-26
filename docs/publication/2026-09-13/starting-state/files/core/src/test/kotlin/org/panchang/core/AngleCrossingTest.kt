package org.panchang.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.panchang.ephemeris.TimeScale
import kotlin.math.abs

/**
 * The boundary solver against arithmetic.
 *
 * With a constant-rate ephemeris the crossing instants have a closed form, so these assert an
 * exact number rather than agreement with another implementation. Everything is checked to one
 * second, which is the solver's stated convergence — not a claim about physical accuracy, which
 * the fake ephemeris does not have any of.
 */
class AngleCrossingTest {

    private val oneSecond = 1.0 / 86_400.0
    private val ephemeris = LinearEphemeris()
    private val calculator = PanchangCalculator(ephemeris)

    /** A quiet Tuesday in 2026, used only as a starting point for the analytic searches. */
    private val baseJdUt = TimeScale.jdUtAtMidnight(2026, 5, 12)

    @Test
    fun `finds a linear crossing to within a second`() {
        val rate = 12.19
        val start = 2_461_000.0
        val angleAt = { t: Double -> TimeScale.normalizeDegrees(100.0 + rate * (t - start)) }

        val expected = start + 50.0 / rate
        val found = AngleCrossing.firstCrossing(angleAt, 150.0, start, start + 5.0, 0.25)

        assertNotNull(found)
        assertEquals(expected, found!!, oneSecond)
    }

    @Test
    fun `finds a crossing of the 360-0 seam`() {
        // The classic failure: an angle running 350 -> 2 does not "increase" if you compare
        // normalised values, so a sign test on raw angles picks the wrong half of the bracket.
        val rate = 12.19
        val start = 2_461_000.0
        val angleAt = { t: Double -> TimeScale.normalizeDegrees(350.0 + rate * (t - start)) }

        val expected = start + 10.0 / rate
        val found = AngleCrossing.firstCrossing(angleAt, 0.0, start, start + 2.0, 0.25)

        assertNotNull(found)
        assertEquals(expected, found!!, oneSecond)
    }

    @Test
    fun `returns null rather than looping when the interval brackets nothing`() {
        val rate = 12.19
        val start = 2_461_000.0
        val angleAt = { t: Double -> TimeScale.normalizeDegrees(350.0 + rate * (t - start)) }

        // Over one day the angle runs 350 -> 2.19 and never reaches 180.
        assertNull(AngleCrossing.firstCrossing(angleAt, 180.0, start, start + 1.0, 0.25))
        assertNull(AngleCrossing.lastCrossingBefore(angleAt, 180.0, start + 1.0, 1.0, 0.25))
    }

    @Test
    fun `lastCrossingBefore returns the latest crossing, not the earliest`() {
        val rate = 12.19
        val start = 2_461_000.0
        val period = 360.0 / rate
        val angleAt = { t: Double -> TimeScale.normalizeDegrees(rate * (t - start)) }

        val at = start + 2.5 * period
        val latest = AngleCrossing.lastCrossingBefore(angleAt, 0.0, at, 3.0 * period, 0.25)
        val earliest = AngleCrossing.firstCrossing(angleAt, 0.0, at - 3.0 * period, at, 0.25)

        assertNotNull(latest)
        assertNotNull(earliest)
        assertEquals(start + 2.0 * period, latest!!, oneSecond)
        assertEquals(start, earliest!!, oneSecond)
    }

    @Test
    fun `tithi 1 boundaries match the closed form`() {
        val rate = ephemeris.elongationDegreesPerDay
        // Half a degree of elongation into the first tithi.
        val jdUt = ephemeris.jdUtWhenElongation(0.5, baseJdUt)
        val tithi = calculator.tithiAt(jdUt)

        assertEquals(0, tithi.index)
        assertEquals("Pratipada", tithi.name)
        assertEquals(Paksha.SHUKLA, tithi.paksha)
        assertEquals(jdUt - 0.5 / rate, tithi.startJdUt, oneSecond)
        assertEquals(jdUt + 11.5 / rate, tithi.endJdUt, oneSecond)
        assertEquals(0.5 / 12.0, tithi.elapsedFraction, 1e-9)
    }

    @Test
    fun `tithi 30 boundaries match the closed form across the seam`() {
        val rate = ephemeris.elongationDegreesPerDay
        // Amavasya: index 29 runs from 348 deg of elongation to 360 == 0, so its end is the
        // seam crossing. This is the case a raw-angle comparison gets wrong.
        val jdUt = ephemeris.jdUtWhenElongation(355.0, baseJdUt)
        val tithi = calculator.tithiAt(jdUt)

        assertEquals(29, tithi.index)
        assertEquals("Amavasya", tithi.name)
        assertEquals(Paksha.KRISHNA, tithi.paksha)
        assertEquals(jdUt - 7.0 / rate, tithi.startJdUt, oneSecond)
        assertEquals(jdUt + 5.0 / rate, tithi.endJdUt, oneSecond)
        assertEquals(7.0 / 12.0, tithi.elapsedFraction, 1e-9)
    }

    @Test
    fun `karana 60 ends at the seam`() {
        val rate = ephemeris.elongationDegreesPerDay
        val jdUt = ephemeris.jdUtWhenElongation(357.0, baseJdUt)
        val karana = calculator.karanaAt(jdUt)

        assertEquals(59, karana.index)
        assertEquals("Naga", karana.name)
        assertEquals(jdUt - 3.0 / rate, karana.startJdUt, oneSecond)
        assertEquals(jdUt + 3.0 / rate, karana.endJdUt, oneSecond)
    }

    @Test
    fun `nakshatra and yoga boundaries land on their target angles`() {
        // The ayanamsha is not linear, so these have no closed form even with a linear
        // ephemeris. Assert the residual instead: at the reported boundary the defining angle
        // must equal its target to within the angle the body covers in the solver's tolerance.
        val jdUt = baseJdUt + 3.25

        val nakshatra = calculator.nakshatraAt(jdUt)
        val nakshatraTolerance = ephemeris.moonDegreesPerDay * oneSecond
        assertResidual(
            calculator.moonSiderealDeg(nakshatra.startJdUt),
            nakshatra.index * Nakshatra.SPAN_DEGREES,
            nakshatraTolerance,
        )
        assertResidual(
            calculator.moonSiderealDeg(nakshatra.endJdUt),
            ((nakshatra.index + 1) % Nakshatra.COUNT) * Nakshatra.SPAN_DEGREES,
            nakshatraTolerance,
        )

        val yoga = calculator.yogaAt(jdUt)
        val yogaTolerance = ephemeris.yogaSumDegreesPerDay * oneSecond
        assertResidual(
            calculator.yogaSumDeg(yoga.startJdUt),
            yoga.index * Yoga.SPAN_DEGREES,
            yogaTolerance,
        )
        assertResidual(
            calculator.yogaSumDeg(yoga.endJdUt),
            ((yoga.index + 1) % Yoga.COUNT) * Yoga.SPAN_DEGREES,
            yogaTolerance,
        )
    }

    @Test
    fun `a tighter tolerance actually tightens the answer`() {
        // Guards against the tolerance being decorative. The coarse solver is allowed 10 minutes
        // and must land within it; the fine solver must be far closer than that.
        val rate = ephemeris.elongationDegreesPerDay
        val jdUt = ephemeris.jdUtWhenElongation(0.5, baseJdUt)
        val exactEnd = jdUt + 11.5 / rate

        val coarse = PanchangCalculator(ephemeris, boundaryToleranceSeconds = 600.0).tithiAt(jdUt)
        val fine = PanchangCalculator(ephemeris, boundaryToleranceSeconds = 0.001).tithiAt(jdUt)

        assertTrue(abs(coarse.endJdUt - exactEnd) <= 300.0 * oneSecond)
        assertTrue(abs(fine.endJdUt - exactEnd) <= 0.001 * oneSecond)
    }

    private fun assertResidual(actualDeg: Double, targetDeg: Double, toleranceDeg: Double) {
        val residual = abs(TimeScale.angleDifference(actualDeg, targetDeg))
        assertTrue(residual <= toleranceDeg) {
            "angle $actualDeg is $residual deg from target $targetDeg, tolerance $toleranceDeg"
        }
    }
}
