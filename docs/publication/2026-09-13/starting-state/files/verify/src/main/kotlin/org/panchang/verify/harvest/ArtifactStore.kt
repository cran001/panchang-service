package org.panchang.verify.harvest

import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * On-disk cache of raw responses and their provenance stamps.
 *
 * Layout, under the cache root (default `verify/cache/`):
 * ```
 * <sourceId>/<cacheKey>.raw              raw response bytes, byte-for-byte as received
 * <sourceId>/<cacheKey>.provenance.json  the Provenance stamp
 * ```
 *
 * The raw bytes are kept deliberately. Parsers are the part of this module most likely
 * to be wrong, and a parser bug found in six months must be fixable by re-parsing what
 * we already have, not by hammering NASA and a volunteer's shared host all over again.
 * Parsed forms are therefore *not* cached: they are cheap to recompute and expensive to
 * have stale.
 */
class ArtifactStore(val root: Path) {

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun rawPath(sourceId: String, cacheKey: String): Path =
        root.resolve(sourceId).resolve("$cacheKey.raw")

    fun provenancePath(sourceId: String, cacheKey: String): Path =
        root.resolve(sourceId).resolve("$cacheKey.provenance.json")

    fun has(sourceId: String, cacheKey: String): Boolean =
        rawPath(sourceId, cacheKey).exists() && provenancePath(sourceId, cacheKey).exists()

    /**
     * Loads a cached artifact, verifying its digest.
     *
     * Returns null when absent, when the recorded [Provenance.harvesterVersion] predates
     * [currentHarvesterVersion] (the request shape changed, so the answer may be to a
     * different question), or when [ttl] has elapsed. Throws [CorruptCacheException] if
     * the bytes are present but do not match their recorded hash — a silent mismatch is
     * exactly the failure mode this stamp exists to catch.
     */
    fun load(
        sourceId: String,
        cacheKey: String,
        currentHarvesterVersion: Int,
        ttl: Duration?,
        now: Instant = Instant.now(),
    ): Pair<Provenance, ByteArray>? {
        if (!has(sourceId, cacheKey)) return null
        val provenance = json.decodeFromString(
            Provenance.serializer(),
            provenancePath(sourceId, cacheKey).readText(),
        )
        if (provenance.harvesterVersion < currentHarvesterVersion) return null

        val bytes = rawPath(sourceId, cacheKey).readBytes()
        val actual = sha256Hex(bytes)
        if (actual != provenance.responseSha256) {
            throw CorruptCacheException("$sourceId/$cacheKey", provenance.responseSha256, actual)
        }
        if (ttl != null) {
            val retrieved = Instant.parse(provenance.retrievedAtUtc)
            if (retrieved.plus(ttl).isBefore(now)) return null
        }
        return provenance to bytes
    }

    /**
     * Loads a cached artifact regardless of TTL or harvester version.
     *
     * Used by offline mode: when the network is unavailable, stale evidence that is
     * honestly labelled stale beats no evidence, provided the caller surfaces the
     * retrieval timestamp. The digest check still applies.
     */
    fun loadIgnoringExpiry(sourceId: String, cacheKey: String): Pair<Provenance, ByteArray>? =
        load(sourceId, cacheKey, currentHarvesterVersion = Int.MIN_VALUE, ttl = null)

    fun store(provenance: Provenance, bytes: ByteArray): Provenance {
        val dir = root.resolve(provenance.sourceId)
        dir.createDirectories()
        val cacheKey = provenance.rawArtifactFile.removeSuffix(".raw")
        val stamped = provenance.copy(
            responseBytes = bytes.size.toLong(),
            responseSha256 = sha256Hex(bytes),
        )
        Files.write(rawPath(provenance.sourceId, cacheKey), bytes)
        provenancePath(provenance.sourceId, cacheKey)
            .writeText(json.encodeToString(Provenance.serializer(), stamped))
        return stamped
    }

    companion object {
        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }

        /**
         * Reduces an arbitrary request descriptor to a filesystem-safe cache key.
         * Keeps the readable part so a human browsing `verify/cache/` can tell what is
         * there, and appends a hash so distinct requests never collide.
         */
        fun cacheKey(readable: String, discriminator: String): String {
            val safe = readable.map { c ->
                if (c.isLetterOrDigit() || c == '-' || c == '_' || c == '.') c else '_'
            }.joinToString("").take(80)
            val hash = sha256Hex(discriminator.toByteArray()).take(12)
            return "$safe-$hash"
        }
    }
}
