package org.panchang.ephemeris

import kotlin.math.max

/**
 * ΔT = TT − UT1, in seconds.
 *
 * ## What this object is, since Phase 2A
 *
 * Two things, layered:
 *
 * - [secondsAtJd] — **the one callers should use.** Observed IERS Earth orientation where
 *   the IERS has measured it (1973-01-02 onward), IERS Bulletin A predictions for the
 *   following year, a parabolic continuation after that, and the Espenak & Meeus fit
 *   before it. [qualityAtJd] says which. See [ObservedDeltaT].
 * - [secondsAtYear] — the Espenak & Meeus piecewise polynomial fit, on its own. It is what
 *   the layer above falls back to for pre-1973 and deep-past dates, and it remains
 *   directly callable because it is the citable published model and the thing every other
 *   ΔT implementation can be compared against.
 *
 * The layering, rather than a replacement, is deliberate: outside the IERS record there is
 * no alternative to the fit, and pretending otherwise would be inventing data.
 *
 * ## Source of the fit
 *
 * Fred Espenak and Jean Meeus, *Polynomial Expressions for Delta T*, published on the NASA
 * Eclipse Web Site (`eclipse.gsfc.nasa.gov/SEhelp/deltatpoly2004.html`) and used for the
 * *Five Millennium Canon of Solar Eclipses* (NASA/TP–2006–214141). It is a piecewise
 * polynomial fit to the historical ΔT record, stated valid from −1999 to +3000.
 *
 * ## Stated uncertainty of the fit
 *
 * The fit is not a measurement. Espenak & Meeus give roughly:
 *
 * - 1955 to the present: sub-second — this era is covered by atomic time and the
 *   polynomials reproduce the published values closely.
 * - 1600–1955: order 1 s, rising to a few seconds at the early end.
 * - 900–1600: tens of seconds to a few minutes.
 * - before 900: minutes to hours; the −1999..500 branches are extrapolations of the
 *   parabola ΔT = −20 + 32u² fitted to eclipse records, with uncertainty measured in
 *   hours at the extremes.
 * - after 2005: extrapolation. Uncertainty grows to roughly ±10 s by 2100 and much more
 *   beyond, because it depends on the unpredictable long-term behaviour of Earth rotation.
 *
 * ## The post-2005 branch, and why [secondsAtJd] no longer uses it
 *
 * The 2005–2050 branch (`62.92 + 0.32217t + 0.005589t²`) was fitted in 2006 and assumed a
 * continued increase in ΔT. Earth's rotation instead sped up slightly, and the fit has
 * been diverging from observation ever since:
 *
 * | epoch    | this fit | observed (IERS) | error |
 * |----------|----------|-----------------|-------|
 * | 2020.0   | 71.60 s  | 69.36 s         | +2.24 s |
 * | 2026.58  | 75.23 s  | 69.17 s         | +6.06 s |
 *
 * That was a real, known systematic offset — about six seconds — in every civil-time
 * result this service produced for present-day dates, shifting every published boundary
 * time by that amount. It is now gone from [secondsAtJd]: for any date the IERS has
 * measured, ΔT is the measurement.
 *
 * The polynomial is *not* deleted, and this branch is still reachable through
 * [secondsAtYear]. Its bias is pinned by test so it stays visible rather than becoming
 * folklore.
 *
 * ## Purity
 *
 * [secondsAtYear] is a pure function of the decimal year with no state at all.
 * [secondsAtJd] reads one classpath resource, once, lazily, and is thereafter pure. No
 * network at build time or run time.
 */
object DeltaT {

    /**
     * ΔT in seconds from the **Espenak & Meeus piecewise fit alone**, at a decimal year.
     *
     * This is the published model, unmodified — including its stale post-2005
     * extrapolation. For dates from 1973 onward prefer [secondsAtJd], which uses observed
     * IERS data instead. See [decimalYear] for the argument convention.
     */
    fun secondsAtYear(year: Double): Double = when {
        year < -500.0 -> {
            val u = (year - 1820.0) / 100.0
            -20.0 + 32.0 * u * u
        }

        year < 500.0 -> {
            val u = year / 100.0
            poly(
                u,
                10583.6, -1014.41, 33.78311, -5.952053,
                -0.1798452, 0.022174192, 0.0090316521,
            )
        }

        year < 1600.0 -> {
            val u = (year - 1000.0) / 100.0
            poly(
                u,
                1574.2, -556.01, 71.23472, 0.319781,
                -0.8503463, -0.005050998, 0.0083572073,
            )
        }

        year < 1700.0 -> {
            val t = year - 1600.0
            poly(t, 120.0, -0.9808, -0.01532, 1.0 / 7129.0)
        }

        year < 1800.0 -> {
            val t = year - 1700.0
            poly(t, 8.83, 0.1603, -0.0059285, 0.00013336, -1.0 / 1174000.0)
        }

        year < 1860.0 -> {
            val t = year - 1800.0
            poly(
                t,
                13.72, -0.332447, 0.0068612, 0.0041116,
                -0.00037436, 0.0000121272, -0.0000001699, 0.000000000875,
            )
        }

        year < 1900.0 -> {
            val t = year - 1860.0
            poly(t, 7.62, 0.5737, -0.251754, 0.01680668, -0.0004473624, 1.0 / 233174.0)
        }

        year < 1920.0 -> {
            val t = year - 1900.0
            poly(t, -2.79, 1.494119, -0.0598939, 0.0061966, -0.000197)
        }

        year < 1941.0 -> {
            val t = year - 1920.0
            poly(t, 21.20, 0.84493, -0.076100, 0.0020936)
        }

        year < 1961.0 -> {
            val t = year - 1950.0
            poly(t, 29.07, 0.407, -1.0 / 233.0, 1.0 / 2547.0)
        }

        year < 1986.0 -> {
            val t = year - 1975.0
            poly(t, 45.45, 1.067, -1.0 / 260.0, -1.0 / 718.0)
        }

        year < 2005.0 -> {
            val t = year - 2000.0
            poly(
                t,
                63.86, 0.3345, -0.060374, 0.0017275,
                0.000651814, 0.00002373599,
            )
        }

        year < 2050.0 -> {
            // Extrapolation. See the class KDoc: known to run ~2 s high in the 2020s.
            val t = year - 2000.0
            poly(t, 62.92, 0.32217, 0.005589)
        }

        year < 2150.0 -> {
            // The -0.5628 * (2150 - y) term exists purely to make this branch join the
            // 2005-2050 branch at 2050 and the long-term parabola at 2150. Dropping it
            // opens a ~56 s step at 2050.
            val u = (year - 1820.0) / 100.0
            -20.0 + 32.0 * u * u - 0.5628 * (2150.0 - year)
        }

        else -> {
            val u = (year - 1820.0) / 100.0
            -20.0 + 32.0 * u * u
        }
    }

    /**
     * ΔT in seconds at a UT-based Julian Day — observed where observation exists.
     *
     * Four regimes, joined without a step (see [qualityAtJd] to find out which one a date
     * falls in):
     *
     * | regime | dates | source |
     * |---|---|---|
     * | [DeltaTQuality.HISTORICAL_FIT] | before 1973-01-02 | Espenak & Meeus, tapered onto the IERS record over the last [ObservedDeltaT.SEAM_TAPER_YEARS] years |
     * | [DeltaTQuality.OBSERVED] | 1973-01-02 … end of the IERS determined record | IERS UT1−UTC + leap seconds |
     * | [DeltaTQuality.PREDICTED] | the following ~1 year | IERS Bulletin A prediction |
     * | [DeltaTQuality.EXTRAPOLATED] | after that | parabola matched in value and slope to the record's end |
     *
     * ## Continuity
     *
     * ΔT is bisected through by every boundary solver in `:core`: they search civil time,
     * and civil time reaches the series only via `jdTt = jdUt + ΔT/86400`. A discontinuity
     * anywhere in ΔT is therefore a place where a bisection can fail to converge, no matter
     * how small the step. Both seams here are continuous by construction — the pre-1973
     * taper is defined to reproduce the first tabulated value exactly at the seam, and the
     * post-record parabola is defined to reproduce the last one — and both are asserted.
     */
    fun secondsAtJd(jdUt: Double): Double {
        val mjd = ObservedDeltaT.modifiedJulianDate(jdUt)
        if (mjd >= ObservedDeltaT.firstMjd) return ObservedDeltaT.secondsAtJd(jdUt)

        // Before the IERS record. Use the published fit, but slide it onto the observed
        // value at the seam so there is no step there. The offset is ~0.06 s and dies away
        // linearly over SEAM_TAPER_YEARS, after which this is the unmodified fit.
        val yearsBefore = (ObservedDeltaT.firstMjd - mjd) / 365.25
        val weight = max(0.0, 1.0 - yearsBefore / ObservedDeltaT.SEAM_TAPER_YEARS)
        return secondsAtYear(decimalYear(jdUt)) + seamOffsetSeconds * weight
    }

    /**
     * How [secondsAtJd] arrived at the value for [jdUt] — measured, predicted,
     * extrapolated, or historical fit.
     *
     * Callers that publish a tolerance need this. A tithi boundary in 2027 is limited by
     * the ephemeris; one in 2100 is limited by nobody knowing how fast the Earth will be
     * turning, which is ±10 s of ΔT and so ±20 s of boundary time. Stating the same
     * confidence for both would be a false claim.
     *
     * It is not on the [Ephemeris] interface because that interface is a frozen contract in
     * this phase. It is reachable here, and via [ObservedDeltaT.qualityAtJd], because both
     * are objects.
     */
    fun qualityAtJd(jdUt: Double): DeltaTQuality = ObservedDeltaT.qualityAtJd(jdUt)

    /**
     * ΔT(seam, observed) − ΔT(seam, Espenak & Meeus) at the first row of the IERS table.
     *
     * Computed rather than hard-coded, so that refreshing the resource cannot leave a stale
     * constant behind and silently reopen a step at the seam.
     */
    private val seamOffsetSeconds: Double by lazy {
        val seamJd = ObservedDeltaT.firstMjd + 2_400_000.5
        ObservedDeltaT.firstSeconds - secondsAtYear(decimalYear(seamJd))
    }

    /**
     * Decimal year of a Julian Day, measured in Julian years from J2000.0.
     *
     * ## Why not Espenak & Meeus' own convention
     *
     * Espenak & Meeus define the argument of their polynomials as `y = year + (month − 0.5) / 12`,
     * i.e. the midpoint of the calendar month. That is fine for tabulating, but as a
     * *function of an instant* it is a staircase: ΔT would be constant through a month and
     * then step by up to ~0.12 s at each month boundary.
     *
     * A discontinuous ΔT is not acceptable here. It makes the UT↔TT mapping non-monotonic
     * at month boundaries, and every boundary time this service publishes — tithi,
     * nakshatra, sunrise — is found by bisecting a function of civil time through that
     * mapping. A 0.12 s step is small, but a bisection that straddles it cannot converge,
     * and this is exactly the class of defect the module exists to remove.
     *
     * So the argument is continuous instead: linear in JD at 365.25 days per year, anchored
     * at J2000.0. The disagreement with the published convention is at most half a month
     * (0.042 yr) of argument, plus the slow drift of the Julian year against the Gregorian
     * one (~0.014 yr per two millennia). Translated into ΔT by the steepest slope any
     * branch has, that is under 0.1 s in the modern era and well under a second even in the
     * ancient branches, whose own uncertainty is measured in minutes.
     */
    fun decimalYear(jd: Double): Double =
        2000.0 + (jd - TimeScale.J2000) / 365.25

    /** Horner evaluation; keeps the polynomial branches above readable and consistent. */
    private fun poly(x: Double, vararg coefficients: Double): Double {
        var result = 0.0
        for (i in coefficients.indices.reversed()) {
            result = result * x + coefficients[i]
        }
        return result
    }
}
