package org.panchang.verify.harvest

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * The passage of time, injected.
 *
 * The rate limiter's whole job is to wait, so testing it against the real clock means a
 * test suite that is slow and flaky in exactly proportion to how conservative the limits
 * are. [FakeTimeSource] lets us assert the wait *decisions* without waiting.
 */
interface TimeSource {
    fun nowMillis(): Long
    suspend fun sleep(millis: Long)
}

object SystemTimeSource : TimeSource {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override suspend fun sleep(millis: Long) {
        if (millis > 0) delay(millis)
    }
}

/** Records every sleep and advances a virtual clock instead of blocking. */
class FakeTimeSource(private var now: Long = 0L) : TimeSource {
    val sleeps = mutableListOf<Long>()

    override fun nowMillis(): Long = now

    override suspend fun sleep(millis: Long) {
        sleeps += millis
        if (millis > 0) now += millis
    }

    /** Advance without recording a sleep, to simulate wall-clock passing between calls. */
    fun advance(millis: Long) {
        now += millis
    }
}

/**
 * Serialises requests per host and enforces a minimum gap between them.
 *
 * Per-host rather than global because the sources have very different tolerances: NASA
 * and USNO run public APIs sized for programmatic use, while the community calendar
 * sites are small volunteer-run installations where an aggressive scraper is a real
 * imposition. DrikPanchang is a commercial site we have no agreement with, so it gets
 * the largest gap of all.
 *
 * Serialisation is per host, so two different hosts still proceed concurrently.
 */
class HostRateLimiter(
    private val defaultDelayMillis: Long = DEFAULT_DELAY_MILLIS,
    private val perHostDelayMillis: Map<String, Long> = DEFAULT_PER_HOST,
    private val timeSource: TimeSource = SystemTimeSource,
) {
    private val mutexes = ConcurrentHashMap<String, Mutex>()
    private val lastCompletionMillis = ConcurrentHashMap<String, Long>()

    fun delayForHost(host: String): Long =
        perHostDelayMillis[host.lowercase()] ?: defaultDelayMillis

    /**
     * Runs [block] holding this host's turnstile, after waiting out any remaining gap.
     * The gap is measured from the *completion* of the previous request, not its start,
     * so a slow response never causes a burst once it finally lands.
     */
    suspend fun <T> withHost(host: String, block: suspend () -> T): T {
        val key = host.lowercase()
        val mutex = mutexes.computeIfAbsent(key) { Mutex() }
        return mutex.withLock {
            val required = delayForHost(key)
            val last = lastCompletionMillis[key]
            if (last != null) {
                val elapsed = timeSource.nowMillis() - last
                val remaining = required - elapsed
                if (remaining > 0) timeSource.sleep(remaining)
            }
            try {
                block()
            } finally {
                lastCompletionMillis[key] = timeSource.nowMillis()
            }
        }
    }

    companion object {
        /** One request per second is the floor for anything we do not own. */
        const val DEFAULT_DELAY_MILLIS = 1_000L

        val DEFAULT_PER_HOST: Map<String, Long> = mapOf(
            // Public government APIs built for programmatic access.
            "ssd.jpl.nasa.gov" to 1_000L,
            "aa.usno.navy.mil" to 1_000L,
            // Volunteer-run community sites on shared hosting. Be a good guest.
            "www.vaisnavacalendar.info" to 3_000L,
            "vaisnavacalendar.info" to 3_000L,
            "www.iskconmumbai.com" to 3_000L,
            "iskconmumbai.com" to 3_000L,
            "www.purebhakti.com" to 3_000L,
            "purebhakti.com" to 3_000L,
            // Commercial site, comparison target only, no agreement with us.
            "www.drikpanchang.com" to 10_000L,
            "drikpanchang.com" to 10_000L,
        )
    }
}
