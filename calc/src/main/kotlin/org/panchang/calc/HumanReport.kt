package org.panchang.calc

import org.panchang.sampradaya.RuleConfidence
import org.panchang.wire.EventResolutionDto
import org.panchang.wire.EventTimeDto
import org.panchang.wire.InstantDto
import org.panchang.wire.ObservanceDecisionDto
import org.panchang.wire.ParanaWindowDto
import org.panchang.wire.ResolvedEventDto
import org.panchang.wire.TithiOccurrenceDto

/**
 * The default, human-readable rendering.
 *
 * ## This class does not convert any time
 *
 * Every clock value below is a **substring of a string `:wire` already produced**. `InstantDto.local`
 * arrives as `2026-01-08T20:12:31+05:30`; the table shows characters 5..9 and 11..15 of it and
 * nothing else. There is no `DateTimeFormatter` here, no `ZoneId`, no arithmetic on a Julian Day.
 * That is the whole reason the shortening is done by slicing rather than by re-formatting: a
 * second formatter in a second module is a second chance to pick a different zone, and the
 * abbreviated form must never be able to disagree with the full one. `--format json` always
 * carries both halves in full.
 *
 * ## What the blanks mean
 *
 * Of the events in the ISKCON catalog, only a small minority name a time of day at all. For the
 * rest the honest answer is the date and the tithi window, because **the tradition names no
 * instant** — there is nothing further to compute and nothing has failed. So the FAST UNTIL cell
 * is genuinely empty for an event with no fast, and reads `no time stated` for a fast the source
 * describes without an hour. Neither is ever filled with a plausible-looking clock time, and the
 * footnote says so in the output rather than only here.
 */
class HumanReport(private val explain: Boolean) {

    fun render(result: CalcResult): String = buildString {
        appendLine("CALCULATED REVIEW DIAGNOSTICS — NOT HUMAN-APPROVED GUIDANCE")
        header(result)
        location(result.site)
        ekadashi(result)
        events(result)
        unresolved(result)
        honesty(result)
    }

    // ── Sections ────────────────────────────────────────────────────────────────────────────

    private fun StringBuilder.header(result: CalcResult) {
        val s = result.yearResolution.sampradaya
        rule(this)
        appendLine("PANCHANG CALCULATOR")
        appendLine("  tradition   ${s.displayName}  (id: ${s.id}, status: ${s.status})")
        when (val scope = result.scope) {
            is Scope.Year -> appendLine("  scope       year ${scope.year}")
            is Scope.Day -> {
                appendLine("  scope       ${scope.date} (one day of ${scope.year})")
                appendLine(
                    "              an Ekadashi is listed if its fast OR its parana falls today; " +
                        "the PARANA column carries its own date",
                )
                appendLine(
                    "              unresolved entries below are the whole year's - an " +
                        "unresolved entry has no date to filter on",
                )
            }
        }
        appendLine("  schema      wire v${result.yearResolution.schemaVersion}")
        appendLine()
        appendLine("  provenance")
        wrap(s.provenanceNote, indent = "    ").forEach { appendLine(it) }
        appendLine()
    }

    private fun StringBuilder.location(site: ResolvedSite) {
        rule(this)
        appendLine("LOCATION")
        appendLine("  coordinateSource  ${site.source}")
        appendLine("  requested         ${site.query}")
        val loc = site.location
        appendLine(
            "  computed at       ${"%.5f".format(loc.latitude)}, " +
                "${"%.5f".format(loc.longitude)}  " +
                "(elevation ${loc.elevationMeters} m, zone ${loc.zone.id})",
        )
        site.place?.let {
            appendLine(
                "  place record      ${it.name} - ${it.kind}, ${it.admin1}, ${it.country}, " +
                    "geonameId ${it.geonameId}",
            )
        }
        site.nearestLabel?.let {
            appendLine(
                "  nearest label     ${it.place.name}, ${it.place.admin1}, ${it.place.country} " +
                    "- ${"%.1f".format(it.distanceKm)} km away (label only)",
            )
        }
        site.notes.forEach { note ->
            appendLine()
            wrap("NOTE: $note", indent = "  ").forEach { appendLine(it) }
        }
        appendLine()
    }

    private fun StringBuilder.ekadashi(result: CalcResult) {
        val list = result.ekadashiYear.observances
        rule(this)
        appendLine("EKADASHI AND MAHADVADASHI  (${list.size})")
        appendLine()
        if (list.isEmpty()) {
            appendLine("  none in scope.")
            appendLine()
            return
        }
        val rows = list.map { o ->
            listOf(
                o.date.toString(),
                o.name,
                o.mahadvadashiType?.displayName ?: o.kind.name,
                tithiCell(o.tithi),
                paranaCell(o.parana),
            )
        }
        table(
            headers = listOf("DATE", "NAME", "KIND", "TITHI WINDOW (LOCAL)", "PARANA (LOCAL)"),
            rows = rows,
            indent = "  ",
        ).forEach { appendLine(it) }
        if (explain) {
            appendLine()
            list.forEach { o -> explainObservance(this, o) }
        }
        appendLine()
    }

    private fun StringBuilder.events(result: CalcResult) {
        val list = result.yearResolution.events
        rule(this)
        appendLine("FESTIVALS AND OBSERVANCES  (${list.size})")
        appendLine()
        if (list.isEmpty()) {
            appendLine("  none in scope.")
            appendLine()
            return
        }
        val rows = list.map { e ->
            listOf(
                e.date.toString(),
                e.group.name,
                e.name,
                e.tithi?.let(::tithiCell) ?: "",
                fastCell(e),
            )
        }
        table(
            headers = listOf(
                "DATE", "GROUP", "NAME", "TITHI WINDOW (LOCAL)", "FAST UNTIL (LOCAL)",
            ),
            rows = rows,
            indent = "  ",
        ).forEach { appendLine(it) }
        if (explain) {
            appendLine()
            list.forEach { e -> explainEvent(this, e) }
        }
        appendLine()
    }

    private fun StringBuilder.unresolved(result: CalcResult) {
        val map = result.yearResolution.unresolved
        rule(this)
        appendLine("UNRESOLVED  (${map.size})")
        appendLine()
        wrap(
            "Catalog entries that produced no date for ${result.scope.year} at this site. They " +
                "are printed, never dropped: a festival that vanishes silently leaves nothing " +
                "for anyone to notice or challenge, which is worse than one that is visibly " +
                "wrong.",
            indent = "  ",
        ).forEach { appendLine(it) }
        appendLine()
        if (map.isEmpty()) {
            appendLine("  none - every catalog entry resolved.")
            appendLine()
            return
        }
        map.forEach { (id, res) ->
            val (kind, why) = when (res) {
                is EventResolutionDto.Resolved -> "resolved" to res.event.name
                is EventResolutionDto.NoOccurrence -> "noOccurrence" to res.why
                is EventResolutionDto.TithiSkipped -> "tithiSkipped" to res.why
                is EventResolutionDto.BeyondTabulatedData -> "beyondTabulatedData" to res.why
            }
            appendLine("  $id  [$kind]")
            wrap(why, indent = "      ").forEach { appendLine(it) }
        }
        appendLine()
    }

    /**
     * The part of the output that says what the numbers above do *not* mean.
     *
     * Printed by default and not behind `--explain`, because the reader who most needs it is the
     * one who will not ask for it. The counts are computed from the payload rather than written
     * as constants, so they cannot fall out of step with the catalog.
     */
    private fun StringBuilder.honesty(result: CalcResult) {
        val events = result.yearResolution.events
        val anchored = events.count { it.fastUntil != null }
        val fastingNoAnchor = events.count { it.fastingNote != null && it.fastUntil == null }
        val inferred = events.mapNotNull { it.fastUntil }
            .filter { it.confidence == RuleConfidence.INFERRED }
            .map { it.anchorDisplayName }
            .distinct()
            .sorted()

        rule(this)
        appendLine("HOW TO READ THIS")
        appendLine()
        val lines = listOf(
            "FAST UNTIL is empty for an event that carries no fast at all. It reads " +
                "'no time stated' for a fast the tradition describes without naming an hour - " +
                "'yogurt is given up for one month' has no instant in it, and inventing one " +
                "would be worse than leaving the cell alone. Of the ${events.size} events in " +
                "scope, $anchored name an anchor and $fastingNoAnchor carry a fast that names " +
                "none. A blank there is the answer, not a gap.",
            "A CONFIRMED time means the astronomy is right - that this really is solar noon, or " +
                "moonrise, at these coordinates. It does not mean the tradition has published " +
                "that clock time. The reference calendars name these anchors in words and print " +
                "no clock times at all, for any day of the year, so there is no oracle anywhere " +
                "against which an event time could be checked. Treat the instant as our reading " +
                "of the tradition's words, computed correctly.",
            if (inferred.isEmpty()) {
                "No INFERRED anchor appears in this scope."
            } else {
                "INFERRED anchors in this scope: ${inferred.joinToString(", ")}. Each rests on a " +
                    "reading this project chose between defensible alternatives and each is " +
                    "flagged for pandit review. Run again with --explain, or read --format json, " +
                    "to see the exact definition used - that text is what a reviewer needs in " +
                    "order to disagree with it."
            },
            "Tithi start and end are global instants shown in this site's civil zone. The tithi " +
                "itself begins at the same moment everywhere on Earth; which local day an " +
                "observance falls on is what varies, and that is what the rules decide.",
            "Times are shown to the minute here. --format json carries every instant twice, as " +
                "a full local ISO string and as a Julian Day in UT, which is what makes a " +
                "disagreement between two implementations diagnosable.",
        )
        lines.forEach { paragraph ->
            wrap("- $paragraph", indent = "  ", hanging = "    ").forEach { appendLine(it) }
            appendLine()
        }
        appendLine("  ${org.panchang.gazetteer.Gazetteer.ATTRIBUTION}")
        rule(this)
    }

    // ── Cells ───────────────────────────────────────────────────────────────────────────────

    /** `MM-DD HH:MM`, sliced out of `:wire`'s ISO string. Never re-formatted. */
    private fun short(i: InstantDto): String = "${i.local.substring(5, 10)} ${i.local.substring(11, 16)}"

    /** `HH:MM` only, for the second bound of a window that lands on the same day. */
    private fun clock(i: InstantDto): String = i.local.substring(11, 16)

    private fun span(a: InstantDto, b: InstantDto): String =
        if (a.local.substring(0, 10) == b.local.substring(0, 10)) {
            "${short(a)} -> ${clock(b)}"
        } else {
            "${short(a)} -> ${short(b)}"
        }

    private fun tithiCell(t: TithiOccurrenceDto): String =
        "${t.name} (${t.paksha.name.lowercase()} ${t.numberInPaksha}, ${t.lunarMonthName}" +
            "${if (t.isAdhikaMonth) ", adhika" else ""})  ${span(t.start, t.end)}"

    private fun paranaCell(p: ParanaWindowDto?): String = when (p) {
        null -> "no window derived"
        else -> "${p.date} ${clock(p.start)} -> ${clock(p.end)}  " +
            "(${p.startReason} -> ${p.endReason}, ${p.durationMinutes.toInt()} min)"
    }

    /**
     * The cell that must not lie.
     *
     * Four distinct states, and none of them is ever a clock time the tradition did not name:
     * no fast; a fast with no anchor; an anchor that has no instant here today; an anchor with
     * an instant or an interval.
     */
    private fun fastCell(e: ResolvedEventDto): String = when (val f = e.fastUntil) {
        null -> if (e.fastingNote == null) "" else "no time stated"
        is EventTimeDto.At -> "${f.anchorDisplayName} ${clock(f.at)}  [${f.confidence}]"
        is EventTimeDto.Window ->
            "${f.anchorDisplayName} ${short(f.start)} -> ${clock(f.end)}  [${f.confidence}]"
        is EventTimeDto.Absent -> "none today: ${f.reason}"
    }

    // ── --explain blocks ────────────────────────────────────────────────────────────────────

    private fun explainEvent(sb: StringBuilder, e: ResolvedEventDto) {
        sb.appendLine("  ${e.date}  ${e.name}  [${e.id}, date confidence ${e.confidence}]")
        wrap("why: ${e.reason}", indent = "      ").forEach { sb.appendLine(it) }
        e.fastingNote?.let { note ->
            wrap("fast: $note", indent = "      ").forEach { sb.appendLine(it) }
        }
        e.fastUntil?.let { f ->
            wrap(
                "anchor: ${f.anchorDisplayName} [${f.confidence}] - ${f.basis}",
                indent = "      ",
            ).forEach { sb.appendLine(it) }
            if (f is EventTimeDto.Absent) {
                wrap(f.reasonText, indent = "      ").forEach { sb.appendLine(it) }
            }
        }
        if (e.fastingNote != null && e.fastUntil == null) {
            wrap(
                "anchor: none. The tradition names no time of day for this fast, so none is " +
                    "computed and none is shown.",
                indent = "      ",
            ).forEach { sb.appendLine(it) }
        }
        sb.appendLine()
    }

    private fun explainObservance(sb: StringBuilder, o: ObservanceDecisionDto) {
        sb.appendLine("  ${o.date}  ${o.name}  [${o.kind}, confidence ${o.confidence}]")
        wrap("why: ${o.reason}", indent = "      ").forEach { sb.appendLine(it) }
        o.parana?.let { p ->
            wrap(
                "parana: ${p.start.local} -> ${p.end.local}; starts at ${p.startReason}, " +
                    "ends at ${p.endReason}; ${"%.1f".format(p.durationMinutes)} minutes wide.",
                indent = "      ",
            ).forEach { sb.appendLine(it) }
        } ?: wrap(
            "parana: no window could be derived; see the ruling above.",
            indent = "      ",
        ).forEach { sb.appendLine(it) }
        sb.appendLine()
    }

    // ── Layout primitives ───────────────────────────────────────────────────────────────────

    private fun rule(sb: StringBuilder) {
        sb.appendLine("=".repeat(100))
    }

    /**
     * Fixed-width columns sized to the data.
     *
     * Widths come from the content rather than from constants, so nothing is ever truncated. A
     * truncated festival name or a truncated instant is a silent loss of exactly the information
     * the row exists to carry, and 100 columns of terminal is not a good enough reason for it.
     */
    private fun table(
        headers: List<String>,
        rows: List<List<String>>,
        indent: String,
    ): List<String> {
        val widths = headers.indices.map { c ->
            maxOf(headers[c].length, rows.maxOfOrNull { it[c].length } ?: 0)
        }
        fun line(cells: List<String>): String = indent + cells
            .mapIndexed { c, v -> if (c == cells.lastIndex) v else v.padEnd(widths[c]) }
            .joinToString("  ")
            .trimEnd()
        return buildList {
            add(line(headers))
            add(indent + widths.joinToString("  ") { "-".repeat(it) })
            rows.forEach { add(line(it)) }
        }
    }

    /** Hard-wraps prose at [width], so a paragraph of provenance stays readable in a terminal. */
    private fun wrap(
        text: String,
        indent: String,
        hanging: String = indent,
        width: Int = 96,
    ): List<String> {
        val out = ArrayList<String>()
        var current = StringBuilder()
        var prefix = indent
        for (word in text.split(' ')) {
            if (current.isEmpty()) {
                current.append(prefix).append(word)
            } else if (current.length + 1 + word.length <= width) {
                current.append(' ').append(word)
            } else {
                out.add(current.toString())
                prefix = hanging
                current = StringBuilder().append(prefix).append(word)
            }
        }
        if (current.isNotEmpty()) out.add(current.toString())
        return out
    }
}
