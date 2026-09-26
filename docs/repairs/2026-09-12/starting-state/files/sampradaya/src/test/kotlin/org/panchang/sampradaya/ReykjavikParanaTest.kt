package org.panchang.sampradaya

import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.panchang.core.GeoLocation
import org.panchang.core.PanchangCalculator
import org.panchang.ephemeris.Vsop87Ephemeris

/**
 * The Reykjavik case: a real, *accepted* site whose midsummer civil days have no usable
 * daylight interval, and the regression test for the parana window that used to run 14 hours
 * there.
 *
 * Reykjavik sits at 64.15°N — below the midnight-sun boundary, so its Sun sets every day of
 * the year — but around the June solstice the sunset falls just past civil midnight into the
 * *next* civil day, and the interval `SunTimes.daylightDays` measures inside one civil day
 * stops being a length of daylight (null or negative). That is the regime the old
 * `derivePotentialParana` mishandled: the "first third of daylight" cap was silently skipped
 * and the window ran to whatever bound came next — at this site on 2026-06-26 that was a
 * 14-hour window presented as CONFIRMED.
 *
 * The precondition here is asserted, not assumed, in the same spirit as
 * [ParanaDaylightEdgeTest]: if the astronomy or zone data ever changes so that Reykjavik
 * stops producing these days, this test fails loudly rather than quietly ceasing to cover
 * the branch it exists for.
 */
class ReykjavikParanaTest {

    @Test
    fun `no window at Reykjavik claims a daylight cap the site does not have`() {
        val calculator = index.ctx.calculator
        val decisions = IskconRules().allObservancesInWindow(index)
        assertTrue(decisions.isNotEmpty(), "the fixture must produce decisions to inspect")

        // ── Premise: the solstice fortnight really is the pathological regime. ──────────────
        val midsummer = LocalDate.of(2026, Month.JUNE, 20)..LocalDate.of(2026, Month.JUNE, 30)
        val unusableDays = midsummer.count { day ->
            val daylight = calculator.sunTimes(day, REYKJAVIK).daylightDays
            daylight == null || daylight <= 0.0
        }
        assertTrue(
            unusableDays >= 5,
            "Reykjavik's late-June civil days were expected to have null or negative " +
                "daylight; only $unusableDays of ${midsummer.count { true }} did. The branch " +
                "under test is no longer exercised — find another site rather than deleting " +
                "this.",
        )

        // ── Behaviour: every parana window that lands on such a day is honestly bounded. ────
        var checkedUnusable = 0
        for (decision in decisions) {
            val window = decision.parana ?: continue
            val daylight = calculator.sunTimes(window.date, REYKJAVIK).daylightDays
            if (!(daylight == null || daylight <= 0.0)) continue
            checkedUnusable++
            assertTrue(
                window.endReason != ParanaBoundReason.ONE_THIRD_DAYLIGHT,
                "the window on ${window.date} claims a third of a daylight interval this " +
                    "site does not have: $window",
            )
            assertTrue(
                window.endReason == ParanaBoundReason.DVADASHI_END ||
                    window.endReason == ParanaBoundReason.NAKSHATRA_END,
                "with no daylight cap the only honest bounds are the Dvadashi's end or a " +
                    "qualifying nakshatra's; this window claims ${window.endReason}: $window",
            )
            val sunrise = calculator.sunTimes(window.date, REYKJAVIK).sunrise.jdUtOrNull
            assertNotNull(sunrise, "a window was emitted on a day with no sunrise: $window")
            assertTrue(
                window.endJdUt - sunrise!! < SANE_BOUND_DAYS,
                "the window on ${window.date} runs " +
                    "${(window.endJdUt - sunrise) * 24.0} hours — the 14-hour regression this " +
                    "test exists to catch: $window",
            )
        }
        assertTrue(
            checkedUnusable > 0,
            "no parana window landed on an unusable-daylight day in 2026, so the behaviour " +
                "half of this test covered nothing; re-examine the fixture",
        )
    }

    @Test
    fun `the 2026-06-26 window is bounded by the Dvadashi, not by a daylight it does not have`() {
        // The exact case the expansion guide filed against the old code: a parana falling on
        // 2026-06-26 at Reykjavik, where the civil sunset precedes the civil sunrise around the
        // solstice and the daylight-third cap is unavailable. The old window ran 834 minutes
        // with the cap silently skipped; the honest window is the *same interval* — sunrise to
        // the Dvadashi's end — carrying the bound it is actually built on.
        val decisions = IskconRules().allObservancesInWindow(index)
        val decision = decisions.firstOrNull { it.parana?.date == LocalDate.of(2026, Month.JUNE, 26) }
        assertNotNull(
            decision,
            "a fast of 2026-06-25 with its parana on the 26th exists at this site; without it " +
                "the named regression case cannot be inspected",
        )
        val window = decision!!.parana!!
        assertEquals(
            ParanaBoundReason.DVADASHI_END,
            window.endReason,
            "the only tradition rule that can bound this window is the Dvadashi's end",
        )
        assertEquals(LocalDate.of(2026, Month.JUNE, 25), decision.date)
        assertTrue(
            window.durationMinutes in 10.0 * 60.0..16.0 * 60.0,
            "the window runs from sunrise to a mid-afternoon Dvadashi end, roughly 14 hours — " +
                "not the ~5 hours a real daylight third would give, and not an unbounded " +
                "sentinel either: ${window.durationMinutes} minutes",
        )
    }

    @Test
    fun `a window that cannot be capped is refused with a sentence, never truncated or invented`() {
        // At Reykjavik itself every 2026 fortnight found a Dvadashi or nakshatra bound, so the
        // refusal path is exercised elsewhere (ParanaDaylightEdgeTest, at sites where no cap
        // survives); this guards the *shape* of any refusal that does occur here.
        for (decision in IskconRules().allObservancesInWindow(index).filter { it.parana == null }) {
            assertTrue(
                decision.reason.contains("No parana window is given"),
                "an absent window must say why: ${decision.reason}",
            )
            assertTrue(
                decision.confidence == RuleConfidence.INFERRED,
                "a refused window is a stated gap, not a confirmed answer",
            )
        }
    }

    private val index: LunarDayIndex get() = REYKJAVIK_2026

    private fun ClosedRange<LocalDate>.count(predicate: (LocalDate) -> Boolean): Int {
        var total = 0
        var day = start
        while (!day.isAfter(endInclusive)) {
            if (predicate(day)) total++
            day = day.plusDays(1)
        }
        return total
    }

    companion object {
        private const val SANE_BOUND_DAYS = 1.5

        private val REYKJAVIK =
            GeoLocation(64.1466, -21.9426, ZoneId.of("Atlantic/Reykjavik"))

        /** Built once; an index is the most expensive object this module constructs. */
        private val REYKJAVIK_2026: LunarDayIndex by lazy {
            LunarDayIndex.build(
                2026,
                ObservanceContext(
                    calculator = PanchangCalculator(Vsop87Ephemeris()),
                    location = REYKJAVIK,
                ),
            )
        }
    }
}
