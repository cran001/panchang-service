package org.panchang.gazetteer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The derived table must not be able to drift away from the sources it claims to come from,
 * and the attribution the licence costs us must not be able to fall off in a refactor.
 *
 * Both are the kind of thing that is obviously fine on the day it is written and quietly
 * wrong two years later. Neither is checkable by reading the diff of the commit that breaks
 * it, because the commit that breaks it touches a different file.
 */
class GazetteerProvenanceTest {

    private val gazetteer = Gazetteer.default
    private val header = gazetteer.provenance

    /** `vendor/`, passed in by the build so the test reads the real manifest, not a copy. */
    private val vendorDir: File
        get() {
            val path = System.getProperty("panchang.vendor.dir")
            assertNotNull(path, "system property panchang.vendor.dir is not set; see gazetteer/build.gradle.kts")
            return File(path!!).also { assertTrue(it.isDirectory, "not a directory: $it") }
        }

    /** `path -> (sha256, bytes)` from the three-column `vendor/MANIFEST.sha256`. */
    private fun manifest(): Map<String, Pair<String, Long>> =
        File(vendorDir, "MANIFEST.sha256").readLines()
            .filterNot { it.startsWith("#") || it.isBlank() }
            .map { it.trim().split(Regex("\\s+")) }
            .filter { it.size == 3 }
            .associate { (hash, size, path) -> path to (hash to size.toLong()) }

    /**
     * Every `sha256`/`bytes` pair recorded in the table's own header still matches
     * `vendor/MANIFEST.sha256`.
     *
     * This is the whole reason the ingest writes source hashes into the artifact. Re-vendor
     * `IN.zip` without re-running `:gazetteer:ingest` and this fails loudly, instead of the
     * repository sitting there with a district table built from an archive that is no longer
     * present and nobody able to tell by looking.
     */
    @Test
    fun `recorded source hashes match the vendor manifest`() {
        val manifest = manifest()
        val recorded = recordedSources()
        assertEquals(
            setOf("geonames/cities15000.zip", "geonames/IN.zip"),
            recorded.keys,
            "the derived table should record exactly the two GeoNames archives as sources",
        )
        for ((path, pair) in recorded) {
            val (hash, bytes) = pair
            val fromManifest = manifest[path]
            assertNotNull(fromManifest, "$path is recorded in gazetteer-v1.tsv but absent from MANIFEST.sha256")
            assertEquals(
                fromManifest!!.first,
                hash,
                "gazetteer-v1.tsv was built from a different $path than the one vendored now; " +
                    "re-run ./gradlew :gazetteer:ingest",
            )
            assertEquals(fromManifest.second, bytes, "byte size of $path disagrees with MANIFEST.sha256")
        }
    }

    /** The vendored archives named in the header are actually present on disk. */
    @Test
    fun `vendored archives exist`() {
        for (path in recordedSources().keys) {
            val f = File(vendorDir, path)
            assertTrue(f.isFile, "missing vendored archive $f")
            assertEquals(recordedSources()[path]!!.second, f.length(), "$path is not the size the header claims")
        }
    }

    /**
     * CC BY 4.0 is attribution-only, which is exactly why this source was chosen over ones
     * whose network copyleft would have forced the sampradaya rules into the open. The
     * attribution is therefore the entire price of the licence, and it has to ship.
     */
    @Test
    fun `attribution ships inside the artifact`() {
        assertTrue(
            header.any { it.contains(Gazetteer.ATTRIBUTION) },
            "Gazetteer.ATTRIBUTION does not appear in the shipped gazetteer-v1.tsv header",
        )
        assertTrue(Gazetteer.ATTRIBUTION.contains("GeoNames"), "attribution must name GeoNames")
        assertTrue(Gazetteer.ATTRIBUTION.contains("CC BY 4.0"), "attribution must name the licence")
        assertTrue(
            header.any { it.contains("CC BY 4.0") },
            "the licence must be recorded in the artifact header",
        )
    }

    /**
     * The elevation decision is recorded in the artifact itself, not only in a markdown file
     * somebody may never open. A future reader wondering why places have no altitude should
     * find the answer in the first thing they look at.
     */
    @Test
    fun `the elevation decision is recorded in the artifact`() {
        val text = header.joinToString("\n")
        assertTrue(text.contains("elevation"), "header must mention the elevation columns")
        assertTrue(text.contains("dem"), "header must mention the dem column")
        assertTrue(text.contains("NOT read"), "header must state that they are not read")
        assertTrue(
            Gazetteer.COLUMNS.none { it.contains("elev") || it == "dem" },
            "the derived table must not have an elevation column at all",
        )
    }

    /** The filter that produced the district rows is written down, exactly. */
    @Test
    fun `the applied filter is recorded`() {
        val text = header.joinToString("\n")
        assertTrue(
            text.contains("feature_class == \"A\" && feature_code == \"ADM2\""),
            "header must record the exact district filter",
        )
    }

    /**
     * The header's `total kept` is the number of rows actually present. A header that
     * disagrees with its own body is worse than no header, because it is believed.
     */
    @Test
    fun `recorded counts agree with the rows actually present`() {
        val total = header.firstNotNullOfOrNull { line ->
            Regex("""total kept\s+(\d+)""").find(line)?.groupValues?.get(1)?.toInt()
        }
        assertNotNull(total, "header does not record a total kept count")
        assertEquals(total, gazetteer.places.size, "header count disagrees with the rows in the file")
    }

    /**
     * Every drop is accounted for: for each source, `read = kept + sum(named drop reasons)`.
     * "26 000 in, 25 940 out" with no account of the 60 is how a data bug survives review.
     */
    @Test
    fun `every dropped row has a named reason and the arithmetic closes`() {
        val text = header.joinToString("\n")
        for (source in listOf("cities15000.txt", "IN.txt")) {
            val line = header.firstOrNull { it.startsWith(source) }
            assertNotNull(line, "header does not report counts for $source")
            val m = Regex("""read (\d+), kept (\d+), dropped (\d+)""").find(line!!)
            assertNotNull(m, "counts for $source are not in the expected form: $line")
            val (read, kept, dropped) = m!!.destructured
            assertEquals(
                read.toInt(),
                kept.toInt() + dropped.toInt(),
                "read/kept/dropped do not add up for $source",
            )
        }
        // Each reported drop count must be attributed to a reason line beneath it.
        val reasons = header.filter { Regex("""^\d+\s+\S""").containsMatchIn(it) }
        val droppedTotal = Regex("""dropped (\d+)""").findAll(text)
            .sumOf { it.groupValues[1].toInt() }
        val attributed = reasons.sumOf { Regex("""^(\d+)""").find(it)!!.groupValues[1].toInt() }
        assertEquals(droppedTotal, attributed, "some dropped rows have no named reason")
    }

    private fun recordedSources(): Map<String, Pair<String, Long>> {
        val out = LinkedHashMap<String, Pair<String, Long>>()
        var current: String? = null
        var hash: String? = null
        for (line in header) {
            Regex("""^source\s+(\S+)""").find(line)?.let { current = it.groupValues[1]; hash = null }
            Regex("""^sha256\s+([0-9a-f]{64})$""").find(line)?.let { hash = it.groupValues[1] }
            Regex("""^bytes\s+(\d+)$""").find(line)?.let {
                val c = current
                val h = hash
                if (c != null && h != null) out[c] = h to it.groupValues[1].toLong()
            }
        }
        return out
    }
}
