package org.panchang.api

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.panchang.calc.CalcEngine
import org.panchang.calc.CalcJson
import org.panchang.calc.LocationResolver
import org.panchang.calc.Sampradayas
import org.panchang.calc.Scope
import org.panchang.core.GeoLocation
import org.panchang.gazetteer.Gazetteer
import org.panchang.gazetteer.PlaceKind
import org.panchang.gazetteer.normalizePlaceName
import org.panchang.sampradaya.SampradayaRules
import org.panchang.sampradaya.VerificationStatus
import org.panchang.wire.MIDNIGHT_SUN_LIMIT_DEG
import org.panchang.wire.SampradayaListDto
import org.panchang.wire.SiteAcceptance
import org.panchang.wire.SiteRejectionCode
import org.panchang.wire.WIRE_SCHEMA_VERSION
import org.panchang.wire.WireRenderer
import org.panchang.wire.acceptSite

/**
 * The HTTP surface: `:calc`'s engine behind six routes.
 *
 * Nothing here computes, resolves or formats. Every route does the same three things — read the
 * request, hand it to the same objects the CLI hands it to, and choose a status code — and the
 * third is the only one this module has an opinion about.
 *
 * ## The status codes, and why they are what they are
 *
 * | Situation | Code | Because |
 * |---|---|---|
 * | Answered | 200 | |
 * | Site above 66° | **422** | Well formed, real place, no answer exists. Not the caller's mistake. |
 * | Latitude 200, NaN, unknown zone id | 400 | The request could not be understood. |
 * | `lat` without `tz`, both `lat` and `place`, no location | 400 | Same. |
 * | Malformed `{year}` or `{date}` | 400 | Same. |
 * | Ambiguous `place` | 409 | Several answers exist and choosing between them is not this service's call. |
 * | `place` or `placeId` with no record | 404 | |
 * | Unknown `{sampradaya}` | 404, naming what is known | Never answered with another tradition's dates. |
 * | Registered but ruleless `{sampradaya}` | 501 | It exists; we have no rules. An empty calendar would read as "your tradition observes nothing". |
 *
 * ## Everything says where it computed
 *
 * Every calendar and day response carries `location.coordinateSource` and
 * `location.coordinatesUsed` — the block `:calc` builds, not one assembled here. The app this
 * project replaces showed a nearby city's name while computing at that city's coordinates instead
 * of the user's, and a user who believes they were given their own district's sunrise has no way
 * to check that belief unless the answer says which point it used.
 */
fun Application.panchangModule(
    engine: CalcEngine = CalcEngine(),
    resolver: LocationResolver = LocationResolver(),
    gazetteer: Gazetteer = Gazetteer.default,
) {
    install(StatusPages) {
        exception<ApiFailure> { call, cause -> call.respondJson(cause.status, cause.body) }
        exception<Throwable> { call, cause ->
            // Never an HTML stack trace: a client that meets one cannot tell a bug from a refusal,
            // and the class name is the only part of it that is safe to publish.
            call.respondJson(
                HttpStatusCode.InternalServerError,
                failure(
                    HttpStatusCode.InternalServerError,
                    ApiErrorCode.INTERNAL_ERROR,
                    "The request failed inside the engine: ${cause::class.qualifiedName}. This is " +
                        "a defect, not a limit of the tradition or of your location.",
                ).body,
            )
        }
    }

    routing {
        route("/v1") {
            get("/health") {
                call.respondJson(
                    HttpStatusCode.OK,
                    apiDocument {
                        put("status", JsonPrimitive("ok"))
                        put("engineVersion", JsonPrimitive(EngineVersion.value))
                    },
                )
            }

            get("/meta") { call.respondJson(HttpStatusCode.OK, meta(call, gazetteer)) }

            get("/places") { call.respondJson(HttpStatusCode.OK, places(call, gazetteer)) }

            get("/places/nearest") {
                call.respondJson(HttpStatusCode.OK, nearestPlace(call, gazetteer))
            }

            get("/calendar/{sampradaya}/{year}") {
                val rules = call.rules()
                val year = call.parameters["year"]!!.let { raw ->
                    raw.toIntOrNull() ?: throw failure(
                        HttpStatusCode.BadRequest,
                        ApiErrorCode.MALFORMED_YEAR,
                        "'$raw' is not a year. Use four digits, e.g. 2026.",
                    )
                }
                call.respondJson(
                    HttpStatusCode.OK,
                    computed(call, engine, resolver, rules, Scope.Year(year)),
                )
            }

            get("/day/{sampradaya}/{date}") {
                val rules = call.rules()
                val date = call.parameters["date"]!!.let { raw ->
                    try {
                        LocalDate.parse(raw)
                    } catch (e: DateTimeParseException) {
                        throw failure(
                            HttpStatusCode.BadRequest,
                            ApiErrorCode.MALFORMED_DATE,
                            "'$raw' is not a date. Use YYYY-MM-DD, e.g. 2026-01-15.",
                        )
                    }
                }
                call.respondJson(
                    HttpStatusCode.OK,
                    computed(call, engine, resolver, rules, Scope.Day(date)),
                )
            }
        }
    }
}

// ── The answer ──────────────────────────────────────────────────────────────────────────────────

/**
 * A year, or a day of one, for a site under a tradition.
 *
 * The body is assembled out of [CalcJson.document] rather than built here. That is the whole
 * design: `request`, `location`, `ekadashiYear` and `yearResolution` are the *same* `JsonElement`
 * instances `:calc --format json` writes, so byte-identity between the two front doors is not a
 * property that has to be maintained — there is only one document, emitted through two doors.
 * `CrossFrontDoorIdentityTest` demonstrates it rather than assuming it.
 *
 * Only the two outermost keys differ, and they must: `:calc` writes `"tool": "panchang-calc"` and
 * this writes `"service"` and `"schemaVersion"`. They sit at the same depth as the roots below
 * them, so even the pretty-printed indentation of `"ekadashiYear": { … }` is character-for-
 * character the same in both documents.
 */
private fun computed(
    call: ApplicationCall,
    engine: CalcEngine,
    resolver: LocationResolver,
    rules: SampradayaRules,
    scope: Scope,
): JsonObject {
    val site = resolveSiteFromQuery(call.request.queryParameters, resolver)
    val document = CalcJson.document(engine.compute(site, rules, scope))
    return apiDocument {
        put("request", document.getValue("request"))
        put("location", document.getValue("location"))
        put("ekadashiYear", document.getValue("ekadashiYear"))
        put("yearResolution", document.getValue("yearResolution"))
    }
}

/**
 * The tradition named in the path, or the refusal that names the ones that exist.
 *
 * An unknown tradition is 404 and never an answer. A registered tradition with no rules is 501:
 * it is a real tradition this project knows about and has not implemented, and returning its empty
 * calendar with a 200 would put "no observances" in front of someone as if it were a finding.
 *
 * The 501 branch has no test in this module because every tradition `:calc`'s `Sampradayas`
 * registers is implemented — ISKCON fully, the nine regional traditions as festival catalogs —
 * so no request can currently reach the branch. Registering a placeholder from a test would
 * mutate the process-wide `SampradayaRegistry` and leak into every other test in the JVM. The
 * branch is here because the alternative is worse: without it, the day a placeholder is
 * registered, this door starts answering with empty calendars and nothing fails.
 */
private fun ApplicationCall.rules(): SampradayaRules {
    val id = parameters["sampradaya"]!!
    val known = Sampradayas.knownIds()
    val rules = Sampradayas[id] ?: throw failure(
        HttpStatusCode.NotFound,
        ApiErrorCode.UNKNOWN_SAMPRADAYA,
        "Unknown sampradaya '$id'. Known: ${known.joinToString(", ")}. An unknown tradition is " +
            "refused rather than answered with another tradition's dates, which would reach the " +
            "user with nothing to tell them apart.",
    ) {
        put("requested", JsonPrimitive(id))
        put("known", JsonArray(known.map { JsonPrimitive(it) }))
    }
    if (rules.status == VerificationStatus.NOT_IMPLEMENTED) {
        throw failure(
            HttpStatusCode.NotImplemented,
            ApiErrorCode.SAMPRADAYA_NOT_IMPLEMENTED,
            "'${rules.id}' (${rules.displayName}) is a tradition this service knows about and " +
                "has no rules for. It is refused rather than answered with an empty calendar, " +
                "which would be indistinguishable from 'your tradition observes nothing this " +
                "year', and rather than answered with another tradition's dates.",
        ) {
            put("sampradaya", JsonPrimitive(rules.id))
            put("status", JsonPrimitive(rules.status.name))
            put("provenanceNote", JsonPrimitive(rules.provenanceNote))
        }
    }
    return rules
}

// ── Discovery ───────────────────────────────────────────────────────────────────────────────────

/**
 * What this service is, what it knows, and what it is obliged to tell you.
 *
 * The sampradaya list is `:wire`'s own [SampradayaListDto] root, so a client reads the same
 * `status` and `provenanceNote` fields here that ride on every calendar. Statuses travel with the
 * data everywhere in this project rather than living in documentation, because a user cannot weigh
 * a date they were not told the provenance of.
 *
 * The GeoNames attribution is not decorative. The table ships under CC BY 4.0, which is
 * attribution-only precisely so that this project is not obliged to relicense the sampradaya rule
 * set, and the cost of that choice is that the credit must appear wherever the data surfaces.
 */
private fun meta(call: ApplicationCall, gazetteer: Gazetteer): JsonObject = apiDocument {
    put("engineVersion", JsonPrimitive(EngineVersion.value))
    put("wireSchemaVersion", JsonPrimitive(WIRE_SCHEMA_VERSION))
    put(
        "sampradayas",
        jsonFor(call.pretty()).encodeToJsonElement(
            SampradayaListDto.serializer(),
            WireRenderer.sampradayaList(Sampradayas.knownIds().mapNotNull { Sampradayas[it] }),
        ),
    )
    putJsonObject("gazetteer") {
        put("records", JsonPrimitive(gazetteer.places.size))
        putJsonObject("byKind") {
            val counts = gazetteer.places.groupingBy { it.kind }.eachCount()
            for (kind in PlaceKind.entries) put(kind.name, JsonPrimitive(counts[kind] ?: 0))
        }
        put("nearestSearchRadiusKm", JsonPrimitive(NEAREST_RADIUS_KM))
        put("attribution", JsonPrimitive(Gazetteer.ATTRIBUTION))
        put("provenance", JsonArray(gazetteer.provenance.map { JsonPrimitive(it) }))
    }
    put(
        "notes",
        buildJsonArray {
            add(
                JsonPrimitive(
                    "Every instant in a calendar payload is emitted twice: 'local', an ISO-8601 " +
                        "offset date-time at the site's civil zone, and 'jdUt', the Julian Day in " +
                        "UT. Given only the string, an implementation that disagrees about a " +
                        "sunrise cannot be told apart from one that agrees about the sunrise and " +
                        "disagrees about the zone.",
                ),
            )
            add(
                JsonPrimitive(
                    "This service declines to compute above the midnight-sun boundary of about " +
                        "${"%.2f".format(java.util.Locale.ROOT, MIDNIGHT_SUN_LIMIT_DEG)} degrees of " +
                        "latitude, north or south, and answers 422 there. The " +
                        "observance rules are defined against a sunrise that does not occur on " +
                        "every day at those latitudes.",
                ),
            )
            add(JsonPrimitive(Gazetteer.ATTRIBUTION))
        },
    )
}

// ── Places ──────────────────────────────────────────────────────────────────────────────────────

/** The radius [Gazetteer.nearest] searches, restated in `/v1/meta` so a caller can see it. */
private const val NEAREST_RADIUS_KM: Double = 150.0

/**
 * Exact name search, and nothing else.
 *
 * `exactMatches` is `:calc`'s own lookup: accent- and case-insensitive, never fuzzy. A search that
 * finds nothing is still a successful search, so this answers 200 with empty lists — unlike
 * `?place=` on a calendar route, which is a *resolution* and answers 404 when it cannot make one
 * site out of the name. `prefixMatches` and `hint` are there so a caller who typed a historical
 * name is told the modern one rather than quietly given a different city.
 */
private fun places(call: ApplicationCall, gazetteer: Gazetteer): JsonObject {
    val q = call.request.queryParameters["q"]?.takeIf { it.isNotBlank() } ?: throw failure(
        HttpStatusCode.BadRequest,
        ApiErrorCode.MISSING_PARAMETER,
        "q is required, e.g. /v1/places?q=Nadia.",
    )
    val exact = gazetteer.byName(q)
    return apiDocument {
        put("query", JsonPrimitive(q))
        put("exactMatches", JsonArray(exact.map { placeJson(it) }))
        put("prefixMatches", JsonArray(gazetteer.startingWith(q, limit = 20).map { placeJson(it) }))
        LocationResolver.HISTORICAL_NAME_HINTS[normalizePlaceName(q)]?.let {
            put("hint", JsonPrimitive(it))
        }
        put(
            "note",
            JsonPrimitive(
                "Names are matched exactly, ignoring case and accents. Nothing is fuzzy-matched: " +
                    "a wrong city is worse than no city, because it produces a complete, " +
                    "plausible, authoritative-looking answer for somewhere the caller has never " +
                    "been. When exactMatches holds more than one record, a calendar request must " +
                    "name one by placeId.",
            ),
        )
        put("attribution", JsonPrimitive(Gazetteer.ATTRIBUTION))
    }
}

/**
 * The nearest record to a point, as a **label**, with the distance to it.
 *
 * Absence is a result, not an error: beyond $NEAREST_RADIUS_KM km the gazetteer returns nothing and
 * so does this, explicitly, with a reason — rather than naming a record 900 km away, which would be
 * a fabrication. The shape mirrors `:wire`'s treatment of an absent instant: the `nearest` key is
 * simply not there, and an `absent` object says which way it happened, so a client cannot format
 * what does not exist.
 *
 * The polar limit is **not** applied here. A record at 69°N is a real place and this endpoint does
 * not compute anything; `computable` carries `:wire`'s verdict and, when false, `:wire`'s own
 * sentence, so a caller learns before they ask for a calendar that they will get a 422.
 */
private fun nearestPlace(call: ApplicationCall, gazetteer: Gazetteer): JsonObject {
    val p = call.request.queryParameters
    val latRaw = p["lat"] ?: throw missingCoordinate("lat")
    val lonRaw = p["lon"] ?: throw missingCoordinate("lon")
    val latitude = latRaw.toDoubleOrNull() ?: throw malformedCoordinate("lat", latRaw)
    val longitude = lonRaw.toDoubleOrNull() ?: throw malformedCoordinate("lon", lonRaw)

    // UTC is a placeholder zone: this endpoint renders no instant, so no zone is used. What is
    // wanted from :wire here is its range checking and its polar verdict, both zone-independent.
    val acceptance = acceptSite(latitude, longitude, "UTC")
    if (acceptance is SiteAcceptance.Rejected &&
        acceptance.code != SiteRejectionCode.ABOVE_POLAR_LIMIT
    ) {
        throw failure(acceptance.code.httpStatus(), acceptance.code.name, acceptance.reason)
    }
    val polar = acceptance as? SiteAcceptance.Rejected

    val found = gazetteer.nearest(latitude, longitude, withinKm = NEAREST_RADIUS_KM)
    return apiDocument {
        putJsonObject("query") {
            put("latitude", JsonPrimitive(latitude))
            put("longitude", JsonPrimitive(longitude))
            put("withinKm", JsonPrimitive(NEAREST_RADIUS_KM))
        }
        put("computable", JsonPrimitive(polar == null))
        polar?.let {
            put("notComputableCode", JsonPrimitive(it.code.name))
            put("notComputableReason", JsonPrimitive(it.reason))
        }
        if (found != null) {
            putJsonObject("nearest") {
                put("place", placeJson(found))
                // The same distance `:calc` prints beside a nearest-place label, computed the same
                // way (`:core`'s haversine) and rounded to the same two places.
                val distanceKm = GeoLocation(latitude, longitude, ZoneId.of("UTC"))
                    .distanceKmTo(found.toGeoLocation())
                put("distanceKm", JsonPrimitive(Math.round(distanceKm * 100.0) / 100.0))
                put(
                    "note",
                    JsonPrimitive(
                        "A label for the coordinates supplied. Nothing is computed at this " +
                            "record's point; pass your own lat/lon/tz to a calendar route.",
                    ),
                )
            }
        } else {
            putJsonObject("absent") {
                put("reason", JsonPrimitive("NO_RECORD_WITHIN_RADIUS"))
                put(
                    "reasonText",
                    JsonPrimitive(
                        "No gazetteer record lies within ${NEAREST_RADIUS_KM.toInt()} km of this " +
                            "point, so this service will not put a place name on it. Naming a " +
                            "record 900 km away would be a fabrication. Calendars for these " +
                            "coordinates are unaffected — pass lat, lon and tz.",
                    ),
                )
            }
        }
        put("attribution", JsonPrimitive(Gazetteer.ATTRIBUTION))
    }
}

private fun missingCoordinate(name: String) = failure(
    HttpStatusCode.BadRequest,
    ApiErrorCode.MISSING_PARAMETER,
    "$name is required, e.g. /v1/places/nearest?lat=19.0760&lon=72.8777.",
)

private fun malformedCoordinate(name: String, raw: String) = failure(
    HttpStatusCode.BadRequest,
    ApiErrorCode.MALFORMED_NUMBER,
    "$name='$raw' is not a number.",
)

// ── Plumbing ────────────────────────────────────────────────────────────────────────────────────

/**
 * `?pretty=1` switches to the indented form `:calc --format json` writes.
 *
 * Not a convenience. It is what allows the cross-front-door test to compare the raw bytes of the
 * two document roots against the CLI's file output instead of comparing parsed trees, which is a
 * weaker claim wearing the same words.
 */
internal fun ApplicationCall.pretty(): Boolean =
    request.queryParameters["pretty"]?.lowercase() in setOf("1", "true", "yes")

private suspend fun ApplicationCall.respondJson(status: HttpStatusCode, body: JsonObject) {
    respondText(
        text = jsonFor(pretty()).encodeToString(JsonObject.serializer(), body),
        contentType = ContentType.Application.Json,
        status = status,
    )
}
