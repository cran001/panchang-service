package org.panchang.verify.horizons

import org.panchang.verify.harvest.ParseReport
import org.panchang.verify.harvest.ParsedArtifact
import org.panchang.verify.harvest.ResponseShapeException
import org.panchang.verify.harvest.Unparsed
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * Parses the text ephemeris table Horizons returns between `$$SOE` and `$$EOE`.
 *
 * The parser is deliberately suspicious of its input. Horizons is a request-driven
 * system: ask for the wrong quantity, the wrong centre, or the wrong frame and it will
 * cheerfully return a well-formed table of the wrong numbers. Column *order* in
 * particular is not stable across quantity sets, so this parser reads the emitted column
 * header and resolves columns by name, and refuses to proceed unless the echoed header
 * block matches what was requested.
 */
object HorizonsParser {

    private const val SOURCE = "jpl-horizons"

    /**
     * The frame declaration Horizons prints in its column-meaning footer. Its presence is
     * the only machine-checkable confirmation that `ObsEcLon` means apparent
     * ecliptic-of-date longitude rather than some other ecliptic.
     */
    private const val REQUIRED_FRAME_TEXT = "Observer-centered IAU76/80 ecliptic-of-date"

    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("yyyy-MMM-dd HH:mm")
        // TIME_DIGITS may be MINUTES, SECONDS or FRACSEC; accept all three.
        .optionalStart().appendLiteral(':').appendValue(ChronoField.SECOND_OF_MINUTE, 2)
        .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
        .toFormatter(Locale.US)

    fun parse(text: String, expected: HorizonsQuery): ParsedArtifact<HorizonsRecord> {
        val (table, report) = parseTable(text, expected)
        return ParsedArtifact(table.records, report)
    }

    fun parseTable(text: String, expected: HorizonsQuery): Pair<HorizonsTable, ParseReport> {
        val lines = text.lines()

        val soe = lines.indexOfFirst { it.trim() == "\$\$SOE" }
        val eoe = lines.indexOfFirst { it.trim() == "\$\$EOE" }
        if (soe < 0 || eoe < 0 || eoe < soe) {
            // Horizons reports bad queries as prose in a 200 response. Surface it.
            throw ResponseShapeException(
                SOURCE,
                "no \$\$SOE/\$\$EOE ephemeris block. First 400 chars of response: " +
                    text.take(400).replace('\n', ' '),
            )
        }

        val header = readHeader(lines, soe, expected)
        assertFrame(text)

        val lonIdx = header.columnNames.indexOfFirst { it.equals("ObsEcLon", ignoreCase = true) }
        val latIdx = header.columnNames.indexOfFirst { it.equals("ObsEcLat", ignoreCase = true) }
        if (lonIdx < 0 || latIdx < 0) {
            throw ResponseShapeException(
                SOURCE,
                "requested observer ecliptic quantities but the table has no ObsEcLon/ObsEcLat " +
                    "column. Columns: ${header.columnNames}",
            )
        }
        val deltaIdx = header.columnNames.indexOfFirst { it.equals("delta", ignoreCase = true) }
        val deldotIdx = header.columnNames.indexOfFirst { it.equals("deldot", ignoreCase = true) }

        val records = mutableListOf<HorizonsRecord>()
        val unparsed = mutableListOf<Unparsed>()
        val warnings = mutableListOf<String>()

        for (i in (soe + 1) until eoe) {
            val raw = lines[i]
            if (raw.isBlank()) continue
            val fields = raw.split(',')
            if (fields.size < header.columnNames.size) {
                unparsed += Unparsed(
                    "line ${i + 1}",
                    raw.take(200),
                    "expected ${header.columnNames.size} comma-separated fields, found ${fields.size}",
                )
                continue
            }
            val dateText = fields[0].trim()
            if (dateText.startsWith("b")) {
                // 'b' marks a B.C. date. We never query those; if one appears the request
                // was not what we thought it was, so refuse rather than guess an era.
                unparsed += Unparsed("line ${i + 1}", raw.take(200), "B.C. date marker, unsupported")
                continue
            }
            val instant = try {
                LocalDateTime.parse(dateText, DATE_FORMAT).toInstant(ZoneOffset.UTC)
            } catch (e: Exception) {
                unparsed += Unparsed("line ${i + 1}", dateText, "unrecognised date format: ${e.message}")
                continue
            }
            val lon = numeric(fields[lonIdx])
            val lat = numeric(fields[latIdx])
            if (lon == null || lat == null) {
                unparsed += Unparsed(
                    "line ${i + 1}",
                    raw.take(200),
                    "ObsEcLon/ObsEcLat not numeric (Horizons prints 'n.a.' when unavailable)",
                )
                continue
            }
            records += HorizonsRecord(
                utc = instant.toString(),
                apparentEclipticLongitudeDeg = lon,
                apparentEclipticLatitudeDeg = lat,
                distanceAu = deltaIdx.takeIf { it >= 0 }?.let { numeric(fields[it]) },
                rangeRateKmPerSec = deldotIdx.takeIf { it >= 0 }?.let { numeric(fields[it]) },
            )
        }

        if (records.isEmpty()) {
            throw ResponseShapeException(
                SOURCE,
                "ephemeris block contained no usable rows (${unparsed.size} unparsed).",
            )
        }
        if (deltaIdx < 0) {
            warnings += "No 'delta' column: this table carries no distance. Request QUANTITIES 20 " +
                "if distance is needed."
        }

        return HorizonsTable(header, records) to ParseReport(records.size, unparsed, warnings)
    }

    private fun readHeader(lines: List<String>, soeIndex: Int, expected: HorizonsQuery): HorizonsHeader {
        fun field(label: String): String? =
            lines.firstOrNull { it.startsWith(label) }
                ?.substringAfter(':')
                ?.trim()

        val target = field("Target body name")
            ?: throw ResponseShapeException(SOURCE, "no 'Target body name' header")
        val center = field("Center body name")
            ?: throw ResponseShapeException(SOURCE, "no 'Center body name' header")
        val site = field("Center-site name")
            ?: throw ResponseShapeException(SOURCE, "no 'Center-site name' header")

        // These three assertions are the difference between a reference value and a
        // number of unknown provenance. CENTER='500@399' must resolve to the geocentre;
        // a topocentric table would silently shift lunar longitude by up to a degree.
        if (!target.startsWith(expected.body.expectedTargetPrefix)) {
            throw ResponseShapeException(
                SOURCE,
                "asked for ${expected.body} (COMMAND='${expected.body.command}') but Horizons " +
                    "answered for target '$target'",
            )
        }
        if (!center.startsWith("Earth (399)")) {
            throw ResponseShapeException(SOURCE, "expected Earth (399) as centre body, got '$center'")
        }
        if (!site.equals("GEOCENTRIC", ignoreCase = true)) {
            throw ResponseShapeException(SOURCE, "expected GEOCENTRIC centre site, got '$site'")
        }

        // The CSV column header is the last non-separator line before $$SOE.
        var idx = soeIndex - 1
        while (idx >= 0 && (lines[idx].isBlank() || lines[idx].trimEnd().all { it == '*' })) idx--
        if (idx < 0) throw ResponseShapeException(SOURCE, "no column header line before \$\$SOE")
        val columnNames = lines[idx].split(',').map { it.trim() }

        val ephemerisSource = Regex("\\{source:\\s*([^}]+)}").find(target)?.groupValues?.get(1)?.trim()

        return HorizonsHeader(
            targetBodyName = target,
            centerBodyName = center,
            centerSiteName = site,
            ephemerisSource = ephemerisSource,
            startTime = field("Start time").orEmpty(),
            stopTime = field("Stop  time") ?: field("Stop time").orEmpty(),
            stepSize = field("Step-size").orEmpty(),
            atmosphericRefraction = field("Atmos refraction"),
            tableFormat = field("Table format"),
            columnNames = columnNames,
        )
    }

    private fun assertFrame(text: String) {
        // The footer wraps at ~79 columns, so normalise whitespace before matching.
        val normalised = text.replace(Regex("\\s+"), " ")
        if (!normalised.contains(REQUIRED_FRAME_TEXT)) {
            throw ResponseShapeException(
                SOURCE,
                "response does not declare the '$REQUIRED_FRAME_TEXT' frame. The longitude " +
                    "column may not be apparent ecliptic-of-date and must not be trusted as one.",
            )
        }
    }

    private fun numeric(field: String): Double? = field.trim().toDoubleOrNull()
}
