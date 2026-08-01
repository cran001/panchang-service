package org.panchang.wire

import java.time.ZoneId
import java.time.zone.ZoneRulesException
import kotlin.math.abs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.panchang.core.GeoLocation

/**
 * The latitude beyond which this service will not answer, north or south.
 *
 * 66° is just inside the polar circles. Above it there are days with no sunrise at all, and the
 * observance rules are written against one — "the tithi running at sunrise", "Dashami still
 * running at arunodaya", "the first third of the daylight period". With no sunrise there is
 * nothing to test the tithi against, and the tradition has no ruling on the substitute that this
 * project has a source for. Inventing one would produce a fasting date that looks authoritative
 * and is not.
 *
 * The limit lives in `:wire` so `:calc`, `:api` and `:publish` cannot disagree about where the
 * world ends. Three copies of a constant is two copies too many.
 *
 * A site exactly *at* 66.0° is accepted: the limit is stated as "above".
 */
const val POLAR_LIMIT_DEG: Double = 66.0

/** Why a requested site was refused. */
@Serializable
enum class SiteRejectionCode {
    /** Latitude beyond [POLAR_LIMIT_DEG]. Not a bug and not a data gap — a stated boundary. */
    ABOVE_POLAR_LIMIT,

    /** A coordinate that is NaN or infinite. */
    NON_FINITE_COORDINATE,

    /** Latitude outside `[-90, 90]`. */
    LATITUDE_OUT_OF_RANGE,

    /** Longitude outside `[-180, 180]`. */
    LONGITUDE_OUT_OF_RANGE,

    /** Elevation outside the range the horizon-dip correction is defined over. */
    ELEVATION_OUT_OF_RANGE,

    /** The zone id is not one this JVM's tz database knows. */
    UNKNOWN_TIME_ZONE,
}

/**
 * Whether this service will compute for a site, and if not, what to tell the person who asked.
 *
 * A boolean would be cheaper and useless: the caller has to put something true on the screen, and
 * "unsupported location" says neither which of the several reasons applies nor whether the user
 * can do anything about it. Serialisable so `:api` can return the refusal as a payload rather
 * than reformatting it into a message of its own.
 */
@Serializable
sealed interface SiteAcceptance {

    @Serializable
    @SerialName("accepted")
    data class Accepted(val site: SiteDto) : SiteAcceptance

    @Serializable
    @SerialName("rejected")
    data class Rejected(
        val code: SiteRejectionCode,
        /** A sentence the caller can show unmodified. */
        val reason: String,
    ) : SiteAcceptance

    val isAccepted: Boolean get() = this is Accepted
}

/**
 * Validate an already-constructed [GeoLocation].
 *
 * Only the polar limit can fail here: `GeoLocation`'s own constructor has already rejected
 * out-of-range coordinates, and re-checking them would be a second opinion nobody asked for.
 */
fun acceptSite(location: GeoLocation): SiteAcceptance =
    polarCheck(location.latitude) ?: SiteAcceptance.Accepted(WireRenderer.site(location))

/**
 * Validate raw request input, before a [GeoLocation] exists.
 *
 * This is the overload the front doors actually need. `GeoLocation` *throws* on bad input, and an
 * HTTP handler or a CLI cannot turn a thrown [IllegalArgumentException] into a useful message
 * without parsing its text. Here every failure is a value with a code on it.
 *
 * @return [SiteAcceptance.Accepted] carrying the site as it will appear in the payload, or
 *   [SiteAcceptance.Rejected] naming the first thing found wrong.
 */
fun acceptSite(
    latitude: Double,
    longitude: Double,
    zoneId: String,
    elevationMeters: Double = 0.0,
): SiteAcceptance {
    if (!latitude.isFinite() || !longitude.isFinite() || !elevationMeters.isFinite()) {
        return reject(
            SiteRejectionCode.NON_FINITE_COORDINATE,
            "Coordinates must be ordinary numbers; got latitude $latitude, longitude " +
                "$longitude, elevation $elevationMeters m.",
        )
    }
    if (latitude !in -90.0..90.0) {
        return reject(
            SiteRejectionCode.LATITUDE_OUT_OF_RANGE,
            "Latitude must be between -90 and 90 degrees; got $latitude.",
        )
    }
    if (longitude !in -180.0..180.0) {
        return reject(
            SiteRejectionCode.LONGITUDE_OUT_OF_RANGE,
            "Longitude must be between -180 and 180 degrees, east positive; got $longitude.",
        )
    }
    if (elevationMeters !in -500.0..12_000.0) {
        return reject(
            SiteRejectionCode.ELEVATION_OUT_OF_RANGE,
            "Elevation must be between -500 and 12000 m above the ellipsoid; got " +
                "$elevationMeters m.",
        )
    }
    polarCheck(latitude)?.let { return it }

    val zone = try {
        ZoneId.of(zoneId)
    } catch (e: ZoneRulesException) {
        return reject(
            SiteRejectionCode.UNKNOWN_TIME_ZONE,
            "'$zoneId' is not a time zone this service knows. Use an IANA zone id such as " +
                "'Asia/Kolkata'.",
        )
    } catch (e: java.time.DateTimeException) {
        return reject(
            SiteRejectionCode.UNKNOWN_TIME_ZONE,
            "'$zoneId' is not a well formed time zone id. Use an IANA zone id such as " +
                "'Asia/Kolkata'.",
        )
    }
    return SiteAcceptance.Accepted(
        WireRenderer.site(GeoLocation(latitude, longitude, zone, elevationMeters)),
    )
}

private fun polarCheck(latitude: Double): SiteAcceptance.Rejected? =
    if (abs(latitude) > POLAR_LIMIT_DEG) {
        reject(
            SiteRejectionCode.ABOVE_POLAR_LIMIT,
            "Latitude $latitude is beyond ${POLAR_LIMIT_DEG.toInt()} degrees. The observance " +
                "rules are defined against sunrise, and above this latitude there are days with " +
                "no sunrise to test a tithi against. The tradition has no ruling we have a " +
                "source for, so this service declines rather than guessing a fasting date.",
        )
    } else {
        null
    }

private fun reject(code: SiteRejectionCode, reason: String): SiteAcceptance.Rejected =
    SiteAcceptance.Rejected(code, reason)
