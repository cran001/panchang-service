package org.panchang.sampradaya

import org.panchang.core.DayDivisions
import org.panchang.core.MoonTimes
import org.panchang.core.RiseSet
import org.panchang.core.SunTimes

/**
 * Why an anchored time does not exist on a given day at a given site.
 *
 * Every one of these is a *correct* answer, not a failure. The site and the date are legitimate
 * and the astronomy is sound; the instant simply is not there to be reported.
 */
enum class AbsenceReason {
    /** The body stayed above its rise/set horizon for the whole civil day: midnight sun. */
    CIRCUMPOLAR_UP,

    /** The body stayed below its rise/set horizon for the whole civil day: polar night. */
    CIRCUMPOLAR_DOWN,

    /**
     * The body crossed the horizon during the day, but not in the direction wanted.
     *
     * Ordinary and non-polar. Moonrise runs about 50 minutes later each day, so roughly once a
     * lunar month a civil day anywhere on Earth contains a moonset and no moonrise. Gaura
     * Purnima's "fast till moonrise" is a full-moon observance and so is the case least likely
     * to hit this — which is exactly why it must still be representable rather than assumed away.
     */
    NO_EVENT_IN_WINDOW,

    /**
     * The Sun never descended to the twilight depression: no dusk, because it never got dark.
     *
     * Distinct from [CIRCUMPOLAR_UP], which is about the rise/set horizon. A site can have a
     * perfectly ordinary sunset and still never reach −6°, which is what a white night is.
     */
    TWILIGHT_NOT_REACHED,

    /**
     * The night could not be bounded, so it could not be divided.
     *
     * Nisita-kala is a fraction of the interval from sunset to the next sunrise. Where either
     * end is missing, or where the "night" comes out non-positive, there is no interval to take
     * an eighth of and no honest way to place the muhurta.
     */
    NIGHT_NOT_WELL_DEFINED,
}

/**
 * The time of day an observance is anchored to, at one site on one date.
 *
 * ## Why not `core.RiseSet`
 *
 * `RiseSet` is the right shape for a horizon crossing and the wrong shape for three of the six
 * anchors this type serves. Solar noon is a meridian transit and always exists, so a type with
 * `CircumpolarDown` in it misrepresents it. Nisita-kala is a muhurta — an *interval* — and
 * flattening it to its midpoint would report a precision the tradition does not state. And dusk
 * can fail because the Sun never reached −6°, a reason `RiseSet` has no case for and would have
 * to mislabel as circumpolar.
 *
 * ## Why this is sealed
 *
 * [Absent] is the whole reason. A missing moonrise at 65°N must arrive at the user as "the Moon
 * does not rise here today", never as a plausible-looking clock time derived from a fallback.
 * A nullable `Double` would let every call site invent its own substitute; a sealed type makes
 * the absence something the renderer has to handle and the reader has to read.
 *
 * All instants are Julian Day in **UT**, matching `core`.
 */
sealed interface EventTime {

    val anchor: ObservanceAnchor

    /**
     * How confident we are in the reading of the prose that produced [anchor] — **not** in the
     * arithmetic, which is ordinary astronomy and is as good as the ephemeris.
     *
     * Derived from [ObservanceAnchor.mappingConfidence] rather than stored, so that no
     * construction site can grade an anchor differently from the anchor's own grading. See the
     * KDoc on [ObservanceAnchor] for why this is graded separately from the event's date.
     */
    val confidence: RuleConfidence get() = anchor.mappingConfidence

    /**
     * The definition used, in words, verbatim into the payload.
     *
     * Not a debug string and not a comment. Two of the six anchors rest on a choice this project
     * made between defensible readings, and a reviewing pandit cannot challenge a choice that
     * was only ever written down in a source file. The definition therefore travels with the
     * value.
     */
    val basis: String

    /** The anchor is an instant, and here it is. */
    data class At(
        override val anchor: ObservanceAnchor,
        val jdUt: Double,
        override val basis: String,
    ) : EventTime {
        init {
            require(jdUt.isFinite()) { "$anchor instant must be finite, was $jdUt" }
            require(basis.isNotBlank()) { "$anchor must state the definition it used" }
        }
    }

    /**
     * The anchor is an interval, because the tradition names one.
     *
     * Nisita-kala is a muhurta of the night, not a point in it. Reporting the midpoint would be
     * a claim the tradition does not make.
     */
    data class Window(
        override val anchor: ObservanceAnchor,
        val startJdUt: Double,
        val endJdUt: Double,
        override val basis: String,
    ) : EventTime {
        init {
            require(startJdUt.isFinite() && endJdUt.isFinite()) {
                "$anchor window bounds must be finite, were $startJdUt..$endJdUt"
            }
            require(endJdUt > startJdUt) {
                "$anchor window is inverted or empty: $startJdUt..$endJdUt"
            }
            require(basis.isNotBlank()) { "$anchor must state the definition it used" }
        }

        val durationMinutes: Double get() = (endJdUt - startJdUt) * 24.0 * 60.0
    }

    /** The anchor has no instant here today, and [reason] says which of the ways that happened. */
    data class Absent(
        override val anchor: ObservanceAnchor,
        val reason: AbsenceReason,
        override val basis: String,
    ) : EventTime {
        init {
            require(basis.isNotBlank()) { "$anchor must state the definition it used" }
        }
    }

    /** The instant if there is exactly one, otherwise null. Windows deliberately yield null. */
    val jdUtOrNull: Double? get() = (this as? At)?.jdUt
}

/**
 * The only place an [EventTime] is built.
 *
 * Every anchor has exactly one definition and it is written here once, so the resolver cannot
 * quietly hold a second one. In particular [fromRiseSet] is the sole conversion from
 * `core.RiseSet`: no call site gets to see a `RiseSet` and decide for itself what a
 * `CircumpolarDown` should be rendered as.
 */
internal object EventTimes {

    /** Definition strings, held apart from the functions so tests can assert on them. */
    const val SUNRISE_BASIS: String =
        "sunrise: the Sun's upper limb on the horizon (centre at −0°50′), the same instant the " +
            "day's panchanga is read at"

    const val SOLAR_NOON_BASIS: String =
        "solar noon: the Sun's upper meridian transit at this longitude. Not 12:00 civil time, " +
            "which can sit more than an hour away from it at a wide zone's edge"

    const val SUNSET_BASIS: String =
        "sunset: the Sun's upper limb on the horizon (centre at −0°50′) while descending"

    const val MOONRISE_BASIS: String =
        "moonrise: the Moon's centre on the horizon while rising, including its parallax at the " +
            "Moon's distance on the day"

    /**
     * Dusk is deliberately *not* sunset. The catalog distinguishes "Fast till sunset" (Rama
     * Navami) from "Fast till dusk" (Nrsimha Caturdasi); collapsing the two would erase a
     * distinction the source itself drew.
     */
    const val DUSK_BASIS: String =
        "dusk: end of civil twilight, the Sun's centre 6° below the horizon after sunset. Read " +
            "as end of civil twilight rather than as sunset because the source distinguishes " +
            "'till dusk' from 'till sunset' and the two must not collapse. No horizon dip is " +
            "applied: twilight is defined against the geometric horizon, which also keeps this " +
            "consistent with the project's sea-level convention. Flagged INFERRED for pandit " +
            "review."

    /**
     * The Nisita assumption, stated in full because it must reach the payload and not stop at a
     * code comment.
     */
    const val NISITA_BASIS: String =
        "Nisita-kala: the 8th of the 15 equal muhurtas of the night, the night taken from sunset " +
            "to the following sunrise — hence the muhurta containing solar midnight. Chosen over " +
            "civil 00:00 because civil midnight is an artefact of the zone meridian and of DST — " +
            "at a wide zone's western edge it sits over an hour from solar midnight, which would " +
            "put the same observance at a different point of the night for two devotees in one " +
            "country. Flagged INFERRED for pandit review."

    /** The night is divided into fifteen muhurtas, as the day is. */
    const val NIGHT_MUHURTAS: Int = 15

    /** Nisita is the eighth of them: the middle one, hence the one holding solar midnight. */
    const val NISITA_MUHURTA: Int = 8

    fun sunrise(sun: SunTimes): EventTime =
        fromRiseSet(ObservanceAnchor.SUNRISE, sun.sunrise, SUNRISE_BASIS)

    /**
     * Always an [EventTime.At]. A body crosses the meridian every day at every latitude, so
     * unlike rise and set this has no absent case at all — which is the first of the three
     * reasons `RiseSet` was the wrong type to reuse.
     */
    fun solarNoon(sun: SunTimes): EventTime =
        EventTime.At(ObservanceAnchor.SOLAR_NOON, sun.solarNoonJdUt, SOLAR_NOON_BASIS)

    fun sunset(sun: SunTimes): EventTime =
        fromRiseSet(ObservanceAnchor.SUNSET, sun.sunset, SUNSET_BASIS)

    fun moonrise(moon: MoonTimes): EventTime =
        fromRiseSet(ObservanceAnchor.MOONRISE, moon.moonrise, MOONRISE_BASIS)

    /**
     * @param twilightEnd the result of `PanchangCalculator.civilTwilightEnd`.
     *
     * `CircumpolarUp` from that solve means the Sun stayed above −6° all day — a white night —
     * so it maps to [AbsenceReason.TWILIGHT_NOT_REACHED] and not to
     * [AbsenceReason.CIRCUMPOLAR_UP], which would be a claim about the rise/set horizon that the
     * solve never tested.
     */
    fun dusk(twilightEnd: RiseSet): EventTime = fromRiseSet(
        anchor = ObservanceAnchor.DUSK,
        riseSet = twilightEnd,
        basis = DUSK_BASIS,
        whenAlwaysAbove = AbsenceReason.TWILIGHT_NOT_REACHED,
    )

    /**
     * The Nisita muhurta of the night beginning at [sunsetOfDay] and ending at
     * [sunriseOfNextDay].
     *
     * No new `core` primitive is needed: [DayDivisions.equalPart] divides the interval between
     * two arbitrary instants and says so, so the night is just another interval to hand it.
     *
     * Both ends are required and the night must be positive. A non-positive night is not merely
     * a polar artefact — at a site whose civil zone is far from its longitude, the sunset and
     * the sunrise falling in one *civil* day can be in either order, and dividing that backwards
     * would silently place the muhurta in the middle of the afternoon.
     */
    fun nisitaKala(sunsetOfDay: RiseSet, sunriseOfNextDay: RiseSet): EventTime {
        val start = sunsetOfDay.jdUtOrNull
        val end = sunriseOfNextDay.jdUtOrNull
        if (start == null || end == null || end <= start) {
            return EventTime.Absent(
                ObservanceAnchor.NISITA_KALA,
                AbsenceReason.NIGHT_NOT_WELL_DEFINED,
                NISITA_BASIS,
            )
        }
        val muhurta = DayDivisions.equalPart(start, end, NISITA_MUHURTA, NIGHT_MUHURTAS)
        return EventTime.Window(
            anchor = ObservanceAnchor.NISITA_KALA,
            startJdUt = muhurta.startJdUt,
            endJdUt = muhurta.endJdUt,
            basis = NISITA_BASIS,
        )
    }

    /**
     * The one and only `RiseSet` → [EventTime] conversion.
     *
     * Private on purpose. Everything above goes through it, so there is exactly one place that
     * decides what an absent horizon crossing means, and no resolver, renderer or future rule
     * can substitute an instant of its own for one.
     */
    private fun fromRiseSet(
        anchor: ObservanceAnchor,
        riseSet: RiseSet,
        basis: String,
        whenAlwaysAbove: AbsenceReason = AbsenceReason.CIRCUMPOLAR_UP,
        whenAlwaysBelow: AbsenceReason = AbsenceReason.CIRCUMPOLAR_DOWN,
    ): EventTime = when (riseSet) {
        is RiseSet.At -> EventTime.At(anchor, riseSet.jdUt, basis)
        RiseSet.CircumpolarUp -> EventTime.Absent(anchor, whenAlwaysAbove, basis)
        RiseSet.CircumpolarDown -> EventTime.Absent(anchor, whenAlwaysBelow, basis)
        RiseSet.NoEventInWindow ->
            EventTime.Absent(anchor, AbsenceReason.NO_EVENT_IN_WINDOW, basis)
    }
}
