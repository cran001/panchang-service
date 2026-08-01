package org.panchang.sampradaya

import java.time.LocalDate

/**
 * How much this project actually knows about a tradition's rules.
 *
 * This is the honesty flag required by the goal brief, and it is surfaced through the API
 * rather than kept as an internal note. A tradition whose rules could not be verified against
 * an authoritative source ships [UNVERIFIED] or [NOT_IMPLEMENTED] — never silently guessed and
 * presented alongside verified output as though the two carried equal weight.
 *
 * Religious correctness is reputational. A wrong fasting date is not a rounding error to the
 * person who kept the fast on it.
 */
enum class VerificationStatus {
    /**
     * Rules are implemented and their output has been checked against a published calendar
     * from the tradition itself, across multiple years and locations.
     */
    VERIFIED,

    /**
     * Rules are implemented from documented descriptions, but the output has not been
     * confirmed against an authoritative published calendar. Usable, clearly labelled, and
     * not to be presented as authoritative.
     */
    UNVERIFIED,

    /**
     * Registered so the tradition is visible and requestable, but no rules exist. Requests
     * return this status rather than falling back to another tradition's answer — a silent
     * fallback would show a Smarta date to a Pushtimarg follower with no indication.
     */
    NOT_IMPLEMENTED,
}

/**
 * Confidence in a single derived result, independent of the tradition's overall status.
 *
 * A [VerificationStatus.VERIFIED] tradition can still produce an individual date that rests on
 * a recovered or assumed rule — most of Phase 4's acharya days are like this. Carrying
 * confidence per result stops one uncertain entry from either being hidden or downgrading
 * everything around it.
 */
enum class RuleConfidence {
    /** Derived from a rule confirmed against every observed year in the reference data. */
    CONFIRMED,

    /** Derived from a rule inferred from observation and consistent with it, but not proven. */
    INFERRED,

    /** A literal date carried over from source data, with no rule behind it. Cannot extrapolate. */
    TABULATED,
}

/** What kind of observance a decision describes. */
enum class FastKind {
    /** The fast falls on the Ekadashi tithi itself. */
    EKADASHI,

    /**
     * The fast was deferred to the Dvadashi. In Gaudiya practice this is not "Ekadashi moved" —
     * the Dvadashi itself becomes the fasting day and takes one of the eight Mahadvadashi names.
     */
    MAHADVADASHI,
}

/**
 * The eight Mahadvadashis.
 *
 * Named in the shipped calendar data of the app this service replaces, and implemented nowhere
 * in its code: when it detected a viddha Ekadashi it simply skipped a day and re-emitted the
 * next one under its ordinary name. Detection of which of these eight applies belongs to the
 * sect rules, because the qualifying conditions differ between traditions.
 */
enum class MahadvadashiType(val displayName: String) {
    UNMILANI("Unmilani"),
    TRISPRSA("Trisprsa"),
    PAKSAVARDHINI("Paksavardhini"),
    JAYA("Jaya"),
    VIJAYA("Vijaya"),
    JAYANTI("Jayanti"),
    PAPANASINI("Papanasini"),
    VANJULI("Vanjuli"),
}

/**
 * Why a parana window starts or ends where it does.
 *
 * Recorded per boundary so a window can explain itself. The app this replaces hardcoded the
 * parana to the day after the fast and assumed the tithi at that sunrise was Dvadashi; when the
 * fast shifted it silently produced a Trayodashi window with no indication anything was unusual.
 * A window that names its own bounds cannot fail that way quietly.
 */
enum class ParanaBoundReason {
    /** Sunrise on the parana day — the ordinary start. */
    SUNRISE,

    /** Hari Vasara, the first quarter of Dvadashi, was still running at sunrise. */
    HARI_VASARA_END,

    /** The Dvadashi ends before the window would otherwise close; parana must precede it. */
    DVADASHI_END,

    /** Traditional cap at the first third of the daylight period. */
    ONE_THIRD_DAYLIGHT,

    /** The Ekadashi or Mahadvadashi tithi had to end before eating could begin. */
    FAST_TITHI_END,

    /** Sunset on the parana day. */
    SUNSET,
}

/**
 * The interval within which the fast must be broken.
 *
 * Both instants are Julian Day in **UT**, matching `core`. [date] is the local calendar date
 * the window falls on, which is not always the day after the fast.
 */
data class ParanaWindow(
    val date: LocalDate,
    val startJdUt: Double,
    val endJdUt: Double,
    val startReason: ParanaBoundReason,
    val endReason: ParanaBoundReason,
) {
    init {
        // The app had no such guard. A late Hari Vasara against an early Dvadashi end inverts
        // the window, and an inverted window formatted for display reads as a perfectly
        // ordinary "07:12 - 06:34" that nobody notices until someone tries to follow it.
        require(endJdUt > startJdUt) {
            "parana window on $date is inverted: start $startJdUt ($startReason) is not before " +
                "end $endJdUt ($endReason). A rule that produces this must return null and say why."
        }
    }

    /** Window length in minutes. Some genuine windows are under 40 minutes wide. */
    val durationMinutes: Double get() = (endJdUt - startJdUt) * 24.0 * 60.0
}

/**
 * A single occurrence of a tithi, as the rule layer receives it.
 *
 * Distinct from `core.Tithi`, which describes the tithi running at one instant. This describes
 * the occurrence as a whole — the thing a rule reasons about when it asks "was Dashami still
 * running at arunodaya".
 *
 * Both instants are Julian Day in **UT**. Because a tithi is the Moon−Sun elongation, these are
 * global: an Ekadashi begins at the same instant everywhere on Earth. Only *which* local day it
 * is observed on varies, and that is exactly the part the rules decide.
 */
data class TithiOccurrence(
    /** 0–29, as `core.Tithi.index`. */
    val index: Int,
    val name: String,
    val numberInPaksha: Int,
    val paksha: org.panchang.core.Paksha,
    val lunarMonthName: String,
    val isAdhikaMonth: Boolean,
    val startJdUt: Double,
    val endJdUt: Double,
) {
    init {
        require(index in 0..29) { "tithi index must be in 0..29, was $index" }
        require(endJdUt > startJdUt) { "tithi occurrence $name has non-positive duration" }
    }
}

/**
 * A tradition's ruling on one Ekadashi.
 *
 * [reason] is not a debug string. Users of a panchang want to know *why* a date differs from
 * the one they expected — from another app, another temple, or last year's pattern — and a
 * rule engine that can only answer "trust me" is not usable for that. It should read like a
 * sentence a devotee would accept from a pandit: "Dashami was still running at arunodaya, so
 * the fast is deferred to the Dvadashi (Unmilani Mahadvadashi)."
 */
data class ObservanceDecision(
    /** The local date on which the fast is kept. */
    val date: LocalDate,
    val name: String,
    val kind: FastKind,
    val mahadvadashiType: MahadvadashiType?,
    /** The tithi occurrence the fast is named for. */
    val tithi: TithiOccurrence,
    /** Null only when a window could not be derived; [reason] must then explain the omission. */
    val parana: ParanaWindow?,
    val reason: String,
    val confidence: RuleConfidence,
) {
    init {
        require(reason.isNotBlank()) { "every decision must state its reason" }
        require((kind == FastKind.MAHADVADASHI) == (mahadvadashiType != null)) {
            "a Mahadvadashi must name which of the eight it is, and an ordinary Ekadashi must " +
                "not name one; got kind=$kind type=$mahadvadashiType"
        }
    }
}
