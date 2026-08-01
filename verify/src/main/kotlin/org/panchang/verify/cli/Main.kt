package org.panchang.verify.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.findOrSetObject
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import org.panchang.verify.drik.DrikPanchangHarvester
import org.panchang.verify.drik.DrikQuery
import org.panchang.verify.grid.ReferenceCities
import org.panchang.verify.grid.ReferenceCity
import org.panchang.verify.harvest.ArtifactStore
import org.panchang.verify.harvest.HarvestContext
import org.panchang.verify.harvest.HarvestException
import org.panchang.verify.harvest.HarvestResult
import org.panchang.verify.harvest.Harvester
import org.panchang.verify.harvest.HostRateLimiter
import org.panchang.verify.harvest.HttpFetcher
import org.panchang.verify.harvest.Provenance
import org.panchang.verify.harvest.RequestSpec
import org.panchang.verify.horizons.HorizonsBody
import org.panchang.verify.horizons.HorizonsHarvester
import org.panchang.verify.horizons.HorizonsQuery
import org.panchang.verify.usno.UsnoHarvester
import org.panchang.verify.usno.UsnoQuery
import org.panchang.verify.vaisnava.IskconMumbaiHarvester
import org.panchang.verify.vaisnava.IskconMumbaiPage
import org.panchang.verify.vaisnava.IskconMumbaiQuery
import org.panchang.verify.vaisnava.PureBhaktiHarvester
import org.panchang.verify.vaisnava.PureBhaktiQuery
import org.panchang.verify.vaisnava.VaisnavaCalendarQuery
import org.panchang.verify.vaisnava.VaisnavaCalendarTxtHarvester
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/** Shared state for a harvest run. */
class HarvestConfig {
    var cacheDir: Path = Path.of("cache")
    var offline: Boolean = false
    var dryRun: Boolean = false
    var allowStale: Boolean = false
    var outFile: Path? = null
}

private val prettyJson = Json { prettyPrint = true; encodeDefaults = true }

class HarvestCli : CliktCommand(name = "harvest") {
    override fun help(context: Context) =
        "Fetch reference data from external sources into verify/cache with provenance stamps. " +
            "Harvesting is a deliberate act: nothing in this project fetches anything at test time."

    private val config by findOrSetObject { HarvestConfig() }

    private val cacheDir by option("--cache-dir", help = "Cache root (default: ./cache)")
        .default("cache")
    private val offline by option(
        "--offline",
        help = "Never touch the network. Uses cached artifacts, including expired ones, " +
            "and fails explicitly when a needed artifact is absent.",
    ).flag()
    private val dryRun by option(
        "--dry-run",
        help = "Print the exact requests that would be made, then stop.",
    ).flag()
    private val allowStale by option(
        "--allow-stale",
        help = "On fetch failure, fall back to an expired cached artifact (recorded as a warning).",
    ).flag()
    private val out by option("--out", help = "Write parsed records as JSON to this file.")

    override fun run() {
        config.cacheDir = Path.of(cacheDir)
        config.offline = offline
        config.dryRun = dryRun
        config.allowStale = allowStale
        config.outFile = out?.let { Path.of(it) }
    }
}

/** Common plumbing for the source subcommands. */
abstract class HarvestSubcommand(name: String) : CliktCommand(name = name) {

    protected val config by findOrSetObject { HarvestConfig() }

    /**
     * Provenance stamps for everything this invocation read, in order.
     *
     * Collected so that a file written with `--out` carries its own attribution. A golden
     * file whose numbers cannot be traced back to a request and a retrieval timestamp is
     * not reference data, it is a set of numbers someone once believed.
     */
    private val collectedProvenance = mutableListOf<Provenance>()

    /** Every request this invocation would make, in order. */
    protected abstract fun plan(): List<Pair<String, RequestSpec>>

    /** Executes the plan, returning JSON-encodable records. */
    protected abstract fun execute(ctx: HarvestContext): String

    override fun run() {
        val plan = plan()
        if (config.dryRun) {
            echo("DRY RUN: ${plan.size} request(s) would be made.")
            for ((label, spec) in plan) {
                echo("  [$label] ${spec.method} ${spec.url}")
                spec.notes.forEach { echo("      note: $it") }
            }
            val hosts = plan.map { it.second.host }.distinct()
            val limiter = HostRateLimiter()
            val seconds = plan.groupBy { it.second.host }
                .map { (host, reqs) -> reqs.size * limiter.delayForHost(host) / 1000.0 }
                .sum()
            echo("  hosts: ${hosts.joinToString(", ")}")
            echo("  minimum elapsed time at configured rate limits: ~${"%.0f".format(seconds)}s")
            return
        }

        val store = ArtifactStore(config.cacheDir)
        val client = if (config.offline) null else HttpFetcher.defaultClient()
        val fetcher = if (client == null) {
            object : org.panchang.verify.harvest.Fetcher {
                override suspend fun fetch(spec: RequestSpec) =
                    error("offline mode reached the fetcher; this is a bug in Harvester.harvest")
            }
        } else {
            HttpFetcher(client, HostRateLimiter())
        }
        try {
            val ctx = HarvestContext(
                store = store,
                fetcher = fetcher,
                offline = config.offline,
                allowStaleOnFetchFailure = config.allowStale,
            )
            val json = execute(ctx)
            config.outFile?.let { path ->
                path.parent?.createDirectories()
                path.writeText(withProvenance(json))
                echo("Wrote ${path.toAbsolutePath()}")
            } ?: echo(json)
        } finally {
            client?.close()
        }
    }

    /** Wraps a records array in an envelope carrying the provenance of every artifact read. */
    private fun withProvenance(recordsJson: String): String {
        val envelope = buildJsonObject {
            put("writtenAtUtc", JsonPrimitive(HarvestContext.ISO_UTC.format(java.time.Instant.now())))
            put("provenance", prettyJson.encodeToJsonElement(serializer(), collectedProvenance.toList()))
            put("records", Json.parseToJsonElement(recordsJson))
        }
        return prettyJson.encodeToString(JsonObject.serializer(), envelope)
    }

    /** Prints the provenance and parse report for one result, then returns its records. */
    protected fun <R> report(label: String, result: HarvestResult<R>): List<R> {
        val p = result.provenance
        collectedProvenance += p
        echo(
            "[$label] ${if (result.fromCache) "cache" else "fetched"} " +
                "status=${p.httpStatus} bytes=${p.responseBytes} sha256=${p.responseSha256.take(12)} " +
                "at=${p.retrievedAtUtc} records=${result.parsed.report.recordCount}",
            err = true,
        )
        result.parsed.report.warnings.forEach { echo("    warning: $it", err = true) }
        result.parsed.report.unparsed.forEach {
            echo("    UNPARSED ${it.location}: ${it.reason} :: ${it.text.take(120)}", err = true)
        }
        return result.parsed.records
    }

    /**
     * Runs one harvest, converting a failure into a message that names the source rather
     * than into a silently short result list.
     */
    protected fun <Q, R> runOne(
        harvester: Harvester<Q, R>,
        query: Q,
        label: String,
        ctx: HarvestContext,
    ): List<R> = try {
        runBlocking { report(label, harvester.harvest(query, ctx)) }
    } catch (e: HarvestException) {
        echo("[$label] FAILED: ${e.message}", err = true)
        throw PrintMessage("Harvest failed for $label. No data was written.", statusCode = 1, printError = true)
    }
}

class HorizonsCommand : HarvestSubcommand("horizons") {
    override fun help(context: Context) =
        "Apparent geocentric ecliptic position of the Moon or Sun from JPL Horizons."

    private val body by option("--body", help = "moon or sun").default("moon")
    private val from by option("--from", help = "Start date, YYYY-MM-DD").required()
    private val to by option("--to", help = "Stop date, YYYY-MM-DD").required()
    private val step by option("--step", help = "Step size, e.g. 1d, 6h, 30m").default("1d")

    private fun query() = HorizonsQuery(
        body = HorizonsBody.parse(body),
        start = from,
        stop = to,
        step = normaliseStep(step),
    )

    private val harvester = HorizonsHarvester()

    override fun plan() = listOf("horizons/$body" to harvester.requestSpec(query()))

    override fun execute(ctx: HarvestContext): String {
        val records = runOne(harvester, query(), "horizons/$body", ctx)
        return prettyJson.encodeToString(serializer(), records)
    }

    /** Accepts "1d" as well as Horizons' own "1 d". */
    private fun normaliseStep(s: String): String =
        Regex("^(\\d+)\\s*([a-zA-Z]+)$").matchEntire(s.trim())
            ?.let { "${it.groupValues[1]} ${it.groupValues[2]}" }
            ?: s
}

class UsnoCommand : HarvestSubcommand("usno") {
    override fun help(context: Context) =
        "Sunrise/sunset/moonrise/moonset from the US Naval Observatory, per reference city."

    private val city by option("--city", help = "Reference city id, or 'all', or 'polar'").default("all")
    private val year by option("--year", help = "Harvest every date in this year").int()
    private val from by option("--from", help = "Start date, YYYY-MM-DD")
    private val to by option("--to", help = "End date, inclusive, YYYY-MM-DD")

    private val harvester = UsnoHarvester()

    private fun cities(): List<ReferenceCity> = when (city.lowercase()) {
        "all" -> ReferenceCities.ALL
        "polar" -> ReferenceCities.POLAR_PROBES
        else -> listOf(ReferenceCities.byId(city))
    }

    private fun dates(): List<LocalDate> {
        if (year != null) {
            val start = LocalDate.of(year!!, 1, 1)
            return generateSequence(start) { it.plusDays(1) }
                .takeWhile { it.year == year }
                .toList()
        }
        val start = from?.let(LocalDate::parse)
            ?: throw PrintMessage("Provide --year, or --from and --to.", statusCode = 1, printError = true)
        val end = to?.let(LocalDate::parse) ?: start
        return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
    }

    private fun queries(): List<Pair<String, UsnoQuery>> =
        cities().flatMap { c -> dates().map { d -> "usno/${c.id}/$d" to UsnoHarvester.queryFor(c, d) } }

    override fun plan() = queries().map { (label, q) -> label to harvester.requestSpec(q) }

    override fun execute(ctx: HarvestContext): String {
        val records = queries().flatMap { (label, q) -> runOne(harvester, q, label, ctx) }
        return prettyJson.encodeToString(serializer(), records)
    }
}

class IskconCommand : HarvestSubcommand("iskcon") {
    override fun help(context: Context) =
        "Vaishnava calendar reference data: vaisnavacalendar.info text calendars (primary), " +
            "iskconmumbai.com and purebhakti.com (cross-checks)."

    private val year by option("--year", help = "Calendar year").int().required()
    private val city by option("--city", help = "Reference city id, or 'all'").default("all")
    private val source by option(
        "--source",
        help = "vaisnavacalendar | iskconmumbai | purebhakti (default: vaisnavacalendar)",
    ).default("vaisnavacalendar")

    private val txt = VaisnavaCalendarTxtHarvester()
    private val mumbai = IskconMumbaiHarvester()
    private val pureBhakti = PureBhaktiHarvester()

    private fun txtQueries(): List<Pair<String, VaisnavaCalendarQuery>> {
        val cities = if (city.lowercase() == "all") ReferenceCities.ALL else listOf(ReferenceCities.byId(city))
        return cities.mapNotNull { c ->
            val q = VaisnavaCalendarQuery.forCity(c, year)
            if (q == null) {
                echo(
                    "[iskcon] ${c.id}: vaisnavacalendar.info publishes no calendar for this " +
                        "location; skipping. Factor 3 is unverifiable here.",
                    err = true,
                )
                null
            } else {
                "vaisnavacalendar/${c.id}/$year" to q
            }
        }
    }

    override fun plan(): List<Pair<String, RequestSpec>> = when (source.lowercase()) {
        "vaisnavacalendar" -> txtQueries().map { (l, q) -> l to txt.requestSpec(q) }
        "iskconmumbai" -> listOf(
            "iskconmumbai/ekadasi" to mumbai.requestSpec(IskconMumbaiQuery(IskconMumbaiPage.EKADASI)),
        )
        "purebhakti" -> listOf("purebhakti/$year" to pureBhakti.requestSpec(PureBhaktiQuery(year)))
        else -> throw PrintMessage("Unknown --source '$source'.", statusCode = 1, printError = true)
    }

    override fun execute(ctx: HarvestContext): String = when (source.lowercase()) {
        "vaisnavacalendar" -> {
            val all = txtQueries().flatMap { (label, q) -> runOne(txt, q, label, ctx) }
            prettyJson.encodeToString(serializer(), all)
        }
        "iskconmumbai" -> {
            val r = runOne(mumbai, IskconMumbaiQuery(IskconMumbaiPage.EKADASI), "iskconmumbai/ekadasi", ctx)
            prettyJson.encodeToString(serializer(), r)
        }
        else -> {
            val r = runOne(pureBhakti, PureBhaktiQuery(year), "purebhakti/$year", ctx)
            prettyJson.encodeToString(serializer(), r)
        }
    }
}

class DrikCommand : HarvestSubcommand("drik") {
    override fun help(context: Context) =
        "drikpanchang.com day panchang. COMPARISON TARGET ONLY: harvested data stays in " +
            "verify/cache (gitignored) and must never be committed, served or redistributed."

    private val date by option("--date", help = "Date, YYYY-MM-DD").required()
    private val geonameId by option("--geoname-id", help = "drikpanchang location id").int()

    private val harvester = DrikPanchangHarvester()

    private fun query() = DrikQuery(LocalDate.parse(date), geonameId)

    override fun plan() = listOf("drik/$date" to harvester.requestSpec(query()))

    override fun execute(ctx: HarvestContext): String {
        val records = runOne(harvester, query(), "drik/$date", ctx)
        echo(
            "Reminder: drikpanchang data is a comparison target only. Do not commit or " +
                "redistribute these values.",
            err = true,
        )
        return prettyJson.encodeToString(serializer(), records)
    }
}

class CitiesCommand : CliktCommand(name = "cities") {
    override fun help(context: Context) = "Print the reference city grid as JSON."
    override fun run() {
        echo(prettyJson.encodeToString(serializer(), ReferenceCities.ALL))
        echo(prettyJson.encodeToString(serializer(), ReferenceCities.POLAR_PROBES))
    }
}

fun main(args: Array<String>) = HarvestCli()
    .subcommands(
        HorizonsCommand(),
        UsnoCommand(),
        IskconCommand(),
        DrikCommand(),
        CitiesCommand(),
    )
    .main(args)
