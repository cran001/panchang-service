package org.panchang.ephemeris

/**
 * ΔT = TT − UT1, in seconds.
 *
 * ## Source
 *
 * Fred Espenak and Jean Meeus, *Polynomial Expressions for Delta T*, published on the NASA
 * Eclipse Web Site (`eclipse.gsfc.nasa.gov/SEhelp/deltatpoly2004.html`) and used for the
 * *Five Millennium Canon of Solar Eclipses* (NASA/TP–2006–214141). It is a piecewise
 * polynomial fit to the historical ΔT record, stated valid from −1999 to +3000.
 *
 * ## Stated uncertainty
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
 * ## Known bias in the post-2005 branch
 *
 * The 2005–2050 branch (`62.92 + 0.32217t + 0.005589t²`) was fitted in 2006 and assumed a
 * continued increase in ΔT. Earth's rotation instead sped up slightly, and the fit has
 * been diverging from observation ever since:
 *
 * | epoch    | this fit | observed | error |
 * |----------|----------|----------|-------|
 * | 2020.0   | 71.6 s   | 69.36 s  | +2.2 s |
 * | 2026.6   | 75.4 s   | ~69.3 s  | +6.1 s |
 *
 * That is a real, known systematic offset — currently about six seconds — in every
 * civil-time result the service produces for present-day dates. It shifts every published
 * boundary time by that amount. (As an error in the *position* series argument it is
 * trivial: 6 s is 3″ of lunar longitude.)
 *
 * It is retained deliberately rather than patched with an ad-hoc correction: it is the
 * published, citable fit, and a hand-tuned replacement would be an unsourced number of
 * exactly the kind this module is supposed to avoid. Replacing it with observed
 * IERS/`finals.all` values plus a proper extrapolation is a Phase 2 item, and is the
 * single largest improvement available to boundary-time accuracy.
 *
 * ## Purity
 *
 * Pure function of the decimal year. No state, no I/O, thread-safe.
 */
object DeltaT {

    /** ΔT in seconds at a decimal year, e.g. `2000.5`. See [decimalYear] for the convention. */
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

    /** ΔT in seconds at a UT-based Julian Day. */
    fun secondsAtJd(jdUt: Double): Double = secondsAtYear(decimalYear(jdUt))

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
