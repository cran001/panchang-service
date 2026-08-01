package org.panchang.ephemeris

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * ΔT from observed IERS Earth orientation.
 *
 * A ΔT table of wrong numbers is the most damaging thing that could be put in this
 * repository: it is plausible-looking data that silently shifts every date the service
 * ever publishes, and nothing downstream would notice. So the tests here are mostly about
 * *provenance and derivation* rather than tolerance — is this the file that was retrieved,
 * was the leap-second arithmetic done, does it reproduce numbers published elsewhere.
 */
@DisplayName("ΔT — observed IERS Earth orientation")
class ObservedDeltaTTest {

    /**
     * The two ΔT values a reader can check against an outside source without reading any
     * of this code.
     *
     * These are the load-bearing assertions of the whole ΔT change, because they test the
     * *derivation* — `ΔT = 32.184 + (TAI−UTC) − (UT1−UTC)` — and not just the plumbing.
     * The IERS publishes UT1−UTC, never ΔT, so getting from one to the other requires the
     * leap-second table, and there is exactly one way to be wrong by a whole second.
     *
     * - **ΔT(2000-01-01) = 63.83 s.** Published widely; it is also, independently, the
     *   constant term of Espenak & Meeus' 1986–2005 branch (63.86 at 2000.0). Agreement to
     *   0.05 s between a fit and a measurement that share no arithmetic is a strong signal.
     * - **ΔT(2020-01-01) = 69.36 s.** The value the pre-existing `DeltaTTest` already
     *   quotes as observed, written there before this table existed.
     *
     * If the leap-second table were off by one entry, or a single modern leap-second count
     * were applied to the whole history, one or both of these would miss by ≈1 s — and a
     * 1 s error looks like nothing until you notice it is in every timestamp.
     */
    @Test
    fun `reproduces independently published deltaT values`() {
        assertEquals(
            63.83,
            DeltaT.secondsAtJd(TimeScale.jdUtAtMidnight(2000, 1, 1)),
            0.02,
            "ΔT(2000-01-01)",
        )
        assertEquals(
            69.36,
            DeltaT.secondsAtJd(TimeScale.jdUtAtMidnight(2020, 1, 1)),
            0.02,
            "ΔT(2020-01-01)",
        )
    }

    /**
     * The bias this whole change exists to remove.
     *
     * Espenak & Meeus' 2005–2050 branch returns ≈75.4 s for 2026-08-01; observed is
     * ≈69.17 s. Assert both that the composed function now returns the observed value and
     * that it is materially below the polynomial — so that if someone re-wires
     * [DeltaT.secondsAtJd] back to the fit, this fails loudly instead of the service
     * quietly regaining a six-second offset.
     */
    @Test
    fun `the stale Espenak-Meeus post-2005 bias is gone`() {
        val jd = TimeScale.jdUtAtMidnight(2026, 8, 1)
        val observed = DeltaT.secondsAtJd(jd)
        val fit = DeltaT.secondsAtYear(DeltaT.decimalYear(jd))

        assertEquals(69.17, observed, 0.05, "observed ΔT at 2026-08-01")
        assertTrue(
            fit - observed > 5.0,
            "expected the Espenak-Meeus fit ($fit s) to sit >5 s above the observed value " +
                "($observed s); if it does not, secondsAtJd is probably still using the fit",
        )
    }

    /** The seam between measured and modelled is where the tolerance has to widen. */
    @Test
    fun `quality bands are reported at the right dates`() {
        assertEquals(
            DeltaTQuality.HISTORICAL_FIT,
            DeltaT.qualityAtJd(TimeScale.jdUtAtMidnight(1950, 6, 1)),
        )
        assertEquals(
            DeltaTQuality.HISTORICAL_FIT,
            DeltaT.qualityAtJd(TimeScale.jdUtAtMidnight(1972, 12, 31)),
        )
        assertEquals(
            DeltaTQuality.OBSERVED,
            DeltaT.qualityAtJd(TimeScale.jdUtAtMidnight(2000, 1, 1)),
        )
        assertEquals(
            DeltaTQuality.OBSERVED,
            DeltaT.qualityAtJd(ObservedDeltaT.observedThroughMjd + 2_400_000.5),
        )
        assertEquals(
            DeltaTQuality.PREDICTED,
            DeltaT.qualityAtJd(ObservedDeltaT.observedThroughMjd + 2_400_000.5 + 1.0),
        )
        assertEquals(
            DeltaTQuality.PREDICTED,
            DeltaT.qualityAtJd(ObservedDeltaT.predictedThroughMjd + 2_400_000.5),
        )
        assertEquals(
            DeltaTQuality.EXTRAPOLATED,
            DeltaT.qualityAtJd(ObservedDeltaT.predictedThroughMjd + 2_400_000.5 + 1.0),
        )
        assertEquals(
            DeltaTQuality.EXTRAPOLATED,
            DeltaT.qualityAtJd(TimeScale.jdUtAtMidnight(2100, 1, 1)),
        )
    }

    /**
     * No step at either seam, at any resolution a bisection could straddle.
     *
     * This is the property that makes ΔT safe to bisect through. `:core` finds every
     * boundary time — tithi, nakshatra, sunrise — by searching civil time, and civil time
     * reaches the series only via `jdTt = jdUt + ΔT/86400`. A discontinuity of any size is
     * a point where that search can fail to converge.
     *
     * Both seams are continuous *by construction* — the pre-1973 taper is defined to
     * reproduce the first tabulated value at the seam and the post-record parabola to
     * reproduce the last — so the assertion is at 1 ms, which is three orders below the
     * table's own 6.8 ms downsampling residual and would catch an off-by-one in the
     * anchoring immediately.
     */
    @Test
    fun `no step at the fit-to-observed or observed-to-extrapolated seams`() {
        for (seamMjd in listOf(ObservedDeltaT.firstMjd, ObservedDeltaT.predictedThroughMjd)) {
            val jd = seamMjd + 2_400_000.5
            for (eps in listOf(1e-6, 1e-3, 1e-1)) {
                val below = DeltaT.secondsAtJd(jd - eps)
                val above = DeltaT.secondsAtJd(jd + eps)
                assertTrue(
                    abs(above - below) < 1e-3,
                    "ΔT steps by ${abs(above - below)} s across MJD $seamMjd at eps=$eps " +
                        "(below=$below above=$above)",
                )
            }
        }
    }

    /**
     * Sweep every half day from 1900 to 2200 and assert ΔT never jumps.
     *
     * Two thresholds, because two different things govern the two eras:
     *
     * - **1973 onward — 5 ms.** Here ΔT is the IERS table plus this module's own
     *   continuation, and both are ours to make smooth. Real ΔT changes by about 1 ms/day,
     *   so 5 ms per half-day is a factor of ten of headroom and still tight enough to catch
     *   a leap-second staircase, which would show up as a clean 1000 ms step.
     * - **Before 1973 — 50 ms.** Here ΔT is the Espenak & Meeus fit, and the *published fit
     *   itself* steps at its branch boundaries: 0.030 s at 1961, and up to ~0.25 s at 1600.
     *   Those steps are inherited from the source, are documented on the pre-existing
     *   `DeltaTTest.continuity across every piece boundary`, and are not this change's to
     *   fix. 50 ms bounds every boundary in the 1900–1973 window while still being 20×
     *   below the 1 s that test allows.
     */
    @Test
    fun `deltaT has no discontinuity anywhere from 1900 to 2200`() {
        fun worstStep(from: Double, to: Double): Pair<Double, Double> {
            var jd = from
            var previous = DeltaT.secondsAtJd(jd)
            var worst = 0.0
            var worstJd = jd
            while (jd < to) {
                jd += 0.5
                val current = DeltaT.secondsAtJd(jd)
                val step = abs(current - previous)
                if (step > worst) {
                    worst = step
                    worstJd = jd
                }
                previous = current
            }
            return worst to worstJd
        }

        val seam = ObservedDeltaT.firstMjd + 2_400_000.5
        val (fitWorst, fitJd) = worstStep(TimeScale.jdUtAtMidnight(1900, 1, 1), seam)
        assertTrue(
            fitWorst < 0.05,
            "largest half-day ΔT step in the Espenak-Meeus era was $fitWorst s near jd $fitJd",
        )

        val (tableWorst, tableJd) = worstStep(seam, TimeScale.jdUtAtMidnight(2200, 1, 1))
        assertTrue(
            tableWorst < 0.005,
            "largest half-day ΔT step from 1973 on was $tableWorst s near jd $tableJd",
        )
    }

    /**
     * ΔT is *not* monotonic, and the table must show that.
     *
     * Since about 2020 the Earth has been rotating slightly faster and ΔT has been falling:
     * 69.36 s at 2020-01-01 against 69.17 s at the end of the observed record in 2026. Any
     * model that only ever increases — as every ΔT polynomial does — cannot be reproducing
     * observation. This test therefore asserts the *decrease*, as evidence that the table
     * really is measurement and not a fit in disguise.
     */
    @Test
    fun `the recent observed decrease in deltaT is present`() {
        val y2020 = DeltaT.secondsAtJd(TimeScale.jdUtAtMidnight(2020, 1, 1))
        val y2026 = DeltaT.secondsAtJd(TimeScale.jdUtAtMidnight(2026, 7, 1))
        assertTrue(
            y2026 < y2020,
            "ΔT was $y2020 s at 2020 and $y2026 s at mid-2026; the IERS record decreases " +
                "over that span and a table that does not is not the IERS record",
        )
        assertTrue(y2020 - y2026 < 1.0, "the decrease should be ~0.2 s, measured ${y2020 - y2026}")
    }

    /**
     * The far-future continuation must be a gentle parabola, not the 2006 polynomial and
     * not a runaway.
     *
     * Espenak & Meeus give ≈203 s at 2100; this module gives ≈88 s. The gap is 115 s and it
     * is not an astronomy disagreement — it is that their extrapolation was anchored to a
     * 2006-era slope that observation has since contradicted. The bounds below are wide on
     * purpose: 60–130 s admits any honest re-anchoring of the record's end but excludes
     * both the stale polynomial and a sign error.
     */
    @Test
    fun `far-future extrapolation is anchored to the observed record`() {
        val y2100 = DeltaT.secondsAtJd(TimeScale.jdUtAtMidnight(2100, 1, 1))
        assertTrue(
            y2100 in 60.0..130.0,
            "ΔT(2100) = $y2100 s is outside the 60-130 s band a continuation anchored to " +
                "the 2027 record end can produce",
        )
        val espenakMeeus = DeltaT.secondsAtYear(2100.0)
        assertTrue(
            espenakMeeus - y2100 > 50.0,
            "expected a large gap to the Espenak-Meeus extrapolation ($espenakMeeus s); if " +
                "there is not one, secondsAtJd has fallen back to the polynomial",
        )
        // Curvature must be the tidal term and nothing larger: over 100 years the parabola
        // may add at most a few tens of seconds beyond the linear part.
        val y2200 = DeltaT.secondsAtJd(TimeScale.jdUtAtMidnight(2200, 1, 1))
        assertTrue(y2200 > y2100, "the continuation must eventually rise (tidal braking)")
        assertTrue(y2200 - y2100 < 200.0, "century-on-century growth of ${y2200 - y2100} s is too fast")
    }

    /** Pre-1973 must still be the published fit, essentially unmodified. */
    @Test
    fun `before the IERS record the Espenak-Meeus fit is used`() {
        for (year in listOf(1600, 1800, 1900, 1950)) {
            val jd = TimeScale.jdUtAtMidnight(year, 6, 1)
            assertEquals(
                DeltaT.secondsAtYear(DeltaT.decimalYear(jd)),
                DeltaT.secondsAtJd(jd),
                0.1,
                "ΔT($year) should be the Espenak-Meeus fit to within the seam taper",
            )
        }
        // More than SEAM_TAPER_YEARS before the record it must be the fit exactly.
        val old = TimeScale.jdUtAtMidnight(1940, 1, 1)
        assertEquals(DeltaT.secondsAtYear(DeltaT.decimalYear(old)), DeltaT.secondsAtJd(old), 1e-12)
    }

    /**
     * The resource is the file that was retrieved, and says so.
     *
     * A committed data file with no provenance is indistinguishable from a made-up one, so
     * the header fields are not optional decoration — they are the only way a later reader
     * can re-derive or refute the table. Assert they are present and well-formed.
     */
    @Test
    fun `the table carries its provenance`() {
        assertEquals(200, ObservedDeltaT.httpStatus)
        assertTrue(
            ObservedDeltaT.sourceUrl.startsWith("https://datacenter.iers.org/"),
            "unexpected source URL '${ObservedDeltaT.sourceUrl}'",
        )
        assertTrue(
            Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$").matches(ObservedDeltaT.retrievedUtc),
            "retrieval timestamp '${ObservedDeltaT.retrievedUtc}' is not ISO-8601 UTC",
        )
        assertTrue(
            Regex("^[0-9a-f]{64}$").matches(ObservedDeltaT.sourceSha256),
            "source SHA-256 '${ObservedDeltaT.sourceSha256}' is not a hex digest",
        )
        assertTrue(ObservedDeltaT.nodeCount > 600, "only ${ObservedDeltaT.nodeCount} table rows")
        assertTrue(
            ObservedDeltaT.observedThroughMjd < ObservedDeltaT.predictedThroughMjd,
            "the observed record must end before the prediction window does",
        )
        assertTrue(
            ObservedDeltaT.firstMjd < ObservedDeltaT.observedThroughMjd,
            "the table must start before the end of the observed record",
        )
    }

    /** MJD conversion, which everything above depends on. J2000 is MJD 51544.5. */
    @Test
    fun `modified julian date conversion`() {
        assertEquals(51544.5, ObservedDeltaT.modifiedJulianDate(TimeScale.J2000), 1e-9)
        assertEquals(41317.0, ObservedDeltaT.modifiedJulianDate(TimeScale.jdUtAtMidnight(1972, 1, 1)), 1e-9)
    }

    /** [Ephemeris.deltaT] on both implementations must be the composed function. */
    @Test
    fun `both ephemerides expose the same deltaT`() {
        val jd = TimeScale.jdUtAtMidnight(2026, 8, 1)
        assertEquals(DeltaT.secondsAtJd(jd), MeeusEphemeris().deltaT(jd), 0.0)
        assertEquals(DeltaT.secondsAtJd(jd), Vsop87Ephemeris().deltaT(jd), 0.0)
    }
}
