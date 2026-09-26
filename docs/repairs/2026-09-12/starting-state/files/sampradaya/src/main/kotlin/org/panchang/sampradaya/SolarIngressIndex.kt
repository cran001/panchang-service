package org.panchang.sampradaya

import java.time.LocalDate
import org.panchang.core.PanchangCalculator

/**
 * The sidereal sign ingresses — the Sankranti instants — over one [LunarDayIndex] window.
 *
 * Serves [EventRule.OnSolarMonth], the rule form the solar calendars (Tamil, Malayalam, Bengali,
 * Odia, and the pan-Indian Sankranti observances) date their festivals by. The Sun's sidereal
 * longitude crosses a multiple of 30° roughly every thirty days, and those crossings are the
 * month boundaries of a solar calendar the way new moons are of a lunar one.
 *
 * ## How the instants are found
 *
 * The window is walked day by day, evaluating the Sun's sidereal longitude at each day's solar
 * noon. The Sun covers about one degree a day against a thirty-degree sign, so a sign change
 * between two consecutive samples is always exactly one ingress and never two; the instant
 * itself is then pinned by bisection between the two samples to well under a second. Sampling at
 * solar noon is a phase choice, not a semantic one — the ingress instant is a single global
 * moment, and any evenly spaced samples that straddle it find the same one.
 *
 * ## Cost
 *
 * One `sunSiderealDeg` per day of the window (about 490 evaluations) plus about forty bisection
 * steps per ingress, twelve ingresses. That is cheap beside the `LunarDayIndex` it is built
 * over, and it is memoised per index by [EventResolver] so a catalog of several solar rules pays
 * for it once.
 *
 * ## Sites with no sunrise
 *
 * [SolarTransition.SANKRANTI_AT_SUNRISE] needs a sunrise to compare against; a day without one
 * is stepped over rather than substituted for, because which instant replaces a sunrise inside
 * the polar circles is a tradition's ruling, not this class's. The site-acceptance layer refuses
 * such latitudes outright, so reaching that branch means the gate was bypassed.
 */
class SolarIngressIndex private constructor(
    /** Every ingress instant by rashi index entered, 0 = Mesha, in time order. */
    private val ingressesBySign: Map<Int, List<Double>>,
    private val index: LunarDayIndex,
) {

    /**
     * Every instant the Sun entered [signIndex] (0 = Mesha) inside the window, in time order.
     *
     * A list, not a single instant, because the resolver's window runs about sixty days past
     * each side of the year: the signs whose ingress falls in November–February occur *twice*
     * in it, and a solar rule for the requested year wants the occurrence that lands inside
     * that year — the first for a November request, the second for a February one.
     */
    fun ingressesOf(signIndex: Int): List<Double> = ingressesBySign[signIndex].orEmpty()

    /** The earliest ingress of [signIndex] in the window, or null when the Sun never enters it. */
    fun ingressOf(signIndex: Int): Double? = ingressesOf(signIndex).firstOrNull()

    /**
     * Every day the solar month [signIndex] opens on within the window, under [transition],
     * in time order — one per ingress, so usually one and twice for the edge-of-window signs.
     */
    fun monthStartDaysOf(signIndex: Int, transition: SolarTransition): List<LocalDate> =
        ingressesOf(signIndex).mapNotNull { ingress -> monthStartDayOf(ingress, transition) }

    /** The first day [signIndex]'s solar month opens on, or null. See [monthStartDaysOf]. */
    fun monthStartDayOf(signIndex: Int, transition: SolarTransition): LocalDate? =
        monthStartDaysOf(signIndex, transition).firstOrNull()

    private fun monthStartDayOf(ingress: Double, transition: SolarTransition): LocalDate? {
        return when (transition) {
            SolarTransition.SANKRANTI_START -> index.location.localDate(ingress)

            SolarTransition.SANKRANTI_AT_SUNRISE -> {
                // The first day whose sunrise follows the ingress. An ingress before that day's
                // sunrise opens the month on the same day; a later one pushes it to the next.
                // Days without a sunrise are stepped over, not substituted for.
                var day = index.location.localDate(ingress)
                var guard = 0
                while (guard < MAX_DAYS_WITHOUT_SUNRISE) {
                    val sunrise = index.sunriseOf(day)
                    if (sunrise != null && sunrise > ingress) return day
                    day = day.plusDays(1)
                    guard++
                }
                null
            }
        }
    }

    companion object {

        /**
         * How far [SolarTransition.SANKRANTI_AT_SUNRISE] will walk forward over sunrise-less days
         * before giving up. Any real site has a sunrise within a few days; the guard exists so a
         * bypassed acceptance gate surfaces as a null rather than a loop.
         */
        private const val MAX_DAYS_WITHOUT_SUNRISE: Int = 10

        /** Bisection stops once the bracket is this tight, in days; well under a millisecond. */
        private const val BISECTION_TOLERANCE_DAYS: Double = 1e-6

        fun build(index: LunarDayIndex): SolarIngressIndex {
            val calculator: PanchangCalculator = index.ctx.calculator

            // Solar noon comes off the SunTimes the LunarDayIndex already solved for every day
            // of its window, so the walk below costs no rise/set solves of its own.
            fun noonOf(day: LocalDate): Double = index.sunTimesOf(day).solarNoonJdUt

            val ingressesBySign = LinkedHashMap<Int, MutableList<Double>>()
            var day = index.windowStart
            var previousSign = signOf(calculator, noonOf(day))
            while (day.isBefore(index.windowEnd)) {
                val nextDay = day.plusDays(1)
                val nextSign = signOf(calculator, noonOf(nextDay))
                if (nextSign != previousSign) {
                    ingressesBySign.getOrPut(nextSign) { ArrayList() } += bisect(
                        calculator,
                        noonOf(day),
                        noonOf(nextDay),
                        nextSign,
                    )
                }
                previousSign = nextSign
                day = nextDay
            }
            return SolarIngressIndex(ingressesBySign, index)
        }

        /** The rashi index (0 = Mesha) the Sun's sidereal longitude places it in at [jdUt]. */
        private fun signOf(calculator: PanchangCalculator, jdUt: Double): Int {
            val longitude = calculator.sunSiderealDeg(jdUt)
            return Math.floorMod(longitude.toInt() / 30, 12)
        }

        /**
         * The instant between [loJdUt] and [hiJdUt] at which the Sun enters [signIndex].
         *
         * Plain bisection on the sidereal longitude: the longitude is monotone over a one-day
         * bracket to far better than the tolerance, so no root-finder cleverer than this is
         * needed. The caller guarantees the sign differs at the two ends.
         */
        private fun bisect(
            calculator: PanchangCalculator,
            loJdUt: Double,
            hiJdUt: Double,
            signIndex: Int,
        ): Double {
            var lo = loJdUt
            var hi = hiJdUt
            while (hi - lo > BISECTION_TOLERANCE_DAYS) {
                val mid = (lo + hi) / 2.0
                if (signOf(calculator, mid) == signIndex) hi = mid else lo = mid
            }
            return (lo + hi) / 2.0
        }
    }
}
