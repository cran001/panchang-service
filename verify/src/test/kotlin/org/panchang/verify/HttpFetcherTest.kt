package org.panchang.verify

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.panchang.verify.harvest.ClientErrorException
import org.panchang.verify.harvest.FakeTimeSource
import org.panchang.verify.harvest.HostRateLimiter
import org.panchang.verify.harvest.HttpFetcher
import org.panchang.verify.harvest.RequestSpec
import org.panchang.verify.harvest.RetryPolicy
import org.panchang.verify.harvest.SourceUnreachableException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * Exercises the retry rules against a loopback server rather than a real source.
 *
 * These are not tagged `network`: nothing leaves the machine. They are skipped rather
 * than failed if the environment forbids binding a loopback socket, because a sandbox
 * that blocks `bind` should not turn `./gradlew build` red.
 */
class HttpFetcherTest {

    private var server: HttpServer? = null
    private val requests = AtomicInteger()
    private val seenUserAgents = mutableListOf<String>()

    private fun start(handler: (HttpExchange) -> Unit): String {
        val s = try {
            HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        } catch (e: Exception) {
            assumeTrue(false, "cannot bind a loopback socket in this environment: ${e.message}")
            error("unreachable")
        }
        s.createContext("/") { exchange ->
            requests.incrementAndGet()
            seenUserAgents += exchange.requestHeaders.getFirst("User-Agent").orEmpty()
            try {
                handler(exchange)
            } finally {
                exchange.close()
            }
        }
        s.executor = null
        s.start()
        server = s
        return "http://${s.address.hostString}:${s.address.port}/probe"
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.write(bytes)
    }

    @BeforeEach
    fun reset() {
        requests.set(0)
        seenUserAgents.clear()
    }

    @AfterEach
    fun stop() {
        server?.stop(0)
        server = null
    }

    private fun fetcher(policy: RetryPolicy = RetryPolicy()) = HttpFetcher(
        client = HttpFetcher.defaultClient(
            requestTimeoutMillis = 5_000,
            connectTimeoutMillis = 2_000,
            socketTimeoutMillis = 5_000,
        ),
        // No courtesy delay against ourselves, and a virtual clock so backoff is free.
        rateLimiter = HostRateLimiter(defaultDelayMillis = 0, perHostDelayMillis = emptyMap(), timeSource = FakeTimeSource()),
        retryPolicy = policy,
        timeSource = FakeTimeSource(),
    )

    @Test
    fun `returns the body and status of a successful response`() = runBlocking {
        val url = start { respond(it, 200, "hello") }
        fetcher().use { f ->
            val r = f.fetch(RequestSpec("test", url))
            assertEquals(200, r.status)
            assertEquals("hello", r.body.decodeToString())
            assertTrue(r.contentType!!.startsWith("text/plain"))
            assertEquals(1, requests.get())
        }
    }

    @Test
    fun `sends the headers the request spec declares`() = runBlocking {
        val url = start { respond(it, 200, "ok") }
        fetcher().use { f ->
            f.fetch(RequestSpec("test", url, headers = mapOf("User-Agent" to HttpFetcher.PROJECT_USER_AGENT)))
        }
        assertEquals(HttpFetcher.PROJECT_USER_AGENT, seenUserAgents.single())
    }

    /**
     * The rule that matters most for being a tolerable client: a 4xx means our request is
     * wrong, so repeating it verbatim can only add load without ever succeeding. 429 is a
     * 4xx too — it means the configured courtesy gap is too small and a human should
     * widen it, not that the machine should push harder.
     */
    @Test
    fun `never retries a 4xx`() = runBlocking {
        val url = start { respond(it, 429, "slow down") }
        fetcher().use { f ->
            val e = assertThrows<ClientErrorException> {
                runBlocking { f.fetch(RequestSpec("test", url)) }
            }
            assertEquals(429, e.status)
            assertTrue(e.message!!.contains("slow down"))
        }
        assertEquals(1, requests.get())
    }

    @Test
    fun `retries a 5xx up to the attempt budget and then fails loudly`() = runBlocking {
        val url = start { respond(it, 503, "maintenance") }
        fetcher(RetryPolicy(maxAttempts = 3)).use { f ->
            val e = assertThrows<SourceUnreachableException> {
                runBlocking { f.fetch(RequestSpec("test", url)) }
            }
            assertEquals(3, e.attempts)
            assertTrue(e.message!!.contains("HTTP 503"), e.message)
        }
        assertEquals(3, requests.get())
    }

    @Test
    fun `recovers when a transient 5xx clears before the budget runs out`() = runBlocking {
        val url = start { exchange ->
            if (requests.get() < 2) respond(exchange, 500, "oops") else respond(exchange, 200, "recovered")
        }
        fetcher().use { f ->
            assertEquals("recovered", f.fetch(RequestSpec("test", url)).body.decodeToString())
        }
        assertEquals(2, requests.get())
    }

    /**
     * A harvest run must not be able to hang. An unreachable port is the cheap version of
     * a source that accepts a connection and never answers.
     */
    @Test
    fun `fails rather than hanging when the host does not answer`() = runBlocking {
        // Port 1 on loopback: reliably refused, never listening.
        val spec = RequestSpec("test", "http://127.0.0.1:1/probe")
        fetcher(RetryPolicy(maxAttempts = 2)).use { f ->
            val e = assertThrows<SourceUnreachableException> {
                runBlocking { f.fetch(spec) }
            }
            assertEquals(2, e.attempts)
        }
    }
}
