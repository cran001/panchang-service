package org.panchang.verify.vaisnava

import kotlinx.serialization.Serializable

/**
 * One calendar day as a community Vaishnava calendar states it.
 *
 * This is a record of what a source *said*, not of what is true. Nothing here is
 * normalised towards our own vocabulary: tithi and naksatra names are kept in the
 * source's own transliteration so that a later disagreement can be traced to either a
 * genuine difference or a transliteration mismatch, and not confused between the two.
 */
@Serializable
data class VaisnavaDay(
    /** ISO date. */
    val date: String,
    /** Two-letter weekday as printed, e.g. "Th". Retained as a cheap parse cross-check. */
    val weekdayAbbrev: String,
    val tithi: String,
    /** "G" for gaura/sukla (waxing), "K" for krsna (waning), as the source prints it. */
    val paksa: String,
    val naksatra: String,
    /** The source's own fasting marker (a `*` in the FAST column). */
    val fastMarked: Boolean,
    /** Masa heading in force, e.g. "Jyestha (Trivikrama)". */
    val masa: String? = null,
    val gaurabda: Int? = null,
    /** Festival, appearance and disappearance lines attached to this date, verbatim. */
    val events: List<String> = emptyList(),
    /**
     * Ekadashi name as taken from a "Fasting for X Ekadasi" line.
     *
     * Note this line attaches to the *fasting* day, which is not always the Ekadasi tithi:
     * on a Mahadvadasi the fast moves to Dvadasi. Recording the name against the day the
     * source marked for fasting preserves that distinction instead of erasing it.
     */
    val fastingFor: String? = null,
    val parana: ParanaWindow? = null,
)

/**
 * A parana (fast-breaking) window.
 *
 * Both ends carry a *basis*, because the window is not an arbitrary clock interval: it is
 * bounded by astronomical or tithi-derived events, and the sect rule is expressed in
 * those terms. "06:18 (sunrise) - 09:52 (1/3 of daylight)" and
 * "08:01 (1/4 of tithi) - 11:05 (1/3 of daylight)" are different rules producing
 * superficially similar strings; discarding the parenthetical would make them
 * indistinguishable and make the sect-rule factor unverifiable.
 */
@Serializable
data class ParanaWindow(
    /** Local clock time, HH:MM, as printed. */
    val start: String,
    val startBasis: String,
    /**
     * Upper bound, or null for an open-ended window.
     *
     * The source prints "Break fast after 10:50 (1/4 of tithi) LT" when only a lower bound
     * applies — three of the ten grid cities have such a day in 2026. Substituting an
     * invented end (sunset, end of day) would turn a rule the source declined to state
     * into an assertion we made up.
     */
    val end: String? = null,
    val endBasis: String? = null,
    /**
     * Clock the times are expressed in: "LT" (local standard) or "DST".
     *
     * This is the DST stress case in its raw form. The source distinguishes them
     * explicitly, so a parana window read without this marker can be an hour wrong for
     * half the year in Auckland and London.
     */
    val clock: String,
)

/** Header block of a vaisnavacalendar.info text calendar. */
@Serializable
data class VaisnavaCalendarHeader(
    /** e.g. "Mayapur [India]". */
    val city: String,
    /** Coordinate string exactly as printed, e.g. "23N25 88E23". */
    val coordinates: String,
    /** UTC offset as printed, e.g. "+5:30". */
    val utcOffset: String,
    /** Generator identification, e.g. "GCal 11, Build 5". */
    val generator: String?,
)

/** A solar ingress line, printed between days as a full-width rule. */
@Serializable
data class SankrantiNote(
    val name: String,
    val text: String,
)
