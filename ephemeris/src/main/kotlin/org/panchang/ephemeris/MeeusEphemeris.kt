package org.panchang.ephemeris

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * [Ephemeris] built on the analytical series in Jean Meeus, *Astronomical Algorithms*
 * (2nd ed., Willmann-Bell 1998).
 *
 * - Sun: Ch. 25, the "low accuracy" solar coordinates (25.2 through 25.5). Not VSOP87.
 * - Moon: Ch. 47, the full printed Table 47.A (60 longitude/distance terms) and
 *   Table 47.B (60 latitude terms), with the E-factor and the additive A1/A2/A3 terms.
 * - Nutation and obliquity: [Nutation] (IAU 1980, Meeus Table 22.A).
 * - ΔT: [DeltaT] (Espenak & Meeus piecewise fit).
 *
 * ## Relationship to the Android engine this replaces
 *
 * The Ch. 47 coefficient tables were transcribed from
 * `DrikPanchangaCalculator.kt` in the Bhagwan Bharose app, which had already typed them out
 * from Meeus. Two corrections were made to what was there:
 *
 * 1. Table 47.A was missing its 60th row, `D=2, M=0, M'=−1, F=−2` with Σl = 0 and
 *    Σr = 8752. Being a distance-only term it cost the app 8.75 km of lunar distance, with
 *    no effect on longitude. It is restored here.
 * 2. Table 47.B was truncated to its first 30 rows. The remaining 30 are restored; they
 *    contribute up to ~0.0008° individually and a few arcseconds in combination.
 *
 * Because rows 31–60 of Table 47.B could not be cross-checked against the app source, they
 * are validated instead by the Ch. 47 worked-example test, which pins β to the published
 * −3.229126°. A transcription error in those rows would move β well outside the tolerance
 * asserted there.
 *
 * Everything else about the app engine's structure was deliberately not carried over: it
 * fed UT-based Julian Days into TT-defined series (no ΔT at all), computed obliquity three
 * inconsistent ways, and scattered the epoch constant 2440587.5 across ten call sites.
 *
 * ## Apparent vs geometric
 *
 * The interface contract is *apparent* geocentric longitude referred to the true equinox
 * of date. For the Sun that is geometric longitude + Δψ + annual aberration. For the Moon
 * it is geometric longitude + Δψ only; see [moonLongitude] for why.
 *
 * ## Purity
 *
 * Stateless and immutable. Every method is a pure function of its argument; instances are
 * interchangeable and safe to share across threads.
 */
class MeeusEphemeris : Ephemeris {

    override val id: String = "meeus"

    /**
     * Worst-case apparent-longitude error claimed, in arcseconds. **Provisional.**
     *
     * Derivation, from the accuracies the sources state for themselves:
     *
     * - Moon, Ch. 47 with the full printed tables: Meeus states ~10″ in longitude and
     *   ~4″ in latitude relative to the complete ELP-2000/82 theory, valid over several
     *   centuries either side of J2000.
     * - Sun, Ch. 25 low-accuracy method: Meeus states an accuracy of about 0.01°, i.e.
     *   36″. This is the binding constraint — the Sun, not the Moon, is the weak leg.
     *   Measured against Meeus' own high-accuracy Example 25.b for 1992-10-13.0 TD, this
     *   implementation lands +9.35″ away, so 36″ is the stated bound, not the typical error.
     *   (By contrast the Moon reproduces Example 47.a to 0.01″, but one date proves
     *   nothing about the bound — it only proves the tables were transcribed correctly.)
     *
     * 36″ is therefore the number claimed. It is *not* a measured figure: nothing here has
     * yet been compared against JPL Horizons. Phase 2 differential testing must replace it
     * with what it actually measures, in either direction.
     *
     * Note also that this bounds each body's longitude separately. Tithi depends on the
     * elongation Moon − Sun, whose error can be as large as the sum, ~46″ ≈ 90 s of tithi
     * boundary timing. Downstream code should not assume the errors cancel.
     *
     * For reference, the app engine documented itself at 0.05° = 180″ and had a ~69 s ΔT
     * error on top of that, which alone is ~35″ of lunar longitude.
     */
    override val claimedAccuracyArcsec: Double = 36.0

    // ─────────────────────────────────────────────────────────────────────────────
    //  Sun — Meeus Ch. 25 (low accuracy solar coordinates)
    // ─────────────────────────────────────────────────────────────────────────────

    override fun sunLongitude(jdTt: Double): Double {
        val t = TimeScale.centuriesSinceJ2000(jdTt)
        val sun = solar(t)
        // Apparent longitude = geometric + nutation in longitude + annual aberration.
        //
        // Meeus (25.10) approximates the pair as "− 0.00569 − 0.00478 sin Ω". The second
        // term is a one-term stand-in for Δψ; we have the real thing from Table 22.A, so
        // the true Δψ is used instead. That is a ~1″ improvement and, more importantly,
        // makes the Sun and the Moon share one definition of the true equinox of date.
        val dPsi = Nutation.nutationLongitudeDegrees(t)
        return TimeScale.normalizeDegrees(sun.trueLongitude + dPsi + aberrationDegrees(sun.radiusAu))
    }

    /**
     * Earth-centre to Sun-centre distance in km, from the Kepler radius vector (25.5).
     *
     * **This is the weakest number this class returns.** (25.5) is an unperturbed two-body
     * radius: it omits the offset between the Earth's centre and the Earth–Moon
     * barycentre (up to ~4670 km, varying over a month) and the planetary perturbations of
     * the Earth's orbit. Against Meeus' VSOP87 example for 1992-10-13.0 TD it is ~8100 km
     * (5.4e-5 AU) high — five parts in 100000.
     *
     * That is harmless for the two things it is used for here — the aberration denominator,
     * where 5e-5 relative error is 0.001″, and the Sun's apparent semi-diameter, where it
     * is 0.05″ — but it is not good enough for anything that needs the actual distance.
     */
    override fun sunDistanceKm(jdTt: Double): Double =
        solar(TimeScale.centuriesSinceJ2000(jdTt)).radiusAu * AU_KM

    /**
     * Annual aberration of the Sun in degrees, Meeus (25.10)/(25.11).
     *
     * −20.4898″ / R, R in AU. The constant is the aberration constant scaled by the
     * Earth's semi-major axis; dividing by the instantaneous radius vector accounts for
     * the Earth moving faster at perihelion. Meeus notes this ignores the eccentricity
     * term of order 0.001″, which is far below anything this class claims.
     */
    private fun aberrationDegrees(radiusAu: Double): Double =
        -20.4898 / radiusAu / 3600.0

    /** Geometric solar quantities at Julian centuries [t] of TT since J2000. */
    private fun solar(t: Double): Solar {
        // (25.2) geometric mean longitude referred to the mean equinox of date
        val l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        // (25.3) mean anomaly
        val m = 357.52911 + 35999.05029 * t - 0.0001537 * t * t
        // (25.4) eccentricity of the Earth's orbit
        val e = 0.016708634 - 0.000042037 * t - 0.0000001267 * t * t

        val mRad = m * DEG_TO_RAD
        // Equation of the centre, Ch. 25 unnumbered series after (25.4)
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(mRad) +
            (0.019993 - 0.000101 * t) * sin(2.0 * mRad) +
            0.000289 * sin(3.0 * mRad)

        val trueLongitude = l0 + c
        val trueAnomaly = m + c
        // (25.5) radius vector in AU
        val r = 1.000001018 * (1.0 - e * e) / (1.0 + e * cos(trueAnomaly * DEG_TO_RAD))

        return Solar(trueLongitude = trueLongitude, radiusAu = r)
    }

    private class Solar(val trueLongitude: Double, val radiusAu: Double)

    // ─────────────────────────────────────────────────────────────────────────────
    //  Moon — Meeus Ch. 47
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Apparent geocentric ecliptic longitude of the Moon.
     *
     * Geometric longitude from Ch. 47 plus Δψ, and **no aberration or light-time term**.
     * That is deliberate, and it is what Meeus does in Ch. 47 (his Example 47.a goes
     * straight from λ = 133.162655° to apparent λ = 133.167265° by adding Δψ = 16.595″).
     *
     * The reasoning: the Moon's light-time is Δ/c ≈ 1.26 s, over which the Moon advances
     * about 0.7″ in longitude. That displacement is a factor of 14 below the 10″ Meeus
     * quotes for the truncated Ch. 47 series and a factor of 50 below this class's overall
     * claimed bound, so applying it would be adjusting noise. Beyond that, the ELP-2000/82
     * mean-longitude constant that Table 47.1 inherits was itself fitted to observations,
     * which partially absorbs the effect. Applying an explicit −0.7″ here would also break
     * agreement with the published worked example, removing the one piece of independent
     * ground truth this module has. If Phase 2 measurement against Horizons shows a
     * systematic ~0.7″ lunar offset, this is the first place to look.
     */
    override fun moonLongitude(jdTt: Double): Double {
        val t = TimeScale.centuriesSinceJ2000(jdTt)
        val moon = lunar(t)
        return TimeScale.normalizeDegrees(moon.longitude + Nutation.nutationLongitudeDegrees(t))
    }

    /**
     * Apparent geocentric ecliptic latitude of the Moon.
     *
     * Nutation in longitude does not enter the latitude: rotating the origin of longitudes
     * about the ecliptic pole leaves β unchanged. The value returned is therefore the
     * Ch. 47 Σb result directly.
     */
    override fun moonLatitude(jdTt: Double): Double =
        lunar(TimeScale.centuriesSinceJ2000(jdTt)).latitude

    /** Geocentric distance to the Moon in km, Meeus (47.1) — 385000.56 + Σr / 1000. */
    override fun moonDistanceKm(jdTt: Double): Double =
        lunar(TimeScale.centuriesSinceJ2000(jdTt)).distanceKm

    private fun lunar(t: Double): Lunar {
        val t2 = t * t
        val t3 = t2 * t
        val t4 = t3 * t

        // ── Fundamental arguments, Meeus (47.1) through (47.5) ───────────────────
        // Moon's mean longitude L'
        val lp = 218.3164477 + 481267.88123421 * t - 0.0015786 * t2 + t3 / 538841.0 - t4 / 65194000.0
        // Mean elongation D
        val d = 297.8501921 + 445267.1114034 * t - 0.0018819 * t2 + t3 / 545868.0 - t4 / 113065000.0
        // Sun's mean anomaly M
        val m = 357.5291092 + 35999.0502909 * t - 0.0001536 * t2 + t3 / 24490000.0
        // Moon's mean anomaly M'
        val mp = 134.9633964 + 477198.8675055 * t + 0.0087414 * t2 + t3 / 69699.0 - t4 / 14712000.0
        // Moon's argument of latitude F
        val f = 93.2720950 + 483202.0175233 * t - 0.0036539 * t2 - t3 / 3526000.0 + t4 / 863310000.0

        // Additive arguments for the Venus (A1), Jupiter (A2) and flattening (A3)
        // perturbations, Ch. 47 immediately after (47.5).
        val a1 = 119.75 + 131.849 * t
        val a2 = 53.09 + 479264.290 * t
        val a3 = 313.45 + 481266.484 * t

        // E accounts for the decreasing eccentricity of the Earth's orbit. Terms whose
        // argument contains M are multiplied by E, those containing 2M by E².
        val e = 1.0 - 0.002516 * t - 0.0000074 * t2
        val e2 = e * e

        val dR = d * DEG_TO_RAD
        val mR = m * DEG_TO_RAD
        val mpR = mp * DEG_TO_RAD
        val fR = f * DEG_TO_RAD
        val lpR = lp * DEG_TO_RAD
        val a1R = a1 * DEG_TO_RAD
        val a2R = a2 * DEG_TO_RAD
        val a3R = a3 * DEG_TO_RAD

        var sigmaL = 0.0
        var sigmaR = 0.0
        for (term in MOON_LON_DIST_TERMS) {
            val arg = term[0] * dR + term[1] * mR + term[2] * mpR + term[3] * fR
            val eFactor = when (abs(term[1].toInt())) {
                1 -> e
                2 -> e2
                else -> 1.0
            }
            sigmaL += term[4] * eFactor * sin(arg)
            sigmaR += term[5] * eFactor * cos(arg)
        }

        var sigmaB = 0.0
        for (term in MOON_LAT_TERMS) {
            val arg = term[0] * dR + term[1] * mR + term[2] * mpR + term[3] * fR
            val eFactor = when (abs(term[1].toInt())) {
                1 -> e
                2 -> e2
                else -> 1.0
            }
            sigmaB += term[4] * eFactor * sin(arg)
        }

        // Additive corrections to Σl: Venus, the Earth's flattening, and Jupiter.
        sigmaL += 3958.0 * sin(a1R)
        sigmaL += 1962.0 * sin(lpR - fR)
        sigmaL += 318.0 * sin(a2R)

        // Additive corrections to Σb.
        sigmaB += -2235.0 * sin(lpR)
        sigmaB += 382.0 * sin(a3R)
        sigmaB += 175.0 * sin(a1R - fR)
        sigmaB += 175.0 * sin(a1R + fR)
        sigmaB += 127.0 * sin(lpR - mpR)
        sigmaB += -115.0 * sin(lpR + mpR)

        // Σl and Σb are in units of 1e-6 degree; Σr is in units of 1e-3 km.
        return Lunar(
            longitude = TimeScale.normalizeDegrees(lp + sigmaL / 1_000_000.0),
            latitude = sigmaB / 1_000_000.0,
            distanceKm = 385000.56 + sigmaR / 1000.0,
        )
    }

    private class Lunar(val longitude: Double, val latitude: Double, val distanceKm: Double)

    // ─────────────────────────────────────────────────────────────────────────────
    //  Nutation and time scale
    // ─────────────────────────────────────────────────────────────────────────────

    override fun nutationAndObliquity(jdTt: Double): NutationObliquity = Nutation.at(jdTt)

    override fun deltaT(jdUt: Double): Double = DeltaT.secondsAtJd(jdUt)

    private companion object {

        const val DEG_TO_RAD = Math.PI / 180.0

        /** IAU 2012 definition of the astronomical unit, exact, in kilometres. */
        const val AU_KM = 149_597_870.7

        /**
         * Meeus **Table 47.A** — periodic terms for the Moon's longitude (Σl) and
         * distance (Σr).
         *
         * Row layout: `D, M, M', F, Σl (1e-6 degree), Σr (1e-3 km)`.
         *
         * All 60 rows. Rows 1–59 match the app engine's table exactly; row 60
         * (`2, 0, −1, −2`, Σl = 0, Σr = 8752) is the distance-only term the app omitted.
         */
        val MOON_LON_DIST_TERMS: Array<DoubleArray> = arrayOf(
            doubleArrayOf(0.0, 0.0, 1.0, 0.0, 6288774.0, -20905355.0),
            doubleArrayOf(2.0, 0.0, -1.0, 0.0, 1274027.0, -3699111.0),
            doubleArrayOf(2.0, 0.0, 0.0, 0.0, 658314.0, -2955968.0),
            doubleArrayOf(0.0, 0.0, 2.0, 0.0, 213618.0, -569925.0),
            doubleArrayOf(0.0, 1.0, 0.0, 0.0, -185116.0, 48888.0),
            doubleArrayOf(0.0, 0.0, 0.0, 2.0, -114332.0, -3149.0),
            doubleArrayOf(2.0, 0.0, -2.0, 0.0, 58793.0, 246158.0),
            doubleArrayOf(2.0, -1.0, -1.0, 0.0, 57066.0, -152138.0),
            doubleArrayOf(2.0, 0.0, 1.0, 0.0, 53322.0, -170733.0),
            doubleArrayOf(2.0, -1.0, 0.0, 0.0, 45758.0, -204586.0),
            doubleArrayOf(0.0, 1.0, -1.0, 0.0, -40923.0, -129620.0),
            doubleArrayOf(1.0, 0.0, 0.0, 0.0, -34720.0, 108743.0),
            doubleArrayOf(0.0, 1.0, 1.0, 0.0, -30383.0, 104755.0),
            doubleArrayOf(2.0, 0.0, 0.0, -2.0, 15327.0, 10321.0),
            doubleArrayOf(0.0, 0.0, 1.0, 2.0, -12528.0, 0.0),
            doubleArrayOf(0.0, 0.0, 1.0, -2.0, 10980.0, 79661.0),
            doubleArrayOf(4.0, 0.0, -1.0, 0.0, 10675.0, -34782.0),
            doubleArrayOf(0.0, 0.0, 3.0, 0.0, 10034.0, -23210.0),
            doubleArrayOf(4.0, 0.0, -2.0, 0.0, 8548.0, -21636.0),
            doubleArrayOf(2.0, 1.0, -1.0, 0.0, -7888.0, 24208.0),
            doubleArrayOf(2.0, 1.0, 0.0, 0.0, -6766.0, 30824.0),
            doubleArrayOf(1.0, 0.0, -1.0, 0.0, -5163.0, -8379.0),
            doubleArrayOf(1.0, 1.0, 0.0, 0.0, 4987.0, -16675.0),
            doubleArrayOf(2.0, -1.0, 1.0, 0.0, 4036.0, -12831.0),
            doubleArrayOf(2.0, 0.0, 2.0, 0.0, 3994.0, -10445.0),
            doubleArrayOf(4.0, 0.0, 0.0, 0.0, 3861.0, -11650.0),
            doubleArrayOf(2.0, 0.0, -3.0, 0.0, 3665.0, 14403.0),
            doubleArrayOf(0.0, 1.0, -2.0, 0.0, -2689.0, -7003.0),
            doubleArrayOf(2.0, 0.0, -1.0, 2.0, -2602.0, 0.0),
            doubleArrayOf(2.0, -1.0, -2.0, 0.0, 2390.0, 10056.0),
            doubleArrayOf(1.0, 0.0, 1.0, 0.0, -2348.0, 6322.0),
            doubleArrayOf(2.0, -2.0, 0.0, 0.0, 2236.0, -9884.0),
            doubleArrayOf(0.0, 1.0, 2.0, 0.0, -2120.0, 5751.0),
            doubleArrayOf(0.0, 2.0, 0.0, 0.0, -2069.0, 0.0),
            doubleArrayOf(2.0, -2.0, -1.0, 0.0, 2048.0, -4950.0),
            doubleArrayOf(2.0, 0.0, 1.0, -2.0, -1773.0, 4130.0),
            doubleArrayOf(2.0, 0.0, 0.0, 2.0, -1595.0, 0.0),
            doubleArrayOf(4.0, -1.0, -1.0, 0.0, 1215.0, -3958.0),
            doubleArrayOf(0.0, 0.0, 2.0, 2.0, -1110.0, 0.0),
            doubleArrayOf(3.0, 0.0, -1.0, 0.0, -892.0, 3258.0),
            doubleArrayOf(2.0, 1.0, 1.0, 0.0, -810.0, 2616.0),
            doubleArrayOf(4.0, -1.0, -2.0, 0.0, 759.0, -1897.0),
            doubleArrayOf(0.0, 2.0, -1.0, 0.0, -713.0, -2117.0),
            doubleArrayOf(2.0, 2.0, -1.0, 0.0, -700.0, 2354.0),
            doubleArrayOf(2.0, 1.0, -2.0, 0.0, 691.0, 0.0),
            doubleArrayOf(2.0, -1.0, 0.0, -2.0, 596.0, 0.0),
            doubleArrayOf(4.0, 0.0, 1.0, 0.0, 549.0, -1423.0),
            doubleArrayOf(0.0, 0.0, 4.0, 0.0, 537.0, -1117.0),
            doubleArrayOf(4.0, -1.0, 0.0, 0.0, 520.0, -1571.0),
            doubleArrayOf(1.0, 0.0, -2.0, 0.0, -487.0, -1739.0),
            doubleArrayOf(2.0, 1.0, 0.0, -2.0, -399.0, 0.0),
            doubleArrayOf(0.0, 0.0, 2.0, -2.0, -381.0, -4421.0),
            doubleArrayOf(1.0, 1.0, 1.0, 0.0, 351.0, 0.0),
            doubleArrayOf(3.0, 0.0, -2.0, 0.0, -340.0, 0.0),
            doubleArrayOf(4.0, 0.0, -3.0, 0.0, 330.0, 0.0),
            doubleArrayOf(2.0, -1.0, 2.0, 0.0, 327.0, 0.0),
            doubleArrayOf(0.0, 2.0, 1.0, 0.0, -323.0, 1165.0),
            doubleArrayOf(1.0, 1.0, -1.0, 0.0, 299.0, 0.0),
            doubleArrayOf(2.0, 0.0, 3.0, 0.0, 294.0, 0.0),
            doubleArrayOf(2.0, 0.0, -1.0, -2.0, 0.0, 8752.0),
        )

        /**
         * Meeus **Table 47.B** — periodic terms for the Moon's latitude (Σb).
         *
         * Row layout: `D, M, M', F, Σb (1e-6 degree)`. All 60 rows.
         *
         * Rows 1–30 are transcribed from the app engine (which had truncated the table
         * there). Rows 31–60 were restored from Meeus and could not be cross-checked
         * against any second copy in this repository; the Ch. 47 worked-example test is
         * what validates them.
         */
        val MOON_LAT_TERMS: Array<DoubleArray> = arrayOf(
            doubleArrayOf(0.0, 0.0, 0.0, 1.0, 5128122.0),
            doubleArrayOf(0.0, 0.0, 1.0, 1.0, 280602.0),
            doubleArrayOf(0.0, 0.0, 1.0, -1.0, 277693.0),
            doubleArrayOf(2.0, 0.0, 0.0, -1.0, 173237.0),
            doubleArrayOf(2.0, 0.0, -1.0, 1.0, 55413.0),
            doubleArrayOf(2.0, 0.0, -1.0, -1.0, 46271.0),
            doubleArrayOf(2.0, 0.0, 0.0, 1.0, 32573.0),
            doubleArrayOf(0.0, 0.0, 2.0, 1.0, 17198.0),
            doubleArrayOf(2.0, 0.0, 1.0, -1.0, 9266.0),
            doubleArrayOf(0.0, 0.0, 2.0, -1.0, 8822.0),
            doubleArrayOf(2.0, -1.0, 0.0, -1.0, 8216.0),
            doubleArrayOf(2.0, 0.0, -2.0, -1.0, 4324.0),
            doubleArrayOf(2.0, 0.0, 1.0, 1.0, 4200.0),
            doubleArrayOf(2.0, 1.0, 0.0, -1.0, -3359.0),
            doubleArrayOf(2.0, -1.0, -1.0, 1.0, 2463.0),
            doubleArrayOf(2.0, -1.0, 0.0, 1.0, 2211.0),
            doubleArrayOf(2.0, -1.0, -1.0, -1.0, 2065.0),
            doubleArrayOf(0.0, 1.0, -1.0, -1.0, -1870.0),
            doubleArrayOf(4.0, 0.0, -1.0, -1.0, 1828.0),
            doubleArrayOf(0.0, 1.0, 0.0, 1.0, -1794.0),
            doubleArrayOf(0.0, 0.0, 0.0, 3.0, -1749.0),
            doubleArrayOf(0.0, 1.0, -1.0, 1.0, -1565.0),
            doubleArrayOf(1.0, 0.0, 0.0, 1.0, -1491.0),
            doubleArrayOf(0.0, 1.0, 1.0, 1.0, -1475.0),
            doubleArrayOf(0.0, 1.0, 1.0, -1.0, -1410.0),
            doubleArrayOf(0.0, 1.0, 0.0, -1.0, -1344.0),
            doubleArrayOf(1.0, 0.0, 0.0, -1.0, -1335.0),
            doubleArrayOf(0.0, 0.0, 3.0, 1.0, 1107.0),
            doubleArrayOf(4.0, 0.0, 0.0, -1.0, 1021.0),
            doubleArrayOf(4.0, 0.0, -1.0, 1.0, 833.0),
            // ── rows 31-60: restored from Meeus, not present in the app source ──
            doubleArrayOf(0.0, 0.0, 1.0, -3.0, 777.0),
            doubleArrayOf(4.0, 0.0, -2.0, 1.0, 671.0),
            doubleArrayOf(2.0, 0.0, 0.0, -3.0, 607.0),
            doubleArrayOf(2.0, 0.0, 2.0, -1.0, 596.0),
            doubleArrayOf(2.0, -1.0, 1.0, -1.0, 491.0),
            doubleArrayOf(2.0, 0.0, -2.0, 1.0, -451.0),
            doubleArrayOf(0.0, 0.0, 3.0, -1.0, 439.0),
            doubleArrayOf(2.0, 0.0, 2.0, 1.0, 422.0),
            doubleArrayOf(2.0, 0.0, -3.0, -1.0, 421.0),
            doubleArrayOf(2.0, 1.0, -1.0, 1.0, -366.0),
            doubleArrayOf(2.0, 1.0, 0.0, 1.0, -351.0),
            doubleArrayOf(4.0, 0.0, 0.0, 1.0, 331.0),
            doubleArrayOf(2.0, -1.0, 1.0, 1.0, 315.0),
            doubleArrayOf(2.0, -2.0, 0.0, -1.0, 302.0),
            doubleArrayOf(0.0, 0.0, 1.0, 3.0, -283.0),
            doubleArrayOf(2.0, 1.0, 1.0, -1.0, -229.0),
            doubleArrayOf(1.0, 1.0, 0.0, -1.0, 223.0),
            doubleArrayOf(1.0, 1.0, 0.0, 1.0, 223.0),
            doubleArrayOf(0.0, 1.0, -2.0, -1.0, -220.0),
            doubleArrayOf(2.0, 1.0, -1.0, -1.0, -220.0),
            doubleArrayOf(1.0, 0.0, 1.0, 1.0, -185.0),
            doubleArrayOf(2.0, -1.0, -2.0, -1.0, 181.0),
            doubleArrayOf(0.0, 1.0, 2.0, 1.0, -177.0),
            doubleArrayOf(4.0, 0.0, -2.0, -1.0, 176.0),
            doubleArrayOf(4.0, -1.0, -1.0, -1.0, 166.0),
            doubleArrayOf(1.0, 0.0, 1.0, -1.0, -164.0),
            doubleArrayOf(4.0, 0.0, 1.0, -1.0, 132.0),
            doubleArrayOf(1.0, 0.0, -1.0, -1.0, -119.0),
            doubleArrayOf(4.0, -1.0, 0.0, -1.0, 115.0),
            doubleArrayOf(2.0, -2.0, 0.0, 1.0, 107.0),
        )
    }
}
