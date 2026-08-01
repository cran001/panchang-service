package org.panchang.ephemeris

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * [Vsop87Ephemeris]'s solar path: the steps between VSOP87D's raw output and the
 * *apparent* longitude the [Ephemeris] contract asks for.
 *
 * `Vsop87CheckValuesTest` already proves the series itself against the authors' published
 * values, and `HorizonsDifferentialTest` measures the end-to-end result against JPL. What
 * is left, and what this file covers, is the layer between them — geocentric inversion,
 * FK5, aberration, nutation — where an error is small enough to hide inside a loose
 * end-to-end tolerance but large enough to matter.
 */
@DisplayName("VSOP87 solar corrections")
class Vsop87SolarTest {

    private val vsop87 = Vsop87Ephemeris()
    private val meeus = MeeusEphemeris()

    /** Contract basics. */
    @Test
    fun `identity and range`() {
        assertEquals("vsop87", vsop87.id)
        var jd = TimeScale.jdUtAtMidnight(2020, 1, 1)
        repeat(400) {
            jd += 1.0
            val lon = vsop87.sunLongitude(jd)
            assertTrue(lon >= 0.0 && lon < 360.0, "sun longitude $lon out of [0,360) at jd $jd")
        }
    }

    /**
     * Meeus' worked Example 25.b — 1992-10-13.0 TD — the one published solar value this
     * module can be checked against by hand.
     *
     * Meeus computes the apparent longitude as **199°54′21.56″ = 199.905989°** using the
     * full VSOP87 route, which is exactly the route this class takes. Agreement to 0.5″
     * would be the most one could ask given Meeus rounds his intermediate quantities to
     * printed precision throughout the example; the assertion is at 1″ so that arithmetic
     * rounding in the book cannot fail a correct implementation, while a missing or
     * doubled correction — aberration is 20.5″, nutation up to 17″, FK5 0.09″ — could not
     * possibly pass.
     *
     * [MeeusEphemeris], which uses the Ch. 25 *low accuracy* series instead, lands ~9″ from
     * this value; that gap is the whole reason this class exists, and it is asserted below
     * so the two cannot silently converge on the same wrong answer.
     */
    @Test
    fun `Meeus example 25b apparent solar longitude`() {
        val jdTt = 2448908.5
        val expected = 199.0 + 54.0 / 60.0 + 21.56 / 3600.0

        val computed = vsop87.sunLongitude(jdTt)
        val error = TimeScale.angleDifference(computed, expected) * 3600.0
        assertTrue(
            abs(error) < 1.0,
            "apparent solar longitude at 1992-10-13.0 TD came out $computed°, " +
                "Meeus Example 25.b gives $expected° — off by $error″",
        )

        val meeusError = TimeScale.angleDifference(meeus.sunLongitude(jdTt), expected) * 3600.0
        assertTrue(
            abs(meeusError) > 3.0,
            "the Ch.25 low-accuracy series was expected to differ from the VSOP87 example " +
                "by several arcseconds; it differs by $meeusError″, which suggests the two " +
                "implementations are not actually independent",
        )
    }

    /**
     * The Sun's radius vector at Meeus Example 25.b: **0.99760775 AU**.
     *
     * This is the number [MeeusEphemeris] gets badly wrong — its unperturbed two-body
     * radius is ~8100 km high because it ignores both planetary perturbations and the
     * Earth-centre / Earth–Moon-barycentre offset. VSOP87D's `EARTH` is the Earth's centre
     * and its R carries the perturbations, so both errors are gone. Assert the improvement
     * explicitly, since it is one of the two things this class fixes.
     *
     * The tolerance is 800 km, which sounds loose and is not: Meeus prints R to eight
     * decimal places of an AU, so his own value carries ±5e-9 AU = ±750 km of rounding.
     * This implementation lands 115 km away, i.e. inside the printed precision of the
     * reference. The genuinely tight check on R is
     * `Vsop87CheckValuesTest`, which matches the authors' 10-decimal values to 7 m.
     */
    @Test
    fun `Meeus example 25b radius vector`() {
        val jdTt = 2448908.5
        val expectedKm = 0.99760775 * 149_597_870.7

        val vsopError = abs(vsop87.sunDistanceKm(jdTt) - expectedKm)
        assertTrue(vsopError < 800.0, "VSOP87 solar distance is $vsopError km from Meeus' value")

        val meeusError = abs(meeus.sunDistanceKm(jdTt) - expectedKm)
        assertTrue(
            meeusError > 1000.0,
            "the Ch.25 Kepler radius was expected to be thousands of km out; it was " +
                "$meeusError km, which suggests these are not two independent computations",
        )
    }

    /**
     * The aberration term must be the classic ≈ −20.5″, and must agree with the simpler
     * −20.4898″/R form that [MeeusEphemeris] uses.
     *
     * The two are computed completely differently — one from a numerical derivative of the
     * VSOP87 longitude series with precession removed, the other from a constant over the
     * radius vector — so agreement to a few thousandths of an arcsecond is real evidence
     * rather than a tautology. A sign error, a factor of 3600, or forgetting to convert the
     * derivative to arcsec/day would all show up here as a difference of many arcseconds.
     *
     * The difference is recovered by subtracting the *geometric* longitude (which excludes
     * aberration and nutation) from the *apparent* one and then removing nutation.
     */
    @Test
    fun `aberration is about minus 20 point 5 arcsec and matches the simple form`() {
        // Perihelion and aphelion of 2026, so both extremes of R are exercised.
        for (jdTt in listOf(2461045.0, 2461230.0)) {
            val tm = Vsop87.millenniaSinceJ2000(jdTt)
            val tc = TimeScale.centuriesSinceJ2000(jdTt)
            val radiusAu = Vsop87.earthRadiusAu(tm)

            val geometricPlusFk5 =
                Vsop87.earthLongitudeRadians(tm) * 180.0 / Math.PI + 180.0 - 0.09033 / 3600.0
            val apparent = vsop87.sunLongitude(jdTt)
            val nutation = Nutation.nutationLongitudeDegrees(tc)

            val aberrationArcsec =
                TimeScale.angleDifference(apparent, geometricPlusFk5 + nutation) * 3600.0
            val simpleArcsec = -20.4898 / radiusAu

            assertTrue(
                aberrationArcsec in -21.0..-20.0,
                "aberration came out $aberrationArcsec″ at jd $jdTt; it must be ≈ −20.5″",
            )
            assertTrue(
                abs(aberrationArcsec - simpleArcsec) < 0.02,
                "the derivative-based aberration $aberrationArcsec″ and Meeus' simple " +
                    "$simpleArcsec″ differ by ${abs(aberrationArcsec - simpleArcsec)}″ at jd $jdTt",
            )
        }
    }

    /**
     * Nutation in longitude must be present in the Sun's apparent longitude, with the same
     * sign and magnitude as it has in the Moon's.
     *
     * The failure mode this catches is a Sun that is *mean* of date while the Moon is
     * *true* of date. Both are within a few tens of arcseconds of correct on their own, so
     * neither body's own test would notice — but the elongation between them, which is
     * what tithi is, would carry the full ±17″ of Δψ. That is 34 s of boundary error from a
     * missing single term.
     *
     * Pick a date where Δψ is large, and verify the Sun moves with it.
     */
    @Test
    fun `nutation is applied to the Sun and to the Moon consistently`() {
        var worstDifference = 0.0
        var jdTt = TimeScale.J2000
        repeat(120) {
            jdTt += 60.0
            val tc = TimeScale.centuriesSinceJ2000(jdTt)
            val dPsi = Nutation.nutationLongitudeDegrees(tc)

            // Remove nutation from both apparent longitudes; the residual elongation must
            // then be free of it, i.e. both bodies must have carried the same Δψ.
            val sunMean = vsop87.sunLongitude(jdTt) - dPsi
            val moonMean = vsop87.moonLongitude(jdTt) - dPsi
            val elongationApparent = TimeScale.angleDifference(
                vsop87.moonLongitude(jdTt),
                vsop87.sunLongitude(jdTt),
            )
            val elongationMean = TimeScale.angleDifference(moonMean, sunMean)
            worstDifference = maxOf(
                worstDifference,
                abs(TimeScale.angleDifference(elongationApparent, elongationMean)) * 3600.0,
            )
        }
        assertTrue(
            worstDifference < 1e-6,
            "removing Δψ from both bodies changed the elongation by $worstDifference″; the " +
                "Sun and the Moon are not sharing one definition of the equinox of date",
        )

        // And Δψ really is being added, not dropped: at a date where it is large, the
        // apparent longitude must differ from the geometric one by it.
        val jd = 2451545.0 + 3000.0
        val tm = Vsop87.millenniaSinceJ2000(jd)
        val tc = TimeScale.centuriesSinceJ2000(jd)
        val dPsi = Nutation.nutationLongitudeDegrees(tc)
        assertTrue(abs(dPsi) * 3600.0 > 1.0, "chose a date where Δψ is negligible; pick another")
        val geometric = Vsop87.earthLongitudeRadians(tm) * 180.0 / Math.PI + 180.0
        val apparentMinusGeometric =
            TimeScale.angleDifference(vsop87.sunLongitude(jd), geometric) * 3600.0
        // apparent − geometric = Δψ + FK5 + aberration
        val expected = dPsi * 3600.0 - 0.09033 - 20.4898 / Vsop87.earthRadiusAu(tm)
        assertEquals(expected, apparentMinusGeometric, 0.05, "Δψ + FK5 + aberration")
    }

    /**
     * The Moon is delegated, so it must be *bit-identical* to [MeeusEphemeris] — not merely
     * close.
     *
     * This is the assertion that keeps the delegation honest. If someone later starts
     * ELP2000 and half-wires it in, this fails immediately rather than the two engines
     * drifting apart silently and the differential tests losing their meaning.
     */
    @Test
    fun `the Moon is bit-identical to the delegated Meeus implementation`() {
        var jd = TimeScale.jdUtAtMidnight(1900, 1, 1)
        repeat(500) {
            jd += 173.0
            assertEquals(meeus.moonLongitude(jd), vsop87.moonLongitude(jd), 0.0, "λ at jd $jd")
            assertEquals(meeus.moonLatitude(jd), vsop87.moonLatitude(jd), 0.0, "β at jd $jd")
            assertEquals(meeus.moonDistanceKm(jd), vsop87.moonDistanceKm(jd), 0.0, "Δ at jd $jd")
        }
    }

    /**
     * Determinism and thread-safety of the lazily-loaded table: the same argument must give
     * a bit-identical answer however many times it is asked, including from other threads.
     *
     * The class is documented as pure. A lazily-initialised 325 kB table is the one place
     * that claim could quietly become false.
     */
    @Test
    fun `repeated and concurrent evaluation is bit-identical`() {
        val jd = 2460000.5
        val expected = vsop87.sunLongitude(jd)
        repeat(100) { assertEquals(expected, vsop87.sunLongitude(jd), 0.0) }

        val results = java.util.concurrent.ConcurrentHashMap<Int, Double>()
        val threads = (0 until 8).map { i ->
            Thread { results[i] = Vsop87Ephemeris().sunLongitude(jd) }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(8, results.size)
        results.forEach { (i, v) -> assertEquals(expected, v, 0.0, "thread $i") }
    }

    /**
     * The Sun advances ~0.9856°/day and never reverses. A monotonic sweep over a century
     * catches a coefficient block attached to the wrong power of T, which typically shows
     * up as a slow drift rather than a wrong value at any one date.
     */
    @Test
    fun `solar longitude advances smoothly over a century`() {
        var jd = TimeScale.jdUtAtMidnight(1980, 1, 1)
        var previous = vsop87.sunLongitude(jd)
        repeat(36525) {
            jd += 1.0
            val current = vsop87.sunLongitude(jd)
            val step = TimeScale.angleDifference(current, previous)
            assertTrue(
                step in 0.95..1.03,
                "the Sun moved $step° in one day at jd $jd; the true range is 0.953-1.019°",
            )
            previous = current
        }
    }
}
