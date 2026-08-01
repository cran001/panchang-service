package org.panchang.wire

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.panchang.core.GeoLocation
import org.panchang.ephemeris.TimeScale
import org.panchang.sampradaya.AbsenceReason
import org.panchang.sampradaya.EventResolution
import org.panchang.sampradaya.EventTime
import org.panchang.sampradaya.ObservanceAnchor
import org.panchang.sampradaya.ObservanceDecision
import org.panchang.sampradaya.ParanaWindow
import org.panchang.sampradaya.ResolvedEvent
import org.panchang.sampradaya.SampradayaRules
import org.panchang.sampradaya.TithiOccurrence
import org.panchang.sampradaya.YearResolution

/**
 * The **only** place in this project that turns a Julian Day into a civil date-time.
 *
 * One renderer, because three front doors that each format their own timestamps are three chances
 * to pick a different zone, a different precision, or a locale-sensitive formatter, and the
 * resulting disagreement shows up as a fasting time that differs between the app and the website.
 *
 * Nothing here decides anything. It converts domain values that the rule layer has already ruled
 * on, and it holds no rule of its own — with the single exception of [acceptSite], which is a
 * shared limit rather than a ruling about an observance.
 *
 * Deterministic by construction: no `Instant.now()`, no default zone, no `Locale.getDefault()`,
 * no dependence on map iteration order. Two runs over the same input produce the same bytes.
 *
 * @param zone the civil zone every `local` string this renderer produces will be expressed in.
 */
class WireRenderer(val zone: ZoneId) {

    constructor(location: GeoLocation) : this(location.zone)

    /**
     * One instant, as the pair described on [InstantDto].
     *
     * The Julian Day is rounded first and the local string is derived from the *rounded* value, so
     * the two halves of the pair are always renderings of one instant rather than of two instants
     * a rounding apart.
     */
    fun instant(jdUt: Double): InstantDto {
        require(jdUt.isFinite()) { "cannot render a non-finite Julian Day: $jdUt" }
        val rounded = roundJd(jdUt)
        val whole = Instant.ofEpochSecond(
            // Rounded to the nearest second, not truncated. Truncation would make every
            // displayed time up to a second early, which is the wrong direction to be wrong in
            // for a "fast until" instant.
            Math.floorDiv(TimeScale.epochMillis(rounded) + 500L, 1000L),
        )
        return InstantDto(local = whole.atZone(zone).format(LOCAL_FORMAT), jdUt = rounded)
    }

    /** All three shapes. [EventTime.Absent] deliberately produces no time field at all. */
    fun eventTime(time: EventTime): EventTimeDto = when (time) {
        is EventTime.At -> EventTimeDto.At(
            anchor = time.anchor,
            anchorDisplayName = time.anchor.displayName,
            confidence = time.confidence,
            basis = time.basis,
            at = instant(time.jdUt),
        )

        is EventTime.Window -> EventTimeDto.Window(
            anchor = time.anchor,
            anchorDisplayName = time.anchor.displayName,
            confidence = time.confidence,
            basis = time.basis,
            start = instant(time.startJdUt),
            end = instant(time.endJdUt),
            durationMinutes = roundMinutes(time.durationMinutes),
        )

        is EventTime.Absent -> EventTimeDto.Absent(
            anchor = time.anchor,
            anchorDisplayName = time.anchor.displayName,
            confidence = time.confidence,
            basis = time.basis,
            reason = time.reason,
            reasonText = absenceSentence(time.anchor, time.reason),
        )
    }

    fun tithi(occurrence: TithiOccurrence): TithiOccurrenceDto = TithiOccurrenceDto(
        index = occurrence.index,
        name = occurrence.name,
        numberInPaksha = occurrence.numberInPaksha,
        paksha = occurrence.paksha,
        lunarMonthName = occurrence.lunarMonthName,
        isAdhikaMonth = occurrence.isAdhikaMonth,
        start = instant(occurrence.startJdUt),
        end = instant(occurrence.endJdUt),
    )

    fun parana(window: ParanaWindow): ParanaWindowDto = ParanaWindowDto(
        date = window.date,
        start = instant(window.startJdUt),
        end = instant(window.endJdUt),
        startReason = window.startReason,
        endReason = window.endReason,
        durationMinutes = roundMinutes(window.durationMinutes),
    )

    fun decision(decision: ObservanceDecision): ObservanceDecisionDto = ObservanceDecisionDto(
        date = decision.date,
        name = decision.name,
        kind = decision.kind,
        mahadvadashiType = decision.mahadvadashiType,
        tithi = tithi(decision.tithi),
        parana = decision.parana?.let(::parana),
        reason = decision.reason,
        confidence = decision.confidence,
    )

    fun event(event: ResolvedEvent): ResolvedEventDto = ResolvedEventDto(
        id = event.id,
        name = event.name,
        group = event.group,
        date = event.date,
        fastingNote = event.fastingNote,
        reason = event.reason,
        confidence = event.confidence,
        fastUntil = event.fastUntil?.let(::eventTime),
        tithi = event.tithi?.let(::tithi),
    )

    fun resolution(resolution: EventResolution): EventResolutionDto = when (resolution) {
        is EventResolution.Resolved -> EventResolutionDto.Resolved(event(resolution.event))
        is EventResolution.NoOccurrence -> EventResolutionDto.NoOccurrence(resolution.why)
        is EventResolution.TithiSkipped -> EventResolutionDto.TithiSkipped(resolution.why)
        is EventResolution.BeyondTabulatedData ->
            EventResolutionDto.BeyondTabulatedData(resolution.why)
    }

    /**
     * A whole year, failures included.
     *
     * `unresolved` is copied across in full and sorted by id. Sorted because the domain map's
     * iteration order is an accident of how the resolver happened to walk the catalog, and a
     * published artifact whose key order shifts between runs cannot be diffed — which would
     * defeat the reason the map is published at all.
     */
    fun yearResolution(
        year: Int,
        location: GeoLocation,
        rules: SampradayaRules,
        resolved: YearResolution,
    ): YearResolutionDto = YearResolutionDto(
        year = year,
        site = site(location),
        sampradaya = sampradaya(rules),
        events = resolved.events.map(::event),
        unresolved = resolved.unresolved.entries
            .sortedBy { it.key }
            .associateTo(LinkedHashMap()) { it.key to resolution(it.value) },
    )

    fun ekadashiYear(
        year: Int,
        location: GeoLocation,
        rules: SampradayaRules,
        observances: List<ObservanceDecision>,
    ): EkadashiYearDto = EkadashiYearDto(
        year = year,
        site = site(location),
        sampradaya = sampradaya(rules),
        observances = observances.map(::decision),
    )

    companion object {

        /**
         * Decimal places kept on every emitted Julian Day.
         *
         * `1e-5` day is 0.864 s, a shade finer than the one-second resolution of the `local`
         * string beside it. Fixed rather than "whatever `Double.toString` felt like" so the same
         * instant always renders as the same characters — a payload that differs from the
         * previous run in the last bit of a float is a payload nobody will bother diffing twice.
         */
        const val JD_DECIMALS: Int = 5

        private const val JD_SCALE: Double = 1e5

        /** Minutes are rounded to milliseconds, for the same reason. */
        private const val MINUTE_SCALE: Double = 1e3

        /**
         * `uuuu` and not `yyyy`: proleptic year, so there is no era to get wrong. [Locale.ROOT]
         * so a build agent in a locale with non-Arabic digits does not emit different bytes.
         * `XXX` renders the offset as `+05:30`, and `Z` at zero offset, both ISO-8601.
         */
        private val LOCAL_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXXX", Locale.ROOT)

        internal fun roundJd(jdUt: Double): Double = Math.round(jdUt * JD_SCALE) / JD_SCALE

        private fun roundMinutes(minutes: Double): Double =
            Math.round(minutes * MINUTE_SCALE) / MINUTE_SCALE

        fun site(location: GeoLocation): SiteDto = SiteDto(
            latitude = location.latitude,
            longitude = location.longitude,
            timeZone = location.zone.id,
            elevationMeters = location.elevationMeters,
        )

        /** Zone-free, so it needs no renderer instance. */
        fun sampradaya(rules: SampradayaRules): SampradayaDto = SampradayaDto(
            id = rules.id,
            displayName = rules.displayName,
            status = rules.status,
            provenanceNote = rules.provenanceNote,
        )

        fun sampradayaList(rules: List<SampradayaRules>): SampradayaListDto =
            SampradayaListDto(sampradayas = rules.map(::sampradaya))

        /**
         * The absence, as a sentence a client can show without rewriting it.
         *
         * The enum name alone reaches a user as `NO_EVENT_IN_WINDOW`, which means nothing to a
         * devotee wondering when to break their fast. Every one of these is a *correct* answer —
         * the astronomy is sound and the instant simply is not there — so the sentence says what
         * is true rather than apologising for a failure.
         */
        internal fun absenceSentence(anchor: ObservanceAnchor, reason: AbsenceReason): String {
            val what = anchor.displayName
            val because = when (reason) {
                AbsenceReason.CIRCUMPOLAR_UP ->
                    "the body stays above the horizon for the whole civil day"

                AbsenceReason.CIRCUMPOLAR_DOWN ->
                    "the body stays below the horizon for the whole civil day"

                AbsenceReason.NO_EVENT_IN_WINDOW ->
                    "no such crossing falls inside this civil day"

                AbsenceReason.TWILIGHT_NOT_REACHED ->
                    "the Sun never descends 6 degrees below the horizon"

                AbsenceReason.NIGHT_NOT_WELL_DEFINED ->
                    "the night could not be bounded here, so it could not be divided"
            }
            return "There is no $what here today: $because."
        }
    }
}
