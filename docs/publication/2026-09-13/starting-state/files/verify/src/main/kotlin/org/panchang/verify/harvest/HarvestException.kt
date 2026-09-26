package org.panchang.verify.harvest

/**
 * Base type for every way harvesting can fail.
 *
 * There is no "return empty list on failure" path anywhere in this module. A harvester
 * that cannot reach its source must fail loudly: the alternative is a conformance run
 * that reports zero discrepancies because it compared against nothing.
 */
sealed class HarvestException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/** The harvester was asked for data it does not hold and offline mode forbids fetching. */
class OfflineException(
    val sourceId: String,
    val cacheKey: String,
    val url: String,
) : HarvestException(
    "Offline mode: no usable cached artifact for $sourceId/$cacheKey. " +
        "Would have fetched $url. Re-run without --offline, or harvest this key first.",
)

/** A 4xx response. Never retried: the request itself is wrong, repeating it is rude. */
class ClientErrorException(
    val status: Int,
    val url: String,
    val bodyExcerpt: String,
) : HarvestException("HTTP $status from $url (not retried). Body excerpt: $bodyExcerpt")

/** A 5xx response or transport failure that survived every retry. */
class SourceUnreachableException(
    val url: String,
    val attempts: Int,
    cause: Throwable? = null,
    detail: String = "",
) : HarvestException(
    "Source unreachable after $attempts attempt(s): $url${if (detail.isEmpty()) "" else " ($detail)"}",
    cause,
)

/**
 * The response arrived but did not look like what we asked for.
 *
 * Thrown when a structural assertion fails — a missing `$$SOE` marker, a reference frame
 * that is not the one requested, a JSON error envelope. Distinct from an empty result:
 * an empty result may be legitimate, a structural mismatch never is.
 */
class ResponseShapeException(
    val sourceId: String,
    message: String,
) : HarvestException("[$sourceId] response did not match the expected shape: $message")

/** A cached artifact exists but its stored bytes no longer hash to its recorded digest. */
class CorruptCacheException(
    val cacheKey: String,
    val expectedSha256: String,
    val actualSha256: String,
) : HarvestException(
    "Cached artifact $cacheKey is corrupt: provenance records sha256=$expectedSha256 " +
        "but the stored bytes hash to $actualSha256. Delete it and re-harvest.",
)
