package org.panchang.ephemeris

import kotlin.math.cos

/**
 * The VSOP87D series for the Earth's heliocentric position, loaded from the vendored
 * coefficient table and evaluated in full.
 *
 * ## Source
 *
 * Bretagnon P., Francou G., *Astron. Astrophys.* **202**, 309 (1988), CDS VizieR catalogue
 * **VI/81**. The file `VSOP87D.ear` in this module's resources is a byte-for-byte copy of
 * `vendor/vsop87_VSOP87D.ear`; see `vendor/PROVENANCE.md` for its retrieval. It is copied
 * rather than read from `vendor/` because a published artefact must not depend on a
 * sibling directory that is not on the classpath, and `vendor/` is input data that nothing
 * at runtime may reach into.
 *
 * Variant **D** is heliocentric *spherical* (L, B, R) referred to the **mean equinox and
 * ecliptic of date**. That is the whole reason this variant was chosen: no precession
 * matrix is needed to get from the theory's frame to the frame the [Ephemeris] contract
 * asks for. What still has to be applied on top is nutation, aberration and the small
 * dynamical→FK5 rotation — see [Vsop87Ephemeris].
 *
 * ## Record format
 *
 * From `vendor/vsop87_vsop87.txt`, verbatim:
 *
 * ```
 * HEADER RECORD  Fortran format : 17x,i1,4x,a7,12x,i1,17x,i1,i7
 *   iv col 18 | bo col 23-29 | ic col 42 | it col 60 | in col 61-67
 * TERM RECORD    Fortran format : 1x,4i1,i5,12i3,f15.11,2f18.11,f14.11,f20.11
 *   iv col 2 | ib col 3 | ic col 4 | it col 5 | n col 6-10 | a col 11-46
 *   S col 47-61 | K col 62-79 | A col 80-97 | B col 98-111 | C col 112-131
 * ```
 *
 * Those are **1-based inclusive Fortran columns**, and the parser below uses them
 * literally. It does not split on whitespace. In the wider VSOP87 files adjacent numeric
 * fields butt up against each other with no separator — the 12 integer arguments `a` in
 * particular run together once any of them reaches three digits — so whitespace splitting
 * silently produces the wrong field count on exactly the rows where it matters and
 * right-looking output everywhere else.
 *
 * ## Evaluation
 *
 * Each series is `Σ_n T^n · Σ_i A_i · cos(B_i + C_i · T)`, where **T is Julian millennia
 * of TT since J2000.0** — `(jdTt − 2451545.0) / 365250`, not centuries. Feeding centuries
 * instead is the standard way to get this wrong: it is right at J2000 to machine precision
 * and drifts smoothly and enormously away from it, so a spot check at the epoch cannot
 * detect it. The check-value test asserts at ten epochs spanning 1099–2000 precisely so
 * that a wrong time unit cannot pass.
 *
 * L and B come out in radians, R in astronomical units.
 *
 * ## Purity and cost
 *
 * The table is parsed once, lazily, into flat `DoubleArray`s of `(A, B, C)` triples and
 * never mutated. Evaluation allocates nothing. There are 2425 terms in total; a full
 * L+B+R evaluation is a few tens of microseconds, which is immaterial next to the
 * bisection loops in `:core` that call it.
 */
object Vsop87 {

    /** Julian days per Julian millennium — the time unit of the VSOP87 series argument. */
    const val DAYS_PER_MILLENNIUM: Double = 365_250.0

    /** Julian millennia of TT since J2000.0. The argument of every VSOP87 series. */
    fun millenniaSinceJ2000(jdTt: Double): Double =
        (jdTt - TimeScale.J2000) / DAYS_PER_MILLENNIUM

    /** Earth's heliocentric longitude L, radians, mean ecliptic and equinox of date. */
    fun earthLongitudeRadians(tMillennia: Double): Double = evaluate(tables.l, tMillennia)

    /** Earth's heliocentric latitude B, radians, mean ecliptic and equinox of date. */
    fun earthLatitudeRadians(tMillennia: Double): Double = evaluate(tables.b, tMillennia)

    /** Earth's heliocentric radius vector R, astronomical units. */
    fun earthRadiusAu(tMillennia: Double): Double = evaluate(tables.r, tMillennia)

    /** Total number of periodic terms loaded, across L, B and R. Used by the load test. */
    val termCount: Int
        get() = tables.l.sumOf { it.size } / 3 +
            tables.b.sumOf { it.size } / 3 +
            tables.r.sumOf { it.size } / 3

    /** Term counts per power of T for one variable, for the load test to assert against. */
    fun termCountsByPower(variable: Int): List<Int> =
        when (variable) {
            1 -> tables.l
            2 -> tables.b
            3 -> tables.r
            else -> throw IllegalArgumentException("variable must be 1 (L), 2 (B) or 3 (R)")
        }.map { it.size / 3 }

    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * `Σ_n T^n · Σ_i A cos(B + C T)`, evaluated with the powers of T accumulated by Horner
     * from the highest power down, so no explicit `T^5` is ever formed.
     */
    private fun evaluate(series: Array<DoubleArray>, t: Double): Double {
        var result = 0.0
        for (power in series.indices.reversed()) {
            val terms = series[power]
            var sum = 0.0
            var i = 0
            while (i < terms.size) {
                sum += terms[i] * cos(terms[i + 1] + terms[i + 2] * t)
                i += 3
            }
            result = result * t + sum
        }
        return result
    }

    private class Tables(
        val l: Array<DoubleArray>,
        val b: Array<DoubleArray>,
        val r: Array<DoubleArray>,
    )

    private val tables: Tables by lazy { load() }

    private const val RESOURCE = "/org/panchang/ephemeris/VSOP87D.ear"

    /** Highest power of T present in VSOP87D; the file has blocks `*T**0` … `*T**5`. */
    private const val MAX_POWER = 5

    private fun load(): Tables {
        // [variable 1..3][power 0..5] -> flat list of A, B, C
        val accumulators = Array(3) { Array(MAX_POWER + 1) { mutableListOf<Double>() } }
        val declaredCounts = HashMap<Pair<Int, Int>, Int>()

        val stream = Vsop87::class.java.getResourceAsStream(RESOURCE)
            ?: error("VSOP87D coefficient table missing from resources at $RESOURCE")

        var currentVariable = -1
        var currentPower = -1
        var lineNumber = 0

        stream.bufferedReader(Charsets.US_ASCII).use { reader ->
            while (true) {
                val raw = reader.readLine() ?: break
                lineNumber++
                // readLine strips \n but not \r; the vendored file has CRLF endings and is
                // committed with -text so it keeps them on every platform.
                val line = raw.trimEnd('\r')
                if (line.isBlank()) continue

                if (line.contains("VSOP87 VERSION")) {
                    require(line.length >= 67) { "short header record at line $lineNumber" }
                    val body = line.substring(22, 29).trim()
                    require(body == "EARTH") {
                        "expected an EARTH-only table, found body '$body' at line $lineNumber"
                    }
                    currentVariable = line.substring(41, 42).trim().toInt()
                    currentPower = line.substring(59, 60).trim().toInt()
                    val declared = line.substring(60, 67).trim().toInt()
                    declaredCounts[currentVariable to currentPower] = declared
                    require(currentVariable in 1..3) {
                        "variable $currentVariable out of range at line $lineNumber"
                    }
                    require(currentPower in 0..MAX_POWER) {
                        "power $currentPower out of range at line $lineNumber"
                    }
                    continue
                }

                require(currentVariable > 0) { "term record before any header at line $lineNumber" }
                require(line.length >= 131) {
                    "term record at line $lineNumber is ${line.length} chars; " +
                        "columns 112-131 hold C, so 131 is the minimum"
                }

                // Cross-check the per-record variable/power against the block header. If the
                // file were ever concatenated or truncated mid-block this catches it.
                val recordVariable = line.substring(3, 4).toInt()
                val recordPower = line.substring(4, 5).toInt()
                require(recordVariable == currentVariable && recordPower == currentPower) {
                    "term record at line $lineNumber declares variable $recordVariable " +
                        "power $recordPower inside a variable $currentVariable " +
                        "power $currentPower block"
                }

                val a = line.substring(79, 97).trim().toDouble()
                val b = line.substring(97, 111).trim().toDouble()
                val c = line.substring(111, 131).trim().toDouble()

                val target = accumulators[currentVariable - 1][currentPower]
                target.add(a)
                target.add(b)
                target.add(c)
            }
        }

        // Every block must contain exactly as many terms as its own header claims.
        for ((key, declared) in declaredCounts) {
            val (variable, power) = key
            val actual = accumulators[variable - 1][power].size / 3
            require(actual == declared) {
                "VSOP87D variable $variable power $power: header declares $declared terms, " +
                    "read $actual"
            }
        }

        fun freeze(variable: Int): Array<DoubleArray> =
            Array(MAX_POWER + 1) { accumulators[variable - 1][it].toDoubleArray() }

        return Tables(l = freeze(1), b = freeze(2), r = freeze(3))
    }
}
