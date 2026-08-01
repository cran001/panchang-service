package org.panchang.sampradaya

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.zone.ZoneOffsetTransition
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.panchang.core.RiseSet

/**
 * The claims a single site structurally cannot make.
 *
 * `GaudiyaEkadashiConformanceTest` now walks ten sites, but every one of its assertions is still
 * of the form "this site agrees with its own oracle". Two properties are invisible to any number
 * of independent per-site comparisons, and both are properties a user of this service depends on:
 *
 * 1. **That location actually reaches the answer.** A service that computed Mayapur's timings and
 *    relabelled them with the requested city's name would pass a per-site suite at Mayapur and
 *    fail every user elsewhere. It is caught only by comparing two sites to *each other*, and
 *    most sharply by two sites in the same time zone, where a copied timing is not even visibly
 *    wrong on the clock. Delhi and Mumbai are both `Asia/Kolkata`.
 *
 * 2. **That the civil offset is right on the days it changes.** No harvested day-by-day
 *    comparison is guaranteed to land near a DST transition — the fasts fall where the Moon puts
 *    them, not where the clocks do — so a transition bug can survive a fully green conformance
 *    run. There was no DST-transition test in this repository before Wave 1.
 *
 * The exclusion this gate carries and the reason it is an exclusion rather than a tolerance are
 * documented in [StaleReferenceDst]; the last test here is what keeps that exclusion honest.
 *
 * Per-site results, including every disagreement, are in `docs/validation-multisite.md`.
 */
class MultiSiteParanaConformanceTest {

    // ================================================================= the requirement, as a test

    /**
     * Two cities in one time zone must not share parana timings.
     *
     * This is the user requirement written as an assertion. The failure it exists to prevent is
     * not hypothetical: it is what a calibration table keyed on one city does, and it is
     * invisible on the clock face, because both cities print times in IST and both look plausible.
     *
     * The claim is made three ways, each stronger than the last:
     *
     * - Delhi and Mumbai are in the same zone, and their sunrise-based parana starts differ.
     * - The difference is not a rounding artefact: at the June solstice it is 38 minutes, and the
     *   test requires more than 10 minutes somewhere in the year.
     * - The difference we compute **matches the difference the reference computes**, date by
     *   date. Two cities can differ by an arbitrary wrong amount; agreeing with the published
     *   inter-city offset to under a minute is what shows the geometry is right rather than
     *   merely non-constant.
     *
     * Mayapur against Mumbai is asserted too, as the widest pair in `Asia/Kolkata`: 15.5° of
     * longitude, which the reference prints as 53 to 71 minutes of sunrise difference. That is the
     * scale of the error a devotee in Mumbai would keep if given Mayapur's timings.
     */
    @Test
    fun `two cities in one time zone do not share parana timings`() {
        val delhi = GaudiyaSites.fixture("delhi")
        val mumbai = GaudiyaSites.fixture("mumbai")

        // ── Premise. Without this the test proves nothing about zones at all. ────────────────
        assertEquals(
            delhi.location.zone,
            mumbai.location.zone,
            "premise: this test is about two cities that share a civil clock",
        )
        assertTrue(
            abs(delhi.location.latitude - mumbai.location.latitude) > 5.0,
            "premise: the two cities must be far enough apart in latitude for the seasonal " +
                "sunrise difference to be the thing under test",
        )

        val comparisons = solarStartComparisons(delhi, mumbai)
        assertTrue(
            comparisons.size >= 15,
            "only ${comparisons.size} dates have a sunrise-based window at both cities; the " +
                "comparison is too thin to conclude anything",
        )

        val worst = comparisons.maxByOrNull { abs(it.ourDifferenceMinutes) }!!
        assertTrue(
            abs(worst.ourDifferenceMinutes) > 10.0,
            ("Delhi and Mumbai share Asia/Kolkata, and across ${comparisons.size} shared parana " +
                "days the largest difference between their parana starts was only " +
                "%.2f minutes (on ${worst.date}). Timings that barely differ between two cities " +
                "1160 km apart mean the location is not reaching the computation.")
                .format(abs(worst.ourDifferenceMinutes)),
        )

        // The reference's own inter-city offset, reproduced. Truncation to the printed minute at
        // both ends can move a difference by just under a minute in either direction, so 1.5
        // minutes is the tightest honest band on a difference of two truncated prints.
        val offBy = comparisons.filter {
            abs(it.ourDifferenceMinutes - it.printedDifferenceMinutes) > 1.5
        }
        assertTrue(
            offBy.isEmpty(),
            "the Delhi-minus-Mumbai parana offset we compute does not match the offset the two " +
                "published calendars print:\n" + offBy.joinToString("\n") {
                    "${it.date}: printed %+.0f min, computed %+.2f min"
                        .format(it.printedDifferenceMinutes, it.ourDifferenceMinutes)
                },
        )

        val mayapurVsMumbai = solarStartComparisons(GaudiyaSites.fixture("mayapur"), mumbai)
        val widest = mayapurVsMumbai.maxByOrNull { abs(it.ourDifferenceMinutes) }!!
        assertTrue(
            abs(widest.ourDifferenceMinutes) > 50.0,
            "Mayapur and Mumbai are 15.5° of longitude apart in one time zone; the published " +
                "calendars differ by 53 to 71 minutes, but the widest difference computed here " +
                "was %.2f minutes on ${widest.date}".format(abs(widest.ourDifferenceMinutes)),
        )

        println(
            ("same-zone separation: delhi-mumbai worst %+.1f min on ${worst.date} over " +
                "${comparisons.size} shared days; mayapur-mumbai worst %+.1f min on " +
                "${widest.date}").format(worst.ourDifferenceMinutes, widest.ourDifferenceMinutes),
        )
    }

    /** One date on which both sites put a sunrise-based parana start. */
    private class StartComparison(
        val date: LocalDate,
        /** First site minus second, from our computation, in minutes. */
        val ourDifferenceMinutes: Double,
        /** First site minus second, as the two calendars print it, in whole minutes. */
        val printedDifferenceMinutes: Double,
    )

    private fun solarStartComparisons(
        first: GaudiyaSiteFixture,
        second: GaudiyaSiteFixture,
    ): List<StartComparison> {
        val firstPrinted = first.goldenParanaDays.associateBy { it.date }
        val secondPrinted = second.goldenParanaDays.associateBy { it.date }
        val out = ArrayList<StartComparison>()
        for (date in firstPrinted.keys.intersect(secondPrinted.keys).sorted()) {
            val a = firstPrinted.getValue(date).parana!!
            val b = secondPrinted.getValue(date).parana!!
            // Only sunrise-based starts. A start taken from the first quarter of the tithi is an
            // instant on the Moon's clock, identical at every longitude by definition, so
            // including those would dilute the very quantity under test with legitimate zeroes.
            if (a.startBasis != "sunrise" || b.startBasis != "sunrise") continue
            if (StaleReferenceDst.excludes(first.cityId, a)) continue
            if (StaleReferenceDst.excludes(second.cityId, b)) continue
            val ourFirst = first.paranaByDate[date]?.parana ?: continue
            val ourSecond = second.paranaByDate[date]?.parana ?: continue
            out += StartComparison(
                date = date,
                ourDifferenceMinutes = first.minutesOfDay(ourFirst.startJdUt) -
                    second.minutesOfDay(ourSecond.startJdUt),
                printedDifferenceMinutes = printedMinutes(a.start) - printedMinutes(b.start),
            )
        }
        return out
    }

    // ================================================================= DST transitions

    /**
     * The civil offset is applied correctly on the two days a year it changes.
     *
     * London and Auckland are chosen because between them they cover both directions in both
     * hemispheres, and because they transition in opposite seasons: London springs forward in
     * March and Auckland falls back in April. Each assertion below states its premise from
     * `java.time`'s own zone rules first, so that a tzdata update which moves a transition
     * changes the premise loudly instead of quietly retargeting the test at an ordinary day.
     *
     * Four things are checked, in increasing distance from the plumbing:
     *
     * 1. The zone really has two one-hour transitions in 2026.
     * 2. The civil day containing a transition is 23 or 25 hours long as measured through
     *    `GeoLocation.jdUtAtStartOfDay`, which is what buckets sunrises into civil days inside
     *    [LunarDayIndex]. A day-length bug here silently reassigns a sunrise, and a reassigned
     *    sunrise can move a fasting date.
     * 3. Local sunrise clock time jumps by about an hour across the transition, in the direction
     *    the offset moved.
     * 4. The parana windows nearest each transition still match the published calendar.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("dstSites")
    fun `parana timings survive the civil daylight-saving transitions`(cityId: String) {
        val site = GaudiyaSites.fixture(cityId)
        val zone = site.location.zone
        val transitions = transitionsIn(zone, GaudiyaSites.YEAR)

        // ── 1. Premise ──────────────────────────────────────────────────────────────────────
        assertEquals(
            2,
            transitions.size,
            "premise: $zone should make exactly two offset transitions in ${GaudiyaSites.YEAR}, " +
                "but tzdata says ${transitions.map { it.instant }}",
        )
        for (transition in transitions) {
            assertEquals(
                3600L,
                abs(transition.duration.seconds),
                "premise: $zone transition at ${transition.instant} is not one hour",
            )
        }
        assertEquals(
            0L,
            transitions.sumOf { it.duration.seconds },
            "premise: the year's transitions should cancel out",
        )

        for (transition in transitions) {
            val date = transition.dateTimeBefore.toLocalDate()
            val forward = transition.duration.seconds > 0

            // ── 2. The civil day really is short or long, as the index measures it ───────────
            val hours = civilDayHours(site, date)
            assertEquals(
                if (forward) 23.0 else 25.0,
                hours,
                SECOND_IN_HOURS,
                "$cityId: the civil day $date contains a " +
                    (if (forward) "spring-forward" else "fall-back") +
                    " transition but measures $hours hours through jdUtAtStartOfDay",
            )
            assertEquals(
                24.0,
                civilDayHours(site, date.minusDays(1)),
                SECOND_IN_HOURS,
                "$cityId: the day before the transition is not 24 hours",
            )
            assertEquals(
                24.0,
                civilDayHours(site, date.plusDays(1)),
                SECOND_IN_HOURS,
                "$cityId: the day after the transition is not 24 hours",
            )

            // ── 3. Sunrise clock time moves with the offset ──────────────────────────────────
            val before = sunriseMinutes(site, date.minusDays(1))
            val after = sunriseMinutes(site, date.plusDays(1))
            assertNotNull(before, "$cityId: no sunrise on ${date.minusDays(1)}")
            assertNotNull(after, "$cityId: no sunrise on ${date.plusDays(1)}")
            val jump = after!! - before!!
            // An hour of offset, less the two days of natural drift in sunrise time, which is a
            // few minutes at these latitudes near the equinoxes. 40 to 80 minutes admits the
            // drift without admitting a missing, doubled or half-applied offset.
            assertTrue(
                abs(jump) in 40.0..80.0 && (jump > 0) == forward,
                ("$cityId: sunrise clock time moved %+.1f minutes across the $date transition; a " +
                    "one-hour %s should move it by about %s an hour")
                    .format(
                        jump,
                        if (forward) "spring forward" else "fall back",
                        if (forward) "+" else "-",
                    ),
            )
        }

        // ── 4. The windows nearest each transition still agree with the calendar ─────────────
        val checked = mutableListOf<String>()
        val failures = mutableListOf<String>()
        for (transition in transitions) {
            val date = transition.dateTimeBefore.toLocalDate()
            val before = site.goldenParanaDays.lastOrNull { it.date < date }
            val after = site.goldenParanaDays.firstOrNull { it.date > date }
            for (day in listOfNotNull(before, after)) {
                val gap = day.date.toEpochDay() - date.toEpochDay()
                assertTrue(
                    abs(gap) <= NEAREST_WINDOW_DAYS,
                    "$cityId: the nearest printed parana window to the $date transition is " +
                        "${day.date}, $gap days away; that is too far to be evidence about the " +
                        "transition",
                )
                checked += "${day.date}(${gap}d)"
                val printed = day.parana!!
                // The bound to compare. Normally the computed window's start; on an [UncappedParana]
                // row there is no window at either end, and the start is re-derived from the
                // Dvadashi span so that the transition is still evidenced rather than skipped.
                // Auckland's nearest window to its 27 September transition is exactly such a row,
                // so without this the only southern spring-forward check in the suite would be lost.
                val startJdUt = site.paranaByDate[day.date]?.parana?.startJdUt
                    ?: if (printed.end == null) site.hariVasaraEndOn(day.date) else null
                if (startJdUt == null) {
                    failures += "${day.date}: no window computed near the $date transition"
                    continue
                }
                val actual = site.civil(startJdUt)
                if (actual.toLocalDate() != day.date) {
                    failures += "${day.date}: computed start falls on ${actual.toLocalDate()}"
                    continue
                }
                val delta = site.minutesOfDay(startJdUt) - printedMinutes(printed.start)
                val band = if (printed.startBasis == "sunrise") SOLAR_BAND else LUNAR_BAND
                if (delta !in band) {
                    failures += "${day.date} start (${printed.startBasis}): calendar " +
                        "${printed.start} ${printed.clock}, computed " +
                        "%02d:%02d:%02d %s, delta %+.2f min, outside %s".format(
                            actual.hour, actual.minute, actual.second, actual.zone.id, delta, band,
                        )
                }
                // The stamp on the row and the zone database must agree about this instant.
                assertEquals(
                    zone.rules.isDaylightSavings(actual.toInstant()),
                    printed.clock == StaleReferenceDst.STAMP,
                    "$cityId ${day.date}: the calendar stamps the window '${printed.clock}' but " +
                        "tzdata disagrees about whether $zone is in daylight saving then",
                )
            }
        }
        println("dst transition check $cityId: windows ${checked.joinToString(", ")}")
        assertTrue(
            failures.isEmpty(),
            "parana disagreements next to a $cityId DST transition:\n" +
                failures.joinToString("\n"),
        )
    }

    // ================================================================= the uncapped-window pairing

    /**
     * Where the source refuses to state a cap is exactly where our rules refuse to give a window.
     *
     * A per-site suite cannot make this claim: at each individual site it reduces to "one day was
     * odd here and one day was odd there", which is what a coincidence looks like. Stated across
     * ten sites and 246 printed windows it is a different thing — three sites carry such a day,
     * three days in total, and **no site carries one without the other in either direction**.
     *
     * That is what identifies the cause. Both programs are detecting the same geometric conflict,
     * from the same Dvadashi and the same daylight: Hari Vasara, before whose end the fast may not
     * be broken, ends *after* the first third of daylight, before whose end it must be broken. The
     * source resolves the empty interval by printing the start and dropping the cap; [IskconRules]
     * resolves it by declining and saying the case needs a pandit's ruling. The disagreement is
     * about doctrine, not about the sky, and this test is what stops a future change from quietly
     * turning it into one — a rule that started refusing windows for some other reason, or one that
     * invented a cap here, would both break this equality.
     *
     * See [UncappedParana]. The open doctrinal question is recorded in
     * `docs/validation-multisite.md`; nothing here decides it.
     */
    @Test
    fun `the source's uncapped windows are exactly the days the rules refuse to bound one`() {
        val printed = sortedMapOf<String, Set<LocalDate>>()
        val refused = sortedMapOf<String, Set<LocalDate>>()
        for (cityId in GaudiyaSites.ALL) {
            val site = GaudiyaSites.fixture(cityId)
            if (site.goldenUncappedParanaDates.isNotEmpty()) {
                printed[cityId] = site.goldenUncappedParanaDates
            }
            if (site.ruleRefusedParanaDates.isNotEmpty()) {
                refused[cityId] = site.ruleRefusedParanaDates
            }
        }

        assertEquals(
            printed,
            refused,
            "the days our rules decline to bound a parana window are no longer exactly the days " +
                "the published calendars decline to cap one. Each side is keyed by the date the " +
                "window falls on. A day only on the left is one the source could not bound and we " +
                "did — check that no cap has been invented. A day only on the right is a refusal " +
                "with no counterpart in the reference, which is a rule error until shown otherwise.",
        )

        // The premise, so that a golden file which lost its uncapped rows turns this test into a
        // loud failure rather than a vacuously green `emptyMap == emptyMap`.
        assertEquals(
            mapOf(
                "mumbai" to setOf(LocalDate.parse("2026-08-24")),
                "new-york" to setOf(LocalDate.parse("2026-10-22")),
                "auckland" to setOf(LocalDate.parse("2026-09-23")),
            ).toSortedMap(),
            printed,
            "the set of uncapped rows in the harvested calendars has changed",
        )

        // And that the conflict really is Hari Vasara outlasting the daylight third, rather than
        // some other reason both programs happened to fall silent on.
        for ((cityId, dates) in printed) {
            val site = GaudiyaSites.fixture(cityId)
            for (date in dates) {
                val hariVasaraEnd = site.hariVasaraEndOn(date)
                assertNotNull(
                    hariVasaraEnd,
                    "$cityId $date: no Dvadashi first quarter ends on this date",
                )
                hariVasaraEnd!!
                val sunTimes = GaudiyaSites.calculator.sunTimes(date, site.location)
                val sunrise = (sunTimes.sunrise as RiseSet.At).jdUt
                val daylightThirdEnd = sunrise + sunTimes.daylightDays!! / 3.0
                assertTrue(
                    hariVasaraEnd > daylightThirdEnd,
                    "$cityId $date: this row is uncapped, but Hari Vasara ends " +
                        "${site.civil(hariVasaraEnd).toLocalTime()} which is *before* the first " +
                        "third of daylight ends at ${site.civil(daylightThirdEnd).toLocalTime()}, " +
                        "so the empty-interval explanation does not hold here",
                )
            }
        }
    }

    /**
     * Every site's `DST` stamps agree with tzdata — except the two that are known not to.
     *
     * This is what makes [StaleReferenceDst] an exclusion a reader can challenge rather than a
     * band that hides a defect. It states, as an executable claim:
     *
     * - the four cities whose countries still observe daylight saving are stamped correctly on
     *   every printed window, so the source's stamp is normally trustworthy and the two failures
     *   below are specific rather than systemic;
     * - Moscow and São Paulo are in zones with **no** transitions in 2026, and the reference
     *   stamps `DST` on 15 and 9 windows anyway;
     * - the stamp costs a real hour, not just a label: on those rows the reference's printed
     *   sunrise start is about 60 minutes later than the site's actual sunrise.
     *
     * If a re-harvested calendar ever drops the stale stamps, this test fails and the exclusion
     * can be deleted. That is the point: an exclusion nobody is told has stopped being necessary
     * becomes permanent by accident.
     */
    @Test
    fun `the stale-DST exclusion still describes the reference`() {
        val wrong = mutableListOf<String>()

        for (cityId in GaudiyaSites.ALL) {
            val site = GaudiyaSites.fixture(cityId)
            val zone = site.location.zone
            for (day in site.goldenParanaDays) {
                val printed = day.parana!!
                val ours = site.paranaByDate[day.date]?.parana ?: continue
                val stampedDst = printed.clock == StaleReferenceDst.STAMP
                val reallyDst = zone.rules.isDaylightSavings(site.civil(ours.startJdUt).toInstant())
                if (stampedDst != reallyDst) {
                    wrong += "$cityId ${day.date}: stamped '${printed.clock}', $zone " +
                        "isDaylightSavings=$reallyDst"
                }
            }
        }

        val byCity = wrong.groupBy { it.substringBefore(' ') }
        assertEquals(
            StaleReferenceDst.CITIES,
            byCity.keys,
            "the set of cities whose DST stamps disagree with tzdata has changed. Expected " +
                "exactly ${StaleReferenceDst.CITIES} — Russia abolished daylight saving in 2011 " +
                "and Brazil in 2019. Disagreements found:\n" + wrong.joinToString("\n"),
        )
        for ((cityId, expected) in StaleReferenceDst.EXPECTED_ROWS) {
            assertEquals(
                expected,
                byCity.getValue(cityId).size,
                "$cityId: the number of stale-DST rows changed:\n" +
                    byCity.getValue(cityId).joinToString("\n"),
            )
        }

        // The stamp is worth a real hour, which is why no tolerance could absorb it honestly.
        val shifts = mutableListOf<String>()
        for (cityId in StaleReferenceDst.CITIES) {
            val site = GaudiyaSites.fixture(cityId)
            assertTrue(
                transitionsIn(site.location.zone, GaudiyaSites.YEAR).isEmpty(),
                "premise: ${site.location.zone} must have no 2026 offset transitions for this " +
                    "diagnosis to hold",
            )
            for (day in site.goldenParanaDays) {
                val printed = day.parana!!
                if (!StaleReferenceDst.excludes(cityId, printed)) continue
                if (printed.startBasis != "sunrise") continue
                val ours = site.paranaByDate[day.date]?.parana ?: continue
                val delta = site.minutesOfDay(ours.startJdUt) - printedMinutes(printed.start)
                if (delta !in -62.0..-58.0) {
                    shifts += ("$cityId ${day.date}: calendar ${printed.start} ${printed.clock}, " +
                        "computed sunrise %.2f min from it — not the one-hour civil offset this " +
                        "exclusion claims, so this row is being excluded for the wrong reason")
                        .format(delta)
                }
            }
        }
        assertTrue(
            shifts.isEmpty(),
            "stale-DST rows that are not simply an hour out:\n" + shifts.joinToString("\n"),
        )
        println(
            "stale reference DST: " + StaleReferenceDst.EXPECTED_ROWS.entries.joinToString(", ") {
                "${it.key} ${it.value} rows"
            } + "; all shifted by one hour of civil offset",
        )
    }

    // ================================================================= helpers

    private fun civilDayHours(site: GaudiyaSiteFixture, date: LocalDate): Double =
        (site.location.jdUtAtEndOfDay(date) - site.location.jdUtAtStartOfDay(date)) * 24.0

    private fun sunriseMinutes(site: GaudiyaSiteFixture, date: LocalDate): Double? {
        val sunrise = GaudiyaSites.calculator.sunTimes(date, site.location).sunrise
        return (sunrise as? RiseSet.At)?.let { site.minutesOfDay(it.jdUt) }
    }

    /** The zone's offset transitions that fall inside [year], in time order. */
    private fun transitionsIn(zone: ZoneId, year: Int): List<ZoneOffsetTransition> {
        val rules = zone.rules
        val out = ArrayList<ZoneOffsetTransition>()
        var cursor: Instant = LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
            .minusSeconds(1)
        while (true) {
            val next = rules.nextTransition(cursor) ?: break
            if (next.instant.atZone(zone).year != year) break
            out += next
            cursor = next.instant
        }
        return out
    }

    private fun printedMinutes(text: String): Double =
        text.substringBefore(':').toInt() * 60.0 + text.substringAfter(':').toInt()

    companion object {

        /**
         * The two DST sites this gate examines directly.
         *
         * Both directions, both hemispheres, opposite seasons. New York and Sydney also observe
         * daylight saving and their stamps are checked by the exclusion test below, but adding
         * them here would repeat the same transition arithmetic for no new evidence.
         */
        @JvmStatic
        fun dstSites(): List<String> = listOf("london", "auckland")

        /** As in `GaudiyaEkadashiConformanceTest`, unchanged. See its KDoc for the derivation. */
        private val SOLAR_BAND = -0.25..1.25
        private val LUNAR_BAND = -3.0..1.25

        private const val SECOND_IN_HOURS = 1.0 / 3600.0

        /** A printed window further than this from a transition is not evidence about it. */
        private const val NEAREST_WINDOW_DAYS = 45L
    }
}
