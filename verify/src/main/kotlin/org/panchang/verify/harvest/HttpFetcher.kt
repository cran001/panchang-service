package org.panchang.verify.harvest

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpMethod
import java.io.IOException

/** Performs a single request, with rate limiting, timeouts and bounded retries. */
interface Fetcher {
    suspend fun fetch(spec: RequestSpec): FetchedResponse
}

class HttpFetcher(
    private val client: HttpClient,
    private val rateLimiter: HostRateLimiter,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val timeSource: TimeSource = SystemTimeSource,
) : Fetcher, AutoCloseable {

    override suspend fun fetch(spec: RequestSpec): FetchedResponse =
        rateLimiter.withHost(spec.host) { fetchWithRetries(spec) }

    private suspend fun fetchWithRetries(spec: RequestSpec): FetchedResponse {
        var lastFailure: Throwable? = null
        var lastDetail = ""
        for (attempt in 1..retryPolicy.maxAttempts) {
            val response = try {
                val r = client.request(spec.url) {
                    method = HttpMethod.parse(spec.method)
                    spec.headers.forEach { (k, v) -> header(k, v) }
                }
                FetchedResponse(
                    status = r.status.value,
                    contentType = r.headers["Content-Type"],
                    body = r.readBytes(),
                )
            } catch (e: IOException) {
                lastFailure = e
                lastDetail = e.message ?: e::class.simpleName.orEmpty()
                if (attempt < retryPolicy.maxAttempts) {
                    timeSource.sleep(retryPolicy.backoffFor(attempt))
                    continue
                }
                break
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                // Ktor's HttpTimeout surfaces as a cancellation; treat as transient.
                lastFailure = e
                lastDetail = "timeout"
                if (attempt < retryPolicy.maxAttempts) {
                    timeSource.sleep(retryPolicy.backoffFor(attempt))
                    continue
                }
                break
            } catch (e: Exception) {
                // Engines do not agree on what a transport failure looks like: CIO wraps
                // some connect and TLS errors in types that are not IOException. Treating
                // an unrecognised transport failure as transient is safe because the
                // attempt budget is small and the eventual failure is still loud, with the
                // original exception attached as the cause.
                lastFailure = e
                lastDetail = e.message ?: e::class.qualifiedName.orEmpty()
                if (attempt < retryPolicy.maxAttempts) {
                    timeSource.sleep(retryPolicy.backoffFor(attempt))
                    continue
                }
                break
            }

            when {
                response.status in 200..299 -> return response
                response.status in 400..499 -> throw ClientErrorException(
                    response.status,
                    spec.url,
                    response.body.decodeToString().take(400),
                )
                else -> {
                    lastDetail = "HTTP ${response.status}"
                    if (attempt < retryPolicy.maxAttempts) {
                        timeSource.sleep(retryPolicy.backoffFor(attempt))
                    }
                }
            }
        }
        throw SourceUnreachableException(
            spec.url,
            retryPolicy.maxAttempts,
            lastFailure,
            lastDetail,
        )
    }

    override fun close() = client.close()

    companion object {
        /**
         * Identify ourselves honestly. A scraper that hides behind a browser string and
         * offers no way to be contacted is one an operator can only respond to by
         * blocking, and we would deserve it.
         */
        const val PROJECT_USER_AGENT =
            "panchang-service-verify/0.1 (ground-truth harvester; contact via project repository)"

        /**
         * Timeouts are mandatory, not tuning. An unbounded socket read is how a harvest
         * run turns into a process that never exits and a developer who kills the build.
         */
        fun defaultClient(
            requestTimeoutMillis: Long = 60_000,
            connectTimeoutMillis: Long = 15_000,
            socketTimeoutMillis: Long = 60_000,
        ): HttpClient = HttpClient(CIO) {
            expectSuccess = false
            followRedirects = true
            install(HttpTimeout) {
                this.requestTimeoutMillis = requestTimeoutMillis
                this.connectTimeoutMillis = connectTimeoutMillis
                this.socketTimeoutMillis = socketTimeoutMillis
            }
        }
    }
}
