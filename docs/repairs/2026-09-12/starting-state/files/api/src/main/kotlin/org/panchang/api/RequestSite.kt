package org.panchang.api

import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import java.time.ZoneId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import org.panchang.calc.LocationResolver
import org.panchang.calc.PlaceFailure
import org.panchang.calc.ResolvedSite
import org.panchang.core.GeoLocation
import org.panchang.wire.SiteAcceptance
import org.panchang.wire.acceptSite

/**
 * Turns query parameters into one site, or throws the refusal that says why.
 *
 * ## What this does and does not own
 *
 * The *resolution* — what a name means, what a record's point is, which notes a user must be
 * shown, and the flat refusal to fuzzy-match — is `:calc`'s [LocationResolver], used here
 * unchanged. This module adds no name table, no radius, no fallback and no preference of its own.
 * If `--place Bombay` fails at the CLI it fails here, with the same sentence, because it is the
 * same code producing it.
 *
 * What this file does own is the *shape of the request*: which query parameters exist, that
 * `lat`/`lon`/`tz` must arrive together, and that coordinates and a place name may not both be
 * given. `:calc` states the same precedence in `CalcCommand.resolveSite`, but privately and in
 * terms of clikt options, so it cannot be called from here; the rules are restated, the reasons
 * are the same ones, and `CrossFrontDoorIdentityTest` asserts that both doors resolve the same
 * inputs to the same site — comparing the whole `location` block minus its echo of the request —
 * rather than trusting that they do.
 *
 * ## Precedence
 *
 * `lat`/`lon`/`tz` wins, because it is exact — the caller's own point, used verbatim. `place`
 * borrows a gazetteer record's representative point, and every response says which it was.
 * Supplying both is refused rather than ranked: whichever this service dropped, the caller would
 * go on believing it had been used.
 */
internal fun resolveSiteFromQuery(params: Parameters, resolver: LocationResolver): ResolvedSite {
    val latRaw = params["lat"]
    val lonRaw = params["lon"]
    val tz = params["tz"]
    val elevationRaw = params["elevation"]
    val place = params["place"]
    val placeIdRaw = params["placeId"]

    val hasCoords = latRaw != null || lonRaw != null || tz != null
    val hasPlace = place != null || placeIdRaw != null

    if (hasCoords && hasPlace) {
        throw failure(
            HttpStatusCode.BadRequest,
            ApiErrorCode.CONFLICTING_LOCATION,
            "Give either lat/lon/tz or place, not both. Two answers to 'where' is not a " +
                "preference to be resolved silently: whichever one this service dropped, the " +
                "caller would keep believing it had been used.",
        )
    }
    if (place != null && placeIdRaw != null) {
        throw failure(
            HttpStatusCode.BadRequest,
            ApiErrorCode.CONFLICTING_LOCATION,
            "Give either place or placeId, not both.",
        )
    }

    if (hasCoords) {
        val latitude = latRaw.requiredDouble("lat", "lat is required alongside lon and tz.")
        val longitude = lonRaw.requiredDouble("lon", "lon is required alongside lat and tz.")
        val zoneId = tz ?: throw failure(
            HttpStatusCode.BadRequest,
            ApiErrorCode.INCOMPLETE_COORDINATES,
            "tz is required with lat and lon. This service will not infer a time zone from " +
                "coordinates: every instant it prints is expressed in the site's civil zone, and " +
                "a guessed zone would shift all of them by whole hours while still looking " +
                "entirely reasonable.",
        )
        val elevation = elevationRaw?.let {
            it.toDoubleOrNull() ?: throw failure(
                HttpStatusCode.BadRequest,
                ApiErrorCode.MALFORMED_NUMBER,
                "elevation='$it' is not a number. Omit it for sea level, which is what the " +
                    "reference calendars are computed at.",
            )
        } ?: 0.0

        // :wire validates the raw input before a GeoLocation exists, because GeoLocation throws
        // and a thrown IllegalArgumentException cannot be turned into a useful response without
        // parsing its text.
        when (val pre = acceptSite(latitude, longitude, zoneId, elevation)) {
            is SiteAcceptance.Accepted -> Unit
            is SiteAcceptance.Rejected -> throw failure(
                pre.code.httpStatus(),
                pre.code.name,
                pre.reason,
            ) {
                put("requested", requestedPoint(latitude, longitude, zoneId, elevation))
                put(
                    "limitStatedBy",
                    JsonPrimitive(
                        "org.panchang.wire — the limit is stated once, for every front door, and " +
                            "this service carries none of its own.",
                    ),
                )
            }
        }

        val location = GeoLocation(latitude, longitude, ZoneId.of(zoneId), elevation)
        val echo = buildString {
            append("lat=").append(latRaw).append("&lon=").append(lonRaw).append("&tz=").append(tz)
            if (elevationRaw != null) append("&elevation=").append(elevationRaw)
        }
        return resolver.byCoordinates(location, echo)
    }

    if (placeIdRaw != null) {
        val id = placeIdRaw.toIntOrNull() ?: throw failure(
            HttpStatusCode.BadRequest,
            ApiErrorCode.MALFORMED_NUMBER,
            "placeId '$placeIdRaw' is not a GeoNames id. Ids are integers, and they are printed " +
                "beside every candidate when a name is ambiguous.",
        )
        val (site, fail) = resolver.byId(id)
        return site ?: throw placeFailure(fail!!)
    }

    if (place != null) {
        val (site, fail) = resolver.byName(place)
        return site ?: throw placeFailure(fail!!)
    }

    throw failure(
        HttpStatusCode.BadRequest,
        ApiErrorCode.MISSING_LOCATION,
        "No location given. Pass lat, lon and tz for your own site (exact, and preferred), or " +
            "place=NAME to use a gazetteer record's representative point.",
    )
}

/**
 * A `:calc` place failure as an HTTP refusal.
 *
 * The status codes are chosen so a client can tell "you are asking about something that is not
 * here" from "you are asking about something that is here more than once":
 *
 * - **404** for a name or id with no record. There is nothing to return and nothing will be
 *   substituted for it.
 * - **409** for an ambiguous name. The request is well formed, the resource exists, and it exists
 *   more than once — a conflict the caller resolves by naming a `placeId`, which the body lists
 *   for every candidate. It is deliberately not 422: 422 is reserved here for "there is no answer
 *   at all", and an ambiguous name has several. It is deliberately not 300, whose redirect
 *   semantics invite a client to follow one of the choices automatically, which is the exact
 *   behaviour this refusal exists to prevent.
 *
 * The message is `:calc`'s own, unedited, including its worked example of the two Raigarh
 * districts. Rewriting it for HTTP would produce a second explanation of one decision.
 */
private fun placeFailure(f: PlaceFailure): ApiFailure = when (f) {
    is PlaceFailure.Ambiguous -> failure(
        HttpStatusCode.Conflict,
        ApiErrorCode.PLACE_AMBIGUOUS,
        f.message,
    ) {
        put("query", JsonPrimitive(f.query))
        put("candidates", JsonArray(f.candidates.map { placeJson(it) }))
        put(
            "resolveWith",
            JsonPrimitive("Re-send the request with placeId=<geonameId> naming one candidate."),
        )
    }

    is PlaceFailure.NotFound -> failure(
        HttpStatusCode.NotFound,
        ApiErrorCode.PLACE_NOT_FOUND,
        f.message,
    ) {
        put("query", JsonPrimitive(f.query))
        f.hint?.let { put("hint", JsonPrimitive(it)) }
        put("prefixMatches", JsonArray(f.prefixMatches.map { placeJson(it) }))
    }

    is PlaceFailure.UnknownId -> failure(
        HttpStatusCode.NotFound,
        ApiErrorCode.PLACE_ID_UNKNOWN,
        f.message,
    ) {
        put("query", JsonPrimitive(f.id))
    }
}

private fun String?.requiredDouble(name: String, whenMissing: String): Double {
    val raw = this ?: throw failure(
        HttpStatusCode.BadRequest,
        ApiErrorCode.INCOMPLETE_COORDINATES,
        whenMissing,
    )
    return raw.toDoubleOrNull() ?: throw failure(
        HttpStatusCode.BadRequest,
        ApiErrorCode.MALFORMED_NUMBER,
        "$name='$raw' is not a number.",
    )
}
