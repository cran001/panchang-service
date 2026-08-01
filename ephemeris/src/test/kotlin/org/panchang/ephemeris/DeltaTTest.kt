package org.panchang.ephemeris

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.abs

@DisplayName("ΔT (Espenak & Meeus piecewise fit)")
class DeltaTTest {

    private val ephemeris = MeeusEphemeris()

    /**
     * Anchor values. These are not precision tests — they exist to catch a branch boundary
     * written with the wrong comparison, which would silently evaluate the neighbouring
     * polynomial and be off by tens of seconds.
     *
     * The reference values are the observed/published ΔT for those epochs, not the
     * polynomial's own output, so the tolerances also encode how well the fit does:
     *
     * - 1900: −2.8 s. The 1900–1920 branch has this as its constant term, so ±0.5 s.
     * - 1950: 29 s. The 1941–1961 branch is centred on 1950 with constant 29.07, ±1 s.
     * - 2000: 63.8 s. The 1986–2005 branch is centred on 2000 with constant 63.86, ±0.5 s.
     * - 2020: 69.4 s observed. The 2005–2050 branch is an extrapolation made in 2006 and
     *   returns ≈71.6 s, so it is ~2.2 s high. ±3 s admits that known bias; tightening it
     *   would require replacing the published fit with an unsourced correction. This is
     *   documented on [DeltaT].
     */
    @Test
    fun `anchor values`() {
        assertEquals(-2.8, DeltaT.secondsAtYear(1900.0), 0.5, "ΔT(1900)")
        assertEquals(29.0, DeltaT.secondsAtYear(1950.0), 1.0, "ΔT(1950)")
        assertEquals(63.8, DeltaT.secondsAtYear(2000.0), 0.5, "ΔT(2000)")
        assertEquals(69.4, DeltaT.secondsAtYear(2020.0), 3.0, "ΔT(2020)")
    }

    /**
     * The 2005–2050 branch is known to run high against observation. Pin the size and sign
     * of that bias so that if someone later swaps the polynomial set the change is visible
     * rather than silent.
     */
    @Test
    fun `post-2005 branch is a known-high extrapolation`() {
        val bias = DeltaT.secondsAtYear(2020.0) - 69.36
        assertTrue(
            bias > 1.0 && bias < 4.0,
            "expected the Espenak-Meeus 2005-2050 extrapolation to sit 1-4 s above the " +
                "observed ΔT(2020) = 69.36 s, measured $bias s",
        )
    }

    /**
     * No polynomial piece may hand over to the next with a visible step.
     *
     * This is the test that catches the classic failure mode: a coefficient typed wrong, or
     * a branch whose independent variable is offset from the wrong origin. Such an error
     * usually leaves the polynomial looking plausible in the middle of its range while
     * opening a large step at one end.
     *
     * The threshold is 1 s. The published fit's real steps are all well under that — the
     * largest, at 1600, is about 0.25 s — while any realistic transcription error produces
     * a step of many seconds or worse. A per-boundary message identifies the culprit.
     */
    @Test
    fun `continuity across every piece boundary`() {
        val boundaries = listOf(
            -500.0, 500.0, 1600.0, 1700.0, 1800.0, 1860.0, 1900.0,
            1920.0, 1941.0, 1961.0, 1986.0, 2005.0, 2050.0, 2150.0,
        )
        val eps = 1e-6
        for (b in boundaries) {
            val below = DeltaT.secondsAtYear(b - eps)
            val above = DeltaT.secondsAtYear(b + eps)
            val step = abs(above - below)
            assertTrue(
                step < 1.0,
                "ΔT steps by $step s at the year $b boundary (below=$below, above=$above)",
            )
        }
    }

    /**
     * The fit must also stay smooth *within* pieces and not blow up anywhere in its stated
     * −1999..+3000 validity range. A sign error in a high-order coefficient typically shows
     * up as an absurd excursion rather than a boundary step.
     */
    @Test
    fun `stays within physically plausible bounds over the full validity range`() {
        var year = -1999.0
        while (year <= 3000.0) {
            val dt = DeltaT.secondsAtYear(year)
            assertTrue(
                dt > -100.0 && dt < 200_000.0,
                "ΔT($year) = $dt s is outside any plausible range for the fit",
            )
            year += 0.25
        }
    }

    /**
     * ΔT is monotonically increasing from the mid-20th century onward in this fit, and
     * changes slowly. Step through year by year and assert the derivative stays small —
     * a large single-year jump means a branch is being entered with a wrong origin.
     */
    @Test
    fun `year-on-year change stays small across the modern era`() {
        var year = 1700.0
        while (year < 2200.0) {
            val step = abs(DeltaT.secondsAtYear(year + 1.0) - DeltaT.secondsAtYear(year))
            assertTrue(step < 5.0, "ΔT changes by $step s between $year and ${year + 1}")
            year += 1.0
        }
    }

    /**
     * Decimal year conversion is continuous and lands on the right calendar year.
     *
     * The convention deliberately departs from Espenak & Meeus' month-midpoint staircase —
     * see [DeltaT.decimalYear] — so the assertion is that each JD maps within a couple of
     * days of the true fractional position in its year, not that it matches `(m − 0.5)/12`.
     * Two days of argument is ~0.003 s of ΔT.
     */
    @Test
    fun `decimal year conversion`() {
        // J2000.0 is 2000-01-01.5, i.e. half a day into the year.
        assertEquals(2000.0 + 0.5 / 366.0, DeltaT.decimalYear(TimeScale.J2000), 3.0 / 365.25)

        // 1970-01-01T00:00Z, the Unix epoch.
        assertEquals(1970.0, DeltaT.decimalYear(TimeScale.UNIX_EPOCH_JD), 3.0 / 365.25)

        // 1992-04-12.0 TD, JDE 2448724.5 (Meeus Example 47.a): day 103 of a leap year.
        assertEquals(1992.0 + 102.0 / 366.0, DeltaT.decimalYear(2448724.5), 3.0 / 365.25)

        // Built from a calendar date, so a bug in either direction shows up.
        assertEquals(
            2026.0 + 212.0 / 365.0,
            DeltaT.decimalYear(TimeScale.jdUtAtMidnight(2026, 8, 1)),
            3.0 / 365.25,
        )

        // Exactly one Julian year of JD must advance the argument by exactly one year, and
        // the mapping must be strictly increasing — ΔT is bisected through, so a staircase
        // or a reversal here would break boundary solving downstream.
        assertEquals(1.0, DeltaT.decimalYear(2451545.0 + 365.25) - DeltaT.decimalYear(2451545.0), 1e-9)
        var jd = 2415020.5
        var previous = DeltaT.decimalYear(jd)
        repeat(1000) {
            jd += 0.017
            val current = DeltaT.decimalYear(jd)
            assertTrue(current > previous, "decimalYear must be strictly increasing at $jd")
            previous = current
        }
    }

    /**
     * ΔT as a function of JD must be continuous and, across the modern era, strictly
     * increasing at half-day resolution.
     *
     * This is the property that Espenak & Meeus' own month-midpoint argument convention
     * would break, and the reason [DeltaT.decimalYear] departs from it: a month-midpoint
     * argument makes ΔT piecewise constant within each month, which fails the strict
     * increase below on 59 out of every 60 samples. See the KDoc there.
     *
     * The one step larger than 0.01 s in the 1990–2040 range is the published fit's own
     * ~0.05 s *downward* discontinuity where the 1986–2005 branch hands over to the
     * 2005–2050 branch. That is inherent to the source, so the strict-increase sweep starts
     * after it; the bounded-step sweep spans the crossover and its threshold sits just
     * above it.
     */
    @Test
    fun `deltaT is continuous and strictly increasing in julian day`() {
        var jd = TimeScale.jdUtAtMidnight(1990, 1, 1)
        val end = TimeScale.jdUtAtMidnight(2040, 1, 1)
        var previous = DeltaT.secondsAtJd(jd)
        while (jd < end) {
            jd += 0.5
            val current = DeltaT.secondsAtJd(jd)
            assertTrue(
                abs(current - previous) < 0.06,
                "ΔT jumps by ${abs(current - previous)} s across half a day at jd $jd",
            )
            previous = current
        }

        jd = TimeScale.jdUtAtMidnight(2010, 1, 1)
        previous = DeltaT.secondsAtJd(jd)
        while (jd < end) {
            jd += 0.5
            val current = DeltaT.secondsAtJd(jd)
            assertTrue(
                current > previous,
                "ΔT must increase strictly inside the 2005-2050 branch; it was flat or " +
                    "falling at jd $jd, which is the signature of a quantised argument",
            )
            previous = current
        }
    }

    /**
     * The interface method must agree with the underlying function and be independent of
     * the host clock or zone.
     */
    @Test
    fun `interface method delegates to the same fit`() {
        val jd = TimeScale.jdUtAtMidnight(2026, 8, 1)
        assertEquals(DeltaT.secondsAtJd(jd), ephemeris.deltaT(jd), 0.0)
        assertEquals(DeltaT.secondsAtYear(DeltaT.decimalYear(jd)), ephemeris.deltaT(jd), 0.0)

        // Present-day ΔT. Observation is ~69.3 s; this fit's extrapolation returns ~75.4 s
        // for mid-2026, a known +6 s bias documented on DeltaT. The window below spans both
        // so it does not have to be moved when the fit is eventually replaced, while still
        // failing loudly if the whole UT/TT bridge breaks.
        assertTrue(
            ephemeris.deltaT(jd) in 65.0..80.0,
            "present-day ΔT came out ${ephemeris.deltaT(jd)} s; expected 65-80 s",
        )
    }
}
