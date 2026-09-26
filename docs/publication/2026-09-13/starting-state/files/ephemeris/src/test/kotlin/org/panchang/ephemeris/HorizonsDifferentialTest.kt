package org.panchang.ephemeris

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Differential test against **JPL Horizons**, and the harness that produced the measured
 * numbers in `docs/accuracy-baseline.md`.
 *
 * The reference files are 365 daily samples of apparent geocentric ecliptic longitude per
 * body at each of 1950, 2026 and 2100, committed under
 * `docs/measurements/horizons-2026-08-01/`. See the README there for exactly how they were
 * requested; the important line is that the time column is **UT**, so every comparison
 * necessarily goes through ΔT and a ΔT disagreement shows up as a longitude error.
 *
 * ## What is asserted, and what is only reported
 *
 * The `println` output is the measurement — run
 * `./gradlew :ephemeris:test --tests '*HorizonsDifferentialTest*' -i` to regenerate the
 * table in the baseline document. The assertions are deliberately looser than the measured
 * values: they are there to catch a *regression*, not to re-state the measurement, and a
 * test that fails when the third decimal moves is a test that gets deleted.
 *
 * ## Why 2100 is asserted differently
 *
 * At 2100 both this module and Horizons are extrapolating ΔT, and they disagree by ~20 s.
 * That is a disagreement about how fast the Earth will be turning in 2100, not about
 * astronomy, and no ephemeris work reduces it. So at 2100 the assertion is on the
 * **clock-removed residual**, which is the part attributable to the series. Asserting the
 * raw error there would be asserting that JPL's ΔT forecast is correct.
 *
 * The files live in `docs/`, outside this module. If they are not found — a source
 * distribution, say — the test is skipped rather than failed: a missing measurement input
 * is not a code defect.
 */
@DisplayName("Differential test vs JPL Horizons")
class HorizonsDifferentialTest {

    private val meeus = MeeusEphemeris()
    private val vsop87 = Vsop87Ephemeris()

    private class Sample(val jdUt: Double, val longitudeDeg: Double)

    /** Error statistics of one quantity at one epoch, all in arcseconds except [clockSeconds]. */
    private class Stats(
        val mean: Double,
        val rms: Double,
        val max: Double,
        val clockSeconds: Double,
        val residualRms: Double,
        val residualMax: Double,
    ) {
        fun row(label: String): String =
            "| %s | %+.2f″ | %.2f″ | %.2f″ | %+.2f s | %.2f″ | %.2f″ |"
                .format(label, mean, rms, max, clockSeconds, residualRms, residualMax)
    }

    @Test
    fun `measure and report`() {
        val dir = locateMeasurements()
        assumeTrue(dir != null, "Horizons reference files not found; skipping measurement")
        requireNotNull(dir)

        val out = StringBuilder()
        out.appendLine()
        out.appendLine("Horizons differential measurement — ${dir.absolutePath}")
        out.appendLine("ΔT source: ${ObservedDeltaT.sourceUrl}")
        out.appendLine(
            "ΔT observed through MJD ${ObservedDeltaT.observedThroughMjd.toInt()}, " +
                "predicted through ${ObservedDeltaT.predictedThroughMjd.toInt()}",
        )
        out.appendLine()
        out.appendLine("| Epoch / quantity | mean | rms | max | clock | resid rms | resid max |")
        out.appendLine("|---|---|---|---|---|---|---|")

        val results = HashMap<String, Stats>()

        for (epoch in listOf(1950, 2026, 2100)) {
            val moonRef = read(File(dir, "moon_$epoch.csv"))
            val sunRef = read(File(dir, "sun_$epoch.csv"))
            require(moonRef.size == sunRef.size) { "sample counts differ at $epoch" }

            // The Moon is identical in both implementations (Vsop87Ephemeris delegates), so
            // it is measured once and labelled as shared.
            val moon = statsFor(moonRef) { jd -> vsop87.moonLongitude(jd) }
            val sunMeeus = statsFor(sunRef) { jd -> meeus.sunLongitude(jd) }
            val sunVsop = statsFor(sunRef) { jd -> vsop87.sunLongitude(jd) }
            val elongMeeus = elongationStats(moonRef, sunRef, meeus)
            val elongVsop = elongationStats(moonRef, sunRef, vsop87)

            out.appendLine(moon.row("$epoch Moon (shared)"))
            out.appendLine(sunMeeus.row("$epoch Sun — meeus"))
            out.appendLine(sunVsop.row("$epoch Sun — **vsop87**"))
            out.appendLine(elongMeeus.row("$epoch Elongation — meeus"))
            out.appendLine(elongVsop.row("$epoch Elongation — **vsop87**"))

            results["${epoch}moon"] = moon
            results["${epoch}sunMeeus"] = sunMeeus
            results["${epoch}sunVsop"] = sunVsop
            results["${epoch}elongMeeus"] = elongMeeus
            results["${epoch}elongVsop"] = elongVsop
        }

        out.appendLine()
        out.appendLine("Tithi advances 1829″/hour, so max elongation error → boundary error:")
        for (epoch in listOf(1950, 2026, 2100)) {
            val e = results["${epoch}elongVsop"]!!
            out.appendLine(
                "  %d: raw max %.2f″ → %.0f s ; clock-removed max %.2f″ → %.0f s"
                    .format(epoch, e.max, e.max / 1829.0 * 3600.0, e.residualMax, e.residualMax / 1829.0 * 3600.0),
            )
        }
        out.appendLine()
        out.appendLine(
            "ΔT at 2026-08-01: espenak-meeus %.2f s, this module %.2f s, difference %+.2f s (%s)"
                .format(
                    DeltaT.secondsAtYear(DeltaT.decimalYear(TimeScale.jdUtAtMidnight(2026, 8, 1))),
                    DeltaT.secondsAtJd(TimeScale.jdUtAtMidnight(2026, 8, 1)),
                    DeltaT.secondsAtJd(TimeScale.jdUtAtMidnight(2026, 8, 1)) -
                        DeltaT.secondsAtYear(DeltaT.decimalYear(TimeScale.jdUtAtMidnight(2026, 8, 1))),
                    DeltaT.qualityAtJd(TimeScale.jdUtAtMidnight(2026, 8, 1)),
                ),
        )
        println(out)

        // ── Assertions. Regression guards, not restatements of the measurement. ──

        for (epoch in listOf(1950, 2026, 2100)) {
            val sunVsop = results["${epoch}sunVsop"]!!
            val sunMeeus = results["${epoch}sunMeeus"]!!
            assertTrue(
                sunVsop.residualRms < 1.0,
                "$epoch: VSOP87 solar residual rms is ${sunVsop.residualRms}″; VSOP87D is a " +
                    "sub-arcsecond theory and anything approaching 1″ means the frame " +
                    "corrections are wrong, not the series",
            )
            assertTrue(
                sunVsop.residualRms < sunMeeus.residualRms / 10.0,
                "$epoch: VSOP87 solar residual rms ${sunVsop.residualRms}″ is not at least " +
                    "10x better than Meeus Ch.25's ${sunMeeus.residualRms}″; if it is not, " +
                    "this class is not earning its 325 kB",
            )
        }

        // Present-day and 1950 ΔT is observed or historical-fit, so the raw error is
        // meaningful and can be bounded directly.
        for (epoch in listOf(1950, 2026)) {
            val elong = results["${epoch}elongVsop"]!!
            assertTrue(
                elong.rms < 5.0 && elong.max < 15.0,
                "$epoch: elongation error rms ${elong.rms}″ max ${elong.max}″ exceeds the " +
                    "5″/15″ regression guard",
            )
        }

        // At 2100 the raw error is a ΔT forecast disagreement. Bound the series instead.
        val far = results["2100elongVsop"]!!
        assertTrue(
            far.residualRms < 15.0,
            "2100: clock-removed elongation residual rms is ${far.residualRms}″; that part " +
                "is the series, not ΔT, and should look like 1950 and 2026",
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Error statistics of `computed(jdTt) − horizons`, in arcseconds, plus the
     * least-squares constant time offset that best explains them.
     *
     * The clock fit solves `error_i ≈ rate_i · dt` for a single `dt` in seconds, with
     * `rate_i` the body's longitude rate at sample `i` measured from the reference series
     * itself. It is what separates "the time argument is wrong" from "the series is wrong",
     * and it is the whole reason the 2100 row in the baseline could be diagnosed as ΔT
     * rather than series decay.
     */
    private fun statsFor(reference: List<Sample>, compute: (Double) -> Double): Stats {
        val errors = DoubleArray(reference.size)
        val rates = DoubleArray(reference.size)
        for (i in reference.indices) {
            val s = reference[i]
            val jdTt = s.jdUt + DeltaT.secondsAtJd(s.jdUt) / 86_400.0
            errors[i] = TimeScale.angleDifference(compute(jdTt), s.longitudeDeg) * 3600.0
            rates[i] = referenceRate(reference, i)
        }
        return summarise(errors, rates)
    }

    private fun elongationStats(
        moonRef: List<Sample>,
        sunRef: List<Sample>,
        ephemeris: Ephemeris,
    ): Stats {
        val errors = DoubleArray(moonRef.size)
        val rates = DoubleArray(moonRef.size)
        for (i in moonRef.indices) {
            val jdUt = moonRef[i].jdUt
            val jdTt = jdUt + DeltaT.secondsAtJd(jdUt) / 86_400.0
            val computed = TimeScale.angleDifference(
                ephemeris.moonLongitude(jdTt),
                ephemeris.sunLongitude(jdTt),
            )
            val expected = TimeScale.angleDifference(
                moonRef[i].longitudeDeg,
                sunRef[i].longitudeDeg,
            )
            errors[i] = TimeScale.angleDifference(computed, expected) * 3600.0
            rates[i] = referenceRate(moonRef, i) - referenceRate(sunRef, i)
        }
        return summarise(errors, rates)
    }

    /** Longitude rate in arcsec per second at sample [i], from the reference series itself. */
    private fun referenceRate(reference: List<Sample>, i: Int): Double {
        val lo = if (i == 0) 0 else i - 1
        val hi = if (i == reference.size - 1) reference.size - 1 else i + 1
        val dDeg = TimeScale.angleDifference(
            reference[hi].longitudeDeg,
            reference[lo].longitudeDeg,
        )
        val dSeconds = (reference[hi].jdUt - reference[lo].jdUt) * 86_400.0
        return dDeg * 3600.0 / dSeconds
    }

    private fun summarise(errors: DoubleArray, rates: DoubleArray): Stats {
        val n = errors.size
        val mean = errors.sum() / n
        val rms = sqrt(errors.sumOf { it * it } / n)
        val max = errors.maxOf { abs(it) }

        var num = 0.0
        var den = 0.0
        for (i in 0 until n) {
            num += rates[i] * errors[i]
            den += rates[i] * rates[i]
        }
        val clock = if (den > 0.0) num / den else 0.0

        var residualSquares = 0.0
        var residualMax = 0.0
        for (i in 0 until n) {
            val r = errors[i] - rates[i] * clock
            residualSquares += r * r
            residualMax = maxOf(residualMax, abs(r))
        }
        return Stats(mean, rms, max, clock, sqrt(residualSquares / n), residualMax)
    }

    private fun read(file: File): List<Sample> =
        file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val comma = line.indexOf(',')
                Sample(line.substring(0, comma).toDouble(), line.substring(comma + 1).toDouble())
            }

    /** Walk up from the working directory looking for the committed Horizons samples. */
    private fun locateMeasurements(): File? {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        repeat(5) {
            val candidate = File(dir, "docs/measurements/horizons-2026-08-01")
            if (File(candidate, "moon_2026.csv").isFile) return candidate
            dir = dir?.parentFile
        }
        return null
    }
}
