package org.panchang.gazetteer.ingest

import org.panchang.gazetteer.Gazetteer
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile

/**
 * Rebuilds `gazetteer-v1.tsv` from the two vendored GeoNames archives.
 *
 * Run it deliberately — `./gradlew :gazetteer:ingest` — never as part of `build`. The
 * derived table is committed data reviewed alongside the code that reads it; a build step
 * able to rewrite it in place would eventually rewrite it in a commit nobody read.
 *
 * ### What is read, and what is deliberately not
 *
 * GeoNames' `geoname` table has 19 tab-separated columns. This ingest reads ten of them.
 * The two it most conspicuously ignores are **`elevation` (15) and `dem` (16)**. That is a
 * decision recorded in `vendor/PROVENANCE.md`, not an oversight: the reference calendars
 * this service is checked against are computed at sea level, so applying a district's true
 * altitude to the horizon-dip term would guarantee divergence at exactly the high-altitude
 * districts we have no reference data for — on sunrise, which decides fasting dates. There
 * is no column here to accidentally use, which is the point.
 *
 * `alternatenames` (3) is also dropped: it is over half the input by volume, is of very
 * uneven quality, and a fuzzy multilingual match is not what the anti-snapping design needs.
 */
object Ingest {
    private const val CITIES_ZIP = "vendor/geonames/cities15000.zip"
    private const val CITIES_ENTRY = "cities15000.txt"
    private const val INDIA_ZIP = "vendor/geonames/IN.zip"
    private const val INDIA_ENTRY = "IN.txt"
    private const val OUTPUT = "gazetteer/src/main/resources/org/panchang/gazetteer/gazetteer-v1.tsv"

    /** GeoNames `geoname` table column indices, by name, so the field numbers appear once. */
    private const val C_ID = 0
    private const val C_NAME = 1
    private const val C_ASCII = 2
    private const val C_LAT = 4
    private const val C_LON = 5
    private const val C_FEATURE_CLASS = 6
    private const val C_FEATURE_CODE = 7
    private const val C_COUNTRY = 8
    private const val C_ADMIN1 = 10
    private const val C_POPULATION = 14
    private const val C_TIMEZONE = 17
    private const val FIELD_COUNT = 19

    /** A reason a row was not kept, and how many rows it accounts for. */
    private class Drops {
        val counts = LinkedHashMap<String, Int>()
        fun drop(reason: String) {
            counts[reason] = (counts[reason] ?: 0) + 1
        }
    }

    private class Row(
        val id: Int,
        val name: String,
        val ascii: String,
        val kind: String,
        val country: String,
        val admin1: String,
        val population: Long,
        val lat: Double,
        val lon: Double,
        val zone: String,
    )

    @JvmStatic
    fun main(args: Array<String>) {
        val root = File(".").canonicalFile
        val citiesZip = File(root, CITIES_ZIP)
        val indiaZip = File(root, INDIA_ZIP)
        for (f in listOf(citiesZip, indiaZip)) {
            check(f.isFile) { "missing vendored archive ${f.path}; run from the repository root" }
        }

        val cityDrops = Drops()
        val districtDrops = Drops()
        val seenIds = HashSet<Int>()

        // Pass 1 over IN.txt: the country file's own ADM1 records give a code-to-name table
        // for India's states and union territories, so district rows can carry a readable
        // first-level division without vendoring a third archive.
        val admin1Names = HashMap<String, String>()
        readEntry(indiaZip, INDIA_ENTRY) { fields ->
            if (fields.size == FIELD_COUNT &&
                fields[C_FEATURE_CLASS] == "A" && fields[C_FEATURE_CODE] == "ADM1"
            ) {
                admin1Names[fields[C_ADMIN1]] = fields[C_NAME]
            }
        }

        val rows = ArrayList<Row>(40_000)
        var citiesRead = 0
        readEntry(citiesZip, CITIES_ENTRY) { fields ->
            citiesRead++
            parse(fields, "CITY", admin1Names, seenIds, cityDrops)?.let(rows::add)
        }

        var indiaRead = 0
        readEntry(indiaZip, INDIA_ENTRY) { fields ->
            indiaRead++
            if (fields.size != FIELD_COUNT) {
                districtDrops.drop("malformed row: field count != $FIELD_COUNT")
            } else if (fields[C_FEATURE_CLASS] != "A" || fields[C_FEATURE_CODE] != "ADM2") {
                districtDrops.drop("filtered out: feature_class != \"A\" or feature_code != \"ADM2\"")
            } else {
                parse(fields, "DISTRICT", admin1Names, seenIds, districtDrops)?.let(rows::add)
            }
        }

        rows.sortWith(compareBy({ it.country }, { it.kind }, { it.id }))

        val citiesKept = rows.count { it.kind == "CITY" }
        val districtsKept = rows.size - citiesKept
        val out = File(root, OUTPUT)
        out.parentFile.mkdirs()
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            fun meta(line: String) = w.write("# $line\n")
            meta("gazetteer-v1 — derived place table. GENERATED FILE; edit the ingest, not this.")
            meta("generated-by  org.panchang.gazetteer.ingest.Ingest")
            meta("generated-at  ${DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))}")
            // The runtime's tzdb decides which zone ids are representable, so it decides
            // which rows survive. Record it: it is an input to this file as surely as the
            // archives are.
            meta("generated-on  JDK ${System.getProperty("java.version")}, tzdb $tzdbVersion")
            meta("")
            meta("licence       CC BY 4.0")
            meta("attribution   ${Gazetteer.ATTRIBUTION}")
            meta("")
            meta("SOURCES — sha256 and byte size must match vendor/MANIFEST.sha256.")
            meta("GazetteerProvenanceTest fails if they diverge, so this table cannot go stale")
            meta("against re-vendored inputs without someone being told.")
            meta("source        geonames/cities15000.zip")
            meta("  sha256      ${sha256(citiesZip)}")
            meta("  bytes       ${citiesZip.length()}")
            meta("  entry       $CITIES_ENTRY")
            meta("  filter      (none) — every row of cities15000.txt is a populated place")
            meta("              of population >= 15000; all are kept as kind=CITY.")
            meta("source        geonames/IN.zip")
            meta("  sha256      ${sha256(indiaZip)}")
            meta("  bytes       ${indiaZip.length()}")
            meta("  entry       $INDIA_ENTRY")
            meta("  filter      feature_class == \"A\" && feature_code == \"ADM2\"  -> kind=DISTRICT")
            meta("              admin2Codes.txt was NOT used: it carries no coordinates, so")
            meta("              districts have to come from the country file.")
            meta("")
            meta("COUNTS")
            meta("  cities15000.txt   read ${citiesRead}, kept ${citiesKept}, dropped ${citiesRead - citiesKept}")
            writeDrops(::meta, cityDrops)
            meta("  IN.txt            read ${indiaRead}, kept ${districtsKept}, dropped ${indiaRead - districtsKept}")
            writeDrops(::meta, districtDrops)
            meta("  total kept        ${rows.size}")
            meta("")
            meta("COLUMNS NOT INGESTED — deliberate")
            meta("  elevation (col 15) and dem (col 16) are NOT read. The reference calendars")
            meta("  are computed at sea level; applying real altitude to the horizon-dip term")
            meta("  would guarantee divergence at high-altitude districts against no reference")
            meta("  able to validate it, on the sunrise that decides fasting dates. Place")
            meta("  elevations therefore do not exist in this table at all, so no code can")
            meta("  reach for one by accident. See vendor/PROVENANCE.md.")
            meta("  alternatenames (col 4) is dropped as bulk of uneven quality.")
            meta("")
            meta("admin1: for country IN, the name of the IN.txt ADM1 record with the matching")
            meta("admin1 code (${admin1Names.size} resolved); for every other country, GeoNames'")
            meta("raw admin1 code, because no code-to-name table was vendored.")
            meta("")
            meta(Gazetteer.COLUMNS.joinToString("\t"))
            for (r in rows) {
                w.write(
                    listOf(
                        r.id, r.kind, r.name, r.ascii, r.country, r.admin1,
                        r.population, fmt(r.lat), fmt(r.lon), r.zone,
                    ).joinToString("\t"),
                )
                w.write("\n")
            }
        }

        println("wrote ${out.path}")
        println("  cities15000.txt read=$citiesRead kept=$citiesKept dropped=${citiesRead - citiesKept}")
        cityDrops.counts.forEach { (r, n) -> println("      $n  $r") }
        println("  IN.txt          read=$indiaRead kept=$districtsKept dropped=${indiaRead - districtsKept}")
        districtDrops.counts.forEach { (r, n) -> println("      $n  $r") }
        println("  total kept=${rows.size}")
    }

    /** The tzdata release the running JVM carries, e.g. `2024b`, or `unknown` if it will not say. */
    private val tzdbVersion: String
        get() = runCatching {
            java.time.zone.ZoneRulesProvider.getVersions("Europe/London").lastKey()
        }.getOrElse { "unknown" }

    private fun writeDrops(meta: (String) -> Unit, drops: Drops) {
        if (drops.counts.isEmpty()) meta("      (no rows dropped)")
        drops.counts.entries.sortedByDescending { it.value }.forEach { (reason, n) ->
            meta("      $n  $reason")
        }
    }

    /**
     * Validates one row and converts it, or records why it could not be. Every rejection has
     * a named reason that reaches the header, because "26 000 rows in, 25 940 out" with no
     * account of the 60 is how a silent data bug survives review.
     */
    private fun parse(
        f: List<String>,
        kind: String,
        admin1Names: Map<String, String>,
        seenIds: MutableSet<Int>,
        drops: Drops,
    ): Row? {
        if (f.size != FIELD_COUNT) {
            drops.drop("malformed row: field count != $FIELD_COUNT")
            return null
        }
        val id = f[C_ID].toIntOrNull()
        if (id == null) {
            drops.drop("unparseable geonameid")
            return null
        }
        if (!seenIds.add(id)) {
            drops.drop("duplicate geonameid")
            return null
        }
        val name = f[C_NAME].trim()
        if (name.isEmpty()) {
            drops.drop("blank name")
            return null
        }
        val lat = f[C_LAT].toDoubleOrNull()
        val lon = f[C_LON].toDoubleOrNull()
        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            drops.drop("missing or out-of-range coordinates")
            return null
        }
        val zone = f[C_TIMEZONE].trim()
        if (zone.isEmpty()) {
            drops.drop("blank IANA time zone")
            return null
        }
        // Naming the offending zone in the drop reason is the difference between a header
        // that says "2 rows lost" and one that says which two and why. GeoNames tracks the
        // IANA database more promptly than a JDK's bundled copy does, so a zone added in a
        // recent tzdata release is unrepresentable on an older runtime — a real dependency
        // of this derived table on the JDK that generated it, and one that ought to be
        // written down rather than discovered.
        if (runCatching { java.time.ZoneId.of(zone) }.isFailure) {
            drops.drop("time zone unknown to this JDK's tzdb: $zone")
            return null
        }
        val country = f[C_COUNTRY].trim()
        if (country.length != 2) {
            drops.drop("missing or malformed country code")
            return null
        }
        val admin1Code = f[C_ADMIN1].trim()
        val admin1 =
            if (country == "IN") admin1Names[admin1Code] ?: admin1Code else admin1Code
        val ascii = f[C_ASCII].trim().ifEmpty { name }
        // A tab or newline in a field would corrupt the TSV. GeoNames does not emit them;
        // assert rather than assume, because a silently split row is a wrong place.
        for (s in listOf(name, ascii, country, admin1, zone)) {
            check(s.none { it == '\t' || it == '\n' || it == '\r' }) {
                "field contains a tab or newline in geonameid $id: $s"
            }
        }
        return Row(
            id = id,
            name = name,
            ascii = ascii,
            kind = kind,
            country = country,
            admin1 = admin1,
            population = f[C_POPULATION].toLongOrNull() ?: 0L,
            lat = lat,
            lon = lon,
            zone = zone,
        )
    }

    /** Five decimals is ~1 m — far finer than a "representative point" for a district means. */
    private fun fmt(v: Double): String =
        String.format(java.util.Locale.ROOT, "%.5f", v).trimEnd('0').trimEnd('.')
            .let { if (it.isEmpty() || it == "-") "0" else it }

    private fun readEntry(zip: File, entryName: String, onRow: (List<String>) -> Unit) {
        ZipFile(zip).use { zf ->
            val entry = zf.getEntry(entryName) ?: error("$entryName not found in ${zip.name}")
            zf.getInputStream(entry).bufferedReader(Charsets.UTF_8).forEachLine { line ->
                if (line.isNotEmpty() && !line.startsWith("#")) onRow(line.split('\t'))
            }
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream: InputStream ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
