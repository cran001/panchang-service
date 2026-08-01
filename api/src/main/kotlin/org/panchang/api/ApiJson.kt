package org.panchang.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.panchang.gazetteer.Gazetteer
import org.panchang.gazetteer.Place
import org.panchang.wire.WIRE_SCHEMA_VERSION
import org.panchang.wire.WireJson

/** What this service calls itself in every payload it emits. */
const val SERVICE_NAME: String = "panchang-api"

/**
 * The version of the engine behind this door, read from the artifact rather than typed here.
 *
 * `api/build.gradle.kts` generates `version.properties` from the Gradle project version. A
 * hand-maintained constant would be a second place for the version to live and would be wrong
 * within one release; a version reported by a service is only worth reading if it cannot drift
 * from the thing it names.
 */
object EngineVersion {

    private const val RESOURCE = "/org/panchang/api/version.properties"

    val value: String by lazy {
        val stream = EngineVersion::class.java.getResourceAsStream(RESOURCE)
            ?: error(
                "$RESOURCE is missing from the classpath: the generateVersionResource task in " +
                    "api/build.gradle.kts did not run, and this service will not invent a " +
                    "version number for itself.",
            )
        val props = java.util.Properties()
        stream.use { props.load(it) }
        props.getProperty("version")
            ?: error("$RESOURCE carries no 'version' key")
    }
}

/**
 * Every payload this module emits, response or refusal, starts here.
 *
 * The two identifying fields are always first and always present: which door answered, and which
 * schema the `:wire` roots inside conform to. A client that meets an error and a client that meets
 * a calendar can therefore read the same two fields off both.
 */
internal fun apiDocument(build: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject {
    put("service", JsonPrimitive(SERVICE_NAME))
    put("schemaVersion", JsonPrimitive(WIRE_SCHEMA_VERSION))
    build()
}

/**
 * The `Json` a response is written with.
 *
 * `WireJson.compact` is the default because that is what `:wire` says transport uses. `?pretty=1`
 * selects `WireJson.pretty`, which is the form `:calc --format json` writes to a file — and that
 * is not a convenience for `curl`. It is what lets the cross-front-door test compare *raw bytes*
 * of the two document roots instead of comparing parsed trees and calling that byte-identity.
 * Both configurations are `:wire`'s; this module defines no third one.
 */
internal fun jsonFor(pretty: Boolean): Json = if (pretty) WireJson.pretty else WireJson.compact

/**
 * A gazetteer record, rendered exactly as `:calc` renders one.
 *
 * `CalcJson.place` is private, so this is a second copy of a field list — the one piece of
 * duplication in this module and the one that could drift silently. `PlaceRenderingTest` pins it:
 * it takes the `location.place` object out of a real `:calc --format json` document and requires
 * this function's output for the same record to be the identical string. If a field is added,
 * renamed or reordered on either side, that test fails rather than the two doors quietly
 * describing the same district differently.
 *
 * The attribution rides on every record because CC BY 4.0 requires it wherever the data surfaces,
 * and a client that copies one record out of a list must copy the obligation with it.
 */
internal fun placeJson(p: Place): JsonElement = buildJsonObject {
    put("geonameId", JsonPrimitive(p.geonameId))
    put("name", JsonPrimitive(p.name))
    put("asciiName", JsonPrimitive(p.asciiName))
    put("kind", JsonPrimitive(p.kind.name))
    put("country", JsonPrimitive(p.country))
    put("admin1", JsonPrimitive(p.admin1))
    put("population", JsonPrimitive(p.population))
    put("latitude", JsonPrimitive(p.latitude))
    put("longitude", JsonPrimitive(p.longitude))
    put("timeZone", JsonPrimitive(p.zone.id))
    put("attribution", JsonPrimitive(Gazetteer.ATTRIBUTION))
}
