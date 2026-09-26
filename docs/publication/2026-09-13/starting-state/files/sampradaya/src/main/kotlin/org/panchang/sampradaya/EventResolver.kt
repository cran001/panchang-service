package org.panchang.sampradaya

import java.time.LocalDate
import java.util.WeakHashMap
import org.panchang.core.GeoLocation
import org.panchang.core.LunarMonth
import org.panchang.core.MonthReckoning
import org.panchang.core.Paksha
import org.panchang.core.RiseSet
import org.panchang.core.SunTimes
import org.panchang.core.Tithi

/**
 * One tithi occurrence in a [LunarDayIndex]'s window, tagged with the month that contains it.
 *
 * A *tithi occurrence*, not a day. The two are not in bijection and the difference is the whole
 * problem: in Mayapur 2026 the Chaturthi of Ashadha's bright fortnight begins after sunrise on
 * 17 July and ends before sunrise on 18 July, so it occupies no day at all, while the Ashtami of
 * the same fortnight runs across both the 21st and the 22nd. Indexing by day would silently lose
 * the first and double-count the second.
 *
 * Instants are Julian Day in **UT**, matching `core`.
 */
data class TithiSpan(
    /** 0–29, as `core.Tithi.index`. */
    val tithiIndex: Int,
    val startJdUt: Double,
    val endJdUt: Double,
    /**
     * Index of the **amanta** month containing this tithi, 0 = Chaitra.
     *
     * Stored amanta whatever the caller's reckoning, because that is the cycle adhika status is
     * defined on and the purnimanta name is a pure function of it — see [monthIndex].
     */
    val amantaMonthIndex: Int,
    /** True when the containing month is intercalary (adhika / mala / Purusottama). */
    val isAdhika: Boolean,
) {
    init {
        require(tithiIndex in 0..29) { "tithi index must be in 0..29, was $tithiIndex" }
        require(endJdUt > startJdUt) { "tithi span $tithiIndex has non-positive duration" }
    }

    val paksha: Paksha get() = if (tithiIndex < 15) Paksha.SHUKLA else Paksha.KRISHNA

    /** 1–15. Krishna 15 is Amavasya; Shukla 15 is Purnima. */
    val numberInPaksha: Int get() = tithiIndex % 15 + 1

    /**
     * The month name this tithi carries under [reckoning], as an index into [LunarMonth.NAMES].
     *
     * Purnimanta runs full moon to full moon, so its Krishna paksha precedes its Shukla paksha
     * and every Krishna fortnight carries the *next* month's name. Shukla fortnights are named
     * identically under both conventions. This mirrors `PanchangCalculator.lunarMonthAt` exactly;
     * it is restated here only so a single amanta lookup can serve both reckonings.
     */
    fun monthIndex(reckoning: MonthReckoning): Int = when (reckoning) {
        MonthReckoning.AMANTA -> amantaMonthIndex
        MonthReckoning.PURNIMANTA ->
            if (paksha == Paksha.KRISHNA) {
                (amantaMonthIndex + 1) % LunarMonth.NAMES.size
            } else {
                amantaMonthIndex
            }
    }

    fun monthName(reckoning: MonthReckoning): String =
        LunarMonth.displayName(monthIndex(reckoning), isAdhika)
}

/**
 * Every tithi occurrence and every sunrise around one Gregorian year at one site.
 *
 * ## Why this exists at all
 *
 * Resolving a catalog of sixty-odd events one at a time would re-derive the same lunation
 * structure sixty times. Building it once costs one `tithiAt` per tithi and one `sunTimes` per
 * day over the window, plus one `lunarMonthAt` per *lunation* rather than per day — the month is
 * looked up only where the tithi index wraps, which is the one place it can change.
 *
 * ## Why the window is wider than the year
 *
 * [EventRule.RelativeTo] offsets cross year boundaries: the last day of a Chaturmasya month is
 * one day before a Purnima that can itself fall in the next Gregorian year. Matching is therefore
 * done over the whole window and filtered to the year afterwards, so an event whose base lies in
 * the previous December is still found.
 *
 * ## Sites with no sunrise
 *
 * A day on which the Sun does not rise contributes no sunrise to the index and is listed in
 * [datesWithoutSunrise]. Rules that ask for "the tithi running at sunrise" then have nothing to
 * answer with, and the resolver says so rather than substituting noon: which instant replaces
 * sunrise inside the polar circles is a tradition's ruling, not this class's.
 */
class LunarDayIndex private constructor(
    val year: Int,
    val ctx: ObservanceContext,
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    private val spans: List<TithiSpan>,
    /** Dates, in order, whose sunrise fell inside the span at the same list position. */
    private val sunriseDatesBySpan: List<List<LocalDate>>,
    private val sunTimesByDate: Map<LocalDate, SunTimes>,
    private val spanIndexBySunriseDate: Map<LocalDate, Int>,
    /** Dates in the window on which the Sun never rose. Empty outside the polar regions. */
    val datesWithoutSunrise: List<LocalDate>,
) {

    val location: GeoLocation get() = ctx.location

    /** All tithi occurrences in the window, in time order. */
    fun spans(): List<TithiSpan> = spans

    /** The dates whose sunrise fell inside the span at position [spanPosition]. */
    fun sunriseDatesAt(spanPosition: Int): List<LocalDate> = sunriseDatesBySpan[spanPosition]

    /** The civil date at this site on which [span] ended. */
    fun endDateOf(span: TithiSpan): LocalDate = location.localDate(span.endJdUt)

    /**
     * Every solar instant of [date] — sunrise, sunset, solar noon and arunodaya.
     *
     * The whole [SunTimes] is retained per date rather than just the sunrise the index is built
     * on, so an event anchored to noon or to sunset costs nothing beyond the solve the index
     * already paid for. Only moonrise and dusk still need a fresh solve, and only for the
     * handful of dated events that name them.
     *
     * Dates outside the index window are computed on demand rather than refused: the caller
     * asking for one is [EventTimes.nisitaKala] wanting the sunrise *after* a date at the very
     * edge, and a real solve is the only honest answer to that.
     */
    fun sunTimesOf(date: LocalDate): SunTimes =
        sunTimesByDate[date] ?: ctx.calculator.sunTimes(date, location)

    /** Sunrise on [date] as Julian Day (UT), or null if the Sun did not rise. */
    fun sunriseOf(date: LocalDate): Double? = sunTimesByDate[date]?.sunrise?.jdUtOrNull

    /** The tithi running at sunrise on [date], or null if there was no sunrise. */
    fun spanAtSunriseOf(date: LocalDate): TithiSpan? =
        spanIndexBySunriseDate[date]?.let { spans[it] }

    /** Dates in the window, in order, that have a sunrise. */
    fun datesWithSunrise(): List<LocalDate> = spanIndexBySunriseDate.keys.toList()

    companion object {

        /**
         * Days of margin on each side of the requested year.
         *
         * Wide enough for a `RelativeTo` chain plus a whole lunar month, so an event landing on
         * 1 January can still find a base event in the previous December.
         */
        const val WINDOW_MARGIN_DAYS: Long = 60

        /**
         * How far past a tithi's end to sample for the next one.
         *
         * `PanchangCalculator` reports a boundary to within half its configured tolerance — a
         * fraction of a second at the default — so about eight seconds clears it with orders of
         * magnitude to spare while remaining negligible against a ~24-hour tithi. Stepping by a
         * bare epsilon risks landing back inside the tithi just left, and the loop never ends.
         */
        private const val NEXT_TITHI_STEP_DAYS: Double = 1e-4

        fun build(year: Int, ctx: ObservanceContext): LunarDayIndex {
            val windowStart = LocalDate.of(year, 1, 1).minusDays(WINDOW_MARGIN_DAYS)
            val windowEnd = LocalDate.of(year, 12, 31).plusDays(WINDOW_MARGIN_DAYS)
            val calc = ctx.calculator
            val location = ctx.location

            val endJd = location.jdUtAtEndOfDay(windowEnd)
            val spans = ArrayList<TithiSpan>()
            var amantaMonthIndex = -1
            var isAdhika = false
            var previousTithiIndex = Int.MAX_VALUE
            var jd = location.jdUtAtStartOfDay(windowStart)
            while (jd < endJd) {
                val tithi = calc.tithiAt(jd)
                // The month can only change where the tithi index wraps past Amavasya, so the
                // expensive new-moon search runs about thirteen times a year instead of 485.
                // A skipped Shukla Pratipada still trips this test, because the wrap is detected
                // by the index decreasing rather than by index 0 being present.
                if (tithi.index < previousTithiIndex) {
                    val month = calc.lunarMonthAt(
                        (tithi.startJdUt + tithi.endJdUt) / 2.0,
                        MonthReckoning.AMANTA,
                    )
                    amantaMonthIndex = month.index
                    isAdhika = month.isAdhika
                }
                previousTithiIndex = tithi.index
                spans += TithiSpan(
                    tithiIndex = tithi.index,
                    startJdUt = tithi.startJdUt,
                    endJdUt = tithi.endJdUt,
                    amantaMonthIndex = amantaMonthIndex,
                    isAdhika = isAdhika,
                )
                jd = tithi.endJdUt + NEXT_TITHI_STEP_DAYS
            }

            // The whole SunTimes is kept, not just the sunrise. Sunset, solar noon and arunodaya
            // all come out of the same solve, so discarding them here only meant solving again
            // later for any rule that wanted one.
            val sunTimesByDate = LinkedHashMap<LocalDate, SunTimes>()
            val sunriseByDate = LinkedHashMap<LocalDate, Double>()
            val datesWithoutSunrise = ArrayList<LocalDate>()
            var date = windowStart
            while (!date.isAfter(windowEnd)) {
                val sunTimes = calc.sunTimes(date, location)
                sunTimesByDate[date] = sunTimes
                when (val sunrise = sunTimes.sunrise) {
                    is RiseSet.At -> sunriseByDate[date] = sunrise.jdUt
                    else -> datesWithoutSunrise += date
                }
                date = date.plusDays(1)
            }

            val sunriseDatesBySpan = List(spans.size) { ArrayList<LocalDate>() }
            val spanIndexBySunriseDate = LinkedHashMap<LocalDate, Int>()
            for ((day, sunriseJd) in sunriseByDate) {
                val position = spans.binarySearch { span ->
                    when {
                        span.startJdUt > sunriseJd -> 1
                        span.endJdUt <= sunriseJd -> -1
                        else -> 0
                    }
                }
                if (position >= 0) {
                    sunriseDatesBySpan[position] += day
                    spanIndexBySunriseDate[day] = position
                }
            }

            return LunarDayIndex(
                year = year,
                ctx = ctx,
                windowStart = windowStart,
                windowEnd = windowEnd,
                spans = spans,
                sunriseDatesBySpan = sunriseDatesBySpan,
                sunTimesByDate = sunTimesByDate,
                spanIndexBySunriseDate = spanIndexBySunriseDate,
                datesWithoutSunrise = datesWithoutSunrise,
            )
        }
    }
}

/**
 * What happened when one [EventDefinition] was resolved for one year at one site.
 *
 * Not a nullable [ResolvedEvent]. A festival that fails to resolve is the most damaging thing
 * this layer can do quietly — the entry simply is not in the calendar and nobody goes looking for
 * it — so every failure carries a reason and [EventResolver.resolveYear] reports the failures
 * alongside the successes.
 */
sealed interface EventResolution {

    data class Resolved(val event: ResolvedEvent) : EventResolution

    /** The rule is well formed but names nothing in this year at this site. */
    data class NoOccurrence(val why: String) : EventResolution

    /**
     * The tithi the rule names was skipped: it began after one sunrise and ended before the next,
     * so no day carries it.
     *
     * Deliberately *not* moved to an adjacent day. Which day a kshaya-tithi observance falls to
     * is a ruling the tradition makes — back to the day the tithi began, or forward to the day it
     * ended — and this phase has no source for the Gaudiya answer. Guessing would produce a date
     * that looks authoritative and is not.
     */
    data class TithiSkipped(val why: String) : EventResolution

    /** A [EventRule.FixedGregorian] entry with no date for this year: the table has run out. */
    data class BeyondTabulatedData(val why: String) : EventResolution
}

/** Everything a catalog produced for one year, successes and failures side by side. */
data class YearResolution(
    val events: List<ResolvedEvent>,
    /** Definitions that produced no date, by id, with the reason each gave. */
    val unresolved: Map<String, EventResolution>,
)

/**
 * Turns [EventDefinition]s into dates.
 *
 * Safe to share once constructed. The only mutable state is a memo of [SolarIngressIndex] keyed
 * weakly by the [LunarDayIndex] it was built over: solar rules in one catalog share a year's
 * ingresses, and recomputing them per definition would re-walk the window once per entry for no
 * new information. The memo never affects a result — only how often it is derived — so sharing
 * the resolver across threads remains sound.
 *
 * ## Rulings this class makes, and why
 *
 * **Adhika maasa suppresses everything.** A rule naming a lunar month matches only the *nija*
 * month; the intercalary Purusottama month matches nothing. This is the tradition's general rule
 * — observances omitted from the adhika month are kept in the nija month of the same name — and
 * the reference calendar shows it directly for 2026: the Mayapur export carries a
 * "Purusottama-adhika Masa" block from 17 May to 15 June holding two Ekadasis and *not one*
 * festival, appearance or disappearance day, while Jyestha's own events sit in the nija Jyestha
 * that follows. An observance specific to Purusottama masa would need a rule form that says so;
 * none exists yet, and none is in the catalog.
 *
 * **A tithi running across two sunrises is taken on the first.** Three Mayapur 2026 cases fix
 * this empirically: Snana Yatra on 29 June with Purnima also at sunrise on the 30th, Srila Rupa
 * Gosvami's disappearance on 24 August with Dvadasi also at sunrise on the 25th, and Gopastami on
 * 17 November with Astami also at sunrise on the 18th. All three sit on the earlier day.
 *
 * **A tithi skipped entirely resolves to nothing, loudly.** See [EventResolution.TithiSkipped].
 *
 * ## Failing loudly
 *
 * A [EventRule.RelativeTo] naming an event not in the catalog, or a cycle of them, is a
 * construction-time error rather than a resolve-time null. The catalog is a compile-time literal;
 * a dangling reference in it is a bug in the catalog, and finding it on the day the festival was
 * due is finding it too late.
 */
class EventResolver(catalog: List<EventDefinition>) {

    val definitions: List<EventDefinition> = catalog.toList()

    private val byId: Map<String, EventDefinition> = definitions.associateBy { it.id }

    /** One [SolarIngressIndex] per [LunarDayIndex] seen; see the class KDoc for why it is cached. */
    private val solarIndexByLunarIndex = WeakHashMap<LunarDayIndex, SolarIngressIndex>()

    init {
        require(byId.size == definitions.size) {
            val duplicates = definitions.groupBy { it.id }.filterValues { it.size > 1 }.keys
            "catalog contains duplicate event ids: $duplicates"
        }
        for (definition in definitions) {
            when (val rule = definition.rule) {
                is EventRule.OnTithi -> {
                    require(rule.tithiNumberInPaksha in 1..15) {
                        "event '${definition.id}' names tithi ${rule.tithiNumberInPaksha}; a " +
                            "tithi is cited as 1..15 within its paksha"
                    }
                    monthIndexOf(rule.lunarMonthName, definition.id)
                }

                is EventRule.OnNakshatraInMonth -> {
                    monthIndexOf(rule.lunarMonthName, definition.id)
                    require(rule.nakshatraName.isNotBlank()) {
                        "event '${definition.id}' names no nakshatra"
                    }
                }

                is EventRule.OnSolarMonth -> {
                    require(rule.solarMonthIndex in 1..12) {
                        "event '${definition.id}' names solar month ${rule.solarMonthIndex}; " +
                            "the rashis are numbered 1..12 with Mesha = 1"
                    }
                    require(rule.dayOfMonth in 1..MAX_SOLAR_DAY_OF_MONTH) {
                        "event '${definition.id}' names day ${rule.dayOfMonth} of a solar " +
                            "month; solar months run 29 to 32 days, so anything beyond " +
                            "$MAX_SOLAR_DAY_OF_MONTH is not a day any solar calendar has"
                    }
                }

                is EventRule.In60YearCycle -> {
                    require(rule.cycleYear in 1..60) {
                        "event '${definition.id}' names cycle year ${rule.cycleYear}; the " +
                            "Samvatsara cycle runs 1..60"
                    }
                    require(
                        rule.baseRule !is EventRule.RelativeTo &&
                            rule.baseRule !is EventRule.In60YearCycle,
                    ) {
                        "event '${definition.id}' nests a " +
                            "${rule.baseRule::class.simpleName} inside In60YearCycle; only a " +
                            "self-contained rule can be nested there"
                    }
                }

                else -> Unit
            }
        }
        // Dangling and circular references are structural errors in the catalog, so they are
        // rejected once, here, rather than surfacing as a missing festival in some future year.
        for (definition in definitions) checkReferences(definition, LinkedHashSet())
    }

    /** Resolve one definition by id. Throws if the catalog does not contain [id]. */
    fun resolve(id: String, index: LunarDayIndex): EventResolution =
        resolve(requireNotNull(byId[id]) { "no event '$id' in this catalog" }, index)

    fun resolve(definition: EventDefinition, index: LunarDayIndex): EventResolution {
        val outcome = datesOf(definition, index, LinkedHashSet())
        val inYear = outcome.matches
            .filter { it.date.year == index.year }
            .distinctBy { it.date }
            .sortedBy { it.date }
        if (inYear.isEmpty()) return explainNothing(definition, index, outcome)

        // Two matches in one Gregorian year is legitimate: a lunar year is ~11 days shorter, so
        // an observance falling in early January can recur in late December. The earlier is
        // returned and the later named, rather than the later being dropped without trace.
        val extra = if (inYear.size > 1) {
            " Also occurs in ${index.year} on " +
                inYear.drop(1).joinToString(", ") { it.date.toString() } + "."
        } else {
            ""
        }
        val chosen = inYear.first()
        return EventResolution.Resolved(
            ResolvedEvent(
                id = definition.id,
                name = definition.name,
                group = definition.group,
                date = chosen.date,
                fastingNote = definition.fastingNote,
                reason = describe(definition.rule) + "." + extra,
                confidence = definition.confidence,
                fastUntil = definition.fastUntil?.let { anchor ->
                    eventTimeOf(anchor, chosen.date, index)
                },
                tithi = chosen.span?.let { occurrenceOf(it, definition.rule) },
            ),
        )
    }

    /** Resolve the whole catalog, keeping the failures. */
    fun resolveYear(index: LunarDayIndex): YearResolution {
        val events = ArrayList<ResolvedEvent>()
        val unresolved = LinkedHashMap<String, EventResolution>()
        for (definition in definitions) {
            when (val outcome = resolve(definition, index)) {
                is EventResolution.Resolved -> events += outcome.event
                else -> unresolved[definition.id] = outcome
            }
        }
        events.sortWith(compareBy({ it.date }, { it.id }))
        return YearResolution(events, unresolved)
    }

    /** Convenience for callers holding no index. Builds one, which is not cheap. */
    fun resolveYear(year: Int, ctx: ObservanceContext): YearResolution =
        resolveYear(LunarDayIndex.build(year, ctx))

    // ── Internals ───────────────────────────────────────────────────────────────────────────

    /**
     * The instant (or muhurta) an anchored fast runs until, at this site on this date.
     *
     * Sunrise, solar noon and sunset are read straight off the [SunTimes] the index already
     * holds. Moonrise and dusk are solved here, which is why anchoring is per event rather than
     * per day: a year's catalog names moonrise once and dusk once, so two extra solves a year is
     * the whole cost of the feature.
     */
    private fun eventTimeOf(
        anchor: ObservanceAnchor,
        date: LocalDate,
        index: LunarDayIndex,
    ): EventTime = when (anchor) {
        ObservanceAnchor.SUNRISE -> EventTimes.sunrise(index.sunTimesOf(date))

        ObservanceAnchor.SOLAR_NOON -> EventTimes.solarNoon(index.sunTimesOf(date))

        ObservanceAnchor.SUNSET -> EventTimes.sunset(index.sunTimesOf(date))

        ObservanceAnchor.MOONRISE ->
            EventTimes.moonrise(index.ctx.calculator.moonTimes(date, index.location))

        ObservanceAnchor.DUSK ->
            EventTimes.dusk(index.ctx.calculator.civilTwilightEnd(date, index.location))

        // The night of the observance runs from this evening's sunset to tomorrow's sunrise, so
        // the muhurta needs both days. Taking the *same* day's sunrise would divide the daylight
        // and place "midnight" in the early afternoon.
        ObservanceAnchor.NISITA_KALA -> EventTimes.nisitaKala(
            sunsetOfDay = index.sunTimesOf(date).sunset,
            sunriseOfNextDay = index.sunTimesOf(date.plusDays(1)).sunrise,
        )
    }

    /**
     * The matched [TithiSpan] as the [TithiOccurrence] a caller can render.
     *
     * The month is named under the *rule's own* reckoning, so a Gaudiya entry stated
     * purnimanta reports the purnimanta name the tradition prints and not the amanta name the
     * span is stored under.
     */
    private fun occurrenceOf(span: TithiSpan, rule: EventRule): TithiOccurrence {
        val reckoning = when (rule) {
            is EventRule.OnTithi -> rule.reckoning
            is EventRule.OnNakshatraInMonth -> rule.reckoning
            else -> MonthReckoning.AMANTA
        }
        return TithiOccurrence(
            index = span.tithiIndex,
            name = Tithi.nameOf(span.tithiIndex),
            numberInPaksha = span.numberInPaksha,
            paksha = span.paksha,
            lunarMonthName = span.monthName(reckoning),
            isAdhikaMonth = span.isAdhika,
            startJdUt = span.startJdUt,
            endJdUt = span.endJdUt,
        )
    }

    private fun explainNothing(
        definition: EventDefinition,
        index: LunarDayIndex,
        outcome: RuleOutcome,
    ): EventResolution {
        val rule = definition.rule
        if (outcome.skipped.isNotEmpty()) {
            return EventResolution.TithiSkipped(outcome.skipped.joinToString("; "))
        }
        if (rule is EventRule.FixedGregorian) {
            return EventResolution.BeyondTabulatedData(
                "'${definition.id}' is a bare list of dates covering " +
                    "${rule.datesByYear.keys.sorted()} and has no entry for ${index.year}; its " +
                    "rule has not been recovered, so it cannot be extrapolated",
            )
        }
        return EventResolution.NoOccurrence(
            "${describe(rule)} did not occur in ${index.year} at ${index.location.latitude}, " +
                "${index.location.longitude}" + outcome.notes.joinToString("") { " ($it)" },
        )
    }

    /**
     * Every date in the index window the rule names — *not* filtered to the year, because a
     * `RelativeTo` base may legitimately sit on the far side of a year boundary from its offset.
     */
    private fun datesOf(
        definition: EventDefinition,
        index: LunarDayIndex,
        visiting: MutableSet<String>,
    ): RuleOutcome = ruleDatesOf(definition.id, definition.rule, index, visiting)

    /**
     * The dates one rule names, by rule rather than by catalog entry.
     *
     * Exists because [EventRule.In60YearCycle] wraps a bare rule, not a catalog entry, so its
     * inner rule has no definition to hand to [onTithi] or [onNakshatra] — only an id for error
     * context. Every other caller reaches here through [datesOf].
     */
    private fun ruleDatesOf(
        eventId: String,
        rule: EventRule,
        index: LunarDayIndex,
        visiting: MutableSet<String>,
    ): RuleOutcome = when (rule) {
        is EventRule.OnTithi -> onTithi(eventId, rule, index)

        is EventRule.OnNakshatraInMonth -> onNakshatra(eventId, rule, index)

        is EventRule.OnSolarMonth -> onSolarMonth(rule, index)

        // A bare date qualified nothing, so it carries no span: there is no tithi that "made"
        // this date and inventing the one that happens to run on it would be a different claim.
        is EventRule.FixedGregorian -> RuleOutcome(
            rule.datesByYear.values
                .filter { !it.isBefore(index.windowStart) && !it.isAfter(index.windowEnd) }
                .sorted()
                .map { Match(it, span = null) },
        )

        is EventRule.In60YearCycle -> {
            val actualCycleYear = SixtyYearCycle.cycleYearOf(index.year)
            if (actualCycleYear != rule.cycleYear) {
                // Suppression, not failure: the event is defined, and this simply is not its
                // year. The note reaches the caller through `unresolved`, where a reader can see
                // that the absence is the rule working.
                RuleOutcome(
                    matches = emptyList(),
                    notes = listOf(
                        "${index.year} is cycle year $actualCycleYear of the sixty-year " +
                            "cycle, not ${rule.cycleYear}",
                    ),
                )
            } else {
                ruleDatesOf(eventId, rule.baseRule, index, visiting)
            }
        }

        is EventRule.RelativeTo -> {
            val base = requireNotNull(byId[rule.eventId]) {
                "event '$eventId' is defined relative to '${rule.eventId}', which is not " +
                    "in this catalog"
            }
            check(visiting.add(eventId)) {
                "event '$eventId' is defined relative to itself through " +
                    "${visiting.toList()}"
            }
            val inner = datesOf(base, index, visiting)
            visiting.remove(eventId)
            RuleOutcome(
                // The base's span is deliberately dropped rather than carried across the offset.
                // It is the tithi that qualified the *base* event; the offset day's own tithi was
                // never consulted by this rule, and reporting either one as "the tithi that
                // qualified this date" would be false.
                matches = inner.matches.map {
                    Match(it.date.plusDays(rule.offsetDays.toLong()), span = null)
                },
                skipped = inner.skipped.map { "base event '${rule.eventId}': $it" },
                notes = inner.notes.map { "base event '${rule.eventId}': $it" },
            )
        }
    }

    private fun onTithi(
        eventId: String,
        rule: EventRule.OnTithi,
        index: LunarDayIndex,
    ): RuleOutcome {
        val monthIndex = monthIndexOf(rule.lunarMonthName, eventId)
        val wantedTithiIndex = tithiIndexOf(rule.paksha, rule.tithiNumberInPaksha)
        val matches = ArrayList<Match>()
        val skipped = ArrayList<String>()
        val notes = ArrayList<String>()
        index.spans().forEachIndexed { position, span ->
            if (span.tithiIndex == wantedTithiIndex &&
                span.monthIndex(rule.reckoning) == monthIndex
            ) {
                when {
                    span.isAdhika -> notes += "the ${LunarMonth.NAMES[monthIndex]} occurrence " +
                        "beginning ${index.location.localDate(span.startJdUt)} fell in the adhika " +
                        "month, where the tradition keeps no observances"

                    !rule.atSunrise -> matches += Match(index.endDateOf(span), span)

                    else -> {
                        val days = index.sunriseDatesAt(position)
                        if (days.isEmpty()) {
                            skipped += "${describe(rule)} was skipped: the tithi ran from " +
                                "${index.location.zonedDateTime(span.startJdUt)} to " +
                                "${index.location.zonedDateTime(span.endJdUt)} without touching " +
                                "a sunrise"
                        } else {
                            // The span is retained, not recomputed: it is the very occurrence
                            // that qualified the date, so the caller can show this festival's
                            // tithi start and end at this user's location.
                            matches += Match(days.first(), span)
                        }
                    }
                }
            }
        }
        return RuleOutcome(matches.sortedBy { it.date }, skipped, notes)
    }

    /**
     * The days of a named month whose sunrise nakshatra matches.
     *
     * Evaluated day by day off the index's own calculator rather than from a precomputed
     * nakshatra index: no catalog entry uses this form yet, and building a second element index
     * for every year to serve nothing would be paid by every caller.
     */
    private fun onNakshatra(
        eventId: String,
        rule: EventRule.OnNakshatraInMonth,
        index: LunarDayIndex,
    ): RuleOutcome {
        val monthIndex = monthIndexOf(rule.lunarMonthName, eventId)
        val dates = ArrayList<LocalDate>()
        val notes = ArrayList<String>()
        var sawAdhika = false
        for (date in index.datesWithSunrise()) {
            val span = index.spanAtSunriseOf(date) ?: continue
            if (span.monthIndex(rule.reckoning) != monthIndex) continue
            if (span.isAdhika) {
                sawAdhika = true
                continue
            }
            val sunrise = index.sunriseOf(date) ?: continue
            // Read at sunrise for the same reason the tithi is: that is the instant the day's
            // panchanga is conventionally taken at.
            val nakshatra = index.ctx.calculator.nakshatraAt(sunrise).name
            if (nakshatra.equals(rule.nakshatraName, ignoreCase = true)) dates += date
        }
        if (sawAdhika) {
            notes += "occurrences in the adhika ${LunarMonth.NAMES[monthIndex]} were not counted"
        }
        // No span: a nakshatra qualified these dates, not a tithi. The tithi running at that
        // sunrise is a fact about the day, not the thing the rule matched on, and reporting it
        // as the qualifying occurrence would misdescribe the rule.
        return RuleOutcome(dates.sorted().map { Match(it, span = null) }, notes = notes)
    }

    /**
     * The days a solar rule names: [rule.dayOfMonth] counted from the opening of the solar
     * month [EventRule.OnSolarMonth.solarMonthIndex], with the opening fixed by
     * [SolarTransition] as that rule states.
     *
     * The window the resolver works over runs ~60 days past each side of the year, so the
     * signs whose ingress falls in November–February open *twice* in it; one match is produced
     * per opening, and the caller's year filter keeps the one that lands inside the requested
     * year. Suppressing the edge openings here instead would silently break a rule resolved
     * for January and February.
     *
     * Like a tabulated date and a nakshatra match, a solar match carries no span: the Sun's
     * position qualified this date, not a tithi, and the tithi running that morning is a fact
     * about the day rather than the thing the rule matched on.
     */
    private fun onSolarMonth(rule: EventRule.OnSolarMonth, index: LunarDayIndex): RuleOutcome {
        val signIndex = rule.solarMonthIndex - 1
        val openings = solarIndexOf(index).monthStartDaysOf(signIndex, rule.transitionPoint)
        if (openings.isEmpty()) {
            return RuleOutcome(
                emptyList(),
                notes = listOf(
                    "the Sun does not enter ${Rashi.NAMES[signIndex]} between " +
                        "${index.windowStart} and ${index.windowEnd} at this site's window",
                ),
            )
        }
        return RuleOutcome(
            openings
                .map { it.plusDays((rule.dayOfMonth - 1).toLong()) }
                .filter { !it.isBefore(index.windowStart) && !it.isAfter(index.windowEnd) }
                .sorted()
                .map { Match(it, span = null) },
        )
    }

    /** The year's [SolarIngressIndex] over [index], built once however many solar rules ask. */
    private fun solarIndexOf(index: LunarDayIndex): SolarIngressIndex =
        synchronized(solarIndexByLunarIndex) {
            solarIndexByLunarIndex[index]
                ?: SolarIngressIndex.build(index).also { solarIndexByLunarIndex[index] = it }
        }

    private fun checkReferences(definition: EventDefinition, seen: MutableSet<String>) {
        val rule = definition.rule
        if (rule !is EventRule.RelativeTo) return
        require(seen.add(definition.id)) {
            "events ${seen.toList()} form a RelativeTo cycle; none of them can ever be dated"
        }
        val target = requireNotNull(byId[rule.eventId]) {
            "event '${definition.id}' is defined relative to '${rule.eventId}', which is not in " +
                "this catalog"
        }
        checkReferences(target, seen)
    }

    private fun describe(rule: EventRule): String = when (rule) {
        is EventRule.OnTithi -> describe(rule)

        is EventRule.OnNakshatraInMonth ->
            "${rule.nakshatraName} at sunrise in ${rule.lunarMonthName} " +
                "(${rule.reckoning.name.lowercase()})"

        is EventRule.OnSolarMonth -> {
            val rashi = Rashi.NAMES[rule.solarMonthIndex - 1]
            val basis = when (rule.transitionPoint) {
                SolarTransition.SANKRANTI_START ->
                    "the month opening on the day the Sun enters $rashi"
                SolarTransition.SANKRANTI_AT_SUNRISE ->
                    "the month opening on the first sunrise after the Sun enters $rashi"
            }
            "day ${rule.dayOfMonth} of the solar month $rashi, $basis"
        }

        is EventRule.In60YearCycle ->
            "a rule held only in cycle year ${rule.cycleYear} of the sixty-year cycle " +
                "(the cyclic year opening ${SixtyYearCycle.gregorianYearOf(rule.cycleYear)}), " +
                "namely: ${describe(rule.baseRule)}"

        is EventRule.RelativeTo -> when {
            rule.offsetDays == 0 -> "the same day as '${rule.eventId}'"
            rule.offsetDays > 0 -> "${rule.offsetDays} day(s) after '${rule.eventId}'"
            else -> "${-rule.offsetDays} day(s) before '${rule.eventId}'"
        }

        is EventRule.FixedGregorian ->
            "a tabulated date for one of ${rule.datesByYear.keys.sorted()}"
    }

    private fun describe(rule: EventRule.OnTithi): String {
        val tithi = "${rule.lunarMonthName} ${rule.paksha.displayName} ${rule.tithiNumberInPaksha}"
        val basis = if (rule.atSunrise) "the tithi running at sunrise" else "the day the tithi ends"
        return "$tithi (${rule.reckoning.name.lowercase()}), $basis"
    }

    /**
     * A date the rule named, together with the tithi occurrence that qualified it if one did.
     *
     * [span] is null wherever nothing tithi-shaped did the qualifying — a tabulated date, a
     * nakshatra match, or an offset from another event. Carrying it here rather than looking it
     * up again at construction time is what makes [ResolvedEvent.tithi] retention rather than
     * recomputation.
     */
    private data class Match(val date: LocalDate, val span: TithiSpan?)

    private data class RuleOutcome(
        val matches: List<Match>,
        /** Occurrences lost to a kshaya tithi, described. */
        val skipped: List<String> = emptyList(),
        /** Anything else worth saying about why a match was or was not made. */
        val notes: List<String> = emptyList(),
    )

    private companion object {

        /**
         * The largest solar day-of-month a rule may name. Solar months run 29 to 32 days; 40 is
         * the outer bound a rule could plausibly intend, and anything beyond it names a day no
         * solar calendar contains.
         */
        const val MAX_SOLAR_DAY_OF_MONTH: Int = 40

        fun monthIndexOf(name: String, eventId: String): Int {
            val index = LunarMonth.NAMES.indexOfFirst { it.equals(name, ignoreCase = true) }
            require(index >= 0) {
                "event '$eventId' names lunar month '$name'; expected one of ${LunarMonth.NAMES}"
            }
            return index
        }

        /**
         * Krishna 15 is Amavasya at index 29, not the "Purnima" that `index % 15` would name.
         * The arithmetic below happens to handle it, and it is spelled out because the equivalent
         * table lookup in the app engine did not.
         */
        fun tithiIndexOf(paksha: Paksha, numberInPaksha: Int): Int =
            if (paksha == Paksha.SHUKLA) numberInPaksha - 1 else 14 + numberInPaksha
    }
}
