package org.panchang.ephemeris

/**
 * How a particular date's ΔT was arrived at.
 *
 * This exists because the honest tolerance on a published boundary time is not the same
 * for next month and for 2100, and the difference is not the ephemeris — it is whether
 * anybody has yet *measured* how fast the Earth was turning. Callers that state a
 * tolerance must be able to ask.
 *
 * The bands are ordered from best-known to least-known.
 */
enum class DeltaTQuality {

    /**
     * Interpolated from IERS-determined UT1−UTC. This is a measurement of the Earth's
     * rotation, not a model of it.
     *
     * Residual error is the monthly downsampling of the source, measured against every one
     * of its 19941 daily rows at max 0.0068 s / rms 0.0016 s. That is 0.003″ of lunar
     * longitude — three orders of magnitude below anything else in this module.
     */
    OBSERVED,

    /**
     * Inside IERS Bulletin A's own ~1 year prediction window. IERS quotes a formal error
     * that reaches roughly 0.05 s of UT1 at one year out, i.e. 0.05 s of ΔT.
     */
    PREDICTED,

    /**
     * Past the end of the IERS record, continued by this module: a parabola matched in
     * value and slope to the end of the record, curving at the long-term tidal rate.
     *
     * **This is a forecast of an unforecastable quantity.** Its uncertainty is roughly
     * ±10 s by 2100 — see [ObservedDeltaT.EXTRAPOLATION_CURVATURE_S_PER_YR2] — which is
     * ±20 s of tithi boundary. No amount of ephemeris work reduces it.
     */
    EXTRAPOLATED,

    /**
     * Before the IERS record begins (1973-01-02). The Espenak & Meeus piecewise polynomial
     * fit to the historical record is used, tapered onto the IERS value at the seam.
     *
     * Uncertainty follows that fit: sub-second back to ~1955, order 1 s to 1600, tens of
     * seconds to minutes before 900, and hours at the extremes of its −1999..+3000 range.
     */
    HISTORICAL_FIT,
}

/**
 * ΔT = TT − UT1 from **observed** Earth orientation, as published by the IERS.
 *
 * ## Why this exists
 *
 * ΔT is not computable. It is the accumulated difference between atomic time and the
 * Earth's actual rotation, and the Earth's rotation is measured, not predicted. Any
 * polynomial "for ΔT" is a fit to past measurements plus an extrapolation, and the
 * extrapolation goes stale. The Espenak & Meeus 2005–2050 branch in [DeltaT] was fitted in
 * 2006 assuming ΔT would keep rising; the Earth instead sped up, and by mid-2026 that
 * branch returns ~75.2 s against an observed ~69.2 s — a +6 s bias on every civil time this
 * service publishes.
 *
 * This object removes that bias for every date the IERS has actually measured.
 *
 * ## Source and derivation
 *
 * The resource `deltat-iers.csv` carries its own provenance header — source URL, HTTP
 * status, retrieval timestamp, byte count and SHA-256 of the file it was derived from.
 * Read it; it is the audit trail, and [sourceUrl] / [retrievedUtc] re-expose it in code.
 *
 * IERS publishes **UT1−UTC**, not ΔT. The conversion is
 *
 * ```
 * ΔT = 32.184 + (TAI − UTC) − (UT1 − UTC)
 * ```
 *
 * where 32.184 s is the fixed TT−TAI offset and TAI−UTC is the integer leap-second count:
 * 10 s from 1972-01-01, stepping by 1 s at 27 further epochs to 37 s from 2017-01-01.
 *
 * The leap-second step is applied **at each source row's own date**, during generation.
 * That matters more than it looks. UT1−UTC sawtooths by a full second at every leap second;
 * the leap-second count steps by the same second at the same instant; the two cancel and
 * ΔT comes out smooth. Get the table wrong by one entry — or apply a single modern value to
 * the whole history — and the result is a clean 1-second staircase that looks like plausible
 * data and quietly moves every published time by a second in the affected decades.
 *
 * That the derivation is right is checked, not asserted: ΔT(2000-01-01) comes out 63.83 s
 * and ΔT(2020-01-01) 69.36 s, both matching the independently published values. Those are
 * pinned in `ObservedDeltaTTest`.
 *
 * ## Sampling
 *
 * Monthly. ΔT changes by about a millisecond a day, so daily rows would be 30× the size for
 * nothing. The generator measured the cost of that choice against all 19941 daily source
 * values: max 0.0068 s, rms 0.0016 s.
 *
 * ## Purity
 *
 * The resource is read once, lazily, into immutable arrays. No network, ever — the build
 * and the runtime both work offline, which is the point of committing the data.
 */
object ObservedDeltaT {

    /**
     * Curvature of the post-record continuation, in s/yr², i.e. half of d²ΔT/dy².
     *
     * This is the second derivative of the long-term parabola ΔT = −20 + 32u²,
     * u = (year − 1820)/100, which Espenak & Meeus (after Morrison & Stephenson) use to
     * express the secular slowing of the Earth's rotation by tidal friction: 32 s per
     * century² gives d²ΔT/dy² = 0.0064 s/yr², and half of that is the quadratic
     * coefficient.
     *
     * ## What this is and is not
     *
     * The *curvature* is physics — tidal braking is steady and well determined. The
     * *slope* at the seam is not: it is the decadal core-mantle variation, which is what
     * actually dominates over a human lifetime and which nobody can predict. So the
     * continuation takes its curvature from theory and its level and slope from the end of
     * the observed record, rather than inheriting a 2006-era polynomial's opinion of both.
     *
     * ## Honest uncertainty
     *
     * The seam slope is a least-squares fit over the final 10 years of observed data. Refit
     * over 5, 15 and 20 year windows and it ranges from −0.037 to +0.238 s/yr. Propagated
     * to 2100 that alone spans about 20 s, so **±10 s at 2100** is the realistic figure, and
     * it is ±20 s of tithi boundary time. Longer-baseline reconstructions of past decadal
     * variation give the same order.
     *
     * For scale: at 2100 this continuation gives ≈88 s. Espenak & Meeus' extrapolation
     * gives ≈203 s. They cannot both be close, and the 115 s between them is not a
     * disagreement about astronomy — it is a disagreement about the Earth.
     */
    const val EXTRAPOLATION_CURVATURE_S_PER_YR2: Double = 0.0032

    /**
     * Years over which the Espenak & Meeus fit is blended onto the IERS value at the start
     * of the record, so ΔT has no step at 1973-01-02.
     *
     * The two disagree there by only ~0.06 s, but a step of any size in ΔT is unacceptable:
     * every boundary time this service publishes is found by bisecting a function of civil
     * time through the UT↔TT mapping, and a bisection that straddles a discontinuity cannot
     * converge. Twenty years spreads 0.06 s over a slope change of 0.003 s/yr, which is
     * three orders of magnitude below the fit's own uncertainty in that era.
     */
    const val SEAM_TAPER_YEARS: Double = 20.0

    /** JD − MJD. */
    private const val MJD_OFFSET: Double = 2_400_000.5

    /** Modified Julian Date of a Julian Day. */
    fun modifiedJulianDate(jd: Double): Double = jd - MJD_OFFSET

    /** URL the underlying UT1−UTC record was retrieved from. */
    val sourceUrl: String get() = table.sourceUrl

    /** UTC timestamp of that retrieval, ISO-8601. */
    val retrievedUtc: String get() = table.retrievedUtc

    /** HTTP status of that retrieval. */
    val httpStatus: Int get() = table.httpStatus

    /** SHA-256 of the source file the table was derived from. */
    val sourceSha256: String get() = table.sourceSha256

    /** MJD of the first row; before this there is no IERS UT1 in the source. */
    val firstMjd: Double get() = table.mjd.first()

    /** MJD through which UT1−UTC is IERS-*determined* rather than predicted. */
    val observedThroughMjd: Double get() = table.observedThroughMjd

    /** MJD of the last row, the end of IERS Bulletin A's prediction window. */
    val predictedThroughMjd: Double get() = table.predictedThroughMjd

    /** Number of interpolation nodes in the table. */
    val nodeCount: Int get() = table.mjd.size

    /** Least-squares ΔT slope over the last decade of observed data, s/yr. */
    val extrapolationSlopeSecondsPerYear: Double get() = table.slopeSecondsPerYear

    /** Which band [jdUt] falls in. See [DeltaTQuality]. */
    fun qualityAtJd(jdUt: Double): DeltaTQuality {
        val mjd = modifiedJulianDate(jdUt)
        return when {
            mjd < table.mjd.first() -> DeltaTQuality.HISTORICAL_FIT
            mjd <= table.observedThroughMjd -> DeltaTQuality.OBSERVED
            mjd <= table.predictedThroughMjd -> DeltaTQuality.PREDICTED
            else -> DeltaTQuality.EXTRAPOLATED
        }
    }

    /** True if [jdUt] is inside the tabulated record (observed or IERS-predicted). */
    fun covers(jdUt: Double): Boolean {
        val mjd = modifiedJulianDate(jdUt)
        return mjd >= table.mjd.first() && mjd <= table.predictedThroughMjd
    }

    /**
     * ΔT in seconds at [jdUt], from the table where it reaches and from the parabolic
     * continuation past its end.
     *
     * **Undefined before [firstMjd]** — it clamps to the first node rather than
     * extrapolating backwards, because the sensible thing to do there is the Espenak &
     * Meeus fit and that composition lives in [DeltaT.secondsAtJd]. Call that, not this,
     * unless you specifically want the IERS-only answer.
     */
    fun secondsAtJd(jdUt: Double): Double {
        val mjd = modifiedJulianDate(jdUt)
        val nodes = table.mjd
        val values = table.deltaT

        if (mjd <= nodes.first()) return values.first()

        if (mjd >= nodes.last()) {
            val dy = (mjd - nodes.last()) / 365.25
            return values.last() +
                table.slopeSecondsPerYear * dy +
                EXTRAPOLATION_CURVATURE_S_PER_YR2 * dy * dy
        }

        // Binary search for the bracketing pair. Nodes are strictly increasing.
        var lo = 0
        var hi = nodes.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (nodes[mid] <= mjd) lo = mid else hi = mid
        }
        val f = (mjd - nodes[lo]) / (nodes[hi] - nodes[lo])
        return values[lo] + f * (values[hi] - values[lo])
    }

    /**
     * ΔT at the first tabulated instant, used by [DeltaT] to anchor the pre-1973 taper.
     */
    val firstSeconds: Double get() = table.deltaT.first()

    // ─────────────────────────────────────────────────────────────────────────────

    private class Table(
        val mjd: DoubleArray,
        val deltaT: DoubleArray,
        val observedThroughMjd: Double,
        val predictedThroughMjd: Double,
        val slopeSecondsPerYear: Double,
        val sourceUrl: String,
        val retrievedUtc: String,
        val httpStatus: Int,
        val sourceSha256: String,
    )

    private const val RESOURCE = "/org/panchang/ephemeris/deltat-iers.csv"

    private val table: Table by lazy { load() }

    private fun load(): Table {
        val stream = ObservedDeltaT::class.java.getResourceAsStream(RESOURCE)
            ?: error("IERS ΔT table missing from resources at $RESOURCE")

        val mjd = ArrayList<Double>(700)
        val values = ArrayList<Double>(700)
        val header = HashMap<String, String>()

        stream.bufferedReader(Charsets.US_ASCII).use { reader ->
            while (true) {
                val raw = reader.readLine() ?: break
                val line = raw.trimEnd('\r').trim()
                if (line.isEmpty()) continue
                if (line.startsWith("#")) {
                    // "# key: value ..." — the first token of the value is what is wanted;
                    // everything after it is prose, e.g. the human-readable date.
                    val body = line.removePrefix("#").trim()
                    val colon = body.indexOf(':')
                    if (colon > 0) {
                        val key = body.substring(0, colon).trim()
                        val value = body.substring(colon + 1).trim()
                        if (key !in header) header[key] = value
                    }
                    continue
                }
                val comma = line.indexOf(',')
                require(comma > 0) { "malformed ΔT row: '$line'" }
                mjd.add(line.substring(0, comma).toDouble())
                values.add(line.substring(comma + 1).toDouble())
            }
        }

        require(mjd.size >= 2) { "ΔT table has ${mjd.size} rows; at least 2 are needed" }
        for (i in 1 until mjd.size) {
            require(mjd[i] > mjd[i - 1]) {
                "ΔT table is not strictly increasing in MJD at row $i (${mjd[i - 1]} then ${mjd[i]})"
            }
        }

        fun required(key: String): String =
            header[key] ?: error("ΔT table header is missing '$key'")

        fun firstToken(key: String): String = required(key).substringBefore(' ')

        return Table(
            mjd = mjd.toDoubleArray(),
            deltaT = values.toDoubleArray(),
            observedThroughMjd = firstToken("observed_through_mjd").toDouble(),
            predictedThroughMjd = firstToken("predicted_through_mjd").toDouble(),
            slopeSecondsPerYear = firstToken("extrapolation_slope_s_per_yr").toDouble(),
            sourceUrl = required("source_url"),
            retrievedUtc = required("retrieved_utc"),
            httpStatus = firstToken("http_status").toInt(),
            sourceSha256 = firstToken("source_sha256"),
        )
    }
}
