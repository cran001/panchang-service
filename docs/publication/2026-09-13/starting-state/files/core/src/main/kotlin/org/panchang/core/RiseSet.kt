package org.panchang.core

/**
 * Outcome of asking for a rise or set event within one civil day at one site.
 *
 * A sealed type, not a `Double`. The app engine signalled polar conditions with sentinel hours:
 * sunrise returned `0.0` for polar day and `24.0` for polar night, while sunset returned `20.0`
 * and `4.0` for the same two conditions — and the classifier downstream tested the sunrise
 * sentinels against the sunset return value, so its `POLAR_DAY` branch was unreachable. Nobody
 * noticed because a sentinel is type-compatible with a real answer. These cases are not
 * compatible, so they are not the same type.
 *
 * The four cases are exhaustive for a fixed day-long window.
 */
sealed interface RiseSet {

    /** The event occurs, at this Julian Day (UT). */
    data class At(val jdUt: Double) : RiseSet

    /** The body is above the horizon for the whole window: midnight sun, or a circumpolar Moon. */
    data object CircumpolarUp : RiseSet

    /** The body is below the horizon for the whole window: polar night. */
    data object CircumpolarDown : RiseSet

    /**
     * The body crosses the horizon during the window, but not in the requested direction.
     *
     * Not a polar case and not an error. Moonrise runs about 50 minutes later each day, so
     * roughly once a lunar month a civil day contains a moonset and no moonrise. Collapsing
     * that into "circumpolar" or into a null would lose the distinction the "fast until
     * moonrise" rules need.
     */
    data object NoEventInWindow : RiseSet

    /** The instant if the event occurred, otherwise `null`. Convenience for callers that map. */
    val jdUtOrNull: Double?
        get() = (this as? At)?.jdUt
}

/**
 * Solar events for one civil day at one site. All instants are Julian Day (UT).
 *
 * @param solarNoonJdUt the instant of upper transit. Always defined, including inside the polar
 *   circles where there is no rise or set — the Sun still crosses the meridian.
 * @param arunodaya four ghatikas (96 minutes) before sunrise; the start of the pre-dawn period
 *   several vrata rules are anchored to. Carries the same case as [sunrise], because a day with
 *   no sunrise has no arunodaya either.
 */
data class SunTimes(
    val sunrise: RiseSet,
    val sunset: RiseSet,
    val solarNoonJdUt: Double,
    val arunodaya: RiseSet,
) {
    /**
     * Length of the day from sunrise to sunset in days, or `null` when either end is undefined.
     * Negative values are possible and correct at sites whose civil zone is far from their
     * longitude, where the sunset in a civil day can precede the sunrise in that same day.
     */
    val daylightDays: Double?
        get() {
            val rise = sunrise.jdUtOrNull ?: return null
            val set = sunset.jdUtOrNull ?: return null
            return set - rise
        }

    /**
     * Daylight beginning at this civil day's sunrise, paired with its following sunset.
     * [followingDay] supplies the immediately following civil day's events at the same site,
     * and is evaluated only if this day's sunset is absent or precedes sunrise.
     *
     * Uses the actual next-date event. Civil days can span 23 or 25 hours across DST; adding
     * 24 hours to an earlier sunset or taking an absolute difference is not valid.
     * Returns null without sunrise or a following sunset in these two civil days. A sunset
     * after another sunrise cannot close this interval. Does not bridge a polar season or
     * invent a missing duration. [sunset] and [daylightDays] keep their civil-date semantics.
     */
    fun daylightInterval(followingDay: () -> SunTimes): DaylightInterval? {
        val rise = sunrise.jdUtOrNull ?: return null
        val sameDateSet = sunset.jdUtOrNull
        if (sameDateSet != null && sameDateSet > rise) {
            return DaylightInterval(rise, sameDateSet)
        }
        val next = followingDay()
        val set = next.sunset.jdUtOrNull ?: return null
        val nextRise = next.sunrise.jdUtOrNull
        if (set <= rise || (nextRise != null && nextRise < set)) return null
        return DaylightInterval(rise, set)
    }

    companion object {
        /** One ghatika is 24 minutes; arunodaya is four of them before sunrise. */
        const val ARUNODAYA_MINUTES_BEFORE_SUNRISE: Double = 96.0
    }
}

/** A real, ordered sunrise-to-following-sunset interval, in Julian Days (UT). */
data class DaylightInterval(val sunriseJdUt: Double, val sunsetJdUt: Double) {
    init {
        require(sunriseJdUt.isFinite() && sunsetJdUt.isFinite() && sunsetJdUt > sunriseJdUt)
    }

    val durationDays: Double get() = sunsetJdUt - sunriseJdUt
}

/** Lunar events for one civil day at one site. */
data class MoonTimes(
    val moonrise: RiseSet,
    val moonset: RiseSet,
)
