package org.panchang.ephemeris

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Assertions against the worked examples printed in Jean Meeus, *Astronomical Algorithms*
 * (2nd ed.).
 *
 * These are the most valuable tests in the module. Everything else here checks the code
 * against itself; these check it against numbers a third party published and that a reader
 * can look up. If one of these fails, the series or the tables are wrong — not the test.
 *
 * ## Provenance and confidence
 *
 * The expected values below are quoted from memory of the printed book, not from a copy in
 * this repository. Confidence is recorded per-value:
 *
 * - **High** — Example 22.a (Δψ, Δε, ε₀, ε), Example 47.a (λ, β, Δ, apparent λ),
 *   Example 25.a (Θ, R). These are distinctive numbers that recur in every
 *   reimplementation of the book and are internally cross-checked by these tests.
 * - **Lower** — Example 25.b's apparent longitude 199°54′21.56″. See
 *   [sunExample25bHighAccuracyCrossCheck] for exactly what is uncertain about it.
 *
 * Nothing here is a value invented to make a test pass.
 */
@DisplayName("Meeus worked examples")
class MeeusWorkedExampleTest {

    private val ephemeris = MeeusEphemeris()

    private companion object {
        /** Degrees per arcsecond, for expressing tolerances in the unit that matters. */
        const val ARCSEC = 1.0 / 3600.0
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Chapter 22 — nutation and obliquity
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Meeus Example 22.a: 1987 April 10.0 TD, JDE = 2446895.5.
     *
     * Published: Δψ = −3.788″, Δε = +9.443″, ε₀ = 23°26′27.407″, ε = 23°26′36.850″.
     *
     * Tolerance: 0.02″ on the nutation quantities. Meeus prints Δψ and Δε to 0.001″ and
     * this implementation uses the same 63-term Table 22.A he does, so a correct
     * transcription should land within a few thousandths. 0.02″ leaves room for rounding
     * in his printed intermediates while still being 800× smaller than the leading term
     * (17.2″ at this date), so any mis-transcribed significant row fails loudly.
     */
    @Test
    fun `example 22a nutation and obliquity`() {
        val jde = 2446895.5
        val t = TimeScale.centuriesSinceJ2000(jde)

        val dPsiArcsec = Nutation.nutationLongitudeDegrees(t) * 3600.0
        val dEpsArcsec = Nutation.nutationObliquityDegrees(t) * 3600.0
        assertEquals(-3.788, dPsiArcsec, 0.02, "nutation in longitude, arcseconds")
        assertEquals(9.443, dEpsArcsec, 0.02, "nutation in obliquity, arcseconds")

        // ε₀ = 23°26′27.407″. Tolerance 0.05″: Meeus' example uses his shorter formula
        // (22.2) while this code uses Laskar's (22.3). The two agree to well under 0.01″
        // at 1987, so 0.05″ is slack, not a fudge.
        val meanObliquity = Nutation.meanObliquityDegrees(t)
        assertEquals(dms(23, 26, 27.407), meanObliquity, 0.05 * ARCSEC, "mean obliquity")

        // ε = ε₀ + Δε = 23°26′36.850″, via the public interface method.
        val actual = ephemeris.nutationAndObliquity(jde)
        assertEquals(dms(23, 26, 36.850), actual.trueObliquity, 0.05 * ARCSEC, "true obliquity")

        // The interface exposes Δψ too; it must be the same number tested above.
        assertEquals(
            Nutation.nutationLongitudeDegrees(t),
            actual.nutationLongitude,
            0.0,
            "nutationAndObliquity must not compute Δψ differently from Nutation",
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Chapter 47 — the Moon
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Meeus Example 47.a: 1992 April 12.0 TD, JDE = 2448724.5.
     *
     * Published intermediate results: λ (geometric) = 133.162655°, β = −3.229126°,
     * Δ = 368409.7 km. Meeus then gives the apparent longitude as 133.167265°, obtained by
     * adding the nutation in longitude of that date, +16.595″.
     *
     * [MeeusEphemeris.moonLongitude] returns the apparent value, so 133.167265° is what is
     * asserted.
     *
     * Tolerances:
     * - λ: 2e-5° (0.072″). Meeus prints six decimals, so the published value itself carries
     *   ±0.0000005°; the rest of the budget covers rounding in his printed Σl. A single
     *   wrong digit in any Table 47.A row with a coefficient above ~70 (that is, 50 of the
     *   60 rows) moves λ by more than this.
     * - β: 2e-5° (0.072″), same reasoning against Table 47.B. This is the assertion that
     *   validates latitude rows 31–60, which had no second source to check against.
     * - Δ: 0.2 km. Meeus prints one decimal and the sum is 385000.56 + Σr/1000 where Σr is
     *   quoted as an integer, so the published figure is good to ~0.05 km. 0.2 km also
     *   confirms the 60th row of Table 47.A (Σr = 8752, i.e. 8.752 km) is present — its
     *   omission in the app engine would fail this by 40×.
     */
    @Test
    fun `example 47a moon longitude latitude and distance`() {
        val jde = 2448724.5

        assertEquals(133.167265, ephemeris.moonLongitude(jde), 2e-5, "apparent longitude")
        assertEquals(-3.229126, ephemeris.moonLatitude(jde), 2e-5, "latitude")
        assertEquals(368409.7, ephemeris.moonDistanceKm(jde), 0.2, "distance, km")

        // Cross-check that the apparent longitude really is geometric + Δψ and nothing
        // else: recovering the geometric value must give Meeus' 133.162655°.
        val dPsi = ephemeris.nutationAndObliquity(jde).nutationLongitude
        assertEquals(
            133.162655,
            ephemeris.moonLongitude(jde) - dPsi,
            2e-5,
            "geometric longitude recovered by removing Δψ",
        )
        // ... and that Δψ at this date is the +16.595″ Meeus quotes.
        assertEquals(16.595, dPsi * 3600.0, 0.05, "nutation in longitude, arcseconds")
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Chapter 25 — the Sun
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Meeus Example 25.a: 1992 October 13.0 TD, JDE = 2448908.5, low-accuracy method.
     *
     * Published intermediates include the true (geometric) longitude Θ = 199.90987° and
     * the radius vector R = 0.99760775 AU. Meeus then gets an apparent longitude of
     * 199.90895° using his shorthand `Θ − 0.00569 − 0.00478 sin Ω`.
     *
     * This implementation replaces the `−0.00478 sin Ω` shorthand with the real Δψ from
     * Table 22.A, so it is expected to differ from Meeus' 199.90895° by of order 1″ — the
     * difference between his one-term nutation approximation and the full series. The
     * tolerance below (0.0015° = 5.4″) is set to accommodate that while remaining far
     * tighter than the 36″ this class claims overall.
     */
    @Test
    fun `example 25a sun radius vector and apparent longitude`() {
        val jde = 2448908.5

        val auKm = 149_597_870.7
        val radiusAu = ephemeris.sunDistanceKm(jde) / auKm

        // Radius vector. The reference figure here, 0.99760775 AU, is from Meeus'
        // high-accuracy VSOP87 example for this instant (25.b), not from 25.a — I am
        // confident of that attribution, and of all eight digits, because it is quoted
        // to eight decimals in the book.
        //
        // The low-accuracy (25.5) formula implemented here returns 0.9976620 AU, which is
        // 5.4e-5 AU (~8100 km) larger. That is expected, not a bug: (25.5) is a two-body
        // Kepler radius with no perturbations, so it omits both the Earth-centre/
        // Earth-Moon-barycentre offset (up to 4670 km, and this date is close to full
        // Moon) and the planetary perturbations of the Earth's orbit. My recollection is
        // that Example 25.a prints R = 0.99766 to five decimals, which agrees, but that
        // recollection is weaker than the 25.b figure so it is not what is asserted.
        //
        // Tolerance 1e-4 AU therefore bounds the *known* truncation, not the arithmetic.
        // It still catches any real error, since a wrong eccentricity or true anomaly
        // moves R by 1e-2 AU or more.
        assertEquals(0.99760775, radiusAu, 1e-4, "radius vector, AU")
        assertTrue(
            radiusAu - 0.99760775 > 0.0,
            "the unperturbed Kepler radius is expected to sit above the VSOP87 value here",
        )

        val apparent = ephemeris.sunLongitude(jde)
        assertEquals(199.90895, apparent, 0.0015, "apparent longitude vs Example 25.a")

        // Recovering the geometric longitude by removing Δψ and aberration must reproduce
        // Meeus' Θ = 199.90987°, which is the direct test of the (25.2)-(25.4) series.
        val dPsi = ephemeris.nutationAndObliquity(jde).nutationLongitude
        val aberration = -20.4898 / (ephemeris.sunDistanceKm(jde) / auKm) * ARCSEC
        assertEquals(
            199.90987,
            apparent - dPsi - aberration,
            5e-5,
            "geometric longitude Θ recovered from the apparent value",
        )
    }

    /**
     * Cross-check against Meeus' *high accuracy* solar example (Example 25.b, same instant,
     * VSOP87 truncated to the accuracy of the book), whose published apparent longitude is
     * **199°54′21.56″ = 199.905989°**.
     *
     * ## What is uncertain here
     *
     * The task brief attributed 199°54′21.56″ to Example 25.a. It is not consistent with
     * Example 25.a: that example's own apparent longitude is 199.90895° = 199°54′32.2″,
     * which is 10.7″ away. 199°54′21.56″ belongs to the VSOP87 example. My recollection of
     * which example the figure comes from is good; my recollection of the last two digits
     * (".56") is less certain, but the arcsecond and above are firm.
     *
     * The assertion is a band rather than a point. Its purpose is not to pin a digit but to
     * demonstrate the size of the *truncation error* of the Ch. 25 low-accuracy series
     * against a genuinely better ephemeris at a date where both are published, which is the
     * evidence behind [MeeusEphemeris.claimedAccuracyArcsec].
     *
     * Measured value: **+9.35″**. That figure is independently corroborated: reconstructing
     * it from Example 25.a's own printed intermediates (Θ = 199.90987°, R = 0.99760775 AU)
     * and Example 25.b's nutation (Δψ = +15.908″) predicts 9.34″ without running any code.
     * Two recollections agreeing to a hundredth of an arcsecond is decent evidence that
     * neither is invented. It is still not a substitute for JPL Horizons; Phase 2 should
     * replace this with a measured comparison.
     */
    @Test
    fun sunExample25bHighAccuracyCrossCheck() {
        val jde = 2448908.5
        val highAccuracy = dms(199, 54, 21.56)

        val error = TimeScale.angleDifference(ephemeris.sunLongitude(jde), highAccuracy) * 3600.0
        assertTrue(
            error in 4.0..15.0,
            "apparent solar longitude is ${"%.2f".format(error)}\" from Example 25.b's " +
                "199°54'21.56\"; the Ch. 25 truncation error here should be about +9.35\"",
        )
        assertTrue(
            kotlin.math.abs(error) < ephemeris.claimedAccuracyArcsec,
            "the measured error at this date must not exceed the accuracy the class claims",
        )
    }

    /** Degrees from a sexagesimal triple, for expected values quoted as D°M′S″. */
    private fun dms(degrees: Int, minutes: Int, seconds: Double): Double =
        degrees + minutes / 60.0 + seconds / 3600.0
}
