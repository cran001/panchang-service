package org.panchang.verify.harvest

import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Everything a harvest run needs that is not specific to one source.
 *
 * [offline] is offline-*first*, not offline-only: a fresh cached artifact is always
 * preferred over a fetch. [offline] only changes what happens when there is no fresh
 * artifact — fetch, or fail explicitly.
 */
class HarvestContext(
    val store: ArtifactStore,
    val fetcher: Fetcher,
    val offline: Boolean = false,
    /**
     * When a fetch fails, may we fall back to an expired cached artifact?
     *
     * Default false. Stale real data is not fabricated data, but using it without the
     * operator asking for it means a conformance report that silently describes last
     * month's ephemeris. If enabled, the fallback is recorded as a warning on the
     * [ParseReport], never silently.
     */
    val allowStaleOnFetchFailure: Boolean = false,
    val now: () -> Instant = Instant::now,
) {
    companion object {
        val ISO_UTC: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT
        fun defaultCacheRoot(projectDir: Path): Path = projectDir.resolve("cache")
        fun defaultGoldenRoot(projectDir: Path): Path = projectDir.resolve("golden")
    }
}

/**
 * Fetches, caches with provenance, and parses one kind of reference data.
 *
 * Subclasses deal only in raw harvested data: no type from `:core` or `:ephemeris`
 * appears here. Harvesters describe what a source said, not what we believe. Turning
 * a source's claim into an assertion about our engine is the conformance harness's job,
 * and keeping that boundary sharp is what stops a harvester from being quietly tuned
 * until it agrees with us.
 */
abstract class Harvester<Q, R> {

    abstract val sourceId: String

    /**
     * Bump whenever the request shape changes (parameters, quantities, endpoint).
     * Cached artifacts stamped with an older version are ignored, because they answer a
     * question we no longer ask.
     */
    abstract val harvesterVersion: Int

    /** How long a cached artifact stays fresh. Null means "never expires". */
    open val cacheTtl: Duration? = null

    abstract fun requestSpec(query: Q): RequestSpec

    /** Human-readable, filesystem-safe key. Must be injective over [Q]. */
    abstract fun cacheKey(query: Q): String

    /**
     * Parses raw bytes into records.
     *
     * Implementations must throw [ResponseShapeException] when the response is not the
     * shape they asked for, and must report every input they could not interpret via
     * [ParsedArtifact.report]. Returning a short list without saying so is forbidden.
     */
    abstract fun parse(bytes: ByteArray, provenance: Provenance): ParsedArtifact<R>

    suspend fun harvest(query: Q, ctx: HarvestContext): HarvestResult<R> {
        val spec = requestSpec(query)
        val key = cacheKey(query)

        ctx.store.load(sourceId, key, harvesterVersion, cacheTtl, ctx.now())?.let { (prov, bytes) ->
            return HarvestResult(prov, fromCache = true, raw = bytes, parsed = parse(bytes, prov))
        }

        if (ctx.offline) {
            val stale = ctx.store.loadIgnoringExpiry(sourceId, key)
                ?: throw OfflineException(sourceId, key, spec.url)
            val (prov, bytes) = stale
            val parsed = parse(bytes, prov)
            return HarvestResult(
                prov,
                fromCache = true,
                raw = bytes,
                parsed = parsed.copy(
                    // Parenthesised deliberately: `list + "a" + "b"` appends two elements
                    // rather than one concatenated string, which shreds the message.
                    report = parsed.report.copy(
                        warnings = parsed.report.warnings + (
                            "Offline mode: served an expired cached artifact retrieved at " +
                                "${prov.retrievedAtUtc} (harvesterVersion=${prov.harvesterVersion}, " +
                                "current=$harvesterVersion)."
                            ),
                    ),
                ),
            )
        }

        val response = try {
            ctx.fetcher.fetch(spec)
        } catch (e: SourceUnreachableException) {
            if (!ctx.allowStaleOnFetchFailure) throw e
            val stale = ctx.store.loadIgnoringExpiry(sourceId, key) ?: throw e
            val (prov, bytes) = stale
            val parsed = parse(bytes, prov)
            return HarvestResult(
                prov,
                fromCache = true,
                raw = bytes,
                parsed = parsed.copy(
                    report = parsed.report.copy(
                        warnings = parsed.report.warnings + (
                            "Fetch failed (${e.message}); fell back to an expired cached " +
                                "artifact retrieved at ${prov.retrievedAtUtc}."
                            ),
                    ),
                ),
            )
        }

        val provenance = ctx.store.store(
            Provenance(
                sourceId = sourceId,
                url = spec.url,
                method = spec.method,
                requestParameters = spec.parameterMap(),
                requestHeaders = spec.headers,
                retrievedAtUtc = HarvestContext.ISO_UTC.format(ctx.now()),
                httpStatus = response.status,
                responseContentType = response.contentType,
                responseBytes = response.body.size.toLong(),
                responseSha256 = ArtifactStore.sha256Hex(response.body),
                rawArtifactFile = "$key.raw",
                harvesterVersion = harvesterVersion,
                notes = spec.notes,
            ),
            response.body,
        )
        return HarvestResult(
            provenance,
            fromCache = false,
            raw = response.body,
            parsed = parse(response.body, provenance),
        )
    }
}
