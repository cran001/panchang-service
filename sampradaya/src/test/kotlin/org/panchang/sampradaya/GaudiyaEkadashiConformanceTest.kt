package org.panchang.sampradaya

import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.panchang.core.GeoLocation

/**
 * [IskconRules] against the published Gaudiya calendar at **all ten harvested sites**.
 *
 * The oracles are `verify/golden/vaisnavacalendar-<city>-2026.json`, GCal 11 Build 5's own output
 * for the sites whose coordinates each file prints in its own `site` block. Where this suite and
 * those files disagree, the presumption is that these rules are wrong.
 *
 * ## Why ten sites and not one
 *
 * Through Wave 0 this suite pinned Mayapur, and every other observance test still does. That made
 * location-generality a claim about the source — "no coordinate is hardcoded; everything flows
 * from `ObservanceContext.location`" — rather than a claim about behaviour, and that is a weaker
 * claim than it looks. Delhi and Mumbai share `Asia/Kolkata`, and the published calendars
 * disagree about *which day to fast* on two occasions in 2026 (Delhi 26 June and 24 August;
 * Mumbai 25 June and 23 August). A suite that only ever evaluates one site cannot tell whether a
 * rule reproduces that or merely reproduces Mayapur. Every assertion below is now evidence at ten
 * latitudes, ten longitudes and eight zones, four of them southern-hemisphere and six of them
 * observing daylight saving.
 *
 * ## Coordinates
 *
 * From each artifact's own site block, never from `ReferenceCities` — see [GaudiyaSiteFixture].
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
 *
 * **The bands were not widened when this suite went multi-site.** They are the Mayapur values
 * unchanged. A band widened until a gate goes green measures nothing; where a site disagrees, the
 * disagreement is recorded and classified in `docs/validation-multisite.md`.
 */
class GaudiyaEkadashiConformanceTest {

    // ------------------------------------------------------------------ fasting days

    @ParameterizedTest(name = "{0}")
    @MethodSource("sites")
    fun `fasting dates match the published calendar`(cityId: String) {
        val site = GaudiyaSites.fixture(cityId)
        val expected = site.goldenFastDays.map { it.date }.toSet()
        val actual = site.decisions.map { it.date }.toSet()

        assertEquals(
            expected.sorted(),
            actual.sorted(),
            "Ekadashi fasting dates disagree with the $cityId 2026 calendar. " +
                "Missing: ${(expected - actual).sorted()}. Extra: ${(actual - expected).sorted()}. " +
                "A fasting-date disagreement is a far stronger signal than a label disagreement " +
                "and must be investigated as a rule error first; see docs/validation-multisite.md.",
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sites")
    fun `every fast the calendar marks is emitted exactly once`(cityId: String) {
        val site = GaudiyaSites.fixture(cityId)
        val counts = site.decisions.groupingBy { it.date }.eachCount()
        assertTrue(
            counts.values.all { it == 1 },
            "duplicate decisions at $cityId on ${counts.filterValues { it > 1 }.keys}",
        )
        // 24 everywhere in 2026 even though it is an adhika year: the extra lunar fortnight's
        // Ekadashis (Padmini and Parama, in late May and mid June) sit inside the same 365 civil
        // days. Read from the site's own oracle rather than hardcoded, but asserted to be 24
        // first, so a golden file that lost records fails here rather than silently weakening the
        // comparison to whatever it still contains.
        assertEquals(24, site.goldenFastDays.size, "the $cityId golden file's fast count changed")
        assertEquals(
            site.goldenFastDays.size,
            site.decisions.size,
            "unexpected number of Ekadashi observances at $cityId in 2026",
        )
    }

    // ------------------------------------------------------------------ names

    @ParameterizedTest(name = "{0}")
    @MethodSource("sites")
    fun `Ekadashi names match the published calendar`(cityId: String) {
        val site = GaudiyaSites.fixture(cityId)
        val mismatches = mutableListOf<String>()
        for (day in site.goldenFastDays) {
            val decision = site.decisions.firstOrNull { it.date == day.date } ?: continue
            val expected = properName(day.fastingFor!!)
            val actual = properName(decision.name)
            if (expected != actual) {
                mismatches += "${day.date}: calendar '$expected', rules '$actual'"
            }
        }
        assertTrue(
            mismatches.isEmpty(),
            "Ekadashi name mismatches at $cityId:\n" + mismatches.joinToString("\n"),
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("sites")
    fun `Mahadvadashi labels match the published calendar`(cityId: String) {
        val site = GaudiyaSites.fixture(cityId)
        val expected = site.golden.mapNotNull { day ->
            val label = day.events.firstOrNull { it in LABEL_TO_TYPE } ?: return@mapNotNull null
            day.date to LABEL_TO_TYPE.getValue(label)
        }.toMap()
        val actual = site.decisions.filter { it.mahadvadashiType != null }
            .associate { it.date to it.mahadvadashiType!! }

        assertEquals(
            expected.toSortedMap(),
            actual.toSortedMap(),
            "Mahadvadashi classification disagrees with the $cityId 2026 calendar. A *label* " +
                "disagreement on a fortnight whose fasting date still matches is the outcome ADR " +
                "0002 predicts from the reference's ephemeris (docs/decisions/" +
                "0002-reference-calendar-disagreement.md); a disagreement that also moves the " +
                "fasting date is not, and is a rule error until shown otherwise.",
        )
    }

    /**
     * A fast the calendar moves to the Dvadashi is a Mahadvadashi exactly when it says so.
     *
     * Both directions matter, and which days are in which set is itself site-dependent. At
     * Mayapur all three Dvadashi fasts of 2026 carry a Mahadvadashi line; at Vrindavan and Delhi
     * two of the four do not, and at Mumbai neither does. A fast deferred to the Dvadashi by an
     * ordinary viddha test is *not* a Mahadvadashi, and promoting it to one gives a devotee the
     * wrong name and the wrong parana rule. Checking only "is it labelled" at Mayapur could never
     * see that, because at Mayapur the two sets happen to coincide.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("sites")
    fun `a deferral is a Mahadvadashi exactly when the calendar names one`(cityId: String) {
        val site = GaudiyaSites.fixture(cityId)
        val fastsOnDvadashi = site.goldenFastDays.filter { it.tithiName == "Dvadasi" }
        assertTrue(
            fastsOnDvadashi.isNotEmpty(),
            "the $cityId golden file marks no fast on a Dvadasi, so this test's premise — that " +
                "the site exercises the deferral path at all — no longer holds. Recheck the file " +
                "rather than deleting the test.",
        )

        val failures = mutableListOf<String>()
        for (day in fastsOnDvadashi) {
            // A missing decision is the fasting-date test's failure; do not report it twice.
            val decision = site.decisions.firstOrNull { it.date == day.date } ?: continue
            val named = day.events.firstOrNull { it in LABEL_TO_TYPE }
            val isMahadvadashi = decision.kind == FastKind.MAHADVADASHI
            if (named != null && !isMahadvadashi) {
                failures += "${day.date}: calendar names a Mahadvadashi ('$named'), rules " +
                    "returned ${decision.kind}"
            }
            if (named == null && isMahadvadashi) {
                failures += "${day.date}: an unnamed deferral was promoted to " +
                    "${decision.mahadvadashiType}; the calendar prints no Mahadvadashi line here"
            }
        }
        assertTrue(
            failures.isEmpty(),
            "deferral classification at $cityId:\n" + failures.joinToString("\n"),
        )
    }

    // ------------------------------------------------------------------ parana

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
     * [IskconRules.provenanceNote] and in ADR 0002: it is harmless for a printed time, but a
     * tithi ending within two minutes of sunrise could in principle move a fasting day.
     */
    private val lunarBand = -3.0..1.25

    @ParameterizedTest(name = "{0}")
    @MethodSource("sites")
    fun `parana windows match the published calendar`(cityId: String) {
        val site = GaudiyaSites.fixture(cityId)
        val expectedDays = site.goldenParanaDays
        assertTrue(
            expectedDays.size in 24..25,
            "the $cityId golden file prints ${expectedDays.size} parana windows; 24 or 25 is the " +
                "shape every harvested file has (25 when the previous December's fast puts a " +
                "window on 1 January), so this file has changed shape",
        )

        val failures = mutableListOf<String>()
        var excludedStaleDst = 0
        var openEnded = 0

        for (day in expectedDays) {
            val expected = day.parana!!
            if (StaleReferenceDst.excludes(cityId, expected)) {
                // A named exclusion, not a widened band. See [StaleReferenceDst] for why, and for
                // what is still compared on these days — everything except the printed clock time.
                excludedStaleDst++
                continue
            }
            val decision = site.paranaByDate[day.date]
            if (decision == null) {
                // Category (b): the source printed a start and no cap, and our rules refused the
                // whole window, both because Hari Vasara outlasts the first third of daylight
                // here. The start is still asserted, against a bound re-derived from the tithi
                // spans rather than from a window that by construction does not exist. See
                // [UncappedParana].
                if (expected.end == null) {
                    openEnded++
                    checkUncapped(failures, site, day.date, expected)
                } else {
                    failures += "${day.date}: calendar prints a parana window, rules produced none"
                }
                continue
            }
            val window = decision.parana!!

            val expectedStartReason = BASIS_TO_REASON[expected.startBasis]
            if (window.startReason != expectedStartReason) {
                failures += "${day.date} start basis: calendar '${expected.startBasis}' " +
                    "(${expectedStartReason ?: "unmapped"}), rules ${window.startReason}"
            }
            checkBound(
                failures, site, day.date, "start",
                window.startJdUt, expected.start, window.startReason,
            )

            val expectedEnd = expected.end
            if (expectedEnd == null) {
                // The source printed no cap but our rules did produce a window. That is a real
                // disagreement about whether the window exists at all, not the [UncappedParana]
                // correspondence, and it is reported rather than allowed.
                openEnded++
                failures += "${day.date}: calendar prints a start with no cap, but the rules " +
                    "produced a bounded window ending ${site.civil(window.endJdUt).toLocalTime()} " +
                    "(${window.endReason}); the two disagree about whether a window exists here"
            } else {
                val expectedEndReason = expected.endBasis?.let { BASIS_TO_REASON[it] }
                if (window.endReason != expectedEndReason) {
                    failures += "${day.date} end basis: calendar '${expected.endBasis}' " +
                        "(${expectedEndReason ?: "unmapped"}), rules ${window.endReason}"
                }
                checkBound(
                    failures, site, day.date, "end",
                    window.endJdUt, expectedEnd, window.endReason,
                )
            }
        }

        // The reverse direction: a window we produce on a day the calendar leaves blank is just
        // as wrong as a missing one, and would go unseen if only the calendar's days were walked.
        val printedDates = expectedDays.map { it.date }.toSet()
        for (decision in site.decisions) {
            val date = decision.parana?.date ?: continue
            if (date.year == GaudiyaSites.YEAR && date !in printedDates) {
                failures += "$date: rules produced a parana window the calendar does not print"
            }
        }

        println(
            "parana conformance $cityId: ${expectedDays.size} printed, $excludedStaleDst " +
                "excluded as stale reference DST, $openEnded open-ended (start-only), " +
                "${failures.size} disagreements",
        )
        assertTrue(
            failures.isEmpty(),
            "parana disagreements at $cityId:\n" + failures.joinToString("\n"),
        )
    }

    /**
     * The start bound of a row the source declined to cap, checked without a window to read it
     * from.
     *
     * The bound is recomputed from the index's own Dvadashi spans, so this is an independent
     * derivation of `1/4 of tithi` rather than a restatement of what [IskconRules] produced — which
     * matters, because what [IskconRules] produced here is nothing. See [UncappedParana].
     */
    private fun checkUncapped(
        failures: MutableList<String>,
        site: GaudiyaSiteFixture,
        date: LocalDate,
        expected: GaudiyaGoldenCalendar.GoldenParana,
    ) {
        if (expected.startBasis != UncappedParana.START_BASIS) {
            failures += "$date: the calendar prints an uncapped window whose start basis is " +
                "'${expected.startBasis}'; every uncapped row observed so far is Hari Vasara " +
                "('${UncappedParana.START_BASIS}') outlasting the first third of daylight, so " +
                "this row is a different phenomenon and needs looking at"
            return
        }
        val hariVasaraEnd = site.hariVasaraEndOn(date)
        if (hariVasaraEnd == null) {
            failures += "$date: the calendar prints a start of ${expected.start} basis " +
                "'${expected.startBasis}', but no Dvadashi in the index has its first quarter " +
                "ending on this date"
            return
        }
        checkBound(
            failures, site, date, "start (uncapped)",
            hariVasaraEnd, expected.start, ParanaBoundReason.HARI_VASARA_END,
        )
    }

    private fun checkBound(
        failures: MutableList<String>,
        site: GaudiyaSiteFixture,
        date: LocalDate,
        which: String,
        jdUt: Double,
        expectedText: String,
        reason: ParanaBoundReason,
    ) {
        val actual = site.civil(jdUt)
        if (actual.toLocalDate() != date) {
            failures += "$date $which: rules put the bound on ${actual.toLocalDate()}"
            return
        }
        val actualMinutes = site.minutesOfDay(jdUt)
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

    /**
     * Every fast gets a parana window, except where the source declines to bound one either.
     *
     * The exception is not a licence: it is the equality asserted in
     * `MultiSiteParanaConformanceTest.the source's uncapped windows are exactly the days the rules
     * refuse to bound one`, restated per site so that a refusal on a day the source *does* cap
     * fails here with that site's name on it. See [UncappedParana] for what these days are.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("sites")
    fun `every 2026 fast has a parana window unless the calendar declines to bound one`(
        cityId: String,
    ) {
        val site = GaudiyaSites.fixture(cityId)
        val unexplained = site.decisions
            .filter { it.parana == null && it.date.plusDays(1) !in site.goldenUncappedParanaDates }
        assertTrue(
            unexplained.isEmpty(),
            "no parana window derived at $cityId for ${unexplained.map { it.date }}, and the " +
                "calendar does print a bounded window on the following day; each decision's " +
                "reason: " + unexplained.joinToString("\n") { it.reason },
        )
    }

    // ------------------------------------------------------------------ reasons and confidence

    @ParameterizedTest(name = "{0}")
    @MethodSource("sites")
    fun `every decision explains itself`(cityId: String) {
        for (decision in GaudiyaSites.fixture(cityId).decisions) {
            assertTrue(
                decision.reason.length > 40 && decision.reason.trimEnd().endsWith("."),
                "$cityId ${decision.date}: reason is not a sentence a devotee could be given: " +
                    "'${decision.reason}'",
            )
        }
    }

    /**
     * The fixture really is built from the artifact's own site block, at every site.
     *
     * Without this, the coordinate discipline described in [GaudiyaSiteFixture] is a comment. A
     * future refactor that reached for `ReferenceCities` instead would still pass most of the
     * conformance assertions at most sites, because most of the differences are under a minute —
     * and would silently reintroduce the datum-versus-rule ambiguity the site block exists to
     * remove.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("sites")
    fun `the fixture uses the coordinates the reference was computed at`(cityId: String) {
        val site = GaudiyaSites.fixture(cityId)
        val stated = GaudiyaGoldenCalendar.site(cityId, GaudiyaSites.YEAR)
        if (stated == null) {
            assertEquals("mayapur", cityId, "only the Mayapur file may omit its site block")
            assertEquals(
                GeoLocation(
                    GaudiyaGoldenCalendar.MAYAPUR_LATITUDE,
                    GaudiyaGoldenCalendar.MAYAPUR_LONGITUDE,
                    ZoneId.of(GaudiyaGoldenCalendar.MAYAPUR_ZONE),
                ),
                site.location,
                "the Mayapur fixture must use the source's own printed 23N25 88E23",
            )
            return
        }
        assertEquals(stated.latitudeDeg, site.location.latitude, 0.0, "$cityId latitude")
        assertEquals(stated.longitudeDeg, site.location.longitude, 0.0, "$cityId longitude")
        assertEquals(ZoneId.of(stated.ianaZone), site.location.zone, "$cityId zone")
    }

    /**
     * Mayapur only, and deliberately.
     *
     * This asserts the *reporting channel* on the resolver seam — that a catalog entry which
     * produces no date is surfaced to a caller rather than silently absent from `events()`. That
     * is a fact about the seam, not about a location, and `eventResolution(year, ctx)` builds its
     * own [LunarDayIndex] rather than reusing the fixture's, so running it at ten sites would add
     * twenty index builds — three to five minutes — to buy no additional evidence. Catalog
     * conformance across sites, if it is ever wanted, belongs in
     * [IskconEventCatalogConformanceTest], where the catalog's oracle mapping already lives.
     */
    @Test
    fun `the 2026 event catalog resolves with nothing left unexplained at Mayapur`() {
        val ctx = GaudiyaSites.fixture("mayapur").ctx
        val rules = IskconRules()
        val resolution = rules.eventResolution(GaudiyaSites.YEAR, ctx)
        assertEquals(
            emptyMap<String, EventResolution>(),
            resolution.unresolved,
            "catalog entries produced no date for 2026",
        )
        assertEquals(resolution.events, rules.events(GaudiyaSites.YEAR, ctx))
    }

    companion object {

        @JvmStatic
        fun sites(): List<String> = GaudiyaSites.ALL

        /**
         * The calendar's Mahadvadashi label text, by the type it names.
         *
         * Written out rather than derived from [MahadvadashiType.displayName] because the two
         * differ in transliteration (`Paksa vardhini` / `Paksavardhini`, `Vyanjuli` / `Vanjuli`)
         * and deriving it would make this test unable to detect a renamed constant.
         */
        private val LABEL_TO_TYPE: Map<String, MahadvadashiType> = mapOf(
            "Paksa vardhini Mahadvadasi" to MahadvadashiType.PAKSAVARDHINI,
            "Trisprsa Mahadvadasi" to MahadvadashiType.TRISPRSA,
            "Vyanjuli Mahadvadasi" to MahadvadashiType.VANJULI,
            "Unmilani Mahadvadasi" to MahadvadashiType.UNMILANI,
            "Jaya Mahadvadasi" to MahadvadashiType.JAYA,
            "Vijaya Mahadvadasi" to MahadvadashiType.VIJAYA,
            "Jayanti Mahadvadasi" to MahadvadashiType.JAYANTI,
            "Papanasini Mahadvadasi" to MahadvadashiType.PAPANASINI,
        )

        /** The calendar's basis wording, by the reason it corresponds to. */
        private val BASIS_TO_REASON: Map<String, ParanaBoundReason> = mapOf(
            "sunrise" to ParanaBoundReason.SUNRISE,
            "1/4 of tithi" to ParanaBoundReason.HARI_VASARA_END,
            "end of tithi" to ParanaBoundReason.DVADASHI_END,
            "1/3 of daylight" to ParanaBoundReason.ONE_THIRD_DAYLIGHT,
            "end of naksatra" to ParanaBoundReason.NAKSHATRA_END,
        )
    }
}
