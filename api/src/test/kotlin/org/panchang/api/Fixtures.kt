package org.panchang.api

import com.github.ajalt.clikt.core.parse
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.panchang.calc.CalcCommand
import org.panchang.wire.WireJson

/**
 * One in-process call to a route, and the bytes that came back.
 *
 * `bodyAsText` decodes the response with the charset the server declared; [bytes] keeps the raw
 * octets so a comparison can be made on those rather than on a decoded string when the claim is
 * about bytes.
 */
data class ApiResponse(val status: Int, val body: String) {
    val bytes: ByteArray get() = body.toByteArray(Charsets.UTF_8)
    val json: JsonObject get() = WireJson.compact.parseToJsonElement(body).jsonObject
}

/**
 * Calls one route on a freshly built application and returns what it wrote.
 *
 * Deliberately one request per `testApplication` block. A year at one site is a few thousand
 * ephemeris evaluations, and `runTest`'s own timeout is generous but not unlimited; batching
 * several years into one block would trade a clear failure for a timeout.
 */
// Existing calculation/route regressions explicitly use signed TEST ONLY scope approvals.
// PublicationControlsApiTest exercises the real unconfigured production defaults separately.
private val testPublication = org.panchang.publication.TestApprovals.service()
fun call(url: String): ApiResponse {
    var status = -1
    var body = ""
    testApplication {
        application { panchangModule(publication = testPublication) }
        val response: HttpResponse = client.get(url)
        status = response.status.value
        body = response.bodyAsText()
    }
    return ApiResponse(status, body)
}

/**
 * Runs the real `:calc` command and returns exactly what it wrote to stdout.
 *
 * Not `CalcEngine` plus `CalcJson.render` assembled here — the actual [CalcCommand] object the
 * `main` function constructs, driven through clikt with the same argument strings a user types.
 * The point of the acceptance test is that two *front doors* agree, so both sides of it have to be
 * front doors.
 */
fun calcJson(vararg args: String): String {
    val out = StringBuilder()
    val err = StringBuilder()
    CalcCommand(out = out, err = err).parse(args.toList() + listOf("--format", "json"))
    check(out.isNotEmpty()) { "calc wrote nothing to stdout; stderr said: $err" }
    // `CalcCommand.run` appends a trailing newline to the payload. The document itself is what is
    // being compared, so it is dropped here and nowhere else.
    return out.toString().trimEnd('\n')
}

/**
 * Extracts `"key": <object>` from a JSON document as the exact characters it occupies.
 *
 * A parse-and-re-encode round trip would silently normalise whitespace, key order and number
 * literals — the three things the cross-front-door claim is *about*. This walks the raw text
 * instead, tracking string state and escapes so that a brace inside a prose field cannot end the
 * region early. What comes back is a substring of the input and nothing else.
 */
fun jsonMemberText(text: String, key: String): String {
    val marker = "\"$key\""
    val at = text.indexOf(marker)
    require(at >= 0) { "no member named $key in the document" }
    require(text.indexOf(marker, at + 1) < 0) { "$key appears more than once; the scan is ambiguous" }

    var i = at + marker.length
    while (i < text.length && text[i] != '{') {
        require(text[i] == ':' || text[i] == ' ') { "member $key is not an object" }
        i++
    }
    val start = i
    var depth = 0
    var inString = false
    var escaped = false
    while (i < text.length) {
        val c = text[i]
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
        } else {
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(at, i + 1)
                }
            }
        }
        i++
    }
    error("unbalanced braces while scanning $key")
}

/**
 * The heavy payloads, computed once for the whole test source set.
 *
 * A year at one site takes seconds; recomputing one per assertion would make this suite slow
 * enough that people stop running it, which is a worse outcome than any of the assertions is
 * worth. Everything here is deterministic, so sharing changes nothing about what is asserted.
 */
object Fixtures {

    const val MUMBAI_QUERY = "lat=19.0760&lon=72.8777&tz=Asia/Kolkata"
    const val DELHI_QUERY = "lat=28.6139&lon=77.2090&tz=Asia/Kolkata"

    val MUMBAI_ARGS = arrayOf("--lat", "19.0760", "--lon", "72.8777", "--tz", "Asia/Kolkata")
    val DELHI_ARGS = arrayOf("--lat", "28.6139", "--lon", "77.2090", "--tz", "Asia/Kolkata")

    /** `:calc --format json`, Mumbai, whole year. */
    val calcMumbaiYear: String by lazy { calcJson(*MUMBAI_ARGS, "--year", "2026") }

    /** `:calc --format json`, Mumbai, one day. */
    val calcMumbaiDay: String by lazy { calcJson(*MUMBAI_ARGS, "--date", "2026-01-15") }

    /** `:calc --format json`, resolved through the gazetteer rather than from coordinates. */
    val calcNadiaYear: String by lazy { calcJson("--place", "Nadia", "--year", "2026") }

    /** The same Mumbai year through HTTP, in the indented form `:calc` writes. */
    val apiMumbaiYearPretty: ApiResponse by lazy {
        call("/v2/calendar/iskcon/2026?$MUMBAI_QUERY&pretty=1")
    }

    /** The same Mumbai year through HTTP, in the compact form the wire actually carries. */
    val apiMumbaiYearCompact: ApiResponse by lazy { call("/v2/calendar/iskcon/2026?$MUMBAI_QUERY") }

    /** The same Mumbai day through HTTP. */
    val apiMumbaiDayPretty: ApiResponse by lazy {
        call("/v2/day/iskcon/2026-01-15?$MUMBAI_QUERY&pretty=1")
    }

    /** A place-resolved year through HTTP. */
    val apiNadiaYearPretty: ApiResponse by lazy {
        call("/v2/calendar/iskcon/2026?place=Nadia&pretty=1")
    }

    /** Delhi, for the two-cities-one-time-zone claim. */
    val apiDelhiYearCompact: ApiResponse by lazy { call("/v2/calendar/iskcon/2026?$DELHI_QUERY") }
}
