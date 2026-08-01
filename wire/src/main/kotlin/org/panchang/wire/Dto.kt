package org.panchang.wire

import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.panchang.core.Paksha
import org.panchang.sampradaya.AbsenceReason
import org.panchang.sampradaya.EventGroup
import org.panchang.sampradaya.FastKind
import org.panchang.sampradaya.MahadvadashiType
import org.panchang.sampradaya.ObservanceAnchor
import org.panchang.sampradaya.ParanaBoundReason
import org.panchang.sampradaya.RuleConfidence
import org.panchang.sampradaya.VerificationStatus

/**
 * Marker for the payloads that are legitimate document roots.
 *
 * A root carries [schemaVersion]; the types nested inside one do not, because repeating the
 * version on every event in a year would be noise and would invite the two copies to disagree.
 */
sealed interface WirePayload {
    val schemaVersion: Int
}

/**
 * One instant, rendered twice on purpose.
 *
 * [local] is what a human reads. [jdUt] is what makes a disagreement diagnosable: given only a
 * rendered string, two implementations that disagree about a sunrise cannot be told apart from
 * two implementations that agree about the sunrise and disagree about the zone. The Android feed
 * this project replaces transmitted only the string, and a timezone bug in it stayed invisible
 * for exactly that reason — there was nothing in the payload to check the string against.
 *
 * @property local ISO-8601 offset date-time at the site's civil zone, to the second:
 *   `2026-01-15T06:20:31+05:30`. Truncated, not rounded; [jdUt] is the authoritative value.
 * @property jdUt Julian Day in **UT**, matching `core` and `sampradaya`. Rounded to
 *   [WireRenderer.JD_DECIMALS] places, which is a hair finer than the one-second resolution of
 *   [local], so the pair never claims a precision the other half cannot support.
 */
@Serializable
data class InstantDto(
    val local: String,
    val jdUt: Double,
)

/**
 * The time of day an observance is anchored to — or the fact that there is no such time here
 * today.
 *
 * Polymorphic rather than a nullable instant, because [Absent] must survive the trip. A missing
 * moonrise at 65°N has to arrive at the client as "the Moon does not rise here today"; a schema
 * with one optional time field would let it arrive as nothing at all, and a client would render
 * a blank where a fast's end belongs.
 *
 * [basis] and [confidence] are carried verbatim from the domain. Two of the six anchors — dusk
 * and Nisita-kala — rest on a reading this project chose between defensible alternatives, and the
 * reasoning is written into `basis` precisely so a reviewing pandit can challenge it. It is
 * useless if it stops at a module boundary.
 */
@Serializable
sealed interface EventTimeDto {

    val anchor: ObservanceAnchor

    /** The anchor's own words, e.g. `midnight (Nisita-kala)`, for clients that render a label. */
    val anchorDisplayName: String

    /** Confidence in the *reading of the prose*, not in the arithmetic. */
    val confidence: RuleConfidence

    /** The definition actually used, in words. */
    val basis: String

    /** The anchor is an instant, and here it is. */
    @Serializable
    @SerialName("at")
    data class At(
        @Serializable(with = ObservanceAnchorSerializer::class)
        override val anchor: ObservanceAnchor,
        override val anchorDisplayName: String,
        @Serializable(with = RuleConfidenceSerializer::class)
        override val confidence: RuleConfidence,
        override val basis: String,
        val at: InstantDto,
    ) : EventTimeDto

    /**
     * The anchor is an interval, because the tradition names one.
     *
     * Nisita-kala is a muhurta of the night, not a point in it. Emitting a midpoint would be a
     * claim the tradition does not make, so both bounds travel.
     */
    @Serializable
    @SerialName("window")
    data class Window(
        @Serializable(with = ObservanceAnchorSerializer::class)
        override val anchor: ObservanceAnchor,
        override val anchorDisplayName: String,
        @Serializable(with = RuleConfidenceSerializer::class)
        override val confidence: RuleConfidence,
        override val basis: String,
        val start: InstantDto,
        val end: InstantDto,
        val durationMinutes: Double,
    ) : EventTimeDto

    /**
     * There is no such instant here today, and [reason] says which of the ways that happened.
     *
     * Deliberately carries **no time-shaped field at all** — not a null, not an empty string.
     * Either of those would let a careless client format it and produce a plausible-looking clock
     * time for a moonrise that does not occur.
     */
    @Serializable
    @SerialName("absent")
    data class Absent(
        @Serializable(with = ObservanceAnchorSerializer::class)
        override val anchor: ObservanceAnchor,
        override val anchorDisplayName: String,
        @Serializable(with = RuleConfidenceSerializer::class)
        override val confidence: RuleConfidence,
        override val basis: String,
        @Serializable(with = AbsenceReasonSerializer::class)
        val reason: AbsenceReason,
        /** The same fact as [reason], in a sentence a client can show unmodified. */
        val reasonText: String,
    ) : EventTimeDto
}

/**
 * A single occurrence of a tithi, start to end, at the requesting site's zone.
 *
 * Present in the payload because devotees ask when the tithi starts and ends *where they are*,
 * and a bare festival date cannot answer that.
 */
@Serializable
data class TithiOccurrenceDto(
    /** 0–29, as `core.Tithi.index`. */
    val index: Int,
    val name: String,
    val numberInPaksha: Int,
    @Serializable(with = PakshaSerializer::class)
    val paksha: Paksha,
    val lunarMonthName: String,
    val isAdhikaMonth: Boolean,
    val start: InstantDto,
    val end: InstantDto,
)

/** The interval within which a fast must be broken, with each bound naming why it is where it is. */
@Serializable
data class ParanaWindowDto(
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val start: InstantDto,
    val end: InstantDto,
    @Serializable(with = ParanaBoundReasonSerializer::class)
    val startReason: ParanaBoundReason,
    @Serializable(with = ParanaBoundReasonSerializer::class)
    val endReason: ParanaBoundReason,
    /** Some genuine windows are under 40 minutes wide, so clients should not assume comfort. */
    val durationMinutes: Double,
)

/** A tradition's ruling on one Ekadashi. */
@Serializable
data class ObservanceDecisionDto(
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val name: String,
    @Serializable(with = FastKindSerializer::class)
    val kind: FastKind,
    /** Which of the eight, when [kind] is `MAHADVADASHI`; absent otherwise. */
    @Serializable(with = MahadvadashiTypeSerializer::class)
    val mahadvadashiType: MahadvadashiType? = null,
    val tithi: TithiOccurrenceDto,
    /** Absent only when no window could be derived, in which case [reason] explains the omission. */
    val parana: ParanaWindowDto? = null,
    /**
     * Why this date and not another, as a sentence.
     *
     * Not a debug string. Users compare against another app, another temple, or last year's
     * pattern, and a rule engine whose only answer is "trust me" is not usable for that.
     */
    val reason: String,
    @Serializable(with = RuleConfidenceSerializer::class)
    val confidence: RuleConfidence,
)

/** An event definition resolved to an actual date at an actual site. */
@Serializable
data class ResolvedEventDto(
    val id: String,
    val name: String,
    @Serializable(with = EventGroupSerializer::class)
    val group: EventGroup,
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    /** Absent when the event is observed without a fast. */
    val fastingNote: String? = null,
    val reason: String,
    /**
     * Confidence in the **date**.
     *
     * Kept separate from `fastUntil.confidence` on purpose: Janmastami's date is CONFIRMED while
     * reading "fast till midnight" as Nisita-kala is INFERRED, and one shared number would have
     * to lie about one of them.
     */
    @Serializable(with = RuleConfidenceSerializer::class)
    val confidence: RuleConfidence,
    /** When the fast ends, computed at this site; absent when the event names no time. */
    val fastUntil: EventTimeDto? = null,
    /** The tithi occurrence that qualified this date; absent for nakshatra and tabulated rules. */
    val tithi: TithiOccurrenceDto? = null,
)

/**
 * What happened when one catalog entry was resolved.
 *
 * Polymorphic and never a bare string, because the four outcomes are not interchangeable: a
 * kshaya tithi is a ruling this project does not have a source for, while a tabulated table
 * running out is a data-maintenance job. A client that cannot tell them apart cannot say anything
 * true to the user about either.
 */
@Serializable
sealed interface EventResolutionDto {

    /** Only appears in [YearResolutionDto.unresolved] if a caller puts it there; normally not. */
    @Serializable
    @SerialName("resolved")
    data class Resolved(val event: ResolvedEventDto) : EventResolutionDto

    /** The rule is well formed but names nothing in this year at this site. */
    @Serializable
    @SerialName("noOccurrence")
    data class NoOccurrence(val why: String) : EventResolutionDto

    /** The tithi the rule names was skipped: no day carried it, and moving it is not our call. */
    @Serializable
    @SerialName("tithiSkipped")
    data class TithiSkipped(val why: String) : EventResolutionDto

    /** A tabulated entry with no date for this year: the table has run out. */
    @Serializable
    @SerialName("beyondTabulatedData")
    data class BeyondTabulatedData(val why: String) : EventResolutionDto
}

/**
 * Everything a catalog produced for one year at one site — successes **and** failures.
 *
 * [unresolved] is not optional and not a debugging aid. The sect seam deliberately returns the
 * entries it could not place, because a silently missing festival is worse than a visibly wrong
 * one: there is no artifact for anyone to notice or challenge. A DTO that dropped the map would
 * reintroduce the exact failure the seam exists to prevent, and the payload would look complete
 * while being short a festival.
 *
 * Keyed by event id and emitted in ascending id order so two runs of the same input diff clean.
 */
@Serializable
data class YearResolutionDto(
    override val schemaVersion: Int = WIRE_SCHEMA_VERSION,
    val year: Int,
    val site: SiteDto,
    val sampradaya: SampradayaDto,
    val events: List<ResolvedEventDto>,
    val unresolved: Map<String, EventResolutionDto>,
) : WirePayload

/** The site a payload was computed for, echoed back so a cached artifact can identify itself. */
@Serializable
data class SiteDto(
    val latitude: Double,
    val longitude: Double,
    /** IANA zone id, e.g. `Asia/Kolkata`. The zone every `local` string in the payload used. */
    val timeZone: String,
    val elevationMeters: Double,
)

/**
 * A tradition, with how much this project actually knows about its rules.
 *
 * [status] and [provenanceNote] travel with every payload rather than living in documentation,
 * because a user cannot weigh a date they were not told the provenance of. Religious correctness
 * is reputational: an unverified date presented alongside a verified one, with nothing to tell
 * them apart, is the failure mode this field exists to stop.
 */
@Serializable
data class SampradayaDto(
    val id: String,
    val displayName: String,
    @Serializable(with = VerificationStatusSerializer::class)
    val status: VerificationStatus,
    val provenanceNote: String,
)

/** The registry, as a document root, for a "which traditions do you know" response. */
@Serializable
data class SampradayaListDto(
    override val schemaVersion: Int = WIRE_SCHEMA_VERSION,
    val sampradayas: List<SampradayaDto>,
) : WirePayload

/** A year's Ekadashi and Mahadvadashi rulings, as a document root. */
@Serializable
data class EkadashiYearDto(
    override val schemaVersion: Int = WIRE_SCHEMA_VERSION,
    val year: Int,
    val site: SiteDto,
    val sampradaya: SampradayaDto,
    val observances: List<ObservanceDecisionDto>,
) : WirePayload
