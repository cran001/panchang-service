package org.panchang.ephemeris

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

@DisplayName("TimeScale")
class TimeScaleTest {

    private val ephemeris = MeeusEphemeris()

    /**
     * The build forces `user.timezone=UTC`. Assert it, because every other test in this
     * module is only meaningful under that assumption and a silent change to the harness
     * should be caught here rather than as a mysterious off-by-hours failure elsewhere.
     *
     * Compared by offset rather than zone ID so that "UTC", "Z" and "Etc/UTC" all pass.
     */
    @Test
    fun `host default zone is UTC`() {
        val offset = ZoneId.systemDefault().rules.getOffset(Instant.EPOCH)
        assertEquals(
            ZoneOffset.UTC,
            offset,
            "tests must run with user.timezone=UTC; the default zone is ${ZoneId.systemDefault()}",
        )
    }

    /**
     * `jdUt(instant(x)) == x` exactly, for JDs that land on a whole millisecond.
     * [TimeScale.instant] rounds to the millisecond, so the round trip can only be exact
     * where the input is representable; every value here is chosen to be.
     */
    @Test
    fun `instant round trip is exact on millisecond boundaries`() {
        val exact = listOf(
            TimeScale.UNIX_EPOCH_JD,
            TimeScale.J2000,
            2448724.5,
            2451545.5,
            2460000.5,
            TimeScale.jdUtAtMidnight(2026, 8, 1),
            TimeScale.jdUtAtMidnight(1900, 1, 1),
            TimeScale.UNIX_EPOCH_JD + 0.25,
            TimeScale.UNIX_EPOCH_JD + 1.0 / 86400.0, // one whole second
        )
        for (jd in exact) {
            assertEquals(jd, TimeScale.jdUt(TimeScale.instant(jd)), 0.0, "round trip of $jd")
        }
    }

    /** The Unix epoch and J2000 must be exactly where the constants say they are. */
    @Test
    fun `anchor instants`() {
        assertEquals(Instant.EPOCH, TimeScale.instant(TimeScale.UNIX_EPOCH_JD))
        assertEquals(0L, TimeScale.epochMillis(TimeScale.UNIX_EPOCH_JD))
        // J2000.0 as a UT-based JD is 2000-01-01T12:00:00Z.
        assertEquals(
            Instant.parse("2000-01-01T12:00:00Z"),
            TimeScale.instant(TimeScale.J2000),
        )
        assertEquals(0.0, TimeScale.centuriesSinceJ2000(TimeScale.J2000), 0.0)
        assertEquals(1.0, TimeScale.centuriesSinceJ2000(TimeScale.J2000 + 36525.0), 0.0)
    }

    /** `jdUtAtMidnight` must agree with the `java.time` route, and be zone-explicit. */
    @Test
    fun `midnight julian day agrees with java_time`() {
        val dates = listOf(
            LocalDate.of(1582, 10, 15),
            LocalDate.of(1900, 1, 1),
            LocalDate.of(1992, 4, 12),
            LocalDate.of(2000, 3, 1),
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2100, 2, 28),
        )
        for (date in dates) {
            val viaJavaTime = TimeScale.jdUt(date.atStartOfDay(), ZoneOffset.UTC)
            assertEquals(viaJavaTime, TimeScale.jdUtAtMidnight(date), 0.0, "$date")
        }
    }

    /**
     * Civil-time conversion must depend only on the zone passed in, never on the host's.
     * The same wall-clock time in Kolkata and in UTC differ by exactly 5h30m of JD.
     */
    @Test
    fun `civil time conversion uses the supplied zone only`() {
        val wallClock = LocalDateTime.of(2026, 8, 1, 6, 0)
        val inUtc = TimeScale.jdUt(wallClock, ZoneOffset.UTC)
        val inKolkata = TimeScale.jdUt(wallClock, ZoneId.of("Asia/Kolkata"))
        // 1e-9 day (~86 µs): the ulp of a modern Julian Day as a Double is ~4.7e-10 day,
        // so a difference of two JDs cannot be asserted more tightly than that.
        assertEquals(5.5 / 24.0, inUtc - inKolkata, 1e-9)

        // And back out again.
        val zdt = TimeScale.zonedDateTime(inKolkata, ZoneId.of("Asia/Kolkata"))
        assertEquals(wallClock, zdt.toLocalDateTime())
    }

    /**
     * `toUt(toTt(x)) ≈ x`. Not exact: [TimeScale.toUt] evaluates ΔT at the TT argument
     * rather than iterating, which is deliberate and documented there. The residual is
     * dΔT/dt × ΔT ≈ (1 s/century) × (70 s), i.e. of order 2e-5 s — far below a microsecond
     * of day. 1e-9 day (86 µs) is the assertion.
     */
    @Test
    fun `TT round trip closes to well under a millisecond`() {
        val samples = listOf(
            TimeScale.jdUtAtMidnight(1700, 1, 1),
            TimeScale.jdUtAtMidnight(1900, 1, 1),
            TimeScale.jdUtAtMidnight(2000, 1, 1),
            TimeScale.jdUtAtMidnight(2026, 8, 1),
            TimeScale.jdUtAtMidnight(2100, 1, 1),
        )
        for (jdUt in samples) {
            val jdTt = TimeScale.toTt(jdUt, ephemeris)
            assertEquals(jdUt, TimeScale.toUt(jdTt, ephemeris), 1e-9, "round trip at $jdUt")

            // And the offset really is ΔT seconds. Tolerance 1e-9 day rather than exact:
            // a JD around 2.45e6 has an ulp of ~4.7e-10 day (~40 µs), so the subtraction
            // jdTt - jdUt cannot resolve the ~1e-4 day offset any more finely than that.
            // This is a property of Julian Days as Doubles, not of the conversion.
            assertEquals(ephemeris.deltaT(jdUt) / 86400.0, jdTt - jdUt, 1e-9)
        }

        // TT leads UT wherever ΔT is positive, which is everywhere except a window around
        // 1900 where ΔT dips slightly negative (the 1900-1920 branch starts at -2.79 s).
        // Asserting the sign of that dip guards the conversion's direction in both cases.
        val jd1900 = TimeScale.jdUtAtMidnight(1900, 1, 1)
        assertTrue(
            TimeScale.toTt(jd1900, ephemeris) < jd1900,
            "ΔT is negative around 1900, so TT must lag UT there",
        )
        val jd2026 = TimeScale.jdUtAtMidnight(2026, 8, 1)
        assertTrue(
            TimeScale.toTt(jd2026, ephemeris) > jd2026,
            "ΔT is ~70 s today, so TT must lead UT",
        )
    }

    @Test
    fun `normalizeDegrees maps into 0 to 360`() {
        assertEquals(0.0, TimeScale.normalizeDegrees(0.0), 0.0)
        assertEquals(0.0, TimeScale.normalizeDegrees(360.0), 0.0)
        assertEquals(0.0, TimeScale.normalizeDegrees(-360.0), 0.0)
        assertEquals(359.0, TimeScale.normalizeDegrees(-1.0), 1e-12)
        assertEquals(1.0, TimeScale.normalizeDegrees(721.0), 1e-12)
        assertEquals(180.0, TimeScale.normalizeDegrees(-180.0), 1e-12)

        for (x in listOf(-100000.0, -0.5, 0.0, 12.345, 359.999, 3600.0, 1e7)) {
            val n = TimeScale.normalizeDegrees(x)
            assertTrue(n >= 0.0 && n < 360.0, "normalizeDegrees($x) = $n")
        }
    }

    /**
     * `angleDifference` across the seam. The contract is a signed difference wrapped to
     * `(-180, +180]`, so exactly 180° apart resolves to **+180**, never −180, in both
     * directions — that asymmetry is what stops a bisection from oscillating at an
     * opposition boundary.
     */
    @Test
    fun `angleDifference wraps to the half open interval`() {
        assertEquals(20.0, TimeScale.angleDifference(10.0, 350.0), 1e-12)
        assertEquals(-20.0, TimeScale.angleDifference(350.0, 10.0), 1e-12)
        assertEquals(1.0, TimeScale.angleDifference(0.5, 359.5), 1e-12)
        assertEquals(-1.0, TimeScale.angleDifference(359.5, 0.5), 1e-12)
        assertEquals(0.0, TimeScale.angleDifference(123.0, 123.0), 0.0)
        assertEquals(0.0, TimeScale.angleDifference(0.0, 360.0), 0.0)

        // Exactly 180 degrees apart, both orders and across the seam.
        assertEquals(180.0, TimeScale.angleDifference(180.0, 0.0), 0.0)
        assertEquals(180.0, TimeScale.angleDifference(0.0, 180.0), 0.0)
        assertEquals(180.0, TimeScale.angleDifference(270.0, 90.0), 0.0)
        assertEquals(180.0, TimeScale.angleDifference(90.0, 270.0), 0.0)
        assertEquals(180.0, TimeScale.angleDifference(359.0, 179.0), 1e-12)

        // Result is always inside the stated interval, for un-normalised inputs too.
        var a = -720.0
        while (a <= 720.0) {
            var b = -720.0
            while (b <= 720.0) {
                val d = TimeScale.angleDifference(a, b)
                assertTrue(d > -180.0 && d <= 180.0 + 1e-12, "angleDifference($a, $b) = $d")
                b += 17.0
            }
            a += 23.0
        }
    }

    /**
     * `angleDifference` must be the exact negation of itself with arguments swapped,
     * except at the 180° case where both directions return +180 by construction.
     */
    @Test
    fun `angleDifference is antisymmetric away from the 180 degree case`() {
        var a = 0.0
        while (a < 360.0) {
            var b = 0.0
            while (b < 360.0) {
                val forward = TimeScale.angleDifference(a, b)
                val backward = TimeScale.angleDifference(b, a)
                if (abs(abs(forward) - 180.0) > 1e-9) {
                    assertEquals(-forward, backward, 1e-9, "antisymmetry at ($a, $b)")
                }
                b += 7.0
            }
            a += 11.0
        }
    }
}
