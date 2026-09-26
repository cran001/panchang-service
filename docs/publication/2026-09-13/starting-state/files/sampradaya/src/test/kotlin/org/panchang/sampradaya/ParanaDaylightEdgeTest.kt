package org.panchang.sampradaya

import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.panchang.core.GeoLocation
import org.panchang.core.PanchangCalculator
import org.panchang.core.RiseSet
import org.panchang.ephemeris.Vsop87Ephemeris

/**
 * Daylight caps must use a real sunrise-to-following-sunset interval. Same-civil-date
 * subtraction can be negative even when daylight exists (USNO Reykjavik audit evidence).
 * Keep the inverted civil-day and polar sweeps; assert every emitted cap and refusal.
 */
class ParanaDaylightEdgeTest {

    /**
     * A site whose civil midnight falls at local solar noon, so every civil day sets before it
     * rises.
     *
     * Longitude 0 with a UTC-12 zone is the extreme of the skew `SunTimes.daylightDays` warns
     * about: mean solar time equals UTC there, so the civil day runs from one solar noon to the
     * next and contains a sunset in its morning and a sunrise in its evening. `daylightDays` is
     * therefore about -0.495 days year-round. The equator is chosen for the latitude so that both
     * events certainly exist and the case under test is the *sign* of the interval, not its
     * absence — the absence case is covered by the sweep below.
     *
     * `Etc/GMT+12` is UTC-12: the Etc zones carry the POSIX sign, inverted from the offset.
     */
    private val invertedDaySite = GeoLocation(0.0, 0.0, ZoneId.of("Etc/GMT+12"))

    @Test
    fun `a site whose civil sunset precedes sunrise uses its actual following sunset`() {
        val index = LunarDayIndex.build(YEAR, ObservanceContext(calculator, invertedDaySite))
        val decisions = IskconRules().allObservancesInWindow(index)
        assertTrue(
            decisions.isNotEmpty(),
            "the fixture must actually reach derivePotentialParana; it produced no decisions",
        )

        // ── Premise. Assert first that this site really is the pathological one. ──────────────
        var sampled = 0
        var date = index.windowStart
        while (!date.isAfter(index.windowEnd)) {
            val daylight = calculator.sunTimes(date, invertedDaySite).daylightDays
            assertNotNull(daylight, "premise: expected both a sunrise and a sunset on $date")
            assertTrue(
                daylight!! <= 0.0,
                "premise: this fixture exists to exercise non-positive daylight, but $date at " +
                    "$invertedDaySite has daylightDays=$daylight. The branch under test is no " +
                    "longer being exercised here; find another site rather than deleting this.",
            )
            sampled++
            date = date.plusDays(SAMPLE_STRIDE_DAYS)
        }
        assertTrue(sampled > 100, "premise sampling covered only $sampled days")

        // ── Behaviour. ───────────────────────────────────────────────────────────────────────
        var bounded = 0
        var daylightCapped = 0
        for (decision in decisions) {
            val window = decision.parana
            if (window == null) {
                assertTrue(
                    decision.reason.contains("No parana window is given"),
                    "an absent window must say why: ${decision.reason}",
                )
                continue
            }
            bounded++
            val interval = calculator.sunTimes(window.date, invertedDaySite).daylightInterval {
                calculator.sunTimes(window.date.plusDays(1), invertedDaySite)
            }!!
            assertTrue(window.endJdUt <= interval.sunriseJdUt + interval.durationDays / 3)
            if (window.endReason == ParanaBoundReason.ONE_THIRD_DAYLIGHT) daylightCapped++
            assertEndMatchesItsStatedReason(index, invertedDaySite, decision, window)
        }
        assertTrue(bounded > 0, "no window survived, so nothing checked the labelling path")

        assertTrue(daylightCapped > 0, "the real following sunset must restore daylight caps")
        for (decision in decisions.filter { it.parana == null }) {
            assertEquals(RuleConfidence.INFERRED, decision.confidence)
            assertFalse(decision.reason.contains("civil sunset"),
                "same-date ordering is not evidence that no following sunset exists")
        }
    }

    /**
     * Nowhere in a high-latitude longitude sweep may a window claim the daylight cap without a
     * positive daylight interval that justifies it.
     *
     * The two latitudes are the polar probe coordinates from
     * `verify/src/main/kotlin/org/panchang/verify/grid/ReferenceCities.kt` (`POLAR_PROBES`:
     * Longyearbyen 78.2232N and McMurdo -77.8419S). They are hard-coded rather than imported
     * because `:verify` depends on `:sampradaya`, so the reverse edge onto this module's test
     * classpath would be a dependency cycle.
     *
     * The zone is held fixed at UTC-12 while the longitude sweeps, which is what makes this a
     * sweep rather than four unrelated sites: the skew between civil time and solar time is
     * `longitude/15 - offset`, so longitude 0 gives the maximum 12-hour skew and longitude 180
     * gives none. The sweep therefore passes through both the ordinary regime — where windows do
     * legitimately claim `ONE_THIRD_DAYLIGHT`, which the non-vacuity assertion below requires —
     * and the inverted one.
     */
    @Test
    fun `no site in a polar longitude sweep claims a daylight cap it cannot justify`() {
        var claimed = 0
        var checkedWindows = 0
        var sitesWithAbsentDaylight = 0
        var sitesWithNegativeDaylight = 0
        val absentReasons = ArrayList<String>()

        for (latitude in POLAR_PROBE_LATITUDES) {
            for (longitude in SWEPT_LONGITUDES) {
                val site = GeoLocation(latitude, longitude, ZoneId.of(SWEEP_ZONE))
                val index = LunarDayIndex.build(YEAR, ObservanceContext(calculator, site))

                // Premise, per site: record which flavours of unusable daylight this site
                // produces, so the tally below can show the sweep straddled both regimes. The
                // two are recorded separately because they are separate facts — polar night
                // leaves the interval undefined, an inverted civil day leaves it negative.
                var absentHere = false
                var negativeHere = false
                var probe = index.windowStart
                while (!probe.isAfter(index.windowEnd) && !(absentHere && negativeHere)) {
                    val daylight = calculator.sunTimes(probe, site).daylightDays
                    if (daylight == null) absentHere = true else if (daylight <= 0.0) negativeHere = true
                    probe = probe.plusDays(PREMISE_STRIDE_DAYS)
                }
                if (absentHere) sitesWithAbsentDaylight++
                if (negativeHere) sitesWithNegativeDaylight++

                for (decision in IskconRules().allObservancesInWindow(index)) {
                    val window = decision.parana
                    if (window == null) {
                        absentReasons += decision.reason
                        continue
                    }
                    checkedWindows++
                    val sunrise = (calculator.sunTimes(window.date, site).sunrise as? RiseSet.At)
                    assertNotNull(
                        sunrise,
                        "a window was emitted on ${window.date} at $site with no sunrise to " +
                            "start from: $window",
                    )
                    val daylight = calculator.sunTimes(window.date, site).daylightInterval {
                        calculator.sunTimes(window.date.plusDays(1), site)
                    }?.durationDays
                    if (window.endReason == ParanaBoundReason.ONE_THIRD_DAYLIGHT) {
                        claimed++
                        assertNotNull(
                            daylight,
                            "ONE_THIRD_DAYLIGHT claimed on ${window.date} at $site where the " +
                                "daylight interval is undefined: $window",
                        )
                        assertTrue(
                            daylight!! > 0.0,
                            "ONE_THIRD_DAYLIGHT claimed on ${window.date} at $site where " +
                                "daylightDays=$daylight: $window",
                        )
                        assertEquals(
                            sunrise!!.jdUt + daylight / 3.0,
                            window.endJdUt,
                            JD_TOLERANCE_DAYS,
                            "the window claiming ONE_THIRD_DAYLIGHT does not end a third of the " +
                                "way through the daylight it names: $window at $site",
                        )
                    } else {
                        // The pre-fix failure produced end = Double.MAX_VALUE. Any real bound is
                        // within a couple of days of the sunrise it starts from.
                        assertTrue(
                            window.endJdUt.isFinite() &&
                                window.endJdUt < sunrise!!.jdUt + SANE_BOUND_DAYS,
                            "the window on ${window.date} at $site ends at ${window.endJdUt}, " +
                                "which is not a bound anyone could act on: $window",
                        )
                    }
                    assertTrue(
                        window.endJdUt > window.startJdUt,
                        "inverted window at $site: $window",
                    )
                }
            }
        }

        // Non-vacuity in both directions: the sweep must have produced real daylight-capped
        // windows (or it proved nothing about the label) and must have visited at least one site
        // where the daylight interval is not positive (or it never approached the branch).
        assertTrue(
            claimed > 0,
            "the sweep checked $checkedWindows windows and none claimed ONE_THIRD_DAYLIGHT, so " +
                "the assertion above was never exercised. Absent-window reasons: " +
                absentReasons.joinToString(" | "),
        )
        assertTrue(
            sitesWithAbsentDaylight + sitesWithNegativeDaylight > 0,
            "no swept site produced a non-positive or absent daylight interval on any day, so " +
                "the sweep no longer reaches the branch under test",
        )
        println(
            "parana daylight sweep: ${SWEPT_LONGITUDES.size * POLAR_PROBE_LATITUDES.size} sites, " +
                "$checkedWindows windows, $claimed claiming ONE_THIRD_DAYLIGHT, " +
                "${absentReasons.size} absent; $sitesWithAbsentDaylight sites saw an undefined " +
                "daylight interval and $sitesWithNegativeDaylight saw a negative one",
        )
    }

    private fun assertEndMatchesItsStatedReason(
        index: LunarDayIndex,
        site: GeoLocation,
        decision: ObservanceDecision,
        window: ParanaWindow,
    ) {
        when (window.endReason) {
            ParanaBoundReason.ONE_THIRD_DAYLIGHT -> {
                val interval = calculator.sunTimes(window.date, site).daylightInterval {
                    calculator.sunTimes(window.date.plusDays(1), site)
                }!!
                assertEquals(interval.sunriseJdUt + interval.durationDays / 3, window.endJdUt,
                    JD_TOLERANCE_DAYS)
            }

            ParanaBoundReason.DVADASHI_END -> {
                val position = index.spans().indexOfFirst { it.startJdUt == decision.tithi.startJdUt }
                assertTrue(position >= 0, "the Ekadashi span for ${decision.date} is not in the index")
                assertEquals(
                    index.spans()[position + 1].endJdUt,
                    window.endJdUt,
                    JD_TOLERANCE_DAYS,
                    "the window claims DVADASHI_END but does not end when the Dvadashi does: $window",
                )
            }

            ParanaBoundReason.NAKSHATRA_END -> {
                val sunrise = calculator.sunTimes(window.date, site).sunrise as RiseSet.At
                assertEquals(
                    calculator.nakshatraAt(sunrise.jdUt).endJdUt,
                    window.endJdUt,
                    JD_TOLERANCE_DAYS,
                    "the window claims NAKSHATRA_END but does not end when that nakshatra does: " +
                        "$window",
                )
            }

            else -> throw AssertionError(
                "an unexpected Parana cap claims ${window.endReason}: $window",
            )
        }
    }

    private val calculator: PanchangCalculator get() = CALCULATOR

    companion object {
        private const val YEAR = 2026

        /** Stride for the premise sweep. Three days covers ~160 samples over the index window. */
        private const val SAMPLE_STRIDE_DAYS = 3L

        /** Stride for the sweep's per-site premise scan; it stops as soon as it has both flavours. */
        private const val PREMISE_STRIDE_DAYS = 2L

        /** Half a second, in days. Both sides recompute the same instant, so this is generous. */
        private const val JD_TOLERANCE_DAYS = 1.0 / 172_800.0

        /** A parana bound more than this far past sunrise is a sentinel, not a ruling. */
        private const val SANE_BOUND_DAYS = 2.0

        /**
         * `POLAR_PROBES` in `verify/.../grid/ReferenceCities.kt`: Longyearbyen, Svalbard and
         * McMurdo Station, Antarctica. Copied rather than imported — see the sweep's KDoc.
         */
        private val POLAR_PROBE_LATITUDES = listOf(78.2232, -77.8419)

        /** Longitude sweep; with [SWEEP_ZONE] fixed these span the full range of clock/sun skew. */
        private val SWEPT_LONGITUDES = listOf(0.0, 90.0, -90.0, 180.0)

        /** UTC-12; the Etc zones carry the POSIX sign, inverted from the offset. */
        private const val SWEEP_ZONE = "Etc/GMT+12"

        /**
         * One ephemeris for the whole class. Nothing here mutates it, and constructing a VSOP87
         * table per site would dominate a suite that is already dominated by index builds.
         */
        private val CALCULATOR = PanchangCalculator(Vsop87Ephemeris())
    }
}
