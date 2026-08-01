package org.panchang.core

import java.time.LocalDate

/**
 * Everything this module knows about one civil date at one site.
 *
 * Deliberately not a "day summary". Each element carries its own start and end, so a consumer
 * can ask whether a tithi was current at sunrise, whether it spans the whole day, whether it
 * ends before arunodaya — the questions the rule layer actually asks. Reducing an element to
 * "the one running at sunrise" throws that away and is the reason the engine this replaces
 * could not express a parana window at all.
 *
 * No sect rules here. Which tithi qualifies a vrata, whether a mahadvadashi applies, when a
 * fast may be broken — all of that is a later module's business and none of it is decidable
 * from astronomy alone.
 *
 * @param referenceJdUt the instant the four angular elements were sampled at: sunrise, per the
 *   usual convention. On a day with no sunrise it falls back to the midpoint of the civil day,
 *   which is arbitrary but at least stated.
 * @param rahuKaal null when the day has no ordinary daylight period — polar day or night, or
 *   the degenerate case of a site whose civil zone is so far from its longitude that the civil
 *   day's sunset precedes its sunrise. An eighth of a non-existent or negative day is not a
 *   thing, and a sentinel would only push the problem downstream.
 */
data class Panchang(
    val date: LocalDate,
    val location: GeoLocation,
    val reckoning: MonthReckoning,
    val ayanamshaId: String,
    val ayanamshaDegrees: Double,
    val referenceJdUt: Double,
    val vaar: Vaar,
    val sun: SunTimes,
    val moon: MoonTimes,
    val tithi: Tithi,
    val nakshatra: Nakshatra,
    val yoga: Yoga,
    val karana: Karana,
    val lunarMonth: LunarMonth,
    val rahuKaal: JdInterval?,
    val yamaganda: JdInterval?,
    val gulika: JdInterval?,
    val abhijit: JdInterval?,
)
