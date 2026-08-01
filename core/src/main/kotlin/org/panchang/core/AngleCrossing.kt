package org.panchang.core

import org.panchang.ephemeris.TimeScale
import kotlin.math.abs
import kotlin.math.min

/**
 * Root finder for "when does this angle cross that value".
 *
 * Every panchanga element is defined by an angle reaching a multiple of some step: a tithi is
 * the Moon−Sun elongation crossing a multiple of 12°, a nakshatra the Moon's sidereal
 * longitude crossing a multiple of 13°20′, a yoga the sum of the two crossing the same. The
 * *instants* of those crossings — not merely which element happens to be running at some
 * sample time — are what every downstream rule is written in terms of, so this solver is the
 * load-bearing part of the module.
 *
 * ## Wrap safety
 *
 * The sign test is [TimeScale.angleDifference], never a comparison of raw normalised angles.
 * Comparing normalised angles is how a bisection silently diverges when the bracket straddles
 * the 360°/0° seam — which is exactly the last tithi of every lunar month, the case that
 * matters most. The seam is therefore not a special case here; it is the ordinary case.
 *
 * ## Precision is not accuracy
 *
 * `firstCrossing` and `lastCrossingBefore` return a time whose distance from the true root of
 * the *supplied function* is at most `toleranceDays / 2`, and the default tolerance is one
 * second. That is a statement about the solver and nothing else. The accuracy of the answer as
 * a physical instant is set entirely by the [org.panchang.ephemeris.Ephemeris] behind the
 * function: an ephemeris with 1″ of longitude error puts a tithi boundary about 2 seconds
 * wrong, and 30″ puts it a minute wrong, however tightly this converges. Do not quote solver
 * tolerance as service accuracy.
 *
 * ## Preconditions the caller owns
 *
 * The supplied angle function must be continuous and, over the search interval, advance by less
 * than 180° per [scanStepDays]. If it advances more, a scan step can straddle the seam and the
 * sign test becomes meaningless. All callers in this module use step sizes derived from the
 * known maximum rates of the Sun and Moon, with large margins.
 */
object AngleCrossing {

    /** One second expressed in days — the default bracket tolerance. */
    const val ONE_SECOND_DAYS: Double = 1.0 / 86_400.0

    /**
     * Hard cap on bisection steps. A one-day bracket needs 17 to reach one second and 47 to
     * reach the resolution of a double at that magnitude, so this can only be hit by a
     * degenerate tolerance. It exists so a bad argument fails fast rather than spinning.
     */
    private const val MAX_BISECTIONS = 80

    /** Hard cap on scan steps, for the same reason. */
    private const val MAX_SCAN_STEPS = 100_000

    /**
     * The earliest time in `[fromJdUt, toJdUt]` at which [angleAt] crosses [targetDeg] going
     * forward (i.e. the signed offset from the target passes from negative to non-negative).
     *
     * Returns `null` when no such crossing is bracketed in the interval. Callers must handle
     * that; this never widens the interval on its own, and never loops looking for one.
     *
     * @param angleAt angle in degrees as a function of Julian Day (UT). Need not be normalised.
     * @param scanStepDays coarse bracketing step. Must satisfy the <180°-per-step precondition.
     * @param toleranceDays bracket width at which bisection stops. Returned value is the
     *   bracket midpoint, so the error against the function's true root is at most half of this.
     */
    fun firstCrossing(
        angleAt: (jdUt: Double) -> Double,
        targetDeg: Double,
        fromJdUt: Double,
        toJdUt: Double,
        scanStepDays: Double,
        toleranceDays: Double = ONE_SECOND_DAYS,
    ): Double? {
        require(toJdUt > fromJdUt) { "empty search interval [$fromJdUt, $toJdUt]" }
        require(scanStepDays > 0.0) { "scanStepDays must be positive, was $scanStepDays" }
        require(toleranceDays > 0.0) { "toleranceDays must be positive, was $toleranceDays" }

        var lo = fromJdUt
        var offsetLo = offset(angleAt, lo, targetDeg)
        if (offsetLo == 0.0) return lo

        var steps = 0
        while (lo < toJdUt && steps < MAX_SCAN_STEPS) {
            val hi = min(lo + scanStepDays, toJdUt)
            val offsetHi = offset(angleAt, hi, targetDeg)
            if (offsetLo < 0.0 && offsetHi >= 0.0) {
                return bisect(angleAt, targetDeg, lo, hi, toleranceDays)
            }
            if (hi >= toJdUt) return null
            lo = hi
            offsetLo = offsetHi
            steps++
        }
        return null
    }

    /**
     * The **latest** crossing of [targetDeg] at or before [atJdUt], searched back at most
     * [maxLookbackDays]. Returns `null` if none is bracketed in that span.
     *
     * This is not `firstCrossing` on a backward window: on a window long enough to contain two
     * crossings, `firstCrossing` returns the earlier one and this returns the later. Element
     * *start* times need the later one, and lunar-month boundaries genuinely can have two
     * candidates in a plausible window, so the distinction is real rather than stylistic.
     */
    fun lastCrossingBefore(
        angleAt: (jdUt: Double) -> Double,
        targetDeg: Double,
        atJdUt: Double,
        maxLookbackDays: Double,
        scanStepDays: Double,
        toleranceDays: Double = ONE_SECOND_DAYS,
    ): Double? {
        require(maxLookbackDays > 0.0) { "maxLookbackDays must be positive, was $maxLookbackDays" }
        require(scanStepDays > 0.0) { "scanStepDays must be positive, was $scanStepDays" }

        val floorJdUt = atJdUt - maxLookbackDays
        var hi = atJdUt
        var offsetHi = offset(angleAt, hi, targetDeg)
        if (offsetHi == 0.0) return hi

        var steps = 0
        while (hi > floorJdUt && steps < MAX_SCAN_STEPS) {
            val lo = maxOf(hi - scanStepDays, floorJdUt)
            val offsetLo = offset(angleAt, lo, targetDeg)
            if (offsetLo < 0.0 && offsetHi >= 0.0) {
                return bisect(angleAt, targetDeg, lo, hi, toleranceDays)
            }
            if (lo <= floorJdUt) return null
            hi = lo
            offsetHi = offsetLo
            steps++
        }
        return null
    }

    /**
     * Bisect a bracket already known to contain a crossing: `offset(lo) < 0 <= offset(hi)`.
     *
     * Plain bisection rather than Brent or a secant method. The bracket is never wider than a
     * fraction of a day, so 17 evaluations reach one second — the superlinear methods would
     * save perhaps eight ephemeris evaluations per boundary while introducing the one failure
     * mode that matters here, losing the bracket on a function that is only piecewise smooth
     * across the seam. Bisection cannot do that. The interval halves every step, so termination
     * is unconditional.
     */
    fun bisect(
        angleAt: (jdUt: Double) -> Double,
        targetDeg: Double,
        loJdUt: Double,
        hiJdUt: Double,
        toleranceDays: Double = ONE_SECOND_DAYS,
    ): Double {
        var lo = loJdUt
        var hi = hiJdUt
        var steps = 0
        while (hi - lo > toleranceDays && steps < MAX_BISECTIONS) {
            val mid = lo + (hi - lo) / 2.0
            if (offset(angleAt, mid, targetDeg) < 0.0) lo = mid else hi = mid
            steps++
        }
        return lo + (hi - lo) / 2.0
    }

    /**
     * Solve `f(t) = 0` by Newton's method with a numerically estimated derivative, for angle
     * functions whose rate is well behaved (hour angle, essentially). Used by the rise/set
     * solver, where a bracket is awkward to form but the derivative is large and near-constant.
     *
     * Returns the last iterate regardless of convergence; callers that need a guarantee should
     * check the residual themselves. It cannot spin: the iteration count is fixed.
     */
    internal fun newtonOnAngle(
        angleAt: (jdUt: Double) -> Double,
        targetDeg: Double,
        seedJdUt: Double,
        maxIterations: Int = 12,
        toleranceDays: Double = ONE_SECOND_DAYS / 100.0,
        derivativeStepDays: Double = 0.002,
    ): Double {
        var t = seedJdUt
        repeat(maxIterations) {
            val f = offset(angleAt, t, targetDeg)
            val rate = TimeScale.angleDifference(
                angleAt(t + derivativeStepDays),
                angleAt(t - derivativeStepDays),
            ) / (2.0 * derivativeStepDays)
            if (rate == 0.0 || !rate.isFinite()) return t
            val dt = -f / rate
            t += dt
            if (abs(dt) < toleranceDays) return t
        }
        return t
    }

    /** Signed offset of the angle from the target, wrapped to `(-180, +180]`. */
    private fun offset(angleAt: (Double) -> Double, jdUt: Double, targetDeg: Double): Double =
        TimeScale.angleDifference(angleAt(jdUt), targetDeg)
}
