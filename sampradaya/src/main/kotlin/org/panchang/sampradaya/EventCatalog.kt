package org.panchang.sampradaya

import java.time.LocalDate
import org.panchang.core.MonthReckoning
import org.panchang.core.Paksha

/**
 * What kind of observance an event is, so clients can let users opt in per group.
 *
 * A Gaudiya calendar carries several hundred entries a year, most of them acharya appearance
 * and disappearance days. Showing all of them to every user is unusable, and picking a subset
 * on the user's behalf is a religious decision this service should not be making. Tagging by
 * group pushes that choice to the client, where the user can make it.
 *
 * The source data this replaces could not support that: its `eventType` field drifts between
 * years — Gaura Purnima, Rama Navami and Janmastami are `Appearance` in 2025 and `Festival` in
 * 2026 — so any type-driven filter silently changes behaviour at the year boundary. The group
 * here is a property of the event definition, not of one year's scrape, and cannot drift.
 */
enum class EventGroup {
    EKADASHI,
    MAJOR_FESTIVAL,
    APPEARANCE,
    DISAPPEARANCE,
    OPTIONAL_FAST,
    /** Month-long or multi-day observances: Kartika vrata, Purushottama, Chaturmasya. */
    SEASONAL,
}

/**
 * How an event's date is determined in a given year.
 *
 * This is the type that decides whether the calendar can generate past the end of the scraped
 * data. The source it replaces holds 177 acharya days as bare Gregorian dates running out on
 * 2026-12-27, with no tithi, paksha or month recorded anywhere in its schema — the upstream
 * rule was discarded during the scrape and cannot be recovered from the file. Every rule form
 * below except [FixedGregorian] extrapolates indefinitely; [FixedGregorian] exists to hold the
 * residue honestly rather than to hide it.
 */
sealed interface EventRule {

    /**
     * The common form: a named tithi in a named lunar month and paksha.
     *
     * @param tithiNumberInPaksha 1–15, as the tithi is normally cited.
     * @param reckoning which month-naming convention [lunarMonthName] is stated in. Amanta and
     *   Purnimanta disagree by one month name for krishna-paksha dates, so a rule that omits
     *   this is ambiguous for exactly half the calendar.
     */
    data class OnTithi(
        val lunarMonthName: String,
        val paksha: Paksha,
        val tithiNumberInPaksha: Int,
        val reckoning: MonthReckoning = MonthReckoning.AMANTA,
        /**
         * When true the event falls on the day the tithi is running at sunrise; when false, on
         * the day it *ends*. Traditions differ, and several appearance days use the latter.
         */
        val atSunrise: Boolean = true,
    ) : EventRule

    /** A nakshatra within a named lunar month — the form used by Janmastami-type reckonings. */
    data class OnNakshatraInMonth(
        val lunarMonthName: String,
        val nakshatraName: String,
        val reckoning: MonthReckoning = MonthReckoning.AMANTA,
    ) : EventRule

    /**
     * A fixed offset in days from another event in the same catalog.
     *
     * Used where a tradition states an observance relative to another rather than by tithi —
     * for example a disappearance day observed the day after a festival.
     */
    data class RelativeTo(val eventId: String, val offsetDays: Int) : EventRule

    /**
     * A literal Gregorian date per year, with no rule behind it.
     *
     * Carries [RuleConfidence.TABULATED] and cannot produce a date for any year absent from
     * [datesByYear]. This is a placeholder for entries whose rule has not yet been recovered,
     * and the count of events still using it is the honest measure of how far the calendar
     * actually extends. It must never be silently extrapolated by repeating last year's date:
     * the two acharya days that do recur across both years of the source data shift by −10 and
     * +19 days, which is what a tithi-derived date looks like when you assume it is fixed.
     */
    data class FixedGregorian(val datesByYear: Map<Int, LocalDate>) : EventRule
}

/**
 * The instant of the day an observance is anchored to.
 *
 * ## Why this exists
 *
 * The catalog carried its time-of-day semantics only as English prose inside `fastingNote` —
 * "Fast till noon", "Fast till moonrise", "Fast till midnight". A client could render the
 * sentence but could not compute the time, so the one piece of information the fasting devotee
 * actually needs at 11:40 in the morning was the one piece the payload did not contain.
 *
 * ## Why the confidence is on the anchor and not on the arithmetic
 *
 * The published Gaudiya calendars name these anchors in words and print **no clock time** for
 * any of them — that holds across all 365 days of the reference data. So there is no oracle
 * against which an anchor's *time* can be checked. What can be checked is the astronomy: our
 * solar noon is a meridian transit and either is or is not the right transit. What cannot be
 * checked is whether the tradition means solar noon by "noon", the *reading* of the prose.
 *
 * [mappingConfidence] grades that reading and nothing else. [SUNRISE], [SOLAR_NOON], [SUNSET]
 * and [MOONRISE] are [RuleConfidence.CONFIRMED] because the words have one astronomical meaning
 * each and the tradition uses them in it. [DUSK] and [NISITA_KALA] are [RuleConfidence.INFERRED]
 * because "dusk" and "midnight" each have several defensible readings and this project picked
 * one; both are flagged for pandit review, and each carries the definition it picked in
 * [EventTime.basis] so a reviewer can see the choice rather than having to guess it.
 */
enum class ObservanceAnchor(val displayName: String, val mappingConfidence: RuleConfidence) {
    SUNRISE("sunrise", RuleConfidence.CONFIRMED),
    SOLAR_NOON("noon", RuleConfidence.CONFIRMED),
    SUNSET("sunset", RuleConfidence.CONFIRMED),
    MOONRISE("moonrise", RuleConfidence.CONFIRMED),
    DUSK("dusk", RuleConfidence.INFERRED),
    NISITA_KALA("midnight (Nisita-kala)", RuleConfidence.INFERRED),
}

/**
 * One entry in a tradition's event catalog.
 *
 * @param sourceNote where this entry's rule came from — a published calendar, a scripture
 *   reference, or the fact that it was inferred from observed dates. Not optional: an entry
 *   whose origin nobody recorded cannot be checked by a reviewing pandit later, and unreviewable
 *   religious content is the thing this project most needs to avoid accumulating.
 */
data class EventDefinition(
    val id: String,
    val name: String,
    val group: EventGroup,
    val rule: EventRule,
    val sourceNote: String,
    val confidence: RuleConfidence,
    /** Described if the day carries a fast; null if it does not. */
    val fastingNote: String? = null,
    /**
     * The instant [fastingNote] says the fast runs until, when it names one.
     *
     * Null for the majority of entries, which either carry no fast at all or carry one with no
     * time of day in it — "Yogurt is given up for one month" has no anchor and inventing one
     * would be worse than having none. Defaulted so that the many entries without a fast are
     * untouched by this field's existence.
     */
    val fastUntil: ObservanceAnchor? = null,
) {
    init {
        require(id.isNotBlank() && id == id.lowercase()) { "event id must be lowercase: '$id'" }
        require(sourceNote.isNotBlank()) { "event '$id' must record where its rule came from" }
        // An anchor with no fast behind it is a time-of-day for an observance that has no
        // time-of-day semantics — the resolver would compute an instant and the payload would
        // show a deadline for a fast the entry never said existed.
        require(fastUntil == null || fastingNote != null) {
            "event '$id' anchors a fast to ${fastUntil?.displayName} but records no fastingNote; " +
                "an anchor with nothing to anchor is a time nobody asked for"
        }
        if (rule is EventRule.FixedGregorian) {
            require(confidence == RuleConfidence.TABULATED) {
                "event '$id' is a bare list of dates but claims confidence $confidence; a date " +
                    "with no rule behind it is TABULATED by definition"
            }
            require(rule.datesByYear.isNotEmpty()) {
                "event '$id' has no dates and no rule, so it can never resolve to anything"
            }
        }
    }
}
