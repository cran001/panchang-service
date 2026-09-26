package org.panchang.ephemeris

import kotlin.math.cos
import kotlin.math.sin

/**
 * Nutation and obliquity of the ecliptic — the single implementation for the whole service.
 *
 * The app engine this project replaces computed obliquity in three different places with
 * three different formulae, and the topocentric path silently dropped the nutation term
 * that the sunrise and sunset paths kept. There is exactly one implementation here and
 * everything else calls it.
 *
 * ## Series
 *
 * Nutation is the full IAU 1980 theory of nutation as printed in Meeus,
 * *Astronomical Algorithms* 2nd ed., **Table 22.A** — all 63 periodic terms, each with its
 * constant and its T-proportional part. Meeus states this gives Δψ and Δε to about 0.5″,
 * dominated by the omission of the planetary and non-rigid-Earth refinements of the later
 * IAU 2000A theory. The abridged 4-term version Meeus also gives (≈1″) is not used here.
 *
 * Argument polynomials are Meeus (22.x), the same fundamental arguments as Ch. 47 but
 * carried to the precision Ch. 22 prints.
 *
 * ## Obliquity
 *
 * Mean obliquity ε₀ uses Laskar's expansion, Meeus (22.3), in U = T/10 Julian *millennia*.
 * Meeus states its accuracy as 0.01″ over ±1000 years of J2000 and a few arcseconds over
 * ±10000 years. This is preferred over the shorter (22.2) because (22.2) degrades fast
 * outside a few centuries and the service is expected to answer for arbitrary dates.
 * Within a few centuries of J2000 the two agree to well under 0.01″, so switching does not
 * disturb agreement with Meeus' Ch. 22 worked example.
 *
 * True obliquity is ε = ε₀ + Δε.
 *
 * Pure, deterministic, thread-safe: all tables are immutable and nothing is cached.
 */
object Nutation {

    private const val DEG_TO_RAD = Math.PI / 180.0

    /** Arcseconds per degree. */
    private const val ARCSEC_PER_DEGREE = 3600.0

    /** Table 22.A coefficients are in units of 0.0001″; this converts a sum to degrees. */
    private const val UNITS_PER_DEGREE = 10000.0 * ARCSEC_PER_DEGREE

    /**
     * IAU 1980 nutation, Meeus Table 22.A.
     *
     * Row layout: `D, M, M', F, Ω, Δψ₀, Δψ₁, Δε₀, Δε₁` where the argument of each periodic
     * term is `D·D + M·M + M'·M' + F·F + Ω·Ω` and
     *
     *     Δψ term = (Δψ₀ + Δψ₁·T) · sin(argument)   in units of 0.0001″
     *     Δε term = (Δε₀ + Δε₁·T) · cos(argument)   in units of 0.0001″
     *
     * The T-proportional parts are tabulated in units of 0.1 × 0.0001″ in Meeus (he prints
     * e.g. "−174.2" against a −171996 constant); they are stored here already scaled to the
     * same 0.0001″ unit as the constants, i.e. −174.2 means −174.2 × 0.0001″ per century.
     *
     * Only the first ~20 rows matter at the 0.01″ level; the tail contributes under 0.001″
     * per term. Correctness of the leading terms is pinned by the Ch. 22 worked-example
     * test (1987 April 10.0 TD → Δψ = −3.788″, Δε = +9.443″).
     */
    private val TERMS: Array<DoubleArray> = arrayOf(
        //   D    M   M'    F    Ω     Δψ₀      Δψ₁      Δε₀     Δε₁
        doubleArrayOf(0.0, 0.0, 0.0, 0.0, 1.0, -171996.0, -174.2, 92025.0, 8.9),
        doubleArrayOf(-2.0, 0.0, 0.0, 2.0, 2.0, -13187.0, -1.6, 5736.0, -3.1),
        doubleArrayOf(0.0, 0.0, 0.0, 2.0, 2.0, -2274.0, -0.2, 977.0, -0.5),
        doubleArrayOf(0.0, 0.0, 0.0, 0.0, 2.0, 2062.0, 0.2, -895.0, 0.5),
        doubleArrayOf(0.0, 1.0, 0.0, 0.0, 0.0, 1426.0, -3.4, 54.0, -0.1),
        doubleArrayOf(0.0, 0.0, 1.0, 0.0, 0.0, 712.0, 0.1, -7.0, 0.0),
        doubleArrayOf(-2.0, 1.0, 0.0, 2.0, 2.0, -517.0, 1.2, 224.0, -0.6),
        doubleArrayOf(0.0, 0.0, 0.0, 2.0, 1.0, -386.0, -0.4, 200.0, 0.0),
        doubleArrayOf(0.0, 0.0, 1.0, 2.0, 2.0, -301.0, 0.0, 129.0, -0.1),
        doubleArrayOf(-2.0, -1.0, 0.0, 2.0, 2.0, 217.0, -0.5, -95.0, 0.3),
        doubleArrayOf(-2.0, 0.0, 1.0, 0.0, 0.0, -158.0, 0.0, 0.0, 0.0),
        doubleArrayOf(-2.0, 0.0, 0.0, 2.0, 1.0, 129.0, 0.1, -70.0, 0.0),
        doubleArrayOf(0.0, 0.0, -1.0, 2.0, 2.0, 123.0, 0.0, -53.0, 0.0),
        doubleArrayOf(2.0, 0.0, 0.0, 0.0, 0.0, 63.0, 0.0, 0.0, 0.0),
        doubleArrayOf(0.0, 0.0, 1.0, 0.0, 1.0, 63.0, 0.1, -33.0, 0.0),
        doubleArrayOf(2.0, 0.0, -1.0, 2.0, 2.0, -59.0, 0.0, 26.0, 0.0),
        doubleArrayOf(0.0, 0.0, -1.0, 0.0, 1.0, -58.0, -0.1, 32.0, 0.0),
        doubleArrayOf(0.0, 0.0, 1.0, 2.0, 1.0, -51.0, 0.0, 27.0, 0.0),
        doubleArrayOf(-2.0, 0.0, 2.0, 0.0, 0.0, 48.0, 0.0, 0.0, 0.0),
        doubleArrayOf(0.0, 0.0, -2.0, 2.0, 1.0, 46.0, 0.0, -24.0, 0.0),
        doubleArrayOf(2.0, 0.0, 0.0, 2.0, 2.0, -38.0, 0.0, 16.0, 0.0),
        doubleArrayOf(0.0, 0.0, 2.0, 2.0, 2.0, -31.0, 0.0, 13.0, 0.0),
        doubleArrayOf(0.0, 0.0, 2.0, 0.0, 0.0, 29.0, 0.0, 0.0, 0.0),
        doubleArrayOf(-2.0, 0.0, 1.0, 2.0, 2.0, 29.0, 0.0, -12.0, 0.0),
        doubleArrayOf(0.0, 0.0, 0.0, 2.0, 0.0, 26.0, 0.0, 0.0, 0.0),
        doubleArrayOf(-2.0, 0.0, 0.0, 2.0, 0.0, -22.0, 0.0, 0.0, 0.0),
        doubleArrayOf(0.0, 0.0, -1.0, 2.0, 1.0, 21.0, 0.0, -10.0, 0.0),
        doubleArrayOf(0.0, 2.0, 0.0, 0.0, 0.0, 17.0, -0.1, 0.0, 0.0),
        doubleArrayOf(2.0, 0.0, -1.0, 0.0, 1.0, 16.0, 0.0, -8.0, 0.0),
        doubleArrayOf(-2.0, 2.0, 0.0, 2.0, 2.0, -16.0, 0.1, 7.0, 0.0),
        doubleArrayOf(0.0, 1.0, 0.0, 0.0, 1.0, -15.0, 0.0, 9.0, 0.0),
        doubleArrayOf(-2.0, 0.0, 1.0, 0.0, 1.0, -13.0, 0.0, 7.0, 0.0),
        doubleArrayOf(0.0, -1.0, 0.0, 0.0, 1.0, -12.0, 0.0, 6.0, 0.0),
        doubleArrayOf(0.0, 0.0, 2.0, -2.0, 0.0, 11.0, 0.0, 0.0, 0.0),
        doubleArrayOf(2.0, 0.0, -1.0, 2.0, 1.0, -10.0, 0.0, 5.0, 0.0),
        doubleArrayOf(2.0, 0.0, 1.0, 2.0, 2.0, -8.0, 0.0, 3.0, 0.0),
        doubleArrayOf(0.0, 1.0, 0.0, 2.0, 2.0, 7.0, 0.0, -3.0, 0.0),
        doubleArrayOf(-2.0, 1.0, 1.0, 0.0, 0.0, -7.0, 0.0, 0.0, 0.0),
        doubleArrayOf(0.0, -1.0, 0.0, 2.0, 2.0, -7.0, 0.0, 3.0, 0.0),
        doubleArrayOf(2.0, 0.0, 0.0, 2.0, 1.0, -7.0, 0.0, 3.0, 0.0),
        doubleArrayOf(2.0, 0.0, 1.0, 0.0, 0.0, 6.0, 0.0, 0.0, 0.0),
        doubleArrayOf(-2.0, 0.0, 2.0, 2.0, 2.0, 6.0, 0.0, -3.0, 0.0),
        doubleArrayOf(-2.0, 0.0, 1.0, 2.0, 1.0, 6.0, 0.0, -3.0, 0.0),
        doubleArrayOf(2.0, 0.0, -2.0, 0.0, 1.0, -6.0, 0.0, 3.0, 0.0),
        doubleArrayOf(2.0, 0.0, 0.0, 0.0, 1.0, -6.0, 0.0, 3.0, 0.0),
        doubleArrayOf(0.0, -1.0, 1.0, 0.0, 0.0, 5.0, 0.0, 0.0, 0.0),
        doubleArrayOf(-2.0, -1.0, 0.0, 2.0, 1.0, -5.0, 0.0, 3.0, 0.0),
        doubleArrayOf(-2.0, 0.0, 0.0, 0.0, 1.0, -5.0, 0.0, 3.0, 0.0),
        doubleArrayOf(0.0, 0.0, 2.0, 2.0, 1.0, -5.0, 0.0, 3.0, 0.0),
        doubleArrayOf(-2.0, 0.0, 2.0, 0.0, 1.0, 4.0, 0.0, 0.0, 0.0),
        doubleArrayOf(-2.0, 1.0, 0.0, 2.0, 1.0, 4.0, 0.0, 0.0, 0.0),
        doubleArrayOf(0.0, 0.0, 1.0, -2.0, 0.0, 4.0, 0.0, 0.0, 0.0),
        doubleArrayOf(-1.0, 0.0, 1.0, 0.0, 0.0, -4.0, 0.0, 0.0, 0.0),
        doubleArrayOf(-2.0, 1.0, 0.0, 0.0, 0.0, -4.0, 0.0, 0.0, 0.0),
        doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, -4.0, 0.0, 0.0, 0.0),
        doubleArrayOf(0.0, 0.0, 1.0, 2.0, 0.0, 3.0, 0.0, 0.0, 0.0),
        doubleArrayOf(0.0, 0.0, -2.0, 2.0, 2.0, -3.0, 0.0, 0.0, 0.0),
        doubleArrayOf(-1.0, -1.0, 1.0, 0.0, 0.0, -3.0, 0.0, 0.0, 0.0),
        doubleArrayOf(0.0, 1.0, 1.0, 0.0, 0.0, -3.0, 0.0, 0.0, 0.0),
        doubleArrayOf(0.0, -1.0, 1.0, 2.0, 2.0, -3.0, 0.0, 0.0, 0.0),
        doubleArrayOf(2.0, -1.0, -1.0, 2.0, 2.0, -3.0, 0.0, 0.0, 0.0),
        doubleArrayOf(0.0, 0.0, 3.0, 2.0, 2.0, -3.0, 0.0, 0.0, 0.0),
        doubleArrayOf(2.0, -1.0, 0.0, 2.0, 2.0, -3.0, 0.0, 0.0, 0.0),
    )

    /**
     * Nutation in longitude (Δψ) and true obliquity (ε), both in **degrees**.
     *
     * This is what [Ephemeris.nutationAndObliquity] delegates to.
     */
    fun at(jdTt: Double): NutationObliquity {
        val t = TimeScale.centuriesSinceJ2000(jdTt)
        val (dPsi, dEps) = sums(t)
        return NutationObliquity(
            nutationLongitude = dPsi / UNITS_PER_DEGREE,
            trueObliquity = meanObliquityDegrees(t) + dEps / UNITS_PER_DEGREE,
        )
    }

    /** Nutation in longitude Δψ, degrees, at Julian centuries [t] of TT since J2000. */
    fun nutationLongitudeDegrees(t: Double): Double = sums(t).first / UNITS_PER_DEGREE

    /**
     * Nutation in obliquity Δε, degrees, at Julian centuries [t] of TT since J2000.
     *
     * Exposed separately from [at] because the Ch. 22 worked example publishes Δε on its
     * own and the test asserts against it directly.
     */
    fun nutationObliquityDegrees(t: Double): Double = sums(t).second / UNITS_PER_DEGREE

    /**
     * Mean obliquity of the ecliptic ε₀ in degrees — Laskar's expansion, Meeus (22.3).
     *
     * U is T/100 in Meeus' notation, i.e. Julian *centuries* divided by 100 = tens of
     * millennia. Meeus warns the expression must not be used beyond U = ±1.0
     * (10000 years from J2000); outside that range it diverges badly.
     */
    fun meanObliquityDegrees(t: Double): Double {
        val u = t / 100.0
        // 23° 26′ 21.448″ expressed in arcseconds.
        var arcsec = 84381.448
        arcsec += -4680.93 * u
        arcsec += -1.55 * pow(u, 2)
        arcsec += 1999.25 * pow(u, 3)
        arcsec += -51.38 * pow(u, 4)
        arcsec += -249.67 * pow(u, 5)
        arcsec += -39.05 * pow(u, 6)
        arcsec += 7.12 * pow(u, 7)
        arcsec += 27.87 * pow(u, 8)
        arcsec += 5.79 * pow(u, 9)
        arcsec += 2.45 * pow(u, 10)
        return arcsec / ARCSEC_PER_DEGREE
    }

    /** (Δψ, Δε) in units of 0.0001″, summed over Table 22.A. */
    private fun sums(t: Double): Pair<Double, Double> {
        // Meeus (22.x). These are the Ch. 22 forms, carried to the precision Meeus prints
        // there; they differ in the last digits from the Ch. 47 forms used for the Moon,
        // which is Meeus' own inconsistency and is immaterial at this level (< 0.0001″).
        val d = 297.85036 + 445267.111480 * t - 0.0019142 * t * t + t * t * t / 189474.0
        val m = 357.52772 + 35999.050340 * t - 0.0001603 * t * t - t * t * t / 300000.0
        val mp = 134.96298 + 477198.867398 * t + 0.0086972 * t * t + t * t * t / 56250.0
        val f = 93.27191 + 483202.017538 * t - 0.0036825 * t * t + t * t * t / 327270.0
        val om = 125.04452 - 1934.136261 * t + 0.0020708 * t * t + t * t * t / 450000.0

        val dr = d * DEG_TO_RAD
        val mr = m * DEG_TO_RAD
        val mpr = mp * DEG_TO_RAD
        val fr = f * DEG_TO_RAD
        val omr = om * DEG_TO_RAD

        var dPsi = 0.0
        var dEps = 0.0
        for (term in TERMS) {
            val arg = term[0] * dr + term[1] * mr + term[2] * mpr + term[3] * fr + term[4] * omr
            dPsi += (term[5] + term[6] * t) * sin(arg)
            dEps += (term[7] + term[8] * t) * cos(arg)
        }
        return dPsi to dEps
    }

    /** Integer power without `Math.pow`'s branch cost; the exponents here are tiny. */
    private fun pow(x: Double, n: Int): Double {
        var r = 1.0
        repeat(n) { r *= x }
        return r
    }
}
