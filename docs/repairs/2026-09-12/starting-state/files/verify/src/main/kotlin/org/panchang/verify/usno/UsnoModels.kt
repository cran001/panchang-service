package org.panchang.verify.usno

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A one-day rise/set/transit request for a single location. */
data class UsnoQuery(
    /** ISO date, YYYY-MM-DD. */
    val date: String,
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    /**
     * UTC offset in hours that USNO should express its times in.
     *
     * USNO takes a fixed numeric offset, not an IANA zone, and has no notion of a DST
     * transition inside the requested day. The caller must therefore resolve the correct
     * offset for this date from the location's IANA zone before asking — which is exactly
     * the arithmetic the DST stress cities exist to test.
     */
    val utcOffsetHours: Double,
    /** Optional label echoed back by USNO; used only to make cached files readable. */
    val label: String? = null,
)

// ---------------------------------------------------------------------------
// Wire format. Field names match USNO's JSON exactly; do not rename.
// ---------------------------------------------------------------------------

@Serializable
data class UsnoResponse(
    val apiversion: String? = null,
    val error: Boolean? = null,
    val type: String? = null,
    val geometry: UsnoGeometry? = null,
    val properties: UsnoProperties? = null,
)

@Serializable
data class UsnoGeometry(
    /** GeoJSON order: [longitude, latitude]. */
    val coordinates: List<Double> = emptyList(),
    val type: String? = null,
)

@Serializable
data class UsnoProperties(val data: UsnoData? = null)

@Serializable
data class UsnoData(
    val day: Int,
    val month: Int,
    val year: Int,
    val tz: Double,
    val isdst: Boolean? = null,
    @SerialName("day_of_week") val dayOfWeek: String? = null,
    val curphase: String? = null,
    val fracillum: String? = null,
    val label: String? = null,
    val closestphase: UsnoClosestPhase? = null,
    val sundata: List<UsnoPhenomenon> = emptyList(),
    val moondata: List<UsnoPhenomenon> = emptyList(),
)

@Serializable
data class UsnoClosestPhase(
    val phase: String,
    val year: Int,
    val month: Int,
    val day: Int,
    val time: String,
)

/**
 * One phenomenon entry.
 *
 * [time] is null for the polar cases, where [phen] carries the whole meaning:
 * "Object continuously above the Horizon". Those are the responses that matter most —
 * they are the cases where a naive "sunrise = sundata[0].time" reader produces either a
 * crash or, far worse, a plausible wrong time.
 */
@Serializable
data class UsnoPhenomenon(
    val phen: String,
    val time: String? = null,
)

// ---------------------------------------------------------------------------
// Normalised form.
// ---------------------------------------------------------------------------

/** A day with no rise or no set, as USNO reports it. */
enum class PolarCondition {
    CONTINUOUSLY_ABOVE_HORIZON,
    CONTINUOUSLY_BELOW_HORIZON,
    CONTINUOUSLY_ABOVE_TWILIGHT_LIMIT,
    CONTINUOUSLY_BELOW_TWILIGHT_LIMIT,
    ;

    companion object {
        fun fromPhen(phen: String): PolarCondition? {
            val p = phen.lowercase()
            if (!p.startsWith("object continuously")) return null
            val twilight = p.contains("twilight")
            val above = p.contains("above")
            return when {
                twilight && above -> CONTINUOUSLY_ABOVE_TWILIGHT_LIMIT
                twilight -> CONTINUOUSLY_BELOW_TWILIGHT_LIMIT
                above -> CONTINUOUSLY_ABOVE_HORIZON
                else -> CONTINUOUSLY_BELOW_HORIZON
            }
        }
    }
}

/**
 * A normalised USNO day.
 *
 * Rises and sets are lists, not single values. A day can legitimately contain two
 * moonsets (one just after local midnight, one just before the next), and collapsing
 * that to a single `moonset` field is a silent data loss that shows up later as an
 * unexplained one-day offset.
 */
@Serializable
data class UsnoDayRecord(
    val date: String,
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val utcOffsetHours: Double,
    val isDst: Boolean?,
    val sunRises: List<String> = emptyList(),
    val sunSets: List<String> = emptyList(),
    val sunUpperTransits: List<String> = emptyList(),
    val beginCivilTwilight: String? = null,
    val endCivilTwilight: String? = null,
    val moonRises: List<String> = emptyList(),
    val moonSets: List<String> = emptyList(),
    val moonUpperTransits: List<String> = emptyList(),
    val sunCondition: PolarCondition? = null,
    val sunTwilightCondition: PolarCondition? = null,
    val moonCondition: PolarCondition? = null,
    val currentMoonPhase: String? = null,
    val fractionIlluminated: String? = null,
    val closestPhase: UsnoClosestPhase? = null,
) {
    /** True when USNO reports the sun as never crossing the horizon on this date. */
    val sunHasNoRiseOrSet: Boolean get() = sunRises.isEmpty() && sunSets.isEmpty()
}
