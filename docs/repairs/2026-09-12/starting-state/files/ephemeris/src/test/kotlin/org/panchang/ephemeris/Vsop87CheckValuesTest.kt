package org.panchang.ephemeris

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs

/**
 * The VSOP87D Earth series against **the theory authors' own published check values**.
 *
 * This is the strongest test in the module and the cheapest. `vendor/vsop87_vsop87.chk` is
 * Bretagnon & Francou's own tabulation of the result of substituting time into their
 * series, distributed with the coefficients precisely so that an implementation can be
 * checked. It is independent published ground truth — not self-consistency, not a
 * regression snapshot of our own output — and it needs no network, no JPL, and no
 * judgement about tolerances: an implementation either reproduces the authors' numbers or
 * it does not.
 *
 * Ten epochs at century intervals from 1099 to 2000 are asserted. The span is what gives
 * the test its power. Nearly every way of getting VSOP87 wrong is *exactly right at J2000*
 * and diverges away from it:
 *
 * - **T in centuries instead of millennia** — the single most common implementation error.
 *   T = 0 at J2000 either way, so a spot check at the epoch cannot see it. At 1900 it puts
 *   the Earth's longitude out by about 0.17 rad.
 * - A power-of-T block dropped or attached to the wrong power — invisible at T = 0 for
 *   every block above `*T**0`.
 * - Column offsets off by one, picking up part of the neighbouring field.
 *
 * The check values are copied into a test resource rather than read from `vendor/` so the
 * test has no dependency on a sibling directory that is not on the classpath; the copy
 * carries its own provenance header.
 */
@DisplayName("VSOP87D — authors' published check values")
class Vsop87CheckValuesTest {

    private class CheckValue(
        val jdTdb: Double,
        val longitudeRad: Double,
        val latitudeRad: Double,
        val radiusAu: Double,
    )

    private val checkValues: List<CheckValue> by lazy {
        val stream = javaClass.getResourceAsStream(
            "/org/panchang/ephemeris/vsop87d-earth-checkvalues.txt",
        ) ?: error("check-value resource missing")
        stream.bufferedReader(Charsets.US_ASCII).useLines { lines ->
            lines.map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { line ->
                    val f = line.split(",")
                    require(f.size == 4) { "malformed check value row: '$line'" }
                    CheckValue(f[0].toDouble(), f[1].toDouble(), f[2].toDouble(), f[3].toDouble())
                }
                .toList()
        }
    }

    /**
     * All ten published blocks, to 1e-9 rad (2e-4 arcsec) in longitude and latitude and
     * 1e-9 au (150 m) in the radius vector.
     *
     * Why not tighter: the check values are printed to ten decimal places, so they carry
     * ±5e-11 of their own rounding, and at the far epochs the longitude series sums to
     * about −5.7e3 rad before reduction modulo 2π, where double precision leaves ~1e-12.
     * 1e-9 is two orders of magnitude inside anything an implementation error could
     * produce and two orders outside the arithmetic noise floor.
     *
     * Why not looser: 1e-9 rad is 2e-4 arcsec, which is four orders of magnitude below the
     * ~3″ this module actually delivers. If this test passes, VSOP87 is not the error.
     */
    @Test
    fun `reproduces all ten published EARTH blocks`() {
        assertEquals(10, checkValues.size, "expected the 10 published VSOP87D EARTH blocks")

        var worstLongitude = 0.0
        var worstLatitude = 0.0
        var worstRadius = 0.0

        for (cv in checkValues) {
            val t = Vsop87.millenniaSinceJ2000(cv.jdTdb)

            // The published l is reduced to [0, 2pi); the raw series is not.
            val l = normalizeRadians(Vsop87.earthLongitudeRadians(t))
            val b = Vsop87.earthLatitudeRadians(t)
            val r = Vsop87.earthRadiusAu(t)

            val dl = abs(signedRadianDifference(l, cv.longitudeRad))
            val db = abs(b - cv.latitudeRad)
            val dr = abs(r - cv.radiusAu)

            assertTrue(dl < 1e-9, "JD ${cv.jdTdb}: l off by $dl rad (got $l, published ${cv.longitudeRad})")
            assertTrue(db < 1e-9, "JD ${cv.jdTdb}: b off by $db rad (got $b, published ${cv.latitudeRad})")
            assertTrue(dr < 1e-9, "JD ${cv.jdTdb}: r off by $dr au (got $r, published ${cv.radiusAu})")

            worstLongitude = maxOf(worstLongitude, dl)
            worstLatitude = maxOf(worstLatitude, db)
            worstRadius = maxOf(worstRadius, dr)
        }

        println(
            "VSOP87D EARTH vs published check values, worst of 10 epochs: " +
                "l %.3e rad  b %.3e rad  r %.3e au".format(worstLongitude, worstLatitude, worstRadius),
        )
    }

    /**
     * The J2000 block, spelled out, because it is the one value a reader can check by eye
     * against `vendor/PROVENANCE.md` and against Meeus.
     */
    @Test
    fun `J2000 block matches the value quoted in PROVENANCE`() {
        val t = Vsop87.millenniaSinceJ2000(2451545.0)
        assertEquals(1.7519238681, normalizeRadians(Vsop87.earthLongitudeRadians(t)), 1e-9, "l")
        assertEquals(-0.0000039656, Vsop87.earthLatitudeRadians(t), 1e-9, "b")
        assertEquals(0.9833276819, Vsop87.earthRadiusAu(t), 1e-9, "r")
    }

    /**
     * Using Julian **centuries** instead of millennia is the error this suite exists to
     * catch, so assert directly that the wrong unit would in fact fail — otherwise the test
     * above proves only that *some* unit works.
     *
     * At 1900 (T = −1 millennium, or −10 centuries) the two differ by ~0.17 rad = 9°.
     */
    @Test
    fun `the centuries-instead-of-millennia error would be caught`() {
        val jd = 2415020.0
        val correct = normalizeRadians(Vsop87.earthLongitudeRadians(Vsop87.millenniaSinceJ2000(jd)))
        val wrong = normalizeRadians(
            Vsop87.earthLongitudeRadians(TimeScale.centuriesSinceJ2000(jd)),
        )
        val difference = abs(signedRadianDifference(correct, wrong))
        assertTrue(
            difference > 0.1,
            "centuries and millennia gave nearly the same answer ($difference rad); the " +
                "check-value assertions above would then not be testing the time unit",
        )
        assertEquals(1.7391225563, correct, 1e-9, "millennia is the correct unit")
    }

    /**
     * The table that was loaded is the table that was shipped: every block's term count
     * matches its own header, and the totals match the published series lengths.
     *
     * [Vsop87] already fails loudly at load time if a block is short, so this is really an
     * assertion that the *right file* is on the classpath — a truncated or substituted
     * resource would change these counts.
     */
    @Test
    fun `all published terms are loaded`() {
        assertEquals(listOf(559, 341, 142, 22, 11, 5), Vsop87.termCountsByPower(1), "L")
        assertEquals(listOf(184, 99, 49, 11, 5, 0), Vsop87.termCountsByPower(2), "B")
        assertEquals(listOf(526, 292, 139, 27, 10, 3), Vsop87.termCountsByPower(3), "R")
        assertEquals(2425, Vsop87.termCount, "total periodic terms")
    }

    private fun normalizeRadians(x: Double): Double {
        val r = x % (2.0 * PI)
        return if (r < 0) r + 2.0 * PI else r
    }

    private fun signedRadianDifference(a: Double, b: Double): Double {
        var d = (a - b) % (2.0 * PI)
        if (d > PI) d -= 2.0 * PI
        if (d <= -PI) d += 2.0 * PI
        return d
    }
}
