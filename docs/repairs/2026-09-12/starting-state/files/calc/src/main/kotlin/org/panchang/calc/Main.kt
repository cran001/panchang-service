package org.panchang.calc

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.int
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.panchang.core.GeoLocation
import org.panchang.wire.SiteAcceptance
import org.panchang.wire.acceptSite

/**
 * The command this whole project exists to serve:
 *
 * > *"a calculator when feeded location and sampradaya it gives me the exact festivals with the
 * > time (if the festival or tithi falls under particular time)."*
 *
 * ```
 * ./gradlew :calc:run --args="--place Nadia --sampradaya iskcon --year 2026"
 * ./gradlew :calc:run --args="--lat 19.0760 --lon 72.8777 --tz Asia/Kolkata --date 2026-01-15"
 * ```
 *
 * Argument style, error reporting and exit codes follow `verify`'s harvest CLI, which established
 * the clikt 5 conventions in this repository: every failure is a sentence the user can act on,
 * written to standard error, followed by a non-zero exit status — never a stack trace.
 *
 * Output goes to [out]; everything that is *not* the payload — banners, warnings, refusals — goes
 * to [err]. That split is what makes `--format json > file.json` produce a file containing only
 * JSON. Both are injected so tests can assert on the bytes rather than on a screen.
 */
class CalcCommand(
    private val out: Appendable = System.out,
    private val err: Appendable = System.err,
    private val resolver: LocationResolver = LocationResolver(),
    private val engine: CalcEngine = CalcEngine(),
) : CliktCommand(name = "panchang-calc") {

    override fun help(context: Context) =
        "Festivals, Ekadashi and fasting times for one location under one sampradaya. " +
            "Give either your own coordinates (--lat/--lon/--tz, preferred and exact) or a " +
            "place name (--place, resolved through the gazetteer). The choice is reported in " +
            "every output."

    // ── Location ────────────────────────────────────────────────────────────────────────────

    private val lat by option("--lat", help = "Latitude in degrees, north positive.").double()
    private val lon by option("--lon", help = "Longitude in degrees, EAST positive.").double()
    private val tz by option(
        "--tz",
        help = "IANA time zone id of the site, e.g. Asia/Kolkata. Required with --lat/--lon: " +
            "guessing a zone from coordinates would silently move every printed time.",
    )
    private val elevation by option(
        "--elevation",
        help = "Metres above the ellipsoid, used only for the horizon dip in rise/set. " +
            "Default 0: the reference calendars are computed at sea level, and a wrong " +
            "non-zero value is worse than none.",
    ).double().default(0.0)

    private val place by option(
        "--place",
        help = "Exact gazetteer place name. Never fuzzy-matched; an ambiguous name is refused " +
            "with the candidates listed.",
    )
    private val placeId by option(
        "--place-id",
        help = "GeoNames id, to settle an ambiguous --place. Ids are printed beside the " +
            "candidates.",
    ).int()

    // ── What to compute ─────────────────────────────────────────────────────────────────────

    private val sampradaya by option(
        "--sampradaya",
        help = "Tradition id. Default: iskcon.",
    ).default("iskcon")

    private val year by option("--year", help = "Calendar year to compute.").int()
    private val date by option(
        "--date",
        help = "A single date, YYYY-MM-DD. The year around it is computed and the festival and " +
            "Ekadashi lists are filtered to this day.",
    )

    private val format by option(
        "--format",
        help = "table (default, human) or json (wire v1 payloads, what the tests assert on).",
    ).default("table")

    private val explain by option(
        "--explain",
        help = "Print each ruling's reason and each anchor's definition in full. The definitions " +
            "are the text a reviewing pandit needs in order to challenge a reading; they are " +
            "always present in --format json.",
    ).flag()

    override fun run() {
        val site = resolveSite()
        when (val acceptance = resolver.accept(site)) {
            is SiteAcceptance.Accepted -> Unit
            is SiteAcceptance.Rejected -> fail(
                "This service declines to compute for ${site.query}.\n\n" +
                    "  code:   ${acceptance.code}\n" +
                    "  reason: ${acceptance.reason}\n\n" +
                    "That limit is stated once, in :wire, and this command does not carry one of " +
                    "its own.",
            )
        }

        val rules = Sampradayas[sampradaya] ?: fail(
            "Unknown sampradaya '$sampradaya'. Known: ${Sampradayas.knownIds().joinToString(", ")}.\n" +
                "An unknown tradition is refused rather than answered with another tradition's " +
                "dates, which would reach the user with nothing to tell them apart.",
        )

        val result = engine.compute(site, rules, scope())

        when (format.lowercase()) {
            "json" -> out.append(CalcJson.render(result)).append('\n')
            "table" -> out.append(HumanReport(explain).render(result))
            else -> fail("Unknown --format '$format'. Use 'table' or 'json'.")
        }

        // Location provenance also goes to stderr, so it is visible even when stdout has been
        // redirected into a file. A user who believes they were given their own district's times
        // should not have to open the file to find out which point was used.
        err.append(
            "[calc] ${site.source} ${"%.5f".format(site.location.latitude)}," +
                "${"%.5f".format(site.location.longitude)} ${site.location.zone.id}" +
                "${site.place?.let { " (${it.name}, ${it.admin1}, ${it.country})" } ?: ""}\n",
        )
    }

    // ── Argument plumbing ───────────────────────────────────────────────────────────────────

    /**
     * Location precedence, and the refusals along the way.
     *
     * `--lat/--lon/--tz` wins because it is exact: the caller's own point, used verbatim. A place
     * name is a fallback that borrows a record's representative point, and the output says so.
     */
    private fun resolveSite(): ResolvedSite {
        val hasCoords = lat != null || lon != null || tz != null
        val hasPlace = place != null || placeId != null

        if (hasCoords && hasPlace) {
            fail(
                "Give either --lat/--lon/--tz or --place, not both. Two answers to 'where' is " +
                    "not a preference to be resolved silently: whichever one this program " +
                    "dropped, the user would keep believing it had been used.",
            )
        }
        if (place != null && placeId != null) {
            fail("Give either --place or --place-id, not both.")
        }

        if (hasCoords) {
            val latitude = lat ?: fail("--lat is required alongside --lon/--tz.")
            val longitude = lon ?: fail("--lon is required alongside --lat/--tz.")
            val zoneId = tz ?: fail(
                "--tz is required with --lat/--lon. This program will not infer a time zone " +
                    "from coordinates: every printed instant is expressed in the site's civil " +
                    "zone, and a guessed zone would shift all of them by whole hours while " +
                    "still looking entirely reasonable.",
            )
            // acceptSite validates raw input first, because GeoLocation throws and a thrown
            // IllegalArgumentException cannot be turned into a useful message without parsing it.
            when (val pre = acceptSite(latitude, longitude, zoneId, elevation)) {
                is SiteAcceptance.Accepted -> Unit
                is SiteAcceptance.Rejected -> fail(
                    "This service declines to compute for these coordinates.\n\n" +
                        "  code:   ${pre.code}\n" +
                        "  reason: ${pre.reason}",
                )
            }
            val location = GeoLocation(latitude, longitude, java.time.ZoneId.of(zoneId), elevation)
            return resolver.byCoordinates(
                location,
                "--lat $latitude --lon $longitude --tz $zoneId",
            )
        }

        if (placeId != null) {
            val (site, failure) = resolver.byId(placeId!!)
            return site ?: fail(failure!!.message)
        }
        if (place != null) {
            val (site, failure) = resolver.byName(place!!)
            return site ?: fail(failure!!.message)
        }

        fail(
            "No location given. Pass --lat/--lon/--tz for your own site (exact, and preferred), " +
                "or --place NAME to use a gazetteer record's representative point.",
        )
    }

    private fun scope(): Scope {
        if (year != null && date != null) {
            fail("Give either --year or --date, not both.")
        }
        date?.let {
            val parsed = try {
                LocalDate.parse(it)
            } catch (e: DateTimeParseException) {
                fail("--date '$it' is not a date. Use YYYY-MM-DD, e.g. 2026-01-15.")
            }
            return Scope.Day(parsed)
        }
        year?.let { return Scope.Year(it) }
        fail("Give --year YYYY or --date YYYY-MM-DD.")
    }

    /**
     * Every refusal in this command goes through here, so they all exit the same way.
     *
     * The message is written to [err] rather than handed to clikt's own `PrintMessage`, and that
     * is not a stylistic preference. Clikt echoes through mordant, which on Windows writes in the
     * console code page; the ambiguity message for `--place Raigarh` names the state
     * `Mahārāshtra`, and through that path the user is told to choose between two candidates
     * printed as `Mah?r?shtra`. A refusal whose whole purpose is to let someone identify their
     * own district must be able to spell it. [ProgramResult] then sets the exit status without
     * printing anything of its own.
     */
    private fun fail(message: String): Nothing {
        err.append(message).append('\n')
        throw ProgramResult(1)
    }
}

/**
 * Entry point, with the streams pinned to UTF-8.
 *
 * Not incidental. On Windows the JVM gives `System.out` the console's code page, which is not
 * UTF-8, and the catalog's own event names carry characters that page cannot represent — a
 * devotee's name would reach the user as `?`. JSON is defined as UTF-8, so a payload written
 * through a code-page stream is not merely ugly, it is malformed. Pinning here rather than in the
 * Gradle task means the shipped jar behaves the same way when run directly.
 */
fun main(args: Array<String>) {
    val stdout = PrintStream(FileOutputStream(FileDescriptor.out), true, "UTF-8")
    val stderr = PrintStream(FileOutputStream(FileDescriptor.err), true, "UTF-8")
    try {
        CalcCommand(out = stdout, err = stderr).main(args)
    } finally {
        stdout.flush()
        stderr.flush()
    }
}
