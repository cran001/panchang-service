package org.panchang.verify

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.panchang.verify.harvest.ArtifactStore
import org.panchang.verify.harvest.CorruptCacheException
import org.panchang.verify.harvest.FetchedResponse
import org.panchang.verify.harvest.Fetcher
import org.panchang.verify.harvest.HarvestContext
import org.panchang.verify.harvest.Harvester
import org.panchang.verify.harvest.OfflineException
import org.panchang.verify.harvest.ParseReport
import org.panchang.verify.harvest.ParsedArtifact
import org.panchang.verify.harvest.Provenance
import org.panchang.verify.harvest.RequestSpec
import org.panchang.verify.harvest.SourceUnreachableException
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.io.path.readText
import kotlin.io.path.writeBytes

/**
 * A fetcher that answers from a script and counts calls, so a test can prove that a
 * second harvest of the same query did not touch the network.
 */
private class ScriptedFetcher(
    private val body: ByteArray = "alpha\n".toByteArray(),
    private val status: Int = 200,
    private val contentType: String? = "text/plain",
    private val failWith: Throwable? = null,
) : Fetcher {
    var calls = 0
        private set
    val urls = mutableListOf<String>()

    override suspend fun fetch(spec: RequestSpec): FetchedResponse {
        calls++
        urls += spec.url
        failWith?.let { throw it }
        return FetchedResponse(status, contentType, body)
    }
}

/** Minimal harvester: one string parameter in, whole body out as one record. */
private class EchoHarvester(
    override val harvesterVersion: Int = 1,
    override val cacheTtl: Duration? = null,
) : Harvester<String, String>() {
    override val sourceId = "test-echo"

    override fun requestSpec(query: String) = RequestSpec(
        sourceId = sourceId,
        baseUrl = "https://example.invalid/echo",
        parameters = listOf("q" to query, "step" to "1 d"),
        headers = mapOf("User-Agent" to "test-agent"),
        notes = listOf("test fixture, not a real source"),
    )

    override fun cacheKey(query: String) = ArtifactStore.cacheKey(query, "v$harvesterVersion|$query")

    override fun parse(bytes: ByteArray, provenance: Provenance): ParsedArtifact<String> =
        ParsedArtifact(listOf(bytes.decodeToString().trim()), ParseReport(recordCount = 1))
}

class ProvenanceAndCacheTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val fixedNow = Instant.parse("2026-08-01T12:00:00Z")

    private fun ctx(
        root: Path,
        fetcher: Fetcher,
        offline: Boolean = false,
        allowStale: Boolean = false,
        now: Instant = fixedNow,
    ) = HarvestContext(ArtifactStore(root), fetcher, offline, allowStale) { now }

    @Test
    fun `stamps every field a re-fetch would need`(@TempDir root: Path) = runBlocking {
        val fetcher = ScriptedFetcher()
        val result = EchoHarvester().harvest("moon", ctx(root, fetcher))

        val p = result.provenance
        assertEquals("test-echo", p.sourceId)
        assertEquals("GET", p.method)
        // The URL must carry the exact encoding used, including the space in "1 d".
        assertEquals("https://example.invalid/echo?q=moon&step=1%20d", p.url)
        assertEquals(mapOf("q" to "moon", "step" to "1 d"), p.requestParameters)
        assertEquals(mapOf("User-Agent" to "test-agent"), p.requestHeaders)
        assertEquals("2026-08-01T12:00:00Z", p.retrievedAtUtc)
        assertEquals(200, p.httpStatus)
        assertEquals("text/plain", p.responseContentType)
        assertEquals(6L, p.responseBytes)
        assertEquals(ArtifactStore.sha256Hex("alpha\n".toByteArray()), p.responseSha256)
        assertEquals(1, p.harvesterVersion)
        assertTrue(p.notes.contains("test fixture, not a real source"))
        assertFalse(result.fromCache)
    }

    /**
     * The raw bytes are the evidence; the parsed form is an opinion about them. A parser
     * bug found later must be fixable by re-reading what we stored rather than by
     * re-requesting it from someone else's server.
     */
    @Test
    fun `writes the raw bytes beside the stamp, byte for byte`(@TempDir root: Path) = runBlocking {
        val body = byteArrayOf(0xC3.toByte(), 0xA9.toByte(), 0x0A) // "é\n" in UTF-8
        val harvester = EchoHarvester()
        harvester.harvest("accented", ctx(root, ScriptedFetcher(body)))

        val store = ArtifactStore(root)
        val key = harvester.cacheKey("accented")
        assertTrue(store.has("test-echo", key))
        assertArrayEqualsBytes(body, java.nio.file.Files.readAllBytes(store.rawPath("test-echo", key)))

        val stamp = json.decodeFromString(
            Provenance.serializer(),
            store.provenancePath("test-echo", key).readText(),
        )
        assertEquals("$key.raw", stamp.rawArtifactFile)
        assertEquals(3L, stamp.responseBytes)
    }

    @Test
    fun `a second harvest of the same query does not touch the network`(@TempDir root: Path) = runBlocking {
        val fetcher = ScriptedFetcher()
        val harvester = EchoHarvester()
        harvester.harvest("moon", ctx(root, fetcher))
        val second = harvester.harvest("moon", ctx(root, fetcher))

        assertEquals(1, fetcher.calls)
        assertTrue(second.fromCache)
        assertEquals(listOf("alpha"), second.parsed.records)
    }

    /**
     * The digest is not decoration. A truncated download, a half-written file after a
     * kill, or an editor "fixing" line endings in a cached artifact must be an error, not
     * a quietly different reference value.
     */
    @Test
    fun `refuses a cached artifact whose bytes no longer match its digest`(@TempDir root: Path) = runBlocking {
        val harvester = EchoHarvester()
        harvester.harvest("moon", ctx(root, ScriptedFetcher()))

        val store = ArtifactStore(root)
        val key = harvester.cacheKey("moon")
        store.rawPath("test-echo", key).writeBytes("tampered\n".toByteArray())

        val e = assertThrows<CorruptCacheException> {
            store.load("test-echo", key, currentHarvesterVersion = 1, ttl = null)
        }
        assertEquals(ArtifactStore.sha256Hex("alpha\n".toByteArray()), e.expectedSha256)
        assertTrue(e.message!!.contains("re-harvest"))
    }

    @Test
    fun `treats an artifact past its TTL as absent`(@TempDir root: Path) = runBlocking {
        val harvester = EchoHarvester(cacheTtl = Duration.ofDays(3))
        val fetcher = ScriptedFetcher()
        harvester.harvest("moon", ctx(root, fetcher, now = fixedNow))

        harvester.harvest("moon", ctx(root, fetcher, now = fixedNow.plus(Duration.ofDays(2))))
        assertEquals(1, fetcher.calls)

        harvester.harvest("moon", ctx(root, fetcher, now = fixedNow.plus(Duration.ofDays(4))))
        assertEquals(2, fetcher.calls)
    }

    /**
     * A cached response answers the question the harvester asked at the time. If the
     * request shape has since changed, reusing it is comparing against the wrong query,
     * which is worse than having no cache at all.
     */
    @Test
    fun `ignores an artifact harvested by an older request shape`(@TempDir root: Path) = runBlocking {
        val v1 = EchoHarvester(harvesterVersion = 1)
        val fetcher = ScriptedFetcher()
        v1.harvest("moon", ctx(root, fetcher))

        val store = ArtifactStore(root)
        assertNull(store.load("test-echo", v1.cacheKey("moon"), currentHarvesterVersion = 2, ttl = null))
        // ...but it is still readable when we deliberately ask for anything we have.
        assertTrue(store.loadIgnoringExpiry("test-echo", v1.cacheKey("moon")) != null)
    }

    @Test
    fun `offline mode fails with an actionable message instead of inventing data`(@TempDir root: Path) = runBlocking {
        val fetcher = ScriptedFetcher()
        val e = assertThrows<OfflineException> {
            runBlocking { EchoHarvester().harvest("never-harvested", ctx(root, fetcher, offline = true)) }
        }
        assertEquals(0, fetcher.calls)
        assertTrue(e.message!!.contains("https://example.invalid/echo?q=never-harvested"), e.message)
        assertTrue(e.message!!.contains("--offline"), e.message)
    }

    /**
     * Serving a stale artifact is acceptable; serving one without saying so is not.
     */
    @Test
    fun `offline mode labels an expired artifact as stale`(@TempDir root: Path) = runBlocking {
        val harvester = EchoHarvester(cacheTtl = Duration.ofDays(1))
        harvester.harvest("moon", ctx(root, ScriptedFetcher(), now = fixedNow))

        val later = fixedNow.plus(Duration.ofDays(30))
        val result = harvester.harvest(
            "moon",
            ctx(root, ScriptedFetcher(), offline = true, now = later),
        )
        assertTrue(result.fromCache)
        assertTrue(
            result.parsed.report.warnings.any { it.contains("expired") && it.contains("2026-08-01T12:00:00Z") },
            result.parsed.report.warnings.toString(),
        )
    }

    @Test
    fun `a fetch failure propagates unless stale fallback was asked for`(@TempDir root: Path) = runBlocking {
        val harvester = EchoHarvester(cacheTtl = Duration.ofDays(1))
        harvester.harvest("moon", ctx(root, ScriptedFetcher(), now = fixedNow))

        val later = fixedNow.plus(Duration.ofDays(30))
        val down = ScriptedFetcher(failWith = SourceUnreachableException("https://example.invalid/echo", 3))

        assertThrows<SourceUnreachableException> {
            runBlocking { harvester.harvest("moon", ctx(root, down, now = later)) }
        }

        val fallback = harvester.harvest("moon", ctx(root, down, allowStale = true, now = later))
        assertTrue(fallback.fromCache)
        assertTrue(fallback.parsed.report.warnings.any { it.contains("Fetch failed") })
    }

    @Test
    fun `distinct queries never share a cache slot`() {
        val h = EchoHarvester()
        // Same sanitised prefix, different discriminator: the hash suffix must separate them.
        assertTrue(h.cacheKey("a/b") != h.cacheKey("a_b"))
        assertTrue(h.cacheKey("moon").startsWith("moon-"))
        // A cache key is used as a filename, so no separator may survive sanitising.
        val hostile = h.cacheKey("../../etc/passwd")
        assertFalse(hostile.contains("/"))
        assertFalse(hostile.contains("\\"))
    }

    private fun assertArrayEqualsBytes(expected: ByteArray, actual: ByteArray) =
        assertEquals(expected.toList(), actual.toList())
}
