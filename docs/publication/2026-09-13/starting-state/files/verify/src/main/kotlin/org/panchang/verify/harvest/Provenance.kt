package org.panchang.verify.harvest

import kotlinx.serialization.Serializable

/**
 * The provenance stamp that accompanies every cached artifact.
 *
 * Every accuracy claim this project makes is downstream of this record. If a stamp is
 * missing or incomplete, the artifact it describes is not evidence — it is an
 * unattributed number, and unattributed numbers are how a calendar quietly goes wrong.
 *
 * Deliberately verbose: we would rather store 400 redundant bytes per artifact than
 * discover in two years that we cannot tell which query produced a value.
 */
@Serializable
data class Provenance(
    /** Stable harvester identifier, e.g. "jpl-horizons". Used as the cache namespace. */
    val sourceId: String,
    /** The full URL that was actually requested, after parameter encoding. */
    val url: String,
    /** HTTP method. Recorded because a future source may need POST. */
    val method: String,
    /**
     * The exact request parameters, before encoding. Kept separately from [url] so a
     * re-fetch can be reconstructed without re-parsing a query string, and so a
     * parameter that was silently dropped by the URL builder is visible by its absence.
     */
    val requestParameters: Map<String, String>,
    /**
     * Request headers we set ourselves. Never contains credentials: none of these
     * sources require a key and this project does not carry one.
     */
    val requestHeaders: Map<String, String>,
    /** UTC instant of retrieval, ISO-8601 (e.g. "2026-08-01T13:24:05.123Z"). */
    val retrievedAtUtc: String,
    val httpStatus: Int,
    val responseContentType: String? = null,
    val responseBytes: Long,
    /** Lowercase hex SHA-256 of the raw response body exactly as received. */
    val responseSha256: String,
    /** Cache-relative filename of the stored raw body. */
    val rawArtifactFile: String,
    /**
     * Bumped whenever a harvester's request shape changes. A cached artifact whose
     * [harvesterVersion] differs from the current one is treated as stale even if its
     * TTL has not elapsed, because it may answer a different question than we now ask.
     */
    val harvesterVersion: Int,
    /** Free-form notes, e.g. redistribution restrictions. */
    val notes: List<String> = emptyList(),
)

/** A line, element, or field the parser saw but could not interpret. */
@Serializable
data class Unparsed(
    /** Where it was, e.g. "line 47" or "dpTableRow[3]". */
    val location: String,
    /** The offending text, truncated. */
    val text: String,
    val reason: String,
)

/**
 * What a parse run produced *and* what it failed to produce.
 *
 * A parser that returns only successes is indistinguishable from a parser that is
 * silently dropping half its input. Callers are expected to look at [unparsed]; the CLI
 * prints it and the conformance harness should refuse to promote a golden file whose
 * unparsed list is non-empty and unreviewed.
 */
@Serializable
data class ParseReport(
    val recordCount: Int,
    val unparsed: List<Unparsed> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    val clean: Boolean get() = unparsed.isEmpty() && warnings.isEmpty()
}

/** Parsed records plus the report describing how completely they were recovered. */
data class ParsedArtifact<R>(
    val records: List<R>,
    val report: ParseReport,
)

/** A harvest outcome: the evidence, where it came from, and how well it was read. */
data class HarvestResult<R>(
    val provenance: Provenance,
    val fromCache: Boolean,
    val raw: ByteArray,
    val parsed: ParsedArtifact<R>,
) {
    // ByteArray in a data class: equals/hashCode would be identity-based and misleading.
    // Provenance.responseSha256 is the meaningful identity, so compare on that.
    override fun equals(other: Any?): Boolean =
        other is HarvestResult<*> && other.provenance == provenance && other.parsed == parsed

    override fun hashCode(): Int = provenance.hashCode() * 31 + parsed.hashCode()
}
