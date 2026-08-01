package org.panchang.sampradaya

import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.panchang.core.GeoLocation
import org.panchang.core.PanchangCalculator
import org.panchang.ephemeris.Vsop87Ephemeris

/**
 * [IskconRules] against the published Gaudiya calendar for Mayapur 2026.
 *
 * The oracle is `verify/golden/vaisnavacalendar-mayapur-2026.json`, GCal 11 Build 5's own output
 * for the site whose coordinates it prints. Where this suite and that file disagree, the
 * presumption is that these rules are wrong.
 *
 * ## What "matches" means for a time
 *
 * The calendar prints parana bounds to the minute and **truncates** rather than rounds — verified
 * across seven years of its output, where the difference between our seconds-accurate solar times
 * and its printed minute is never negative and never reaches a full minute. So a delta of `+0.4`
 * minutes is not "close enough", it is exact agreement seen through a truncated print; the honest
 * acceptance band is `[0, 1)` minutes rather than a symmetric tolerance around zero. The bands
 * below are that interval plus a stated allowance, and each allowance names the physical
 * disagreement it is covering. They are deliberately too tight to absorb a rule error: a bound
 * derived from the wrong tithi is out by tens of minutes to hours, and a bound derived from the
 * wrong day by a day.
 */
class GaudiyaEkadashiConformanceTest {

    private val location = GeoLocation(
        GaudiyaGoldenCalendar.MAYAPUR_LATITUDE,
        GaudiyaGoldenCalendar.MAYAPUR_LONGITUDE,
        ZoneId.of(GaudiyaGoldenCalendar.MAYAPUR_ZONE),
    )
    private val ctx = ObservanceContext(PanchangCalculator(Vsop87Ephemeris()), location)
    private val rules = IskconRules()
    private val golden = GaudiyaGoldenCalendar.mayapur2026()

    private val decisions2026 = rules.ekadashiObservances(2026, ctx)

    /**
     * 2026's decisions plus 2025's, because the first parana of 2026 belongs to the last fast of
     * 2025 (31 December, an Ekadashi-kshaya deferral). Checking only the fasts *dated* 2026 would
     * quietly skip the 1 January window the calendar prints.
     */
    private val decisionsSpanningYearBoundary =
        rules.ekadashiObservances(2025, ctx) + decisions2026

    // ------------------------------------------------------------------ fasting days

    @Test
    fun `fasting dates match the published calendar`() {
        val expected = golden.filter { it.fastingFor != null }.map { it.date }.toSet()
        val actual = decisions2026.map { it.date }.toSet()

        assertEquals(
            expected.sorted(),
            actual.sorted(),
            "Ekadashi fasting dates disagree with the Mayapur 2026 calendar. " +
                "Missing: ${(expected - actual).sorted()}. Extra: ${(actual - expected).sorted()}.",
        )
    }

    @Test
    fun `every fast the calendar marks is emitted exactly once`() {
        val counts = decisions2026.groupingBy { it.date }.eachCount()
        assertTrue(
            counts.values.all { it == 1 },
            "duplicate decisions on ${counts.filterValues { it > 1 }.keys}",
        )
        // 24 in 2026 even though it is an adhika year: the extra lunar fortnight's Ekadashis
        // (Padmini on 27 May, Parama on 11 June) sit inside the same 365 civil days.
        assertEquals(24, decisions2026.size, "unexpected number of Ekadashi observances in 2026")
    }

    // ------------------------------------------------------------------ names

    @Test
    fun `Ekadashi names match the published calendar`() {
        val mismatches = mutableListOf<String>()
        for (day in golden.filter { it.fastingFor != null }) {
            val decision = decisions2026.firstOrNull { it.date == day.date } ?: continue
            val expected = properName(day.fastingFor!!)
            val actual = properName(decision.name)
            if (expected != actual) {
                mismatches += "${day.date}: calendar '$expected', rules '$actual'"
            }
        }
        assertTrue(
            mismatches.isEmpty(),
            "Ekadashi name mismatches:\n" + mismatches.joinToString("\n"),
        )
    }

    /**
     * Strips the trailing word so `Sat-tila Ekadasi` and `Sat-tila Ekadashi` compare equal.
     *
     * The two spellings of the tithi are transliteration, not disagreement; the proper name in
     * front of it is the part that carries a claim, and it is compared verbatim, so `Kamada` where
     * the calendar says `Varuthini` still fails.
     */
    private fun properName(full: String): String = full.trim().removeSuffix("Ekadashi")
        .removeSuffix("Ekadasi").trim()

    // ------------------------------------------------------------------ Mahadvadashis

    /**
     * The calendar's Mahadvadashi label text, by the type it names.
     *
     * Written out rather than derived from [MahadvadashiType.displayName] because the two differ
     * in transliteration (`Paksa vardhini` / `Paksavardhini`, `Vyanjuli` / `Vanjuli`) and deriving
     * it would make this test unable to detect a renamed constant.
     */
    private val labelToType: Map<String, MahadvadashiType> = mapOf(
        "Paksa vardhini Mahadvadasi" to MahadvadashiType.PAKSAVARDHINI,
        "Trisprsa Mahadvadasi" to MahadvadashiType.TRISPRSA,
        "Vyanjuli Mahadvadasi" to MahadvadashiType.VANJULI,
        "Unmilani Mahadvadasi" to MahadvadashiType.UNMILANI,
        "Jaya Mahadvadasi" to MahadvadashiType.JAYA,
        "Vijaya Mahadvadasi" to MahadvadashiType.VIJAYA,
        "Jayanti Mahadvadasi" to MahadvadashiType.JAYANTI,
        "Papanasini Mahadvadasi" to MahadvadashiType.PAPANASINI,
    )

    @Test
    fun `Mahadvadashi labels match the published calendar`() {
        val expected = golden.mapNotNull { day ->
            val label = day.events.firstOrNull { it in labelToType } ?: return@mapNotNull null
            day.date to labelToType.getValue(label)
        }.toMap()
        val actual = decisions2026.filter { it.mahadvadashiType != null }
            .associate { it.date to it.mahadvadashiType!! }

        assertEquals(
            expected.toSortedMap(),
            actual.toSortedMap(),
            "Mahadvadashi classification disagrees with the Mayapur 2026 calendar. The calendar " +
                "names five in 2026: Paksa vardhini on 26 June and 5 December, Trisprsa on 11 " +
                "July and 21 November, Vyanjuli on 24 August.",
        )
    }

    @Test
    fun `an unnamed deferral is not silently promoted to a Mahadvadashi`() {
        // 2026-08-24 is the year's only fast the calendar prints on a day marked
        // "Dvadasi (suitable for fasting)" *with* a Mahadvadashi line, and 2026-06-26 and
        // 2026-12-05 are the other two Dvadashi fasts. Every other 2026 fast sits on the
        // Ekadashi itself; if a viddha test started firing spuriously this would catch it.
        val fastsOnDvadashi = golden.filter { it.fastingFor != null && it.tithiName == "Dvadasi" }
            .map { it.date }
        assertEquals(
            listOf("2026-06-26", "2026-08-24", "2026-12-05").map(LocalDate::parse),
            fastsOnDvadashi,
            "the golden file itself changed shape; this test's premise needs rechecking",
        )
        for (date in fastsOnDvadashi) {
            val decision = decisions2026.first { it.date == date }
            assertTrue(
                decision.kind == FastKind.MAHADVADASHI,
                "$date is a deferred fast the calendar names a Mahadvadashi, but the rules " +
                    "returned ${decision.kind}",
            )
        }
    }

    // ------------------------------------------------------------------ parana

    /** The calendar's basis wording, by the reason it corresponds to. */
    private val basisToReason: Map<String, ParanaBoundReason> = mapOf(
        "sunrise" to ParanaBoundReason.SUNRISE,
        "1/4 of tithi" to ParanaBoundReason.HARI_VASARA_END,
        "end of tithi" to ParanaBoundReason.DVADASHI_END,
        "1/3 of daylight" to ParanaBoundReason.ONE_THIRD_DAYLIGHT,
        "end of naksatra" to ParanaBoundReason.NAKSHATRA_END,
    )

    /**
     * Solar bounds: `[0, 1)` for the truncated print, widened by a quarter minute either side.
     *
     * Our sunrise agrees with the calendar's to about a second, so nothing but the truncation is
     * being absorbed here. Fifteen seconds of slack covers the difference between its solar
     * algorithm and ours without admitting anything that could be a rule error.
     */
    private val solarBand = -0.25..1.25

    /**
     * Lunar bounds: the same interval, widened downwards by three minutes.
     *
     * Our tithi instants run systematically *early* against the calendar's — measured across
     * 2021-2027 at between 0.2 and 2.3 minutes, roughly 52 arcseconds of Moon−Sun elongation,
     * which is an ephemeris difference and not a rule difference. Three minutes covers the
     * observed spread with margin. This asymmetry is itself a finding and is recorded in
     * [IskconRules.provenanceNote]: it is harmless for a printed time, but a tithi ending within
     * two minutes of sunrise could in principle move a fasting day.
     */
    private val lunarBand = -3.0..1.25

    @Test
    fun `parana windows match the published calendar`() {
        val expectedDays = golden.filter { it.parana != null }
        assertEquals(25, expectedDays.size, "the golden file's parana count changed")

        val byDate = decisionsSpanningYearBoundary
            .mapNotNull { d -> d.parana?.let { it.date to d } }
            .toMap()
        val failures = mutableListOf<String>()

        for (day in expectedDays) {
            val decision = byDate[day.date]
            if (decision == null) {
                failures += "${day.date}: calendar prints a parana window, rules produced none"
                continue
            }
            val window = decision.parana!!
            val expected = day.parana!!

            val expectedStartReason = basisToReason[expected.startBasis]
            if (window.startReason != expectedStartReason) {
                failures += "${day.date} start basis: calendar '${expected.startBasis}' " +
                    "(${expectedStartReason ?: "unmapped"}), rules ${window.startReason}"
            }
            val expectedEndReason = expected.endBasis?.let { basisToReason[it] }
            if (window.endReason != expectedEndReason) {
                failures += "${day.date} end basis: calendar '${expected.endBasis}' " +
                    "(${expectedEndReason ?: "unmapped"}), rules ${window.endReason}"
            }

            checkBound(
                failures, day.date, "start",
                window.startJdUt, expected.start, window.startReason,
            )
            val expectedEnd = expected.end
            if (expectedEnd == null) {
                failures += "${day.date}: calendar prints no end bound; this suite assumes both"
            } else {
                checkBound(
                    failures, day.date, "end",
                    window.endJdUt, expectedEnd, window.endReason,
                )
            }
        }

        // The reverse direction: a window we produce on a day the calendar leaves blank is just
        // as wrong as a missing one, and would go unseen if only the calendar's days were walked.
        val printedDates = expectedDays.map { it.date }.toSet()
        for (decision in decisions2026) {
            val date = decision.parana?.date ?: continue
            if (date.year == 2026 && date !in printedDates) {
                failures += "$date: rules produced a parana window the calendar does not print"
            }
        }

        assertTrue(failures.isEmpty(), "parana disagreements:\n" + failures.joinToString("\n"))
    }

    private fun checkBound(
        failures: MutableList<String>,
        date: LocalDate,
        which: String,
        jdUt: Double,
        expectedText: String,
        reason: ParanaBoundReason,
    ) {
        val actual = location.zonedDateTime(jdUt)
        if (actual.toLocalDate() != date) {
            failures += "$date $which: rules put the bound on ${actual.toLocalDate()}"
            return
        }
        val actualMinutes = actual.hour * 60.0 + actual.minute + actual.second / 60.0 +
            actual.nano / 60.0e9
        val expectedMinutes = expectedText.substringBefore(':').toInt() * 60.0 +
            expectedText.substringAfter(':').toInt()
        val delta = actualMinutes - expectedMinutes
        val solar = reason == ParanaBoundReason.SUNRISE ||
            reason == ParanaBoundReason.ONE_THIRD_DAYLIGHT ||
            reason == ParanaBoundReason.SUNSET
        val band = if (solar) solarBand else lunarBand
        if (delta !in band) {
            failures += "$date $which ($reason): calendar $expectedText, rules " +
                "%02d:%02d:%02d, delta %+.2f min, outside %s"
                    .format(actual.hour, actual.minute, actual.second, delta, band)
        }
    }

    @Test
    fun `every 2026 fast has a parana window`() {
        val without = decisions2026.filter { it.parana == null }
        assertTrue(
            without.isEmpty(),
            "no parana window derived for ${without.map { it.date }}; each decision's reason: " +
                without.joinToString("\n") { it.reason },
        )
    }

    // ------------------------------------------------------------------ reasons and confidence

    @Test
    fun `every decision explains itself`() {
        for (decision in decisions2026) {
            assertTrue(
                decision.reason.length > 40 && decision.reason.trimEnd().endsWith("."),
                "${decision.date}: reason is not a sentence a devotee could be given: " +
                    "'${decision.reason}'",
            )
        }
    }

    @Test
    fun `the 2026 event catalog resolves with nothing left unexplained`() {
        // Not a duplicate of the catalog's own suite: this asserts the reporting channel added to
        // the seam actually carries failures, so that a future year in which something does not
        // resolve is visible to a caller rather than silently absent from events().
        val resolution = rules.eventResolution(2026, ctx)
        assertEquals(
            emptyMap<String, EventResolution>(),
            resolution.unresolved,
            "catalog entries produced no date for 2026",
        )
        assertEquals(resolution.events, rules.events(2026, ctx))
    }
}
