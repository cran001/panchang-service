package org.panchang.api

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.panchang.wire.SiteRejectionCode

/**
 * Refusal codes this module states for itself.
 *
 * Deliberately *not* overlapping [SiteRejectionCode]: a rejection that comes from `:wire` travels
 * under `:wire`'s own name and with `:wire`'s own sentence, unedited. These are the failures that
 * belong to the HTTP shape of the request — which parameters were given, and whether they parse —
 * and nothing else in the project has an opinion about those.
 */
enum class ApiErrorCode {
    /** No `lat`/`lon`/`tz` and no `place`/`placeId`. */
    MISSING_LOCATION,

    /** Coordinates *and* a place name. Two answers to "where" is not a preference to rank. */
    CONFLICTING_LOCATION,

    /** Some of `lat`/`lon`/`tz` but not all three. */
    INCOMPLETE_COORDINATES,

    /** A numeric parameter that is not a number. */
    MALFORMED_NUMBER,

    /** `{date}` is not `YYYY-MM-DD`. */
    MALFORMED_DATE,

    /** `{year}` is not an integer. */
    MALFORMED_YEAR,

    /** A required query parameter was absent. */
    MISSING_PARAMETER,

    /** The name matches more than one record and this service will not choose. */
    PLACE_AMBIGUOUS,

    /** No record carries this name. Nothing is substituted for it. */
    PLACE_NOT_FOUND,

    /** No record carries this GeoNames id. */
    PLACE_ID_UNKNOWN,

    /** The tradition is not in the registry. The response names what is. */
    UNKNOWN_SAMPRADAYA,

    /** Implemented locally but excluded from the public launch. */
    SAMPRADAYA_OUTSIDE_RELEASE,

    /**
     * The tradition is registered — it exists and this project knows it does — but carries no
     * rules. It is refused rather than answered with an empty calendar, which would be
     * indistinguishable from "your tradition observes nothing this year".
     */
    SAMPRADAYA_NOT_IMPLEMENTED,

    /** Something threw. The response says so rather than returning an HTML stack trace. */
    INTERNAL_ERROR,
}

/**
 * A refusal, thrown from wherever it is discovered and turned into a response by `StatusPages`.
 *
 * Carries the finished body rather than a message, because several refusals have to say more than
 * a sentence — the ambiguous-place case has to list every candidate, and a caller who is told only
 * "ambiguous" has been given no way to proceed.
 */
class ApiFailure(
    val status: HttpStatusCode,
    val body: JsonObject,
) : RuntimeException(body.toString())

/**
 * Build a refusal body.
 *
 * `error.code` is a machine-readable name — either an [ApiErrorCode] or a [SiteRejectionCode],
 * never a locally invented string — and `error.message` is a sentence the caller may show
 * unmodified. Anything a caller needs in order to *act* (candidates, known ids, hints) goes beside
 * `error`, not inside it, so it survives a client that only renders the message.
 */
internal fun failure(
    status: HttpStatusCode,
    code: String,
    message: String,
    extra: JsonObjectBuilder.() -> Unit = {},
): ApiFailure = ApiFailure(
    status,
    apiDocument {
        if (status == HttpStatusCode.UnprocessableEntity || status == HttpStatusCode.NotImplemented) {
            putJsonObject("publication") {
                put("state", JsonPrimitive("UNSUPPORTED"))
                put("guidance", JsonPrimitive("WITHHELD"))
                put("absenceMeaning", JsonPrimitive("GUIDANCE_WITHHELD_NOT_NO_EVENT"))
            }
        }
        putJsonObject("error") {
            put("status", JsonPrimitive(status.value))
            put("code", JsonPrimitive(code))
            put("message", JsonPrimitive(message))
        }
        extra()
    },
)

internal fun failure(
    status: HttpStatusCode,
    code: ApiErrorCode,
    message: String,
    extra: JsonObjectBuilder.() -> Unit = {},
): ApiFailure = failure(status, code.name, message, extra)

/**
 * How a `:wire` site rejection becomes a status code.
 *
 * The distinction is whether the caller sent something wrong or asked something this service
 * cannot answer:
 *
 * - [SiteRejectionCode.ABOVE_POLAR_LIMIT] is **422**. The request is well formed and the
 *   coordinates are a real place on Earth; there is simply no answer, because the observance rules
 *   are defined against a sunrise that does not occur there on every day of the year. Calling that
 *   400 would tell a devotee in Tromsø they made a typing mistake.
 * - Everything else — a latitude of 200, a NaN, an unknown zone id — is **400**: the request could
 *   not be understood, and the caller can fix it by sending something else.
 */
internal fun SiteRejectionCode.httpStatus(): HttpStatusCode = when (this) {
    SiteRejectionCode.ABOVE_POLAR_LIMIT -> HttpStatusCode.UnprocessableEntity
    SiteRejectionCode.NON_FINITE_COORDINATE,
    SiteRejectionCode.LATITUDE_OUT_OF_RANGE,
    SiteRejectionCode.LONGITUDE_OUT_OF_RANGE,
    SiteRejectionCode.ELEVATION_OUT_OF_RANGE,
    SiteRejectionCode.UNKNOWN_TIME_ZONE,
    -> HttpStatusCode.BadRequest
}

/** The coordinates as the caller sent them, echoed into a refusal that has no resolved site. */
internal fun requestedPoint(
    latitude: Double?,
    longitude: Double?,
    zoneId: String?,
    elevationMeters: Double?,
): JsonElement = buildJsonObject {
    latitude?.let { put("latitude", JsonPrimitive(it)) }
    longitude?.let { put("longitude", JsonPrimitive(it)) }
    zoneId?.let { put("timeZone", JsonPrimitive(it)) }
    elevationMeters?.let { put("elevationMeters", JsonPrimitive(it)) }
}
