package org.panchang.verify.harvest

import java.net.URI

/**
 * A fully-described outbound request.
 *
 * Parameters are an ordered list rather than a map so the URL we build is byte-stable
 * across runs: a provenance stamp whose URL reorders itself between JVMs is useless for
 * reproducing a fetch.
 */
data class RequestSpec(
    val sourceId: String,
    val baseUrl: String,
    val parameters: List<Pair<String, String>> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val method: String = "GET",
    /** Recorded verbatim into the provenance stamp, e.g. redistribution restrictions. */
    val notes: List<String> = emptyList(),
) {
    val host: String get() = URI(baseUrl).host ?: error("no host in baseUrl: $baseUrl")

    /** The exact URL that will be requested, with the exact encoding that will be used. */
    val url: String
        get() = if (parameters.isEmpty()) {
            baseUrl
        } else {
            baseUrl + "?" + parameters.joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }
        }

    fun parameterMap(): Map<String, String> = parameters.toMap()

    companion object {
        private const val UNRESERVED =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

        /**
         * RFC 3986 percent-encoding of everything outside the unreserved set.
         *
         * Hand-rolled rather than `URLEncoder`, which is an
         * `application/x-www-form-urlencoded` encoder and turns a space into `+`. That is
         * wrong inside a query value for Horizons' `STEP_SIZE='1 d'`, and the failure is
         * silent: the API accepts it and returns a different step.
         */
        fun encode(s: String): String = buildString {
            for (b in s.toByteArray(Charsets.UTF_8)) {
                val c = b.toInt().toChar()
                if (c in UNRESERVED) append(c) else append("%%%02X".format(b))
            }
        }
    }
}

/** What actually came back. Body is kept as bytes; charset decisions belong to parsers. */
data class FetchedResponse(
    val status: Int,
    val contentType: String?,
    val body: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is FetchedResponse && other.status == status && other.body.contentEquals(body)

    override fun hashCode(): Int = status * 31 + body.contentHashCode()
}

/**
 * Retry shape for transient failures.
 *
 * 4xx is never retried: the request is wrong, and repeating a wrong request at a public
 * service is both useless and impolite. 429 is a 4xx and is therefore also not retried —
 * it means our configured [HostRateLimiter] gap is too small and a human should widen it,
 * not that the machine should keep pushing.
 */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialBackoffMillis: Long = 2_000L,
    val backoffMultiplier: Double = 3.0,
    val maxBackoffMillis: Long = 30_000L,
) {
    fun backoffFor(attempt: Int): Long {
        val raw = initialBackoffMillis * Math.pow(backoffMultiplier, (attempt - 1).toDouble())
        return raw.toLong().coerceAtMost(maxBackoffMillis)
    }
}
