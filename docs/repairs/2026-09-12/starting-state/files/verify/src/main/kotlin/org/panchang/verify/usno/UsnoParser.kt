package org.panchang.verify.usno

import kotlinx.serialization.json.Json
import org.panchang.verify.harvest.ParseReport
import org.panchang.verify.harvest.ParsedArtifact
import org.panchang.verify.harvest.ResponseShapeException
import org.panchang.verify.harvest.Unparsed

/**
 * Parses USNO `rstt/oneday` responses.
 *
 * Every `phen` string is either mapped to a known slot or reported as unparsed. An
 * unrecognised phenomenon must never be dropped: USNO adds and renames phenomena between
 * API versions, and the failure mode of a silent drop is a location that quietly loses
 * its sunrise and falls back to whatever the consumer does with an empty list.
 */
object UsnoParser {

    private const val SOURCE = "usno-rstt-oneday"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(text: String, expected: UsnoQuery): ParsedArtifact<UsnoDayRecord> {
        val response = try {
            json.decodeFromString(UsnoResponse.serializer(), text)
        } catch (e: Exception) {
            throw ResponseShapeException(SOURCE, "response is not valid USNO JSON: ${e.message}")
        }

        if (response.error == true) {
            // USNO answers a malformed request with HTTP 200 and an error envelope, so a
            // status check alone is not enough to know the request succeeded.
            throw ResponseShapeException(
                SOURCE,
                "USNO returned an error envelope (type='${response.type}') for " +
                    "date=${expected.date} coords=${expected.latitudeDeg},${expected.longitudeDeg}",
            )
        }

        val data = response.properties?.data
            ?: throw ResponseShapeException(SOURCE, "no properties.data in response")

        val unparsed = mutableListOf<Unparsed>()
        val warnings = mutableListOf<String>()

        val actualDate = "%04d-%02d-%02d".format(data.year, data.month, data.day)
        if (actualDate != expected.date) {
            throw ResponseShapeException(
                SOURCE,
                "asked for ${expected.date} but USNO answered for $actualDate",
            )
        }
        // USNO echoes the coordinates it actually used. If they differ from what we sent,
        // every derived sunrise is for the wrong place; a warning is the minimum.
        val coords = response.geometry?.coordinates
        if (coords != null && coords.size == 2) {
            val dLon = kotlin.math.abs(coords[0] - expected.longitudeDeg)
            val dLat = kotlin.math.abs(coords[1] - expected.latitudeDeg)
            if (dLon > 1e-4 || dLat > 1e-4) {
                warnings += "USNO echoed coordinates (${coords[1]}, ${coords[0]}) which differ " +
                    "from the requested (${expected.latitudeDeg}, ${expected.longitudeDeg})."
            }
        }
        if (kotlin.math.abs(data.tz - expected.utcOffsetHours) > 1e-6) {
            warnings += "USNO echoed tz=${data.tz} but we requested ${expected.utcOffsetHours}."
        }

        val sunRises = mutableListOf<String>()
        val sunSets = mutableListOf<String>()
        val sunTransits = mutableListOf<String>()
        var beginCivil: String? = null
        var endCivil: String? = null
        var sunCondition: PolarCondition? = null
        var sunTwilightCondition: PolarCondition? = null

        for ((i, p) in data.sundata.withIndex()) {
            val polar = PolarCondition.fromPhen(p.phen)
            when {
                polar != null && p.phen.contains("Twilight", ignoreCase = true) ->
                    sunTwilightCondition = polar
                polar != null -> sunCondition = polar
                p.phen.equals("Rise", true) -> p.time?.let { sunRises += it }
                p.phen.equals("Set", true) -> p.time?.let { sunSets += it }
                p.phen.equals("Upper Transit", true) -> p.time?.let { sunTransits += it }
                // Lower transit is midnight sun bookkeeping; recorded as understood but unused.
                p.phen.equals("Lower Transit", true) -> Unit
                p.phen.equals("Begin Civil Twilight", true) -> beginCivil = p.time
                p.phen.equals("End Civil Twilight", true) -> endCivil = p.time
                else -> unparsed += Unparsed(
                    "sundata[$i]",
                    "${p.phen} @ ${p.time}",
                    "unrecognised solar phenomenon",
                )
            }
        }

        val moonRises = mutableListOf<String>()
        val moonSets = mutableListOf<String>()
        val moonTransits = mutableListOf<String>()
        var moonCondition: PolarCondition? = null

        for ((i, p) in data.moondata.withIndex()) {
            val polar = PolarCondition.fromPhen(p.phen)
            when {
                polar != null -> moonCondition = polar
                p.phen.equals("Rise", true) -> p.time?.let { moonRises += it }
                p.phen.equals("Set", true) -> p.time?.let { moonSets += it }
                p.phen.equals("Upper Transit", true) -> p.time?.let { moonTransits += it }
                p.phen.equals("Lower Transit", true) -> Unit
                else -> unparsed += Unparsed(
                    "moondata[$i]",
                    "${p.phen} @ ${p.time}",
                    "unrecognised lunar phenomenon",
                )
            }
        }

        if (sunRises.isEmpty() && sunSets.isEmpty() && sunCondition == null) {
            warnings += "No solar rise or set and no polar condition reported. This is neither a " +
                "normal day nor a documented polar day; treat with suspicion."
        }

        val record = UsnoDayRecord(
            date = actualDate,
            latitudeDeg = coords?.getOrNull(1) ?: expected.latitudeDeg,
            longitudeDeg = coords?.getOrNull(0) ?: expected.longitudeDeg,
            utcOffsetHours = data.tz,
            isDst = data.isdst,
            sunRises = sunRises,
            sunSets = sunSets,
            sunUpperTransits = sunTransits,
            beginCivilTwilight = beginCivil,
            endCivilTwilight = endCivil,
            moonRises = moonRises,
            moonSets = moonSets,
            moonUpperTransits = moonTransits,
            sunCondition = sunCondition,
            sunTwilightCondition = sunTwilightCondition,
            moonCondition = moonCondition,
            currentMoonPhase = data.curphase,
            fractionIlluminated = data.fracillum,
            closestPhase = data.closestphase,
        )

        return ParsedArtifact(listOf(record), ParseReport(1, unparsed, warnings))
    }
}
