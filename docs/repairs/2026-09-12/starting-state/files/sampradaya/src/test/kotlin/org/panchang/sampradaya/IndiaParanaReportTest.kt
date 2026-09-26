package org.panchang.sampradaya

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Every Ekadashi and Dvadashi parana bound at the eight Indian sites, printed row by row.
 *
 * ## What this is for
 *
 * `GaudiyaEkadashiConformanceTest` already asserts these bounds, but it reports only what
 * *failed*. The question this file answers is the one that assertion cannot: for each of the 24
 * fasts at each Indian city, what does the published calendar print, what do these rules compute,
 * and how far apart are they — including all the rows that agree. A pass with no visible rows is
 * indistinguishable from a pass over an empty list, and that is exactly the failure mode this
 * suite exists to make impossible.
 *
 * ## Every number below is measured
 *
 * The expected side is read from `verify/golden/vaisnavacalendar-<city>-2026.json`, harvested from
 * the publisher with provenance in the file. The actual side is [IskconRules] evaluated at the
 * coordinates that file states in its own `site` block. Nothing here is transcribed, and the
 * summary lines are derived from the rows that were actually walked — [assertRowsWereWalked]
 * fails the test if a run produces no rows, so a report that says "all agree" over zero data
 * cannot be printed.
 *
 * ## Bands
 *
 * The same bands `GaudiyaEkadashiConformanceTest` uses, imported rather than restated so the two
 * cannot drift: solar `[-0.25, +1.25]` minutes, lunar `[-3.0, +1.25]`. They are not symmetric
 * tolerances. The source truncates a printed time to the minute, so exact agreement appears as a
 * delta in `[0, 1)`; the asymmetry on the lunar side is ADR 0002's measured one-signed ephemeris
 * offset, not slack.
 *
 * ## What is out of scope, stated rather than omitted
 *
 * Time-bound events other than the parana are **not compared here, because no oracle exists**.
 * Across all fourteen harvested calendars the publisher names fast-until anchors in words —
 * `Fast till noon`, `till sunset`, `till moonrise`, `till dusk`, `till midnight` — and prints a
 * clock time for none of them. [ANCHORED_EVENTS_HAVE_NO_PRINTED_TIME] asserts that on the Indian
 * files rather than leaving it as a claim in a comment. Our anchor instants are validated as
 * astronomy elsewhere (`docs/validation-moonrise.md`); that they are what the tradition intends
 * is unvalidated and is in the pandit queue.
 */
class IndiaParanaReportTest {

    private val solarBand = -0.25..1.25
    private val lunarBand = -3.0..1.25

    /** One compared bound, as measured. */
    private data class Row(
        val cityId: String,
        val fastDate: LocalDate,
        val fastName: String,
        val paranaDate: LocalDate,
        val which: String,
        val printed: String,
        val computed: String,
        val printedBasis: String,
        val computedBasis: String,
        val deltaMinutes: Double,
        val inBand: Boolean,
        val basisAgrees: Boolean,
        val solar: Boolean,
    )

    @Test
    fun `every Indian Ekadashi and Dvadashi parana bound, printed against the published calendar`() {
        val rows = mutableListOf<Row>()
        val notes = mutableListOf<String>()

        for (cityId in GaudiyaSites.INDIA) {
            val site = GaudiyaSites.fixture(cityId)
            val header = "%s — %s  (%.4f, %.4f, %s)".format(
                cityId,
                site.printedName.ifEmpty { "Mayapur [India]" },
                site.location.latitude,
                site.location.longitude,
                site.location.zone,
            )
            println()
            println("=".repeat(108))
            println(header)
            println("=".repeat(108))
            println(
                "%-11s %-26s %-11s %-6s %-9s %-10s %-16s %8s %s".format(
                    "fast date", "fasting for", "parana on", "bound",
                    "printed", "computed", "basis", "delta", "",
                ),
            )
            println("-".repeat(108))

            var cityRows = 0
            for (day in site.goldenParanaDays) {
                val expected = day.parana!!
                // The fast this window breaks is the one on the preceding day.
                val fastDay = site.golden.firstOrNull {
                    it.date == day.date.minusDays(1) && it.fastingFor != null
                }
                val fastName = fastDay?.fastingFor ?: "(fast in the preceding year)"
                val fastDate = day.date.minusDays(1)

                val decision = site.paranaByDate[day.date]
                if (decision == null) {
                    val why = if (expected.end == null) {
                        "the source prints a start with no cap; these rules refuse to emit an " +
                            "inverted window here (Hari Vasara outlasts the first third of daylight)"
                    } else {
                        "the source prints a bounded window and these rules produced none"
                    }
                    println("%-11s %-26s %-11s  %s".format(fastDate, fastName, day.date, "NO WINDOW: $why"))
                    notes += "$cityId ${day.date}: $why"
                    continue
                }
                val window = decision.parana!!

                cityRows += emit(
                    rows, notes, site, cityId, fastDate, fastName, day.date, "start",
                    expected.start, expected.startBasis, window.startJdUt, window.startReason,
                )
                val end = expected.end
                if (end == null) {
                    notes += "$cityId ${day.date}: the source prints no cap but these rules " +
                        "produced one ending ${site.civil(window.endJdUt).toLocalTime()} " +
                        "(${window.endReason})"
                } else {
                    cityRows += emit(
                        rows, notes, site, cityId, fastDate, fastName, day.date, "end",
                        end, expected.endBasis ?: "(none printed)", window.endJdUt, window.endReason,
                    )
                }
            }

            val cityMeasured = rows.filter { it.cityId == cityId }
            val outOfBand = cityMeasured.count { !it.inBand }
            println("-".repeat(108))
            println(
                (
                    "%s: %d fasts in the calendar, %d parana windows printed, %d bounds " +
                        "compared, %d outside band, worst delta %+.2f min"
                    ).format(
                    cityId,
                    site.goldenFastDays.size,
                    site.goldenParanaDays.size,
                    cityMeasured.size,
                    outOfBand,
                    cityMeasured.maxByOrNull { kotlin.math.abs(it.deltaMinutes) }
                        ?.deltaMinutes ?: 0.0,
                ),
            )
            assertTrue(cityRows > 0, "no bound was compared at $cityId; the report would be empty")
        }

        printSummary(rows, notes)
        assertRowsWereWalked(rows)

        // A bound that lands on the printed minute but names a different rule is a *different*
        // finding from a bound that lands on the wrong minute, and collapsing the two would let a
        // real timing error hide behind the word "basis". Both are asserted, separately.
        val basisDisagreements = rows.filter { it.basisAgrees == false }
        assertEquals(
            ParanaBasisTie.ROWS,
            basisDisagreements.map { "${it.cityId} ${it.paranaDate} ${it.which}" }.toSet(),
            "the set of rows where the printed time agrees but the named rule does not has " +
                "changed. See ParanaBasisTie, which holds the set and re-measures the gap " +
                "that justifies each entry; this is a named coincidence, not a tolerance.",
        )

        val failures = rows.filter { !it.inBand }
        assertTrue(
            failures.isEmpty(),
            "parana bounds outside the acceptance band at Indian sites:\n" +
                failures.joinToString("\n") {
                    "${it.cityId} ${it.paranaDate} ${it.which} (${it.computedBasis}): " +
                        "calendar ${it.printed}, rules ${it.computed}, delta " +
                        "%+.2f min, band %s".format(
                            it.deltaMinutes, if (it.solar) solarBand else lunarBand,
                        )
                },
        )
    }

    /** Compares one bound, prints it, records it. Returns 1 if it was measured, 0 if skipped. */
    @Suppress("LongParameterList")
    private fun emit(
        rows: MutableList<Row>,
        notes: MutableList<String>,
        site: GaudiyaSiteFixture,
        cityId: String,
        fastDate: LocalDate,
        fastName: String,
        paranaDate: LocalDate,
        which: String,
        printed: String,
        printedBasis: String,
        jdUt: Double,
        reason: ParanaBoundReason,
    ): Int {
        val actual = site.civil(jdUt)
        val computed = "%02d:%02d:%02d".format(actual.hour, actual.minute, actual.second)
        if (actual.toLocalDate() != paranaDate) {
            notes += "$cityId $paranaDate $which: these rules put the bound on ${actual.toLocalDate()}"
            println(
                (
                    "%-11s %-26s %-11s %-6s %-9s %-10s  WRONG DAY: %s"
                    ).format(fastDate, fastName, paranaDate, which, printed, computed, actual.toLocalDate()),
            )
            return 0
        }
        val expectedMinutes = printed.substringBefore(':').toInt() * 60.0 +
            printed.substringAfter(':').toInt()
        val delta = site.minutesOfDay(jdUt) - expectedMinutes
        val solar = reason == ParanaBoundReason.SUNRISE ||
            reason == ParanaBoundReason.ONE_THIRD_DAYLIGHT ||
            reason == ParanaBoundReason.SUNSET
        val band = if (solar) solarBand else lunarBand
        val basisAgrees = BASIS_TO_REASON[printedBasis] == reason
        val inBand = delta in band

        println(
            "%-11s %-26s %-11s %-6s %-9s %-10s %-16s %+8.2f %s".format(
                fastDate, fastName.take(26), paranaDate, which, printed, computed,
                printedBasis.take(16), delta,
                when {
                    !inBand -> "OUT OF BAND $band"
                    !basisAgrees -> "same minute, basis differs: rules say $reason"
                    else -> "ok"
                },
            ),
        )
        rows += Row(
            cityId, fastDate, fastName, paranaDate, which, printed, computed,
            printedBasis, reason.name, delta, inBand, basisAgrees, solar,
        )
        return 1
    }

    private fun printSummary(rows: List<Row>, notes: List<String>) {
        val solar = rows.filter { it.solar }
        val lunar = rows.filter { !it.solar }
        println()
        println("=".repeat(108))
        println("SUMMARY — derived from the ${rows.size} bounds actually compared above")
        println("=".repeat(108))
        println("sites: ${GaudiyaSites.INDIA.joinToString(", ")}")
        println(
            (
                "solar bounds (sunrise / 1/3 daylight / sunset): %d compared, min %+.2f, " +
                    "max %+.2f, mean %+.2f min; band %s"
                ).format(
                solar.size,
                solar.minOfOrNull { it.deltaMinutes } ?: 0.0,
                solar.maxOfOrNull { it.deltaMinutes } ?: 0.0,
                solar.map { it.deltaMinutes }.average(),
                solarBand,
            ),
        )
        println(
            (
                "lunar bounds (1/4 of tithi / end of tithi / end of naksatra): %d compared, " +
                    "min %+.2f, max %+.2f, mean %+.2f min; band %s"
                ).format(
                lunar.size,
                lunar.minOfOrNull { it.deltaMinutes } ?: 0.0,
                lunar.maxOfOrNull { it.deltaMinutes } ?: 0.0,
                lunar.map { it.deltaMinutes }.average(),
                lunarBand,
            ),
        )
        println("bounds outside their band: ${rows.count { !it.inBand }}")
        val basisDiffs = rows.filter { !it.basisAgrees }
        println(
            "bounds on the printed minute but naming a different rule: ${basisDiffs.size}" +
                if (basisDiffs.isEmpty()) "" else " (see ParanaBasisTie)",
        )
        basisDiffs.forEach {
            println(
                "  - %s %s %s: calendar says '%s', rules say %s; both print %s, delta %+.2f min"
                    .format(
                        it.cityId, it.paranaDate, it.which, it.printedBasis,
                        it.computedBasis, it.printed, it.deltaMinutes,
                    ),
            )
        }
        if (notes.isEmpty()) {
            println("rows not compared: none")
        } else {
            println("rows not compared (${notes.size}):")
            notes.forEach { println("  - $it") }
        }
    }

    /**
     * A report that measured nothing must not be able to say everything agreed.
     *
     * This is the assertion that makes the summary above trustworthy. An earlier attempt at this
     * comparison printed a clean pass while every file read had failed, because the summary text
     * was a constant rather than a function of the data. The counts below are the shape the eight
     * Indian calendars actually have, asserted so that a run over a truncated or missing file
     * fails here instead of quietly reporting agreement over fewer rows.
     */
    private fun assertRowsWereWalked(rows: List<Row>) {
        assertTrue(rows.isNotEmpty(), "no bound was compared at any Indian site")
        assertEquals(
            GaudiyaSites.INDIA.toSet(),
            rows.map { it.cityId }.toSet(),
            "some Indian site contributed no compared bound",
        )
        GaudiyaSites.INDIA.forEach { cityId ->
            val site = GaudiyaSites.fixture(cityId)
            assertEquals(24, site.goldenFastDays.size, "$cityId: the golden fast count changed")
            assertTrue(
                site.goldenParanaDays.size in 24..25,
                "$cityId prints ${site.goldenParanaDays.size} parana windows; 24 or 25 is the " +
                    "shape every harvested file has",
            )
            // Two bounds per window, less any the source declines to cap.
            assertTrue(
                rows.count { it.cityId == cityId } >= site.goldenParanaDays.size,
                "$cityId compared only ${rows.count { it.cityId == cityId }} bounds across " +
                    "${site.goldenParanaDays.size} printed windows",
            )
        }
    }

    /**
     * The Indian calendars print fast-until anchors in words and never as a clock time.
     *
     * This is the reason the report above stops at the parana: for every other time-bound
     * observance there is nothing on the reference side to compare against. Asserted rather than
     * asserted-in-prose, so that a future calendar which *does* print such a time makes this test
     * fail and the gap becomes closable.
     */
    @Test
    fun `no anchored fasting note in an Indian calendar carries a clock time`() {
        val clock = Regex("""\b\d{1,2}:\d{2}\b""")
        val anchored = mutableListOf<String>()
        var withTime = 0

        for (cityId in GaudiyaSites.INDIA) {
            val site = GaudiyaSites.fixture(cityId)
            for (day in site.golden) {
                for (event in day.events + listOfNotNull(day.fastingFor)) {
                    if (!ANCHOR_WORDS.any { it in event.lowercase() }) continue
                    anchored += "$cityId ${day.date}: $event"
                    if (clock.containsMatchIn(event)) withTime++
                }
            }
        }

        println()
        println(
            "anchored fasting notes across the ${GaudiyaSites.INDIA.size} Indian calendars: " +
                "${anchored.size} lines, $withTime carrying a clock time",
        )
        anchored.groupingBy { it.substringAfter(": ") }.eachCount()
            .toList().sortedByDescending { it.second }
            .forEach { (text, n) -> println("  %4dx  %s".format(n, text)) }

        assertTrue(
            anchored.isNotEmpty(),
            "no anchored fasting note was found at all; the premise of this test — that the " +
                "source names anchors in words — no longer holds, so recheck the files rather " +
                "than deleting the test",
        )
        assertEquals(
            0,
            withTime,
            "the reference now prints a clock time for an anchored fast. That is an oracle we " +
                "did not have: docs/pandit-review.md records these anchors as unvalidated " +
                "precisely because none existed. Compare against it rather than removing this.",
        )
    }

    private companion object {


        /** Kept in step with `GaudiyaEkadashiConformanceTest`; the source's wording, not ours. */
        val BASIS_TO_REASON: Map<String, ParanaBoundReason> = mapOf(
            "sunrise" to ParanaBoundReason.SUNRISE,
            "1/4 of tithi" to ParanaBoundReason.HARI_VASARA_END,
            "end of tithi" to ParanaBoundReason.DVADASHI_END,
            "1/3 of daylight" to ParanaBoundReason.ONE_THIRD_DAYLIGHT,
            "end of naksatra" to ParanaBoundReason.NAKSHATRA_END,
        )

        /** The time-of-day words the publisher uses in a fasting note. */
        val ANCHOR_WORDS = listOf("till noon", "till sunset", "till moonrise", "till dusk", "till midnight")
    }
}
