package org.panchang.ephemeris

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Properties the implementation must hold regardless of the numbers: purity, thread
 * safety, and mean motion consistent with the actual sky.
 *
 * The mean-motion tests are weak as accuracy checks — they would pass with a moderately
 * broken series — but they are strong against the two errors that would otherwise be
 * invisible: a wrong time argument (feeding centuries where days are wanted, or the
 * reverse) and a longitude that wraps or reverses when it should not.
 */
@DisplayName("MeeusEphemeris properties")
class MeeusEphemerisPropertiesTest {

    private val ephemeris = MeeusEphemeris()

    /** A spread of dates: past, present, future, and both Meeus example epochs. */
    private val sampleJd = listOf(
        2299160.5,   // 1582-10-15, the Gregorian reform
        2415020.5,   // 1900-01-01
        2446895.5,   // Example 22.a
        2448724.5,   // Example 47.a
        2448908.5,   // Example 25.a
        TimeScale.J2000,
        2461000.125, // an arbitrary non-midnight instant in 2025
        2488069.5,   // 2100-01-01
    )

    @Test
    fun `identity and claimed accuracy`() {
        assertEquals("meeus", ephemeris.id)
        assertTrue(
            ephemeris.claimedAccuracyArcsec > 0.0 && ephemeris.claimedAccuracyArcsec < 180.0,
            "the claim must be positive and better than the 0.05 degree the app engine claimed",
        )
    }

    @Test
    fun `longitudes are normalised and latitude is bounded`() {
        var jd = 2451545.0
        repeat(2000) {
            val sun = ephemeris.sunLongitude(jd)
            val moon = ephemeris.moonLongitude(jd)
            assertTrue(sun >= 0.0 && sun < 360.0, "sun longitude $sun out of range at $jd")
            assertTrue(moon >= 0.0 && moon < 360.0, "moon longitude $moon out of range at $jd")

            val lat = ephemeris.moonLatitude(jd)
            assertTrue(abs(lat) < 6.0, "moon latitude $lat out of range at $jd")

            val dist = ephemeris.moonDistanceKm(jd)
            assertTrue(dist in 355_000.0..407_000.0, "moon distance $dist km out of range at $jd")

            val sunDist = ephemeris.sunDistanceKm(jd)
            assertTrue(
                sunDist in 147_000_000.0..152_500_000.0,
                "sun distance $sunDist km out of range at $jd",
            )
            jd += 0.37
        }
    }

    /**
     * Determinism: identical input must produce bit-identical output, forever. Compared on
     * raw bits rather than with a tolerance, because "close enough" is not the property
     * being tested — frozen regression snapshots depend on exact reproducibility.
     */
    @Test
    fun `same input yields bit-identical output`() {
        for (jd in sampleJd) {
            assertBitsEqual(ephemeris.sunLongitude(jd), ephemeris.sunLongitude(jd), "sunLongitude")
            assertBitsEqual(ephemeris.moonLongitude(jd), ephemeris.moonLongitude(jd), "moonLongitude")
            assertBitsEqual(ephemeris.moonLatitude(jd), ephemeris.moonLatitude(jd), "moonLatitude")
            assertBitsEqual(ephemeris.moonDistanceKm(jd), ephemeris.moonDistanceKm(jd), "moonDistanceKm")
            assertBitsEqual(ephemeris.sunDistanceKm(jd), ephemeris.sunDistanceKm(jd), "sunDistanceKm")
            assertBitsEqual(ephemeris.deltaT(jd), ephemeris.deltaT(jd), "deltaT")
            assertEquals(ephemeris.nutationAndObliquity(jd), ephemeris.nutationAndObliquity(jd))
        }
    }

    /** A second instance must be indistinguishable from the first — no per-instance state. */
    @Test
    fun `separate instances agree bit for bit`() {
        val other = MeeusEphemeris()
        for (jd in sampleJd) {
            assertBitsEqual(ephemeris.sunLongitude(jd), other.sunLongitude(jd), "sunLongitude")
            assertBitsEqual(ephemeris.moonLongitude(jd), other.moonLongitude(jd), "moonLongitude")
            assertBitsEqual(ephemeris.moonLatitude(jd), other.moonLatitude(jd), "moonLatitude")
        }
    }

    /**
     * Thread safety. Eight threads hammer a shared instance over the same inputs; every
     * result must match the single-threaded reference bit for bit. A mutable field or a
     * lazily-initialised cache anywhere in the implementation would show up here.
     */
    @Test
    fun `concurrent callers agree with the single-threaded result`() {
        val jds = (0 until 400).map { TimeScale.J2000 + it * 0.913 }
        val reference = jds.map { jd ->
            doubleArrayOf(
                ephemeris.sunLongitude(jd),
                ephemeris.moonLongitude(jd),
                ephemeris.moonLatitude(jd),
                ephemeris.moonDistanceKm(jd),
                ephemeris.nutationAndObliquity(jd).trueObliquity,
            )
        }

        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val tasks = (0 until threads).map {
                Callable {
                    jds.map { jd ->
                        doubleArrayOf(
                            ephemeris.sunLongitude(jd),
                            ephemeris.moonLongitude(jd),
                            ephemeris.moonLatitude(jd),
                            ephemeris.moonDistanceKm(jd),
                            ephemeris.nutationAndObliquity(jd).trueObliquity,
                        )
                    }
                }
            }
            for (future in pool.invokeAll(tasks)) {
                val result = future.get(60, TimeUnit.SECONDS)
                for (i in jds.indices) {
                    for (k in reference[i].indices) {
                        assertBitsEqual(
                            reference[i][k],
                            result[i][k],
                            "value $k at jd ${jds[i]} differed between threads",
                        )
                    }
                }
            }
        } finally {
            pool.shutdownNow()
        }
    }

    /**
     * The Sun's apparent longitude advances ~0.9856°/day (360° / 365.2422 d tropical year).
     *
     * Accumulated with [TimeScale.angleDifference] so the 360°/0° seam is handled; summing
     * raw normalised differences would produce a −359° step once a year and is precisely
     * the bug this module exists to avoid.
     */
    @Test
    fun `sun longitude advances about 0_9856 degrees per day`() {
        val start = TimeScale.J2000
        val days = 365
        var total = 0.0
        var previous = ephemeris.sunLongitude(start)
        for (i in 1..days) {
            val current = ephemeris.sunLongitude(start + i)
            val step = TimeScale.angleDifference(current, previous)
            assertTrue(
                step in 0.94..1.03,
                "solar daily motion $step deg on day $i is outside the annual range " +
                    "(0.953 at aphelion to 1.019 at perihelion)",
            )
            total += step
            previous = current
        }
        assertEquals(0.98565, total / days, 0.001, "mean solar motion, deg/day")
    }

    /**
     * Moon − Sun elongation increases ~12.1907°/day: 360° per mean synodic month of
     * 29.530588 d.
     *
     * Measured over 1000 days rather than one month. An individual synodic month ranges
     * from about 29.27 to 29.83 days, so a single month can be 3–4° away from a 360° sweep
     * and asserting the mean rate over one month proves nothing. Over 1000 days (≈34
     * lunations) the anomalistic and evectional variations average out and the residual is
     * small, which lets the tolerance be tight enough to be worth something: 0.02°/day
     * here, i.e. 0.16% of the rate.
     *
     * Also asserts strict monotonicity at every step — the elongation never goes backwards,
     * since the Moon's motion (11.8°–15.4°/day) always exceeds the Sun's ~1°/day.
     */
    @Test
    fun `moon-sun elongation increases about 12_19 degrees per day`() {
        val start = TimeScale.J2000
        val days = 1000.0
        val steps = 4000
        val dt = days / steps

        var total = 0.0
        var previous = elongation(start)
        for (i in 1..steps) {
            val current = elongation(start + i * dt)
            val step = TimeScale.angleDifference(current, previous)
            assertTrue(step > 0.0, "elongation went backwards at step $i (by $step deg)")
            total += step
            previous = current
        }

        assertEquals(12.1907, total / days, 0.02, "mean elongation rate, deg/day")

        // One mean synodic month, with the slack a single lunation genuinely needs. This
        // is the direct statement of "360 degrees per synodic month" and is kept despite
        // the loose tolerance because it is the property a reader will look for.
        var monthTotal = 0.0
        previous = elongation(start)
        for (i in 1..300) {
            val current = elongation(start + i * 29.530588 / 300.0)
            monthTotal += TimeScale.angleDifference(current, previous)
            previous = current
        }
        assertEquals(360.0, monthTotal, 6.0, "elongation swept over one mean synodic month")
    }

    /**
     * The Moon's daily motion in longitude must stay inside its real bounds. This is a
     * sharper check than the mean rate: a single mis-transcribed large coefficient shifts
     * the extremes long before it shifts the mean.
     */
    @Test
    fun `moon daily motion stays within its physical bounds`() {
        val start = TimeScale.J2000
        var previous = ephemeris.moonLongitude(start)
        for (i in 1..2000) {
            val current = ephemeris.moonLongitude(start + i)
            val step = TimeScale.angleDifference(current, previous)
            assertTrue(
                step in 11.5..15.5,
                "lunar daily motion $step deg/day on day $i is outside the observed " +
                    "range of about 11.8 to 15.4",
            )
            previous = current
        }
    }

    /**
     * ΔT is not optional. Evaluating the series at a UT-based JD instead of the TT one, as
     * the app engine did, must be detectable: at present-day ΔT of ~70 s the Moon moves
     * ~35″. Confirm the implementation is actually sensitive to the argument at that level,
     * so nobody can "fix" a failing test by dropping the conversion.
     */
    @Test
    fun `a 70 second shift in the argument moves the moon by tens of arcseconds`() {
        val jdUt = TimeScale.jdUtAtMidnight(2026, 8, 1)
        val jdTt = TimeScale.toTt(jdUt, ephemeris)
        val shiftArcsec =
            abs(TimeScale.angleDifference(ephemeris.moonLongitude(jdTt), ephemeris.moonLongitude(jdUt))) * 3600.0
        assertTrue(
            shiftArcsec > 25.0,
            "ignoring ΔT should displace the Moon by ~35 arcsec; measured $shiftArcsec",
        )
        assertTrue(
            shiftArcsec > ephemeris.claimedAccuracyArcsec / 2.0,
            "the ΔT error the app engine carried is comparable to this class's whole error budget",
        )
    }

    private fun elongation(jdTt: Double): Double =
        TimeScale.normalizeDegrees(ephemeris.moonLongitude(jdTt) - ephemeris.sunLongitude(jdTt))

    private fun assertBitsEqual(expected: Double, actual: Double, what: String) {
        assertEquals(
            java.lang.Double.doubleToLongBits(expected),
            java.lang.Double.doubleToLongBits(actual),
            "$what: expected $expected but got $actual (bit-level comparison)",
        )
    }
}
