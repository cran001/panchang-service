package org.panchang.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.panchang.ephemeris.TimeScale
import kotlin.math.abs

/**
 * Structural properties of the element sequences over a whole lunation.
 *
 * These are the assertions that would have caught a boundary solver that works everywhere
 * except at the seam, or one whose start and end searches disagree: a single wrong boundary
 * shows up as a gap or an overlap somewhere in the tiling.
 */
class TithiPartitionTest {

    private val oneSecond = 1.0 / 86_400.0
    private val ephemeris = LinearEphemeris()
    private val calculator = PanchangCalculator(ephemeris)
    private val start = TimeScale.jdUtAtMidnight(2026, 1, 9) + 0.37

    @Test
    fun `tithis tile the timeline with no gap and no overlap`() {
        var jdUt = start
        var previous: Tithi? = null

        // 33 steps covers a full synodic month and wraps past Amavasya twice over.
        repeat(33) {
            val tithi = calculator.tithiAt(jdUt)

            assertTrue(tithi.endJdUt > tithi.startJdUt) {
                "tithi ${tithi.index} has non-positive duration"
            }
            assertTrue(tithi.startJdUt <= jdUt && jdUt <= tithi.endJdUt) {
                "tithi ${tithi.index} does not contain the instant it was computed for"
            }
            assertTrue(tithi.elapsedFraction >= 0.0 && tithi.elapsedFraction < 1.0) {
                "elapsedFraction ${tithi.elapsedFraction} outside [0, 1)"
            }

            previous?.let { before ->
                assertEquals(before.endJdUt, tithi.startJdUt, 2.0 * oneSecond) {
                    "gap or overlap between tithi ${before.index} and ${tithi.index}"
                }
                assertEquals((before.index + 1) % Tithi.COUNT, tithi.index) {
                    "tithi index jumped from ${before.index} to ${tithi.index}"
                }
            }

            previous = tithi
            // Step just past the reported end. Three seconds clears the solver's own tolerance
            // in either direction, so the next call is unambiguously inside the next tithi.
            jdUt = tithi.endJdUt + 3.0 * oneSecond
        }
    }

    @Test
    fun `every tithi index and name appears exactly once per lunation`() {
        val seen = mutableListOf<Tithi>()
        var jdUt = ephemeris.jdUtWhenElongation(0.1, start)
        repeat(Tithi.COUNT) {
            val tithi = calculator.tithiAt(jdUt)
            seen += tithi
            jdUt = tithi.endJdUt + 3.0 * oneSecond
        }

        assertEquals((0..29).toList(), seen.map { it.index })
        assertEquals("Purnima", seen[14].name)
        assertEquals("Amavasya", seen[29].name)
        assertEquals(15, seen.count { it.paksha == Paksha.SHUKLA })
        assertEquals(15, seen.count { it.paksha == Paksha.KRISHNA })
        assertEquals((1..15).toList(), seen.take(15).map { it.numberInPaksha })
    }

    @Test
    fun `a lunation of tithis spans one synodic month`() {
        val first = calculator.tithiAt(ephemeris.jdUtWhenElongation(0.1, start))
        var jdUt = first.endJdUt + 3.0 * oneSecond
        var last = first
        repeat(Tithi.COUNT - 1) {
            last = calculator.tithiAt(jdUt)
            jdUt = last.endJdUt + 3.0 * oneSecond
        }

        val synodicMonth = 360.0 / ephemeris.elongationDegreesPerDay
        assertEquals(synodicMonth, last.endJdUt - first.startJdUt, 2.0 * oneSecond)
    }

    @Test
    fun `nakshatras and yogas also tile without gaps`() {
        for (spec in listOf(
            ElementSpec("nakshatra", Nakshatra.COUNT) { calculator.nakshatraAt(it) },
            ElementSpec("yoga", Yoga.COUNT) { calculator.yogaAt(it) },
            ElementSpec("karana", Karana.COUNT) { calculator.karanaAt(it) },
        )) {
            var jdUt = start
            var previous: PanchangaElement? = null
            repeat(20) {
                val element = spec.at(jdUt)
                assertTrue(element.endJdUt > element.startJdUt) { "${spec.label} has no duration" }
                previous?.let { before ->
                    assertEquals(before.endJdUt, element.startJdUt, 2.0 * oneSecond) {
                        "gap or overlap in ${spec.label} between ${before.index} and ${element.index}"
                    }
                    assertEquals((before.index + 1) % spec.count, element.index) {
                        "${spec.label} index jumped from ${before.index} to ${element.index}"
                    }
                }
                previous = element
                jdUt = element.endJdUt + 3.0 * oneSecond
            }
        }
    }

    @Test
    fun `elapsed fraction tracks the angle, not the clock`() {
        // The fraction is defined on the swept angle. With a constant-rate ephemeris the two
        // coincide, which is precisely why this check is meaningful here: any off-by-one in the
        // start angle would show up as a constant offset.
        val jdUt = ephemeris.jdUtWhenElongation(200.0, start)
        val tithi = calculator.tithiAt(jdUt)
        assertEquals(16, tithi.index)
        assertEquals((200.0 - 16 * 12.0) / 12.0, tithi.elapsedFraction, 1e-9)

        // The two agree only as well as the boundaries are known. Each is solved to a bracket of
        // one second on a tithi of about 85 000 s, so the fraction inherits roughly 1.2e-5 of
        // slack; 3e-5 covers both boundaries with margin and would still catch an off-by-one
        // division or a boundary taken from the wrong side.
        val elapsedByTime = (jdUt - tithi.startJdUt) / (tithi.endJdUt - tithi.startJdUt)
        assertEquals(tithi.elapsedFraction, elapsedByTime, 3e-5)
    }

    @Test
    fun `elongation is wrap-safe at the seam`() {
        val justBefore = ephemeris.jdUtWhenElongation(359.99, start)
        val justAfter = justBefore + 0.02 / ephemeris.elongationDegreesPerDay

        assertEquals(29, calculator.tithiAt(justBefore).index)
        assertEquals(0, calculator.tithiAt(justAfter).index)
        assertTrue(abs(TimeScale.angleDifference(calculator.elongationDeg(justAfter), 0.0)) < 0.02)
    }

    private class ElementSpec(
        val label: String,
        val count: Int,
        val at: (Double) -> PanchangaElement,
    )
}
