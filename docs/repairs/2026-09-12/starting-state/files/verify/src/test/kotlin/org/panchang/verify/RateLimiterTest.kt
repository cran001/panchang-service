package org.panchang.verify

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.panchang.verify.harvest.FakeTimeSource
import org.panchang.verify.harvest.HostRateLimiter
import org.panchang.verify.harvest.RetryPolicy
import java.net.URI

/**
 * The limiter is tested against a virtual clock. Testing it against the real one would
 * mean a suite whose runtime grows with how considerate we are to the sources, which is
 * a direct incentive to shorten the gaps.
 */
class RateLimiterTest {

    @Test
    fun `does not delay the first request to a host`() = runBlocking {
        val clock = FakeTimeSource()
        val limiter = HostRateLimiter(timeSource = clock)
        limiter.withHost("example.test") { }
        assertEquals(emptyList<Long>(), clock.sleeps)
    }

    @Test
    fun `waits the full gap when two requests are back to back`() = runBlocking {
        val clock = FakeTimeSource()
        val limiter = HostRateLimiter(timeSource = clock)
        limiter.withHost("example.test") { }
        limiter.withHost("example.test") { }
        assertEquals(listOf(1_000L), clock.sleeps)
    }

    @Test
    fun `waits only the remainder when time has already passed`() = runBlocking {
        val clock = FakeTimeSource()
        val limiter = HostRateLimiter(timeSource = clock)
        limiter.withHost("example.test") { }
        clock.advance(400)
        limiter.withHost("example.test") { }
        assertEquals(listOf(600L), clock.sleeps)
    }

    @Test
    fun `does not wait at all when the gap has already elapsed`() = runBlocking {
        val clock = FakeTimeSource()
        val limiter = HostRateLimiter(timeSource = clock)
        limiter.withHost("example.test") { }
        clock.advance(5_000)
        limiter.withHost("example.test") { }
        assertEquals(emptyList<Long>(), clock.sleeps)
    }

    /**
     * Measured from completion, not from dispatch. A source that takes 30 seconds to
     * answer must not then be hit again immediately just because the clock ran during
     * its own response.
     */
    @Test
    fun `measures the gap from the end of the previous request`() = runBlocking {
        val clock = FakeTimeSource()
        val limiter = HostRateLimiter(timeSource = clock)
        limiter.withHost("example.test") { clock.advance(30_000) }
        limiter.withHost("example.test") { }
        assertEquals(listOf(1_000L), clock.sleeps)
    }

    @Test
    fun `throttles hosts independently`() = runBlocking {
        val clock = FakeTimeSource()
        val limiter = HostRateLimiter(timeSource = clock)
        limiter.withHost("a.test") { }
        limiter.withHost("b.test") { }
        limiter.withHost("c.test") { }
        assertEquals(emptyList<Long>(), clock.sleeps)
    }

    @Test
    fun `treats host case as insignificant`() = runBlocking {
        val clock = FakeTimeSource()
        val limiter = HostRateLimiter(timeSource = clock)
        limiter.withHost("Example.TEST") { }
        limiter.withHost("example.test") { }
        assertEquals(listOf(1_000L), clock.sleeps)
    }

    /**
     * The floor is one request per second for anything we do not own, and the community
     * and commercial sites are deliberately slower. If someone lowers these, this test is
     * the place where they have to say so out loud.
     */
    @Test
    fun `applies the configured per-host courtesy gaps`() {
        val limiter = HostRateLimiter()
        assertEquals(1_000L, limiter.delayForHost("ssd.jpl.nasa.gov"))
        assertEquals(1_000L, limiter.delayForHost("aa.usno.navy.mil"))
        assertEquals(3_000L, limiter.delayForHost("www.vaisnavacalendar.info"))
        assertEquals(3_000L, limiter.delayForHost("www.iskconmumbai.com"))
        assertEquals(3_000L, limiter.delayForHost("www.purebhakti.com"))
        assertEquals(10_000L, limiter.delayForHost("www.drikpanchang.com"))
        // Anything unconfigured still gets the one-per-second floor.
        assertEquals(1_000L, limiter.delayForHost("some.new.source.example"))
    }

    /**
     * A harvester pointed at a host with no configured gap silently falls back to the
     * default. That is safe, but for the sources we actually use we want the gap to be a
     * deliberate entry rather than an accident of the default.
     */
    @Test
    fun `every host this module actually contacts has an explicit gap`() {
        val hosts = listOf(
            "https://ssd.jpl.nasa.gov/api/horizons.api",
            "https://aa.usno.navy.mil/api/rstt/oneday",
            "https://www.vaisnavacalendar.info/calendars",
            "https://www.iskconmumbai.com",
            "https://www.purebhakti.com",
            "https://www.drikpanchang.com",
        ).map { URI(it).host }

        val configured = HostRateLimiter.DEFAULT_PER_HOST
        hosts.forEach { host ->
            assertTrue(configured.containsKey(host), "no explicit rate limit configured for $host")
            assertTrue(
                configured.getValue(host) >= HostRateLimiter.DEFAULT_DELAY_MILLIS,
                "$host is configured faster than the one-request-per-second floor",
            )
        }
    }
}

class RetryPolicyTest {

    @Test
    fun `backs off exponentially and then stops growing`() {
        val p = RetryPolicy()
        assertEquals(2_000L, p.backoffFor(1))
        assertEquals(6_000L, p.backoffFor(2))
        assertEquals(18_000L, p.backoffFor(3))
        assertEquals(30_000L, p.backoffFor(4))
        assertEquals(30_000L, p.backoffFor(10))
    }

    /**
     * Three attempts, not thirty. A harvest run is a considered act performed by a human
     * who can rerun it; grinding away at an unavailable public service is not.
     */
    @Test
    fun `keeps the attempt budget small by default`() {
        assertEquals(3, RetryPolicy().maxAttempts)
    }
}
