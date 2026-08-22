package org.panchang.sampradaya

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import org.panchang.core.GeoLocation
import org.panchang.core.PanchangCalculator
import org.panchang.core.RiseSet
import org.panchang.ephemeris.Vsop87Ephemeris

/**
 * The fourteen harvested sites, and one built-once fixture per site.
 *
 * ## Why this exists
 *
 * Until Wave 1 every observance test pinned Mayapur, so location-generality was asserted by
 * construction — "nothing reads a hardcoded coordinate" — and never by evidence. Ten published
 * calendars now exist and this object is what lets a suite walk all of them without paying the
 * index cost ten times per test method.
 *
 * ## Why the cache is not an optimisation detail
 *
 * [LunarDayIndex.build] solves roughly 850 sunrises and 900 tithi boundaries; it takes ten to
 * sixteen seconds per site on this hardware. `GaudiyaEkadashiConformanceTest` has eight test
 * methods, and JUnit constructs a fresh test instance for every one of them, so an index built in
 * a field would be built eighty times — twenty minutes for a suite whose useful work is two.
 * The cache is what makes a ten-site gate runnable at all, which is why it lives in an object
 * shared across test classes rather than in any one class's companion.
 *
 * The index window is the requested year ± [LunarDayIndex.WINDOW_MARGIN_DAYS] (60 days), so a
 * single 2026 index already contains the December 2025 fast whose parana falls on 1 January 2026.
 * That is why [GaudiyaSiteFixture.allDecisions] replaces the old "2025's decisions plus 2026's"
 * pair of index builds: same coverage, half the work.
 */
object GaudiyaSites {

    const val YEAR: Int = 2026

    /**
     * Every city id with a harvested calendar in `verify/golden`, in the order a reader would
     * want to see results: the two dhamas, then the rest of India, then west to east.
     */
    val ALL: List<String> = listOf(
        "mayapur",
        "vrindavan",
        "delhi",
        "mumbai",
        "chennai",
        "bangalore",
        "ahmedabad",
        "guwahati",
        "london",
        "new-york",
        "sao-paulo",
        "moscow",
        "auckland",
        "sydney",
    )

    /**
     * The eight sites that share `Asia/Kolkata`. Same civil clock, eight different skies.
     *
     * Ahmedabad (72E37) and Guwahati (91E44) are 19.1 degrees apart — 76 minutes of solar time
     * inside one offset — which is the requirement this project exists to satisfy, stated as a
     * pair of rows rather than as a claim.
     */
    val ASIA_KOLKATA: List<String> = listOf(
        "mayapur", "vrindavan", "delhi", "mumbai",
        "chennai", "bangalore", "ahmedabad", "guwahati",
    )

    /** The Indian sites, for suites that report on India specifically. Identical to [ASIA_KOLKATA]. */
    val INDIA: List<String> = ASIA_KOLKATA

    /**
     * One ephemeris and one calculator for the whole test JVM.
     *
     * Nothing in [PanchangCalculator] or [Vsop87Ephemeris] is mutated by these suites, and
     * building a VSOP87 table per site would add cost to a run already dominated by index builds.
     */
    val calculator: PanchangCalculator by lazy { PanchangCalculator(Vsop87Ephemeris()) }

    private val cache = ConcurrentHashMap<String, GaudiyaSiteFixture>()

    fun fixture(cityId: String): GaudiyaSiteFixture =
        cache.computeIfAbsent(cityId) { GaudiyaSiteFixture.build(it) }
}

/**
 * One site's oracle, coordinates, and the decisions our rules produce there.
 *
 * ## Coordinates come from the artifact, never from the reference grid
 *
 * [location] is built from the golden file's own `site` block. A reference has to be evaluated at
 * the coordinates it was actually computed for: the source prints Delhi as `28N40 77E13`
 * (28.667/77.217) where our reference grid carries 28.6139/77.2090, a difference of about
 * 12 seconds of sunrise. Substituting the grid's coordinates would make a datum disagreement and
 * a rule disagreement indistinguishable the moment a time is rounded to the printed minute, and
 * the whole value of this gate is that those two stay separable.
 *
 * The Mayapur file predates the site block and returns null from
 * [GaudiyaGoldenCalendar.site]; the `MAYAPUR_*` constants — themselves the source's own printed
 * `23N25 88E23` — cover that one case and only that one.
 */
class GaudiyaSiteFixture private constructor(
    val cityId: String,
    /** The city as the publisher names it, e.g. `Bombay [India]`. Empty for Mayapur. */
    val printedName: String,
    val location: GeoLocation,
    /** The standard (non-DST) offset the golden file states in its header, or null for Mayapur. */
    val printedUtcOffset: ZoneOffset?,
    val golden: List<GaudiyaGoldenCalendar.GoldenDay>,
) {

    val ctx: ObservanceContext = ObservanceContext(GaudiyaSites.calculator, location)

    /** Built once per site, on first use. See [GaudiyaSites]' note on why this matters. */
    val index: LunarDayIndex by lazy { LunarDayIndex.build(GaudiyaSites.YEAR, ctx) }

    /**
     * Every decision in the index window, unfiltered by year.
     *
     * The window runs from 60 days before 1 January to 60 days after 31 December, so this
     * includes the previous December's last fast — whose parana window the calendar prints on
     * 1 January and which a year-filtered list would silently omit.
     */
    val allDecisions: List<ObservanceDecision> by lazy {
        IskconRules().allObservancesInWindow(index).sortedBy { it.date }
    }

    /** The decisions dated in [GaudiyaSites.YEAR]; identical to `ekadashiObservances(YEAR, ctx)`. */
    val decisions: List<ObservanceDecision> by lazy {
        allDecisions.filter { it.date.year == GaudiyaSites.YEAR }
    }

    /** Our parana windows by the civil date they fall on, across the whole index window. */
    val paranaByDate: Map<LocalDate, ObservanceDecision> by lazy {
        allDecisions.mapNotNull { d -> d.parana?.let { it.date to d } }.toMap()
    }

    /** The golden days that print a parana window, in date order. */
    val goldenParanaDays: List<GaudiyaGoldenCalendar.GoldenDay> by lazy {
        golden.filter { it.parana != null }
    }

    /** The golden days the calendar marks as fasting days, in date order. */
    val goldenFastDays: List<GaudiyaGoldenCalendar.GoldenDay> by lazy {
        golden.filter { it.fastingFor != null }
    }

    /**
     * The days in [GaudiyaSites.YEAR] on which the source prints a start but declines to state a
     * cap — its "Break fast after…" rows. See [UncappedParana].
     */
    val goldenUncappedParanaDates: Set<LocalDate> by lazy {
        goldenParanaDays.filter { it.parana!!.end == null }.map { it.date }.toSet()
    }

    /**
     * The days in [GaudiyaSites.YEAR] on which our rules decline to give a window, by the date the
     * window would have fallen on rather than by the fasting date.
     *
     * Keyed on the window's day so it can be compared directly with the source's printed rows.
     */
    val ruleRefusedParanaDates: Set<LocalDate> by lazy {
        allDecisions.filter { it.parana == null }
            .map { it.date.plusDays(1) }
            .filter { it.year == GaudiyaSites.YEAR }
            .toSet()
    }

    /**
     * The end of Hari Vasara — the first quarter of the Dvadashi — falling on [date], or null.
     *
     * Re-derived here from the index's own tithi spans rather than read off a [ParanaWindow],
     * because the rows this is needed for are exactly the ones where no window exists. It uses
     * only public data and the definition the source itself prints as `1/4 of tithi`, so it is an
     * independent check of the bound and not a restatement of whatever [IskconRules] computed.
     */
    fun hariVasaraEndOn(date: LocalDate): Double? = index.spans().asSequence()
        .filter { it.numberInPaksha == DVADASHI_NUMBER_IN_PAKSHA }
        .map { it.startJdUt + (it.endJdUt - it.startJdUt) / 4.0 }
        .firstOrNull { location.localDate(it) == date }

    /** The instant [jdUt] as civil time at this site. */
    fun civil(jdUt: Double): ZonedDateTime = location.zonedDateTime(jdUt)

    /** Minutes past local civil midnight of [jdUt], to sub-second resolution. */
    fun minutesOfDay(jdUt: Double): Double = with(civil(jdUt)) {
        hour * 60.0 + minute + second / 60.0 + nano / 60.0e9
    }

    override fun toString(): String = "$cityId (${location.latitude}, ${location.longitude}, " +
        "${location.zone})"

    companion object {

        private const val DVADASHI_NUMBER_IN_PAKSHA = 12

        internal fun build(cityId: String): GaudiyaSiteFixture {
            val site = GaudiyaGoldenCalendar.site(cityId, GaudiyaSites.YEAR)
            val golden = GaudiyaGoldenCalendar.load(cityId, GaudiyaSites.YEAR)
            if (site == null) {
                check(cityId == "mayapur") {
                    "golden calendar for '$cityId' states no site block. Only the Mayapur file " +
                        "predates the block; every other file must say where it was computed, " +
                        "because evaluating it anywhere else proves nothing."
                }
                return GaudiyaSiteFixture(
                    cityId = cityId,
                    printedName = "",
                    location = GeoLocation(
                        GaudiyaGoldenCalendar.MAYAPUR_LATITUDE,
                        GaudiyaGoldenCalendar.MAYAPUR_LONGITUDE,
                        ZoneId.of(GaudiyaGoldenCalendar.MAYAPUR_ZONE),
                    ),
                    printedUtcOffset = null,
                    golden = golden,
                )
            }
            check(site.cityId.isEmpty() || site.cityId == cityId) {
                "golden calendar file for '$cityId' describes itself as '${site.cityId}'"
            }
            return GaudiyaSiteFixture(
                cityId = cityId,
                printedName = site.city,
                location = GeoLocation(
                    site.latitudeDeg,
                    site.longitudeDeg,
                    ZoneId.of(site.ianaZone),
                ),
                printedUtcOffset = parseOffset(site.utcOffset),
                golden = golden,
            )
        }

        /** Parses the header's `+5:30` / `-3:00` / `+0:00` into a [ZoneOffset]. */
        private fun parseOffset(text: String): ZoneOffset {
            val sign = if (text.startsWith("-")) -1 else 1
            val body = text.removePrefix("+").removePrefix("-")
            val hours = body.substringBefore(':').toInt()
            val minutes = body.substringAfter(':').toInt()
            return ZoneOffset.ofTotalSeconds(sign * (hours * 3600 + minutes * 60))
        }
    }
}

/**
 * The one row where the calendar and these rules name different bounds that are the same instant.
 *
 * ## What was measured
 *
 * Ahmedabad, parana of 2026-11-06. The calendar prints `10:31` and calls it `1/3 of daylight`;
 * these rules give 10:31:04 and call it `end of tithi`. Both bounds exist, both are computed, and
 * they are **0.93 seconds apart**:
 *
 * | | instant (IST) |
 * |---|---|
 * | end of the Dvadashi | 10:31:04.467 |
 * | end of the first third of daylight | 10:31:05.395 |
 *
 * [ParanaBasisTie.gapSeconds] recomputes that gap from the site's own index rather than trusting
 * the numbers above, so this exception cannot outlive the condition that justifies it.
 *
 * ## Why this is not a rule error
 *
 * The rule takes whichever cap comes first, which is the correct reading and is why it names the
 * Dvadashi here. The two candidates are only this close at this longitude: on the same date the
 * gap at the seven other Indian sites runs from 2.58 minutes (Mumbai) to 75.05 (Guwahati). A rule
 * that picked the wrong cap would miss by minutes everywhere, not by a second in one place.
 *
 * 0.93 s is well inside our own input error — tithi instants agree with JPL to about 17 s and
 * sunrise with USNO to about 28 s — so no ephemeris work can decide which name is right, and the
 * devotee is given 10:31 either way.
 *
 * ## Why a named tie and not a widened band
 *
 * The clock time is *not* excluded: the printed minute is still asserted and agrees to +0.07 min.
 * Only the *label* is excepted, and only where [MAX_GAP_SECONDS] holds. A tolerance on the basis
 * comparison would let a bound taken from the wrong tithi — tens of minutes out — pass under the
 * same allowance.
 */
object ParanaBasisTie {

    /**
     * How close two candidate caps must be before disagreeing about which one to name is a tie
     * rather than an error.
     *
     * Two seconds: above the 0.93 s measured here so the assertion is not knife-edged against
     * itself, and far below the ~17 s at which our own tithi instants are uncertain, so it cannot
     * absorb a disagreement that better inputs would have settled.
     */
    const val MAX_GAP_SECONDS: Double = 2.0

    /** The rows this applies to, keyed `<cityId> <paranaDate> <start|end>`. */
    val ROWS: Set<String> = setOf("ahmedabad 2026-11-06 end")

    fun covers(cityId: String, date: LocalDate, which: String): Boolean =
        "$cityId $date $which" in ROWS

    /**
     * Seconds between the two caps this rule chooses among on [date] at [site], recomputed.
     *
     * Derives both from the site's own index — the Dvadashi's end from the tithi spans, the first
     * third of daylight from that day's sunrise and daylight length — so a run where the
     * astronomy has moved reports the real gap instead of the one written in the KDoc above.
     */
    fun gapSeconds(site: GaudiyaSiteFixture, date: LocalDate): Double? {
        val sun = GaudiyaSites.calculator.sunTimes(date, site.location)
        val sunrise = (sun.sunrise as? RiseSet.At)?.jdUt ?: return null
        val daylight = sun.daylightDays?.takeIf { it > 0.0 } ?: return null
        val oneThird = sunrise + daylight / 3.0
        val dvadashiEnd = site.index.spans().asSequence()
            .filter { it.numberInPaksha == 12 }
            .map { it.endJdUt }
            .firstOrNull { site.location.localDate(it) == date }
            ?: return null
        return kotlin.math.abs(oneThird - dvadashiEnd) * 86_400.0
    }
}

/**
 * The three days on which the source prints a parana start and no cap, and what they turned out
 * to be.
 *
 * ## What was expected, and what was found
 *
 * These were expected to be rows where the publisher had simply declined to state an end. They
 * are not. On all three the source's own two caps are in conflict: **Hari Vasara — the first
 * quarter of the Dvadashi, before whose end the fast may not be broken — ends after the first
 * third of daylight, before whose end it must be broken.** The interval is empty, and the source
 * resolves that by printing the start and dropping the cap.
 *
 * | Site | Window | Hari Vasara ends | First third of daylight ends |
 * |---|---|---|---|
 * | mumbai | 2026-08-24 | 10:49:47 | 10:34:39 |
 * | auckland | 2026-09-23 | 10:30:39 | 10:12:24 |
 * | new-york | 2026-10-22 | 11:15:28 | 10:51:46 |
 *
 * Our rules meet the same conflict on the same day at the same site and resolve it the other way:
 * [IskconRules] refuses to emit an inverted window and says in the decision's own reason that the
 * case needs a pandit's ruling rather than a computed answer. **Both programs detect the same
 * conflict from the same geometry; they differ only in what to do about it, and that difference is
 * doctrinal, not astronomical.**
 *
 * ## What is asserted
 *
 * The start bound, and the correspondence. No cap is invented — a fabricated end would be a claim
 * about a tradition that the only reference available declines to make — and no assertion is
 * deleted, which would quietly stop checking the start as well. The start we would derive is
 * recomputed from the index's tithi spans by [GaudiyaSiteFixture.hariVasaraEndOn] and compared
 * with the printed one; and `MultiSiteParanaConformanceTest` asserts across all fourteen sites that the
 * days the source declines to cap are **exactly** the days our rules decline to bound. That
 * equality is the evidence that matters: three sites, three dates, and no site carrying one
 * without the other.
 *
 * Which of the two resolutions a devotee should follow is an open question for a pandit. It is
 * recorded in `docs/validation-multisite.md` and is not settled by any tolerance here.
 */
object UncappedParana {

    /** The printed start basis on every one of these rows. Asserted, not assumed. */
    const val START_BASIS: String = "1/4 of tithi"
}

/**
 * The one named exclusion this gate carries, and the reason it is named rather than absorbed.
 *
 * ## What is wrong with the reference
 *
 * GCal 11 Build 5 applies daylight saving at Moscow and São Paulo in 2026. Russia abolished
 * daylight saving in 2011 and Brazil in 2019, so neither country has any in 2026. The source's
 * own parana rows say so: fifteen Moscow windows carry the `DST` stamp between 2026-03-30 and
 * 2026-10-23 (the pre-2011 Russian rule, which followed the EU dates), and nine São Paulo
 * windows carry it in 2026-01-15…02-28 and 2026-10-23…12-21 (the pre-2019 Brazilian rule).
 *
 * The stamp is not merely a label on an otherwise-correct time. Moscow's 2026-06-12 parana starts
 * at a printed `04:45`, where sunrise at 55°45′N 37°37′E on that date is about `03:45` MSK. The
 * printed clock time is a real hour ahead of the site's real civil time. London, New York,
 * Auckland and Sydney all stamp `DST` on exactly the days their zones really are in daylight
 * saving in 2026, so this is specific to these two cities: it is a defect in the reference, not
 * in us.
 *
 * ## Why exclusion rather than tolerance
 *
 * A tolerance wide enough to swallow an hour would swallow every rule error this suite exists to
 * catch — a bound taken from the wrong tithi is out by tens of minutes. An exclusion is visible
 * in the failure count, is scoped to the exact rows that carry the defect, and is pinned by
 * `MultiSiteParanaConformanceTest.the stale-DST exclusion still describes the reference`, which
 * fails if a re-harvested calendar ever stops carrying it. A reader can see this and challenge
 * it; a widened band would leave them nothing to challenge.
 *
 * Only the *clock-time comparison* is excluded. Fasting dates, Ekadashi names, Mahadvadashi
 * labels and the bound *bases* at these sites are all still compared, because an offset applied
 * to a printed time cannot move any of them.
 */
object StaleReferenceDst {

    /** The two cities whose country abolished daylight saving before 2026. */
    val CITIES: Set<String> = setOf("moscow", "sao-paulo")

    /** The source's stamp on a parana row it believes to be in daylight saving. */
    const val STAMP: String = "DST"

    /** Expected number of affected parana rows per city; asserted, not assumed. */
    val EXPECTED_ROWS: Map<String, Int> = mapOf("moscow" to 15, "sao-paulo" to 9)

    fun excludes(cityId: String, parana: GaudiyaGoldenCalendar.GoldenParana): Boolean =
        cityId in CITIES && parana.clock == STAMP

    const val REASON: String =
        "the reference applies daylight saving this country abolished before 2026 " +
            "(Russia 2011, Brazil 2019); see StaleReferenceDst"
}
