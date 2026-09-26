package org.panchang.ephemeris

import kotlin.math.PI

/**
 * [Ephemeris] with the **full VSOP87D theory for the Sun** and the Meeus Ch. 47 series for
 * the Moon.
 *
 * ## Why this class exists
 *
 * Measurement, not theory, put it here. The Phase 1 baseline
 * (`docs/accuracy-baseline.md`) measured [MeeusEphemeris] against 365 daily JPL Horizons
 * samples at each of 1950, 2026 and 2100 and found the Sun carrying roughly **four times**
 * the Moon's error — 17.3″ rms against 4.0″ at 2026. That is expected: Ch. 47 is a
 * truncation of the full lunar theory, whereas Ch. 25's *"low accuracy"* solar method is
 * stated by Meeus himself as good to about 0.01° = 36″.
 *
 * Tithi is the Moon − Sun elongation, so the Sun's error propagates into it undiminished.
 * Replacing the Sun was therefore the cheapest large accuracy gain available: one already
 * vendored coefficient file against 36 files and 2.5 MB for ELP2000.
 *
 * ## What is different from [MeeusEphemeris], and what is not
 *
 * | | this class | [MeeusEphemeris] |
 * |---|---|---|
 * | Sun longitude | VSOP87D, all 2425 terms | Meeus Ch. 25 low-accuracy series |
 * | Sun distance | VSOP87D R — the Earth's true radius vector | Kepler two-body radius, ~8100 km high |
 * | Moon | **identical — delegated** | Meeus Ch. 47 |
 * | Nutation, obliquity | [Nutation] (IAU 1980) | same |
 * | ΔT | [DeltaT] | same |
 *
 * [MeeusEphemeris] is untouched and stays in the codebase deliberately: it is the Android
 * app's self-contained offline engine, and it is the subject of the differential tests that
 * would catch a regression here. Two independent implementations that agree to a few
 * arcseconds are worth more than one implementation that is merely asserted to be right.
 *
 * ## The Moon is delegated, and that is now the binding constraint
 *
 * [moonLongitude], [moonLatitude] and [moonDistanceKm] forward to a private
 * [MeeusEphemeris]. This is not an oversight and not a stub — it is a deferred task.
 * ELP2000-82B is a separate, much larger piece of work (36 vendored files; see
 * `vendor/PROVENANCE.md`), and the baseline showed it buys the remaining ~3″ of lunar error
 * → ~0.1″, which moves a tithi boundary by well under a minute.
 *
 * The consequence is that **once the Sun is fixed, the Moon sets this class's accuracy**,
 * and [claimedAccuracyArcsec] reflects the Moon, not VSOP87. Fixing the Sun did not make
 * the whole ephemeris sub-arcsecond; it made the Moon the thing worth fixing next.
 *
 * ## Getting from VSOP87 to the [Ephemeris] contract
 *
 * The contract asks for *apparent* geocentric ecliptic longitude referred to the *true*
 * equinox of date. VSOP87D gives heliocentric spherical coordinates of the **Earth**,
 * referred to the **mean** equinox and ecliptic of date, in the **dynamical** frame. Four
 * steps close the gap, and each is a real effect at the arcsecond level:
 *
 * 1. **Geocentric inversion.** λ☉ = L⊕ + 180°. Exact — the Sun and the Earth are opposite
 *    each other as seen from the other, and VSOP87's `EARTH` really is the Earth's centre
 *    for versions A–E (the Earth–Moon barycentre is a separate body code), so there is no
 *    barycentre offset to remove.
 * 2. **Dynamical → FK5**, −0.09033″ (Meeus Ch. 25, *Higher accuracy*). Small, constant, and
 *    applied because Horizons and the IAU reference the FK5/ICRS realisation while VSOP87
 *    is built on the dynamical ecliptic. Skipping it would leave a 0.09″ bias — invisible
 *    against the Moon's 3″ but a free correction.
 * 3. **Aberration and light-time**, ≈ −20.5″. The dominant correction of the four. See
 *    [aberrationDegrees].
 * 4. **Nutation in longitude** Δψ, up to ±17″, from [Nutation] — the step from the mean
 *    equinox of date to the true one.
 *
 * No precession step appears in that list, and that is the point of choosing variant D over
 * variant B: D is already of date.
 *
 * ## Purity
 *
 * Stateless and immutable once [Vsop87]'s table has been loaded (once, lazily, from the
 * classpath). Every method is a pure function of its argument and safe to share across
 * threads.
 */
class Vsop87Ephemeris : Ephemeris {

    override val id: String = "vsop87"

    /**
     * Worst-case apparent-longitude error, in arcseconds. **Measured**, not claimed by any
     * theory.
     *
     * Measured 2026-08-01 by `HorizonsDifferentialTest` against
     * `docs/measurements/horizons-2026-08-01/` — 365 daily apparent geocentric longitudes
     * per body at each of 1950, 2026 and 2100, from JPL Horizons. The full table is in
     * `docs/accuracy-baseline.md`.
     *
     * ## What this number is
     *
     * These methods take **jdTt**. ΔT is therefore *not* part of what they can get wrong —
     * it enters at the caller's UT→TT conversion, separately, and is bounded separately by
     * [DeltaT.qualityAtJd]. The right measure of this class is consequently the error with
     * a constant time offset removed:
     *
     * | epoch | Sun resid rms | Sun resid max | Moon resid rms | Moon resid max |
     * |---|---|---|---|---|
     * | 1950 | 0.00″ | 0.01″ | 2.48″ | 10.29″ |
     * | 2026 | 0.01″ | 0.01″ | 2.80″ | 8.11″ |
     * | 2100 | 0.01″ | 0.01″ | 2.79″ | 11.82″ |
     *
     * **12″ is the claim**: the largest of those lunar residual maxima, 11.82″, rounded up.
     * It is the Meeus Ch. 47 truncation and nothing else. VSOP87D's own contribution is
     * 0.01″ — four orders of magnitude below it, and below anything else in this module.
     * The Sun has stopped being a source of error at all.
     *
     * Note how flat the Moon's residual is across 150 years: 2.48″, 2.80″, 2.79″. The
     * lunar series does not decay over this span. Everything that looked like decay in the
     * Phase 1 baseline was ΔT.
     *
     * ## What it does not cover
     *
     * - **Raw error at 2100 is larger — 23.53″ max on the Moon.** That extra is a +20 s
     *   clock offset: this module and Horizons extrapolate ΔT differently, and neither is
     *   knowledge. A caller converting from civil time in 2100 must add its own ΔT
     *   uncertainty (≈±10 s, so ≈±5.5″ on the Moon) on top of this constant, and should
     *   branch on [DeltaT.qualityAtJd] to know when to.
     * - Three sample years are good phase coverage but are not a continuous 1900–2200
     *   envelope. 12″ is a measured maximum rounded up, not a proof.
     * - Tithi depends on the elongation Moon − Sun, which is now essentially the Moon
     *   alone: 2.51″ rms / 9.88″ max at 1950 and 2.88″ / 8.79″ at 2026, against 9.74″ /
     *   28.53″ and 16.34″ / 34.92″ before. At 1829″/hour that is a worst-case tithi
     *   boundary error of **19 s**, down from 56–69 s.
     */
    override val claimedAccuracyArcsec: Double = 12.0

    /**
     * The Moon, and only the Moon, comes from here. See the class KDoc: ELP2000-82B is a
     * deliberately deferred task, and until it lands the Meeus Ch. 47 series is what this
     * class uses — unmodified, so the two implementations return bit-identical lunar
     * positions and any lunar regression shows up in both or neither.
     */
    private val meeus = MeeusEphemeris()

    // ─────────────────────────────────────────────────────────────────────────────
    //  Sun — VSOP87D
    // ─────────────────────────────────────────────────────────────────────────────

    override fun sunLongitude(jdTt: Double): Double {
        val tm = Vsop87.millenniaSinceJ2000(jdTt)
        val radiusAu = Vsop87.earthRadiusAu(tm)

        val geometric = sunGeometricLongitudeDegrees(tm)
        val dPsi = Nutation.nutationLongitudeDegrees(TimeScale.centuriesSinceJ2000(jdTt))

        return TimeScale.normalizeDegrees(
            geometric + dPsi + aberrationDegrees(tm, radiusAu),
        )
    }

    /**
     * The Sun's *geometric* geocentric longitude in the FK5 frame, mean equinox of date,
     * degrees — steps 1 and 2 of the four in the class KDoc.
     *
     * The FK5 term is Meeus Ch. 25's Δλ = −0.09033″. Meeus pairs it with a latitude
     * correction Δβ = +0.03916″(cos λ′ − sin λ′); that one is not computed here because
     * [Ephemeris] exposes no solar latitude to apply it to. If a `sunLatitude` is ever
     * added, it must be added with it.
     */
    private fun sunGeometricLongitudeDegrees(tMillennia: Double): Double {
        val earthLongitude = Vsop87.earthLongitudeRadians(tMillennia) * RAD_TO_DEG
        return earthLongitude + 180.0 + FK5_LONGITUDE_CORRECTION_DEG
    }

    /**
     * Earth-centre to Sun-centre distance in km, from VSOP87D's radius vector.
     *
     * This is a real improvement over [MeeusEphemeris.sunDistanceKm], which uses an
     * unperturbed two-body radius and runs ~8100 km high because it ignores both the
     * Earth-centre/barycentre offset and the planetary perturbations of the Earth's orbit.
     * VSOP87D's `EARTH` body is the Earth's centre and its R carries the perturbations, so
     * neither error is present. Against the authors' own check values it agrees to
     * 4.7e-11 au ≈ 7 m at the worst of ten epochs spanning 1099–2000.
     */
    override fun sunDistanceKm(jdTt: Double): Double =
        Vsop87.earthRadiusAu(Vsop87.millenniaSinceJ2000(jdTt)) * AU_KM

    /**
     * Annual aberration plus light-time for the Sun, in degrees. Always about −20.5″.
     *
     * Meeus Ch. 25, *Higher accuracy*: Δλ = −0.005775518 · R · (dλ/dt), with R in AU and
     * dλ/dt the daily variation of the Sun's geocentric longitude **in a fixed reference
     * frame**, in arcseconds per day. The constant is the light-time for one astronomical
     * unit, 0.0057755183 days, so the expression is literally "how far the Sun appears to
     * move while its light is in transit" — light-time and stellar aberration are the same
     * displacement here and must not both be applied.
     *
     * Two details that are easy to get wrong:
     *
     * - dλ/dt is taken by central difference over ±0.5 day of the VSOP87 longitude. The
     *   Sun's longitude is smooth on that scale (dλ/dt ≈ 3548″/day, varying by ~1%/month),
     *   so the truncation error is far below 0.001″.
     * - VSOP87D is referred to the equinox **of date**, so its longitude rate includes
     *   general precession, ~0.1377″/day. The formula wants a fixed frame, so precession is
     *   subtracted. It is only 4 parts in 100000 of the rate — 0.0008″ of aberration — but
     *   it costs one subtraction to be right instead of nearly right.
     *
     * The simpler −20.4898″/R of Meeus (25.10), which [MeeusEphemeris] uses, agrees with
     * this to about 0.003″; `Vsop87SolarTest` asserts that agreement so that a sign or
     * factor error here cannot pass.
     */
    private fun aberrationDegrees(tMillennia: Double, radiusAu: Double): Double {
        val halfStepMillennia = 0.5 / Vsop87.DAYS_PER_MILLENNIUM
        val before = Vsop87.earthLongitudeRadians(tMillennia - halfStepMillennia)
        val after = Vsop87.earthLongitudeRadians(tMillennia + halfStepMillennia)
        // The Sun's longitude is the Earth's + 180°, so their rates are identical.
        val rateArcsecPerDay = (after - before) * RAD_TO_ARCSEC - GENERAL_PRECESSION_ARCSEC_PER_DAY
        return -LIGHT_TIME_DAYS_PER_AU * radiusAu * rateArcsecPerDay / 3600.0
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Moon — delegated to the Meeus Ch. 47 series, pending ELP2000-82B
    // ─────────────────────────────────────────────────────────────────────────────

    /** Delegated to [MeeusEphemeris]. See the class KDoc — ELP2000-82B is deferred. */
    override fun moonLongitude(jdTt: Double): Double = meeus.moonLongitude(jdTt)

    /** Delegated to [MeeusEphemeris]. See the class KDoc — ELP2000-82B is deferred. */
    override fun moonLatitude(jdTt: Double): Double = meeus.moonLatitude(jdTt)

    /** Delegated to [MeeusEphemeris]. See the class KDoc — ELP2000-82B is deferred. */
    override fun moonDistanceKm(jdTt: Double): Double = meeus.moonDistanceKm(jdTt)

    // ─────────────────────────────────────────────────────────────────────────────
    //  Shared machinery
    // ─────────────────────────────────────────────────────────────────────────────

    override fun nutationAndObliquity(jdTt: Double): NutationObliquity = Nutation.at(jdTt)

    /**
     * ΔT from [DeltaT] — observed IERS Earth orientation where it exists.
     *
     * [DeltaT.qualityAtJd] tells a caller whether a given date's ΔT was measured,
     * predicted, or extrapolated. That distinction, not [claimedAccuracyArcsec], is what
     * bounds a far-future boundary time.
     */
    override fun deltaT(jdUt: Double): Double = DeltaT.secondsAtJd(jdUt)

    private companion object {

        const val RAD_TO_DEG = 180.0 / PI

        const val RAD_TO_ARCSEC = 180.0 * 3600.0 / PI

        /** IAU 2012 definition of the astronomical unit, exact, in kilometres. */
        const val AU_KM = 149_597_870.7

        /**
         * Meeus Ch. 25: the constant longitude offset from VSOP87's dynamical ecliptic to
         * the FK5 frame, −0.09033″, in degrees.
         */
        const val FK5_LONGITUDE_CORRECTION_DEG = -0.09033 / 3600.0

        /** Light-time for one astronomical unit, in days — Meeus' 0.005775518. */
        const val LIGHT_TIME_DAYS_PER_AU = 0.005775518

        /**
         * General precession in longitude at J2000, 5029.0966″/Julian century (IAU 1976,
         * the same model as the IAU 1980 nutation this module uses), expressed per day.
         *
         * Its variation over the ±few centuries this service covers is under 0.1%, and it
         * only enters a 0.0008″ term, so a constant is ample.
         */
        const val GENERAL_PRECESSION_ARCSEC_PER_DAY = 5029.0966 / 36525.0
    }
}
