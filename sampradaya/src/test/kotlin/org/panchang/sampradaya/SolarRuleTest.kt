package org.panchang.sampradaya

import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.panchang.core.GeoLocation
import org.panchang.core.PanchangCalculator
import org.panchang.ephemeris.Vsop87Ephemeris

/**
 * The solar machinery behind [EventRule.OnSolarMonth] and [EventRule.In60YearCycle], at
 * Chennai in 2026 — a solar-calendar city for a solar-calendar rule set.
 *
 * Expected-date assertions are windowed, not pinned: this project has no Tamil panchang to
 * serve as an oracle, so the tests assert what the astronomy itself guarantees (which
 * fortnight of which month an ingress falls in) rather than quoting a day nobody has
 * verified against.
 */
class SolarRuleTest {

    // ── OnSolarMonth ────────────────────────────────────────────────────────────────────────

    @Test
    fun `solar months open when the Sun enters the sign`() {
        // Makara Sankranti — Thai Pongal — falls in the middle two weeks of January.
        val makara = dateOf(EventRule.OnSolarMonth(solarMonthIndex = 10, dayOfMonth = 1))
        assertTrue(
            makara.month == Month.JANUARY && makara.dayOfMonth in 13..16,
            "the Sun enters Makara in mid-January, not on $makara",
        )

        // Mesha Sankranti — Puthandu — in mid-April.
        val mesha = dateOf(EventRule.OnSolarMonth(solarMonthIndex = 1, dayOfMonth = 1))
        assertTrue(
            mesha.month == Month.APRIL && mesha.dayOfMonth in 12..16,
            "the Sun enters Mesha in mid-April, not on $mesha",
        )

        // Simha Sankranti — Chingam — in the second half of August.
        val simha = dateOf(EventRule.OnSolarMonth(solarMonthIndex = 5, dayOfMonth = 1))
        assertTrue(
            simha.month == Month.AUGUST && simha.dayOfMonth in 14..21,
            "the Sun enters Simha in the second half of August, not on $simha",
        )
    }

    @Test
    fun `every sign opens once a year, in order, a month apart`() {
        val openings = (1..12).map { sign ->
            dateOf(EventRule.OnSolarMonth(solarMonthIndex = sign, dayOfMonth = 1))
        }
        // All twelve occur within the requested Gregorian year — Mesha is mid-April, so a
        // window running Jan-Dec of the same year holds all of them.
        assertTrue(openings.all { it.year == YEAR }, "openings leaked out of $YEAR: $openings")

        // In date order (the sign order wraps the Gregorian year at Makara), the twelve
        // openings are distinct and each runs 25 to 40 days after the last — the bounds a
        // sign transit of 29.5 to 31.5 days plus slack allows. Eleven consecutive gaps plus
        // twelve distinct openings is a complete cycle.
        val sorted = openings.sorted()
        assertEquals(12, sorted.toSet().size, "sign openings must be distinct: $sorted")
        sorted.zipWithNext().forEach { (earlier, later) ->
            val gap = later.toEpochDay() - earlier.toEpochDay()
            assertTrue(gap in 25..40, "the Sun crosses a sign in 25-40 days, not $gap days")
        }
    }

    @Test
    fun `days of the solar month count from its opening`() {
        val thai1 = dateOf(EventRule.OnSolarMonth(solarMonthIndex = 10, dayOfMonth = 1))
        val thai2 = dateOf(EventRule.OnSolarMonth(solarMonthIndex = 10, dayOfMonth = 2))
        val thai10 = dateOf(EventRule.OnSolarMonth(solarMonthIndex = 10, dayOfMonth = 10))
        assertEquals(1L, thai2.toEpochDay() - thai1.toEpochDay(), "day 2 follows day 1")
        assertEquals(9L, thai10.toEpochDay() - thai1.toEpochDay(), "day 10 is nine days after day 1")
    }

    @Test
    fun `the Bengali month-opening never precedes the ingress day and never lags it by more than a day`() {
        val calculator = index.ctx.calculator
        val site = index.location
        for (sign in 1..12) {
            val ingressDay = dateOf(
                EventRule.OnSolarMonth(sign, 1, SolarTransition.SANKRANTI_START),
            )
            val sunriseDay = dateOf(
                EventRule.OnSolarMonth(sign, 1, SolarTransition.SANKRANTI_AT_SUNRISE),
            )
            val offset = sunriseDay.toEpochDay() - ingressDay.toEpochDay()
            assertTrue(
                offset in 0..1,
                "sign $sign: the first sunrise after an ingress is the ingress day or the " +
                    "next one, not $offset days away ($ingressDay -> $sunriseDay)",
            )
            if (offset == 1L) {
                // Premise: the one-day push must be justified by the ingress falling after
                // that morning's sunrise at this site. The ingress matched here is the one
                // whose civil date *is* the START day — the window can hold two ingresses of
                // the same sign, and only one of them opened this month.
                val sunrise = calculator.sunTimes(ingressDay, site).sunrise.jdUtOrNull
                val ingress = SolarIngressIndex.build(index)
                    .ingressesOf(sign - 1)
                    .firstOrNull { site.localDate(it) == ingressDay }
                assertTrue(
                    sunrise != null && ingress != null && ingress > sunrise,
                    "sign $sign pushed to $sunriseDay without the ingress ($ingress) falling " +
                        "after the $ingressDay sunrise ($sunrise)",
                )
            }
        }
    }

    @Test
    fun `a solar rule explains itself in terms of the sign and the day`() {
        val outcome = resolver(
            EventRule.OnSolarMonth(solarMonthIndex = 10, dayOfMonth = 1),
        ).resolve("probe", index)
        assertTrue(outcome is EventResolution.Resolved)
        val reason = (outcome as EventResolution.Resolved).event.reason
        assertTrue(reason.contains("Makara"), "the reason names the sign: $reason")
        assertTrue(reason.contains("solar month"), "the reason names the reckoning: $reason")
        assertEquals(null, outcome.event.tithi, "a solar rule is not qualified by a tithi")
    }

    // ── In60YearCycle ───────────────────────────────────────────────────────────────────────

    @Test
    fun `the cycle arithmetic round-trips against its published anchor`() {
        // The published cycle years 2024-2026: Krodhi (43), Vishvavasu (44), Parabhava (45).
        assertEquals(43, SixtyYearCycle.cycleYearOf(2024))
        assertEquals(44, SixtyYearCycle.cycleYearOf(2025))
        assertEquals(45, SixtyYearCycle.cycleYearOf(2026))
        // The mapping is many-to-one by construction — 1900 and 1960 open the same cycle
        // position — so inversion is asserted only within the anchor's own cycle
        // (2024–2083), and as an equivalence class beyond it.
        for (year in 2024..2083) {
            assertEquals(
                year,
                SixtyYearCycle.gregorianYearOf(SixtyYearCycle.cycleYearOf(year)),
                "the mapping must invert over $year",
            )
        }
        for (year in listOf(1900, 1964, 2084, 2144)) {
            val roundTrip = SixtyYearCycle.gregorianYearOf(SixtyYearCycle.cycleYearOf(year))
            assertTrue(
                (year - roundTrip) % 60 == 0,
                "outside one anchor cycle the round trip must stay in the same class: " +
                    "$year -> $roundTrip",
            )
        }
    }

    @Test
    fun `a rule held for one cycle year resolves in that year and only that year`() {
        val thisCycleYear = SixtyYearCycle.cycleYearOf(YEAR)
        val base = EventRule.OnSolarMonth(solarMonthIndex = 1, dayOfMonth = 1)
        val held = resolver(EventRule.In60YearCycle(cycleYear = thisCycleYear, baseRule = base))
            .resolve("probe", index)
        assertTrue(held is EventResolution.Resolved, "in its own cycle year it must resolve")

        val other = resolver(
            EventRule.In60YearCycle(cycleYear = thisCycleYear + 1, baseRule = base),
        ).resolve("probe", index)
        assertTrue(other is EventResolution.NoOccurrence)
        assertTrue(
            (other as EventResolution.NoOccurrence).why.contains("cycle year"),
            "the suppression must say it is the cycle that withheld the event: ${other.why}",
        )
    }

    @Test
    fun `a cycle rule cannot nest a rule that needs the catalog around it`() {
        val bad = EventRule.In60YearCycle(
            cycleYear = 1,
            baseRule = EventRule.RelativeTo("probe", 1),
        )
        val threw = org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            EventResolver(listOf(definition("probe", bad)))
        }
        assertTrue(threw.message!!.contains("self-contained"), threw.message)
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────

    private fun resolver(rule: EventRule) = EventResolver(listOf(definition("probe", rule)))

    private fun definition(id: String, rule: EventRule) = EventDefinition(
        id = id,
        name = "probe",
        group = EventGroup.MAJOR_FESTIVAL,
        rule = rule,
        sourceNote = "test probe",
        confidence = RuleConfidence.INFERRED,
    )

    private fun dateOf(rule: EventRule): LocalDate {
        val outcome = resolver(rule).resolve("probe", index)
        assertTrue(
            outcome is EventResolution.Resolved,
            "rule $rule did not resolve in $YEAR: $outcome",
        )
        return (outcome as EventResolution.Resolved).event.date
    }

    private val index: LunarDayIndex get() = CHENNAI_2026

    companion object {
        private const val YEAR = 2026

        /** Built once; an index is the most expensive object this module constructs. */
        private val CHENNAI_2026: LunarDayIndex by lazy {
            LunarDayIndex.build(
                2026,
                ObservanceContext(
                    calculator = PanchangCalculator(Vsop87Ephemeris()),
                    location = GeoLocation(13.0827, 80.2707, ZoneId.of("Asia/Kolkata")),
                ),
            )
        }
    }
}
