package org.panchang.core

import org.panchang.ephemeris.Ephemeris
import org.panchang.ephemeris.TimeScale
import java.time.LocalDate

/**
 * Computes panchanga primitives from an [Ephemeris] and a [GeoLocation].
 *
 * Pure calendar astronomy. There is nothing religious or sectarian in this class, and nothing
 * about it is specific to any sampradaya — it answers *when* a tithi ran, never *whether* it
 * qualifies for anything.
 *
 * ## Time scales
 *
 * Every public parameter and every returned instant is **UT**, expressed as a Julian Day and
 * named `jdUt`. The conversion to TT happens once, at the point each ephemeris call is made,
 * via [TimeScale.toTt]. There is no bare `jd` anywhere in this module; the app engine's single
 * largest systematic error was feeding UT-based Julian Days straight into TT-defined series,
 * which in 2026 is a ~69 s offset that grows quadratically away from the present.
 *
 * ## Thread safety
 *
 * Stateless and safe to share, provided the supplied [Ephemeris] honours its own purity
 * contract.
 */
class PanchangCalculator(
    val ephemeris: Ephemeris,
    val ayanamsha: Ayanamsha = Ayanamsha.LAHIRI,
    val reckoning: MonthReckoning = MonthReckoning.AMANTA,
    /**
     * Width of the bracket at which boundary bisection stops. The returned instant is the
     * bracket midpoint, so the error against the true root of the ephemeris-defined function is
     * at most half of this. It says nothing about physical accuracy — see [AngleCrossing].
     */
    val boundaryToleranceSeconds: Double = 1.0,
) {

    init {
        require(boundaryToleranceSeconds > 0.0 && boundaryToleranceSeconds.isFinite()) {
            "boundaryToleranceSeconds must be positive and finite, was $boundaryToleranceSeconds"
        }
    }

    private val toleranceDays: Double = boundaryToleranceSeconds / 86_400.0

    // ── Angles ──────────────────────────────────────────────────────────────────────────────

    /** Ayanamsha in degrees at the UT instant [jdUt]. */
    fun ayanamshaDegAt(jdUt: Double): Double =
        ayanamsha.degreesAt(TimeScale.toTt(jdUt, ephemeris))

    /** Sidereal longitude of the Sun in degrees, `[0, 360)`. */
    fun sunSiderealDeg(jdUt: Double): Double {
        val jdTt = TimeScale.toTt(jdUt, ephemeris)
        return TimeScale.normalizeDegrees(ephemeris.sunLongitude(jdTt) - ayanamsha.degreesAt(jdTt))
    }

    /** Sidereal longitude of the Moon in degrees, `[0, 360)`. Geocentric — see [Topocentric]. */
    fun moonSiderealDeg(jdUt: Double): Double {
        val jdTt = TimeScale.toTt(jdUt, ephemeris)
        return TimeScale.normalizeDegrees(ephemeris.moonLongitude(jdTt) - ayanamsha.degreesAt(jdTt))
    }

    /**
     * Moon−Sun elongation in degrees, `[0, 360)`. The quantity tithi and karana are defined on.
     *
     * No ayanamsha is subtracted, on purpose: it cancels exactly in a difference of longitudes,
     * so a tithi boundary is the same instant under every sidereal convention. Subtracting it
     * from both terms, as the app engine did, gives the same answer while hiding that fact.
     */
    fun elongationDeg(jdUt: Double): Double {
        val jdTt = TimeScale.toTt(jdUt, ephemeris)
        return TimeScale.normalizeDegrees(
            ephemeris.moonLongitude(jdTt) - ephemeris.sunLongitude(jdTt),
        )
    }

    /**
     * Sum of the sidereal longitudes of Sun and Moon in degrees, `[0, 360)`. The quantity yoga
     * is defined on. Unlike elongation, this does *not* cancel the ayanamsha — it doubles it —
     * so yoga boundaries move with the choice of [ayanamsha].
     */
    fun yogaSumDeg(jdUt: Double): Double {
        val jdTt = TimeScale.toTt(jdUt, ephemeris)
        val ayan = ayanamsha.degreesAt(jdTt)
        return TimeScale.normalizeDegrees(
            (ephemeris.moonLongitude(jdTt) - ayan) + (ephemeris.sunLongitude(jdTt) - ayan),
        )
    }

    // ── Angular elements ────────────────────────────────────────────────────────────────────

    /** The tithi running at [jdUt], with its exact start and end. */
    fun tithiAt(jdUt: Double): Tithi {
        val angle = elongationDeg(jdUt)
        val index = indexOf(angle, Tithi.SPAN_DEGREES, Tithi.COUNT)
        val (start, end) = boundaries(::elongationDeg, index, Tithi.COUNT, Tithi.SPAN_DEGREES, jdUt, "tithi")
        return Tithi(
            index = index,
            name = Tithi.nameOf(index),
            numberInPaksha = index % 15 + 1,
            paksha = Tithi.pakshaOf(index),
            startJdUt = start,
            endJdUt = end,
            elapsedFraction = elapsedFraction(angle, index, Tithi.SPAN_DEGREES),
        )
    }

    /** The nakshatra running at [jdUt], with its exact start and end. */
    fun nakshatraAt(jdUt: Double): Nakshatra {
        val angle = moonSiderealDeg(jdUt)
        val index = indexOf(angle, Nakshatra.SPAN_DEGREES, Nakshatra.COUNT)
        val (start, end) =
            boundaries(::moonSiderealDeg, index, Nakshatra.COUNT, Nakshatra.SPAN_DEGREES, jdUt, "nakshatra")
        val elapsed = elapsedFraction(angle, index, Nakshatra.SPAN_DEGREES)
        return Nakshatra(
            index = index,
            name = Nakshatra.NAMES[index],
            pada = (elapsed * 4).toInt().coerceIn(0, 3) + 1,
            startJdUt = start,
            endJdUt = end,
            elapsedFraction = elapsed,
        )
    }

    /** The yoga running at [jdUt], with its exact start and end. */
    fun yogaAt(jdUt: Double): Yoga {
        val angle = yogaSumDeg(jdUt)
        val index = indexOf(angle, Yoga.SPAN_DEGREES, Yoga.COUNT)
        val (start, end) = boundaries(::yogaSumDeg, index, Yoga.COUNT, Yoga.SPAN_DEGREES, jdUt, "yoga")
        val name = Yoga.NAMES[index]
        return Yoga(
            index = index,
            name = name,
            isInauspicious = name in Yoga.INAUSPICIOUS,
            startJdUt = start,
            endJdUt = end,
            elapsedFraction = elapsedFraction(angle, index, Yoga.SPAN_DEGREES),
        )
    }

    /** The karana running at [jdUt], with its exact start and end. */
    fun karanaAt(jdUt: Double): Karana {
        val angle = elongationDeg(jdUt)
        val index = indexOf(angle, Karana.SPAN_DEGREES, Karana.COUNT)
        val (start, end) =
            boundaries(::elongationDeg, index, Karana.COUNT, Karana.SPAN_DEGREES, jdUt, "karana")
        val name = Karana.nameOf(index)
        return Karana(
            index = index,
            name = name,
            isInauspicious = name in Karana.INAUSPICIOUS,
            startJdUt = start,
            endJdUt = end,
            elapsedFraction = elapsedFraction(angle, index, Karana.SPAN_DEGREES),
        )
    }

    // ── Lunar month ─────────────────────────────────────────────────────────────────────────

    /** Julian Day (UT) of the last new moon at or before [jdUt]. */
    fun previousNewMoon(jdUt: Double): Double = lastLunation(jdUt, 0.0, "new moon")

    /** Julian Day (UT) of the last full moon at or before [jdUt]. */
    fun previousFullMoon(jdUt: Double): Double = lastLunation(jdUt, 180.0, "full moon")

    /**
     * The lunar month containing [jdUt] under [reckoning] (defaulting to the instance's).
     *
     * Adhika detection follows the classical rule: a lunar month in which the Sun makes no
     * sidereal rashi ingress is intercalary. Both bounding new moons are located to the
     * configured tolerance and the Sun's rashi is evaluated at each — the app engine ran a
     * fixed-count bisection on a `> 180°` predicate instead, which is a coarser test of the
     * same thing.
     *
     * Kshaya maasa — the very rare *suppressed* month, when two ingresses fall inside one
     * lunation — is not detected. It last occurred in 1983 and next occurs in 2124, and getting
     * it right requires naming rules this module has no reason to hold yet. A month that would
     * be kshaya is reported as an ordinary month with the earlier of the two names.
     */
    fun lunarMonthAt(jdUt: Double, reckoning: MonthReckoning = this.reckoning): LunarMonth {
        val elongation = elongationDeg(jdUt)
        val paksha = if (elongation < 180.0) Paksha.SHUKLA else Paksha.KRISHNA

        val amantaStart = previousNewMoon(jdUt)
        val amantaEnd = nextLunation(amantaStart, 0.0, "new moon")

        val rashiAtStart = Rashi.indexOf(sunSiderealDeg(amantaStart))
        val rashiAtEnd = Rashi.indexOf(sunSiderealDeg(amantaEnd))
        val isAdhika = rashiAtStart == rashiAtEnd

        // The month is named for the rashi the Sun enters during it, which is one past the
        // rashi it occupied at the opening new moon. For an adhika month no ingress occurs, and
        // the same expression yields the name of the nija month that follows — which is the
        // name an adhika month takes.
        val amantaIndex = (rashiAtStart + 1) % Rashi.COUNT

        return when (reckoning) {
            MonthReckoning.AMANTA -> LunarMonth(
                index = amantaIndex,
                name = LunarMonth.displayName(amantaIndex, isAdhika),
                reckoning = reckoning,
                paksha = paksha,
                isAdhika = isAdhika,
                startJdUt = amantaStart,
                endJdUt = amantaEnd,
            )

            MonthReckoning.PURNIMANTA -> {
                // Purnimanta runs full moon to full moon, so its Krishna paksha precedes its
                // Shukla paksha. The fortnight amanta calls "Chaitra Krishna" is purnimanta's
                // "Vaishakha Krishna": one month later, and only during Krishna paksha.
                val index = if (paksha == Paksha.KRISHNA) {
                    (amantaIndex + 1) % LunarMonth.NAMES.size
                } else {
                    amantaIndex
                }
                val start = previousFullMoon(jdUt)
                val end = nextLunation(start, 180.0, "full moon")
                LunarMonth(
                    index = index,
                    name = LunarMonth.displayName(index, isAdhika),
                    reckoning = reckoning,
                    paksha = paksha,
                    isAdhika = isAdhika,
                    startJdUt = start,
                    endJdUt = end,
                )
            }
        }
    }

    // ── Solar and lunar events ──────────────────────────────────────────────────────────────

    /**
     * Sunrise, sunset, solar noon and arunodaya for the civil day [date] at [location].
     *
     * The search window is `[start of date, start of date + 1)` **in the site's zone**, so a
     * returned sunrise always falls on the requested local date, including on the 23- and
     * 25-hour civil days either side of a DST transition.
     *
     * @param refinementPasses exposed only so the regression test for the engine's un-iterated
     *   sunset can compare a single pass against convergence. Pass the default in production.
     */
    @JvmOverloads
    fun sunTimes(
        date: LocalDate,
        location: GeoLocation,
        refinementPasses: Int = DEFAULT_REFINEMENT_PASSES,
    ): SunTimes {
        val windowStart = location.jdUtAtStartOfDay(date)
        val windowEnd = location.jdUtAtEndOfDay(date)
        val solver = sunSolver(location)
        val sunrise = solver.solve(windowStart, windowEnd, RiseSetDirection.RISE, refinementPasses, toleranceDays)
        val sunset = solver.solve(windowStart, windowEnd, RiseSetDirection.SET, refinementPasses, toleranceDays)
        val arunodaya = when (sunrise) {
            is RiseSet.At ->
                RiseSet.At(sunrise.jdUt - SunTimes.ARUNODAYA_MINUTES_BEFORE_SUNRISE / 1440.0)
            else -> sunrise
        }
        return SunTimes(
            sunrise = sunrise,
            sunset = sunset,
            solarNoonJdUt = solver.transitInWindow(windowStart, windowEnd),
            arunodaya = arunodaya,
        )
    }

    /**
     * Moonrise and moonset for the civil day [date] at [location].
     *
     * Absent from the app engine entirely. Note that moonrise runs roughly 50 minutes later each
     * day, so about once a lunar month a civil day legitimately has no moonrise; that returns
     * [RiseSet.NoEventInWindow], which is distinct from the polar cases.
     */
    @JvmOverloads
    fun moonTimes(
        date: LocalDate,
        location: GeoLocation,
        refinementPasses: Int = DEFAULT_REFINEMENT_PASSES,
    ): MoonTimes {
        val windowStart = location.jdUtAtStartOfDay(date)
        val windowEnd = location.jdUtAtEndOfDay(date)
        val solver = moonSolver(location)
        return MoonTimes(
            moonrise = solver.solve(windowStart, windowEnd, RiseSetDirection.RISE, refinementPasses, toleranceDays),
            moonset = solver.solve(windowStart, windowEnd, RiseSetDirection.SET, refinementPasses, toleranceDays),
        )
    }

    /** Geometric altitude of the Sun's centre in degrees at [jdUt], excluding refraction. */
    fun sunAltitudeDeg(jdUt: Double, location: GeoLocation): Double =
        sunSolver(location).altitudeDeg(jdUt)

    /** Geometric altitude of the Moon's centre in degrees at [jdUt], excluding refraction. */
    fun moonAltitudeDeg(jdUt: Double, location: GeoLocation): Double =
        moonSolver(location).altitudeDeg(jdUt)

    /** The altitude at which the Sun is deemed to rise or set at [location]. */
    fun sunHorizonAltitudeDeg(location: GeoLocation): Double = Horizon.sunAltitudeDegrees(location)

    /** The altitude at which the Moon is deemed to rise or set at [location] and [jdUt]. */
    fun moonHorizonAltitudeDeg(jdUt: Double, location: GeoLocation): Double =
        Horizon.moonAltitudeDegrees(
            ephemeris.moonDistanceKm(TimeScale.toTt(jdUt, ephemeris)),
            location.elevationMeters,
        )

    // ── Aggregate ───────────────────────────────────────────────────────────────────────────

    /** The full panchang for one civil date at one site. */
    fun panchang(date: LocalDate, location: GeoLocation): Panchang {
        val windowStart = location.jdUtAtStartOfDay(date)
        val windowEnd = location.jdUtAtEndOfDay(date)
        val sun = sunTimes(date, location)
        val moon = moonTimes(date, location)

        // Panchanga elements are conventionally read at sunrise. With no sunrise there is no
        // conventional answer, so the midpoint of the civil day is used and said so.
        val reference = sun.sunrise.jdUtOrNull ?: ((windowStart + windowEnd) / 2.0)
        val vaar = Vaar.ofLocalDate(date)

        val sunriseJdUt = sun.sunrise.jdUtOrNull
        val sunsetJdUt = sun.sunset.jdUtOrNull
        val hasDaylight = sunriseJdUt != null && sunsetJdUt != null && sunsetJdUt > sunriseJdUt

        return Panchang(
            date = date,
            location = location,
            reckoning = reckoning,
            ayanamshaId = ayanamsha.id,
            ayanamshaDegrees = ayanamshaDegAt(reference),
            referenceJdUt = reference,
            vaar = vaar,
            sun = sun,
            moon = moon,
            tithi = tithiAt(reference),
            nakshatra = nakshatraAt(reference),
            yoga = yogaAt(reference),
            karana = karanaAt(reference),
            lunarMonth = lunarMonthAt(reference),
            rahuKaal = if (hasDaylight) DayDivisions.rahuKaal(vaar, sunriseJdUt!!, sunsetJdUt!!) else null,
            yamaganda = if (hasDaylight) DayDivisions.yamaganda(vaar, sunriseJdUt!!, sunsetJdUt!!) else null,
            gulika = if (hasDaylight) DayDivisions.gulika(vaar, sunriseJdUt!!, sunsetJdUt!!) else null,
            abhijit = if (hasDaylight) DayDivisions.abhijit(sunriseJdUt!!, sunsetJdUt!!) else null,
        )
    }

    // ── Internals ───────────────────────────────────────────────────────────────────────────

    /**
     * The Sun's ecliptic latitude is under one arcsecond and is taken as zero, which shifts a
     * computed sunrise by well under a second of time.
     */
    private fun sunSolver(location: GeoLocation) = RiseSetSolver(
        ephemeris = ephemeris,
        location = location,
        equatorialAt = { jdTt ->
            Coordinates.eclipticToEquatorial(
                ephemeris.sunLongitude(jdTt),
                0.0,
                ephemeris.nutationAndObliquity(jdTt).trueObliquity,
            )
        },
        horizonAltitudeAt = { Horizon.sunAltitudeDegrees(location) },
    )

    private fun moonSolver(location: GeoLocation) = RiseSetSolver(
        ephemeris = ephemeris,
        location = location,
        equatorialAt = { jdTt ->
            Coordinates.eclipticToEquatorial(
                ephemeris.moonLongitude(jdTt),
                ephemeris.moonLatitude(jdTt),
                ephemeris.nutationAndObliquity(jdTt).trueObliquity,
            )
        },
        horizonAltitudeAt = { jdTt ->
            Horizon.moonAltitudeDegrees(ephemeris.moonDistanceKm(jdTt), location.elevationMeters)
        },
    )

    private fun indexOf(angleDeg: Double, spanDeg: Double, divisions: Int): Int =
        (angleDeg / spanDeg).toInt().coerceIn(0, divisions - 1)

    /**
     * Start and end of the division [index] is in, as exact crossing instants.
     *
     * The start is the *latest* crossing of the opening angle at or before [jdUt] and the end
     * the *first* crossing of the closing angle after it, so the two always bracket [jdUt] and
     * `end > start` holds by construction. The closing angle of the last division is 0°, i.e.
     * the 360°/0° seam, which the solver handles as an ordinary case.
     */
    private fun boundaries(
        angleAt: (Double) -> Double,
        index: Int,
        divisions: Int,
        spanDeg: Double,
        jdUt: Double,
        label: String,
    ): Pair<Double, Double> {
        val startTarget = index * spanDeg
        val endTarget = ((index + 1) % divisions) * spanDeg
        val start = AngleCrossing.lastCrossingBefore(
            angleAt, startTarget, jdUt, ELEMENT_SEARCH_DAYS, ELEMENT_SCAN_STEP_DAYS, toleranceDays,
        ) ?: throw IllegalStateException(
            "no $label start crossing of $startTarget deg within $ELEMENT_SEARCH_DAYS days " +
                "before jdUt=$jdUt; the ephemeris is moving far slower than the real Sun and Moon",
        )
        val end = AngleCrossing.firstCrossing(
            angleAt, endTarget, jdUt, jdUt + ELEMENT_SEARCH_DAYS, ELEMENT_SCAN_STEP_DAYS, toleranceDays,
        ) ?: throw IllegalStateException(
            "no $label end crossing of $endTarget deg within $ELEMENT_SEARCH_DAYS days " +
                "after jdUt=$jdUt; the ephemeris is moving far slower than the real Sun and Moon",
        )
        return start to end
    }

    /** Fraction of the division's angular span already elapsed, clamped into `[0, 1)`. */
    private fun elapsedFraction(angleDeg: Double, index: Int, spanDeg: Double): Double {
        val delta = TimeScale.angleDifference(angleDeg, index * spanDeg)
        return (delta.coerceAtLeast(0.0) / spanDeg).coerceIn(0.0, NEARLY_ONE)
    }

    private fun lastLunation(jdUt: Double, targetDeg: Double, label: String): Double =
        AngleCrossing.lastCrossingBefore(
            ::elongationDeg, targetDeg, jdUt, LUNATION_SEARCH_DAYS, LUNATION_SCAN_STEP_DAYS, toleranceDays,
        ) ?: throw IllegalStateException(
            "no $label within $LUNATION_SEARCH_DAYS days before jdUt=$jdUt",
        )

    /**
     * The next occurrence of the same lunar phase after [fromJdUt], which must itself be an
     * occurrence. The search starts 25 days later because the synodic month is never shorter
     * than 29.2 days, which keeps exactly one crossing in the window and costs fewer
     * evaluations than scanning from the start.
     */
    private fun nextLunation(fromJdUt: Double, targetDeg: Double, label: String): Double =
        AngleCrossing.firstCrossing(
            ::elongationDeg,
            targetDeg,
            fromJdUt + 25.0,
            fromJdUt + LUNATION_SEARCH_DAYS,
            LUNATION_SCAN_STEP_DAYS,
            toleranceDays,
        ) ?: throw IllegalStateException(
            "no $label within ${LUNATION_SEARCH_DAYS - 25.0} days after jdUt=$fromJdUt",
        )

    companion object {
        /**
         * Outer refinement passes for rise and set. Convergence normally takes two; the extra
         * headroom costs a few ephemeris evaluations and covers high latitudes, where the
         * declination term is far more sensitive.
         */
        const val DEFAULT_REFINEMENT_PASSES: Int = 6

        /**
         * How far to look for an element boundary. The longest possible tithi is 1.12 days and
         * the longest nakshatra 1.14, so 2.5 days is roughly double the worst case.
         */
        const val ELEMENT_SEARCH_DAYS: Double = 2.5

        /**
         * Coarse bracketing step for element boundaries. The fastest of these angles advances
         * about 16.4°/day, so a quarter-day step moves it about 4° — two orders of magnitude
         * inside the 180° the wrap-safe sign test requires.
         */
        const val ELEMENT_SCAN_STEP_DAYS: Double = 0.25

        /** A synodic month is 29.27–29.83 days; 31 covers it with margin. */
        const val LUNATION_SEARCH_DAYS: Double = 31.0

        const val LUNATION_SCAN_STEP_DAYS: Double = 0.25

        /** Largest double strictly below 1.0, for clamping a half-open fraction. */
        private val NEARLY_ONE: Double = Math.nextDown(1.0)
    }
}
