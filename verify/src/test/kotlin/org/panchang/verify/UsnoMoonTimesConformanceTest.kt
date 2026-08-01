package org.panchang.verify

import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.panchang.core.GeoLocation
import org.panchang.core.PanchangCalculator
import org.panchang.core.RiseSet
import org.panchang.ephemeris.MeeusEphemeris
import org.panchang.ephemeris.TimeScale
import org.panchang.verify.grid.ReferenceCities
import org.panchang.verify.usno.PolarCondition
import org.panchang.verify.usno.UsnoDayRecord

/**
 * Moonrise and moonset against the US Naval Observatory.
 *
 * `verify/golden/usno-grid-2026-04-14.json` has carried USNO's `moonRises` and `moonSets` for
 * the ten reference cities since it was harvested, and until this test nothing read them:
 * [UsnoParserTest] exercises the parser against a fixture, which proves we can read USNO's
 * JSON, not that our astronomy agrees with it. This is the check that the sun already had in
 * `core/.../UsnoSunTimesConformanceTest` and the Moon did not.
 *
 * It exists to put a measured number under the `MOONRISE` fast anchor. One catalog entry ends
 * its fast at moonrise; grading that rule's confidence requires knowing how good our moonrise
 * is, not assuming it inherits the sun's accuracy. The Moon does not: it moves ~13°/day against
 * the stars, its horizontal parallax is about 1° where the Sun's is negligible, and its
 * rise/set horizon therefore depends on its distance. A larger residual than the sun's would
 * have been unsurprising.
 *
 * ## Reference data
 *
 * `verify/golden/usno-grid-2026-04-14.json` (ten cities, 2026-04-14) and
 * `verify/golden/usno-polar-2026-06-21.json` / `usno-polar-2026-12-21.json` (Longyearbyen at
 * 78.2°N and McMurdo at 77.8°S). Retrieved 2026-08-01 from `https://aa.usno.navy.mil/api/rstt/oneday`,
 * HTTP 200, with the per-request provenance recorded in each file's `provenance` block.
 *
 * Read straight from `verify/golden` rather than copied into a fixture: a second physical copy
 * of a reference file is a copy that can silently drift from the harvested original.
 *
 * ## What USNO's times are, exactly
 *
 * **Local, not UT**, expressed in the fixed offset that was sent as the `tz` request parameter
 * and echoed back in each record's `utcOffsetHours`. Each comparison below therefore builds its
 * [GeoLocation] from that same fixed offset, not from the city's IANA zone. On 2026-04-14 the
 * two happen to agree for every grid city, but relying on that coincidence is how an hour of
 * DST error gets to hide inside an astronomical comparison.
 *
 * **Rounded to the whole minute**, with no seconds field in the API response. That caps what
 * any comparison against this source can resolve at ±30 s, whatever the underlying code does.
 *
 * ## Convention
 *
 * USNO reckons moonrise from the Moon's **upper limb** on the apparent horizon. `Horizon`
 * expresses the same convention geocentrically as Meeus eq. 15.1, `h₀ = 0.7275·π − 34′`: the
 * `0.7275` is `1 − 0.2725`, i.e. the parallax shift less the semidiameter, so the two
 * conventions are the same statement and the comparison is like for like. A comparison against
 * a source using the Moon's *centre* would be biased by roughly the semidiameter, about a
 * minute of time, and would look exactly like a real error.
 *
 * ## Tolerance
 *
 * **Measured before the band was written, not after.** Over the twenty grid events the signed
 * error (ours − USNO) came out at **worst |Δ| = 27 s (Mumbai moonrise), rms 17 s, mean +1.8 s**;
 * the three further comparable polar events fell at +16 s, +2 s and −6 s. Every one of the
 * twenty-three is inside USNO's own publication rounding.
 *
 * The band asserted is ±30 s. That figure is not a fit to the measurement — it is the half-width
 * of the one-minute rounding, the tightest band a minute-resolution reference can justify — but
 * the measurement is what shows we sit inside it, with 3 s to spare at the worst city rather
 * than by luck. If a future change pushes any event past 30 s, it has moved further than
 * rounding can explain and is a real regression. Do not widen this band to accommodate a
 * failure, and do not tune `Horizon`'s constants to buy headroom inside it.
 *
 * No accuracy better than 30 s is claimed. See `docs/validation-moonrise.md` for what this does
 * and does not establish, including the fact that the twenty grid events share one lunar
 * geometry.
 */
class UsnoMoonTimesConformanceTest {

    /**
     * Half of USNO's one-minute publication granularity. Worst measured event: 27 s.
     * See the tolerance note above before changing this.
     */
    private val toleranceSeconds = 30

    private val json = Json { ignoreUnknownKeys = true }

    private val calculator = PanchangCalculator(MeeusEphemeris())

    @Serializable
    private data class Envelope(val records: List<UsnoDayRecord> = emptyList())

    @TestFactory
    fun `moonrise and moonset agree with USNO across the ten reference cities`(): List<DynamicTest> {
        val records = records("usno-grid-2026-04-14.json")
        assertEquals(
            ReferenceCities.ALL.size,
            records.size,
            "the golden grid no longer covers every reference city",
        )
        return records.map { rec ->
            val name = cityName(rec)
            DynamicTest.dynamicTest("$name ${rec.date}") {
                // Both events must exist and be single-valued here; if the golden file ever
                // stops saying so, fail loudly rather than quietly compare nothing.
                assertEquals(1, rec.moonRises.size, "$name: expected exactly one USNO moonrise")
                assertEquals(1, rec.moonSets.size, "$name: expected exactly one USNO moonset")
                assertAgrees(rec, name)
            }
        }
    }

    /**
     * The polar probes, where the Moon's shallow approach to the horizon is worst and where the
     * answer is sometimes "no event" rather than a time.
     *
     * These rows are the reason [RiseSet] is a sealed type. On 2026-06-21 McMurdo has a moonrise
     * and no moonset, and on 2026-12-21 the Moon is continuously above the horizon at
     * Longyearbyen and continuously below it at McMurdo. A moonrise routine returning `null` or
     * `0.0` for all three would be indistinguishable from one that is simply broken, and the
     * "fast until moonrise" rule cannot be evaluated safely without the distinction.
     */
    @TestFactory
    fun `moon rise and set at the polar probes match USNO including the no-event cases`(): List<DynamicTest> {
        return listOf("usno-polar-2026-06-21.json", "usno-polar-2026-12-21.json")
            .flatMap { file -> records(file).map { rec -> file to rec } }
            .map { (file, rec) ->
                DynamicTest.dynamicTest("${cityName(rec)} ${rec.date} ($file)") {
                    assertAgrees(rec, cityName(rec))
                }
            }
    }

    // ── Comparison ──────────────────────────────────────────────────────────────────────────

    private fun assertAgrees(rec: UsnoDayRecord, name: String) {
        val zone = fixedOffsetZone(rec.utcOffsetHours)
        val location = GeoLocation.of(rec.latitudeDeg, rec.longitudeDeg, zone.id)
        val times = calculator.moonTimes(LocalDate.parse(rec.date), location)
        assertMatches(name, "moonrise", times.moonrise, rec.moonRises, rec.moonCondition, zone)
        assertMatches(name, "moonset", times.moonset, rec.moonSets, rec.moonCondition, zone)
    }

    /**
     * One event against USNO's list for it.
     *
     * USNO expresses "there is no such event" in two different ways, and they mean different
     * things: an empty list with a `moonCondition` is the polar case, and an empty list without
     * one is the ordinary once-a-month day where moonrise has slipped past midnight. Mapping
     * both onto "no time" would let a genuine polar misclassification pass.
     */
    private fun assertMatches(
        city: String,
        label: String,
        actual: RiseSet,
        expected: List<String>,
        condition: PolarCondition?,
        zone: ZoneId,
    ) {
        if (expected.isEmpty()) {
            val want = when (condition) {
                PolarCondition.CONTINUOUSLY_ABOVE_HORIZON -> RiseSet.CircumpolarUp
                PolarCondition.CONTINUOUSLY_BELOW_HORIZON -> RiseSet.CircumpolarDown
                else -> RiseSet.NoEventInWindow
            }
            assertEquals(want, actual, "$city $label: USNO reports no event (condition=$condition)")
            return
        }
        // A day with two of the same event is legitimate but is not what these files contain;
        // silently comparing against the first would hide the second.
        assertEquals(1, expected.size, "$city $label: USNO lists ${expected.size} events")

        val jdUt = (actual as? RiseSet.At)?.jdUt
        assertTrue(jdUt != null) {
            "$city $label: USNO has an event at ${expected.single()}, we returned $actual"
        }
        val zoned = TimeScale.zonedDateTime(jdUt!!, zone)
        val ourSeconds = zoned.hour * 3600 + zoned.minute * 60 + zoned.second
        val (hour, minute) = expected.single().split(":").map { it.toInt() }
        val delta = ourSeconds - (hour * 3600 + minute * 60)
        assertTrue(abs(delta) <= toleranceSeconds) {
            "%s %s: computed %02d:%02d:%02d, USNO %s, delta %+d s (tolerance +-%d s)"
                .format(city, label, zoned.hour, zoned.minute, zoned.second, expected.single(), delta, toleranceSeconds)
        }
    }

    // ── Golden data ─────────────────────────────────────────────────────────────────────────

    private fun records(fileName: String): List<UsnoDayRecord> =
        json.decodeFromString(Envelope.serializer(), goldenFile(fileName).readText()).records

    /**
     * The golden file, found by walking up from the test's working directory.
     *
     * Gradle runs `:verify:test` with the module directory as the working directory, so
     * `golden/<name>` resolves on the first step; the walk exists so that running this class
     * from an IDE, which usually picks the root, finds the same single physical file rather
     * than failing or, worse, finding a copy.
     */
    private fun goldenFile(name: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            for (relative in listOf("golden", "verify/golden")) {
                val candidate = File(dir, "$relative/$name")
                if (candidate.isFile) return candidate
            }
            dir = dir.parentFile
        }
        error("could not find verify/golden/$name from ${System.getProperty("user.dir")}")
    }

    /**
     * The fixed offset USNO was asked for, as a zone.
     *
     * Deliberately not the city's IANA zone. USNO has no notion of a zone, only of the numeric
     * offset it was handed, so the offset it echoed back is the only definition of "local" that
     * its printed times are consistent with.
     */
    private fun fixedOffsetZone(offsetHours: Double): ZoneId =
        ZoneOffset.ofTotalSeconds((offsetHours * 3600.0).roundToInt())

    private fun cityName(rec: UsnoDayRecord): String =
        (ReferenceCities.ALL + ReferenceCities.POLAR_PROBES).firstOrNull {
            abs(it.latitudeDeg - rec.latitudeDeg) < 1e-6 && abs(it.longitudeDeg - rec.longitudeDeg) < 1e-6
        }?.displayName ?: "${rec.latitudeDeg}, ${rec.longitudeDeg}"
}
