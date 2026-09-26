package org.panchang.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.panchang.ephemeris.TimeScale
import kotlin.math.abs

/**
 * Lunar month naming under both reckonings, and adhika maasa detection.
 *
 * The reckoning is a parameter everywhere it appears. Nothing in this module picks one for you,
 * because the two disagree about the name of every Krishna paksha and a downstream rule quoted
 * as "Krishna Ekadashi of Margashirsha" therefore means different fortnights in Gujarat and in
 * Uttar Pradesh.
 */
class LunarMonthTest {

    private val oneSecond = 1.0 / 86_400.0
    private val ephemeris = LinearEphemeris()
    private val calculator = PanchangCalculator(ephemeris)
    private val start = TimeScale.jdUtAtMidnight(2026, 1, 9) + 0.37

    /** Synodic month implied by the fake ephemeris, in days. */
    private val synodicMonth = 360.0 / ephemeris.elongationDegreesPerDay

    @Test
    fun `a lunar month spans exactly one synodic month and contains its instant`() {
        var jdUt = start
        repeat(6) {
            for (reckoning in MonthReckoning.entries) {
                val month = calculator.lunarMonthAt(jdUt, reckoning)
                assertEquals(synodicMonth, month.endJdUt - month.startJdUt, 4.0 * oneSecond) {
                    "$reckoning month length is not one lunation"
                }
                assertTrue(month.startJdUt <= jdUt && jdUt < month.endJdUt) {
                    "$reckoning month does not contain the instant it was computed for"
                }
                assertEquals(LunarMonth.NAMES.size, 12)
            }
            jdUt += 7.0
        }
    }

    @Test
    fun `the two reckonings agree during shukla paksha and differ during krishna`() {
        // Shukla: elongation in [0, 180). Both conventions are inside the same named month.
        val shukla = ephemeris.jdUtWhenElongation(60.0, start)
        val amantaShukla = calculator.lunarMonthAt(shukla, MonthReckoning.AMANTA)
        val purnimantaShukla = calculator.lunarMonthAt(shukla, MonthReckoning.PURNIMANTA)
        assertEquals(Paksha.SHUKLA, amantaShukla.paksha)
        assertEquals(Paksha.SHUKLA, purnimantaShukla.paksha)
        assertEquals(amantaShukla.index, purnimantaShukla.index) {
            "the reckonings must name the same month during shukla paksha"
        }

        // Krishna: elongation in [180, 360). Purnimanta is already one month ahead.
        val krishna = ephemeris.jdUtWhenElongation(240.0, start)
        val amantaKrishna = calculator.lunarMonthAt(krishna, MonthReckoning.AMANTA)
        val purnimantaKrishna = calculator.lunarMonthAt(krishna, MonthReckoning.PURNIMANTA)
        assertEquals(Paksha.KRISHNA, amantaKrishna.paksha)
        assertEquals(Paksha.KRISHNA, purnimantaKrishna.paksha)
        assertEquals((amantaKrishna.index + 1) % 12, purnimantaKrishna.index) {
            "purnimanta krishna paksha belongs to the following month"
        }
        assertNotEquals(amantaKrishna.name, purnimantaKrishna.name)
    }

    @Test
    fun `month bounds are the phases the reckoning is named for`() {
        val jdUt = ephemeris.jdUtWhenElongation(240.0, start)

        val amanta = calculator.lunarMonthAt(jdUt, MonthReckoning.AMANTA)
        for (bound in listOf(amanta.startJdUt, amanta.endJdUt)) {
            val elongation = TimeScale.angleDifference(calculator.elongationDeg(bound), 0.0)
            assertTrue(abs(elongation) < 1e-3) { "amanta bound is not a new moon: $elongation deg" }
        }

        val purnimanta = calculator.lunarMonthAt(jdUt, MonthReckoning.PURNIMANTA)
        for (bound in listOf(purnimanta.startJdUt, purnimanta.endJdUt)) {
            val elongation = TimeScale.angleDifference(calculator.elongationDeg(bound), 180.0)
            assertTrue(abs(elongation) < 1e-3) { "purnimanta bound is not a full moon: $elongation deg" }
        }
    }

    @Test
    fun `paksha follows the elongation, not the month index`() {
        for (elongationDeg in listOf(0.5, 90.0, 179.0, 181.0, 270.0, 359.5)) {
            val jdUt = ephemeris.jdUtWhenElongation(elongationDeg, start)
            val expected = if (elongationDeg < 180.0) Paksha.SHUKLA else Paksha.KRISHNA
            assertEquals(expected, calculator.lunarMonthAt(jdUt).paksha) {
                "wrong paksha at elongation $elongationDeg"
            }
        }
    }

    @Test
    fun `an adhika month occurs and is followed by the nija month of the same name`() {
        // A lunation is 29.53 days and a solar month 30.44, so the lunar year runs 0.37 months
        // short and an intercalary month must appear roughly every 2.7 years. Walking six years
        // must therefore find at least one — if none appears, the ingress test is not firing.
        val months = walkMonths(start, years = 6)
        val adhikaPositions = months.indices.filter { months[it].isAdhika }

        assertTrue(adhikaPositions.isNotEmpty()) { "no adhika maasa in six years" }
        assertTrue(adhikaPositions.size <= 4) {
            "found ${adhikaPositions.size} adhika months in six years, which is far too many"
        }

        for (position in adhikaPositions) {
            val adhika = months[position]
            assertTrue(adhika.name.startsWith("Adhika ")) { "adhika month not labelled: ${adhika.name}" }

            // No solar rashi ingress inside an adhika month: that is the definition.
            assertEquals(
                Rashi.indexOf(calculator.sunSiderealDeg(adhika.startJdUt)),
                Rashi.indexOf(calculator.sunSiderealDeg(adhika.endJdUt - oneSecond)),
            ) { "the Sun changed rashi inside a month reported as adhika" }

            val next = months.getOrNull(position + 1) ?: continue
            assertFalse(next.isAdhika) { "two consecutive adhika months" }
            assertEquals(adhika.index, next.index) {
                "the nija month following an adhika month carries the same name"
            }
            assertEquals(LunarMonth.NAMES[adhika.index], next.name)
        }
    }

    @Test
    fun `ordinary months contain exactly one solar rashi ingress`() {
        for (month in walkMonths(start, years = 2)) {
            val rashiAtStart = Rashi.indexOf(calculator.sunSiderealDeg(month.startJdUt))
            val rashiAtEnd = Rashi.indexOf(calculator.sunSiderealDeg(month.endJdUt - oneSecond))
            if (month.isAdhika) {
                assertEquals(rashiAtStart, rashiAtEnd)
            } else {
                assertEquals((rashiAtStart + 1) % Rashi.COUNT, rashiAtEnd) {
                    "an ordinary month must contain exactly one ingress"
                }
                assertEquals(rashiAtEnd, month.index) {
                    "the month is named for the rashi the Sun enters during it"
                }
            }
        }
    }

    @Test
    fun `month indices advance by one except across an adhika month`() {
        val months = walkMonths(start, years = 4)
        months.zipWithNext { before, after ->
            val expected = if (before.isAdhika) before.index else (before.index + 1) % 12
            assertEquals(expected, after.index) {
                "month index went ${before.index} (adhika=${before.isAdhika}) -> ${after.index}"
            }
            assertEquals(before.endJdUt, after.startJdUt, 4.0 * oneSecond) {
                "gap or overlap between consecutive lunar months"
            }
        }
    }

    @Test
    fun `the instance default reckoning is used only when none is passed`() {
        val purnimantaCalculator = PanchangCalculator(ephemeris, reckoning = MonthReckoning.PURNIMANTA)
        val jdUt = ephemeris.jdUtWhenElongation(240.0, start)

        assertEquals(MonthReckoning.AMANTA, calculator.lunarMonthAt(jdUt).reckoning)
        assertEquals(MonthReckoning.PURNIMANTA, purnimantaCalculator.lunarMonthAt(jdUt).reckoning)

        // An explicit argument overrides the instance default in both directions.
        assertEquals(
            calculator.lunarMonthAt(jdUt, MonthReckoning.PURNIMANTA),
            purnimantaCalculator.lunarMonthAt(jdUt),
        )
        assertEquals(
            calculator.lunarMonthAt(jdUt),
            purnimantaCalculator.lunarMonthAt(jdUt, MonthReckoning.AMANTA),
        )
    }

    @Test
    fun `new and full moons alternate and are half a lunation apart`() {
        var jdUt = start
        repeat(5) {
            val newMoon = calculator.previousNewMoon(jdUt)
            val fullMoon = calculator.previousFullMoon(jdUt)
            assertTrue(newMoon <= jdUt && fullMoon <= jdUt)
            val separation = abs(newMoon - fullMoon)
            assertEquals(synodicMonth / 2.0, separation, 4.0 * oneSecond) {
                "a new moon and the adjacent full moon are half a lunation apart"
            }
            jdUt += 7.3
        }
    }

    /** Successive amanta months covering [years] years from [fromJdUt]. */
    private fun walkMonths(fromJdUt: Double, years: Int): List<LunarMonth> {
        val months = mutableListOf<LunarMonth>()
        var jdUt = fromJdUt
        val until = fromJdUt + years * 365.25
        while (jdUt < until) {
            val month = calculator.lunarMonthAt(jdUt, MonthReckoning.AMANTA)
            months += month
            jdUt = month.endJdUt + 3.0 * oneSecond
        }
        return months
    }
}
