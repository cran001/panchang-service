package org.panchang.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.panchang.ephemeris.TimeScale
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * Solar events, including the regression test for the defect that motivated most of this
 * module: the engine's sunset was computed in a single pass while its sunrise was iterated, so
 * sunset carried a systematic error that then propagated into Abhijit, Rahu Kaal and the
 * parana cap.
 */
class SunTimesTest {

    private val oneSecond = 1.0 / 86_400.0
    private val ephemeris = LinearEphemeris()
    private val calculator = PanchangCalculator(ephemeris)

    /** A date in the middle of the tested year, fixed so the tests read the same. */
    private val referenceDate = LocalDate.of(2026, 5, 12)

    private val midLatitude = GeoLocation(45.0, 0.0, ZoneId.of("UTC"))
    private val highLatitude = GeoLocation(55.0, 0.0, ZoneId.of("UTC"))

    @Test
    fun `sunrise precedes solar noon precedes sunset`() {
        val date = referenceDate
        val times = calculator.sunTimes(date, midLatitude)

        val sunrise = requireAt(times.sunrise)
        val sunset = requireAt(times.sunset)
        assertTrue(sunrise < times.solarNoonJdUt) { "sunrise $sunrise not before noon ${times.solarNoonJdUt}" }
        assertTrue(times.solarNoonJdUt < sunset) { "noon ${times.solarNoonJdUt} not before sunset $sunset" }
        assertTrue(times.daylightDays!! in 0.2..0.8)
    }

    /**
     * Defect #1, stated as a physical condition rather than as a difference between two of our
     * own numbers: at the reported sunrise and at the reported sunset the Sun's altitude must
     * actually equal the horizon altitude. A converged solution satisfies this; a single pass
     * does not, because it evaluates the declination at noon and then never revisits it.
     */
    @Test
    fun `converged sunrise and sunset sit exactly on the horizon`() {
        val horizon = calculator.sunHorizonAltitudeDeg(highLatitude)
        for (offset in listOf(0L, 30L, 91L, 182L, 273L)) {
            val date = referenceDate.plusDays(offset)
            val times = calculator.sunTimes(date, highLatitude)
            for (event in listOf(times.sunrise, times.sunset)) {
                val jdUt = requireAt(event)
                val residual = abs(calculator.sunAltitudeDeg(jdUt, highLatitude) - horizon)
                assertTrue(residual < 1e-5) {
                    "altitude residual $residual deg at $jdUt on $date is not on the horizon"
                }
            }
        }
    }

    @Test
    fun `a single pass is measurably wrong and iterating fixes it`() {
        // Near an equinox the solar declination moves fastest, so evaluating it at noon instead
        // of at the event costs the most. This is the case the engine's sunset always got wrong.
        val equinoxJdUt = ephemeris.jdUtWhenSunLongitude(0.0, TimeScale.jdUtAtMidnight(2026, 1, 1))
        val date = highLatitude.localDate(equinoxJdUt)

        val converged = calculator.sunTimes(date, highLatitude)
        val singlePass = calculator.sunTimes(date, highLatitude, refinementPasses = 1)

        val horizon = calculator.sunHorizonAltitudeDeg(highLatitude)
        for (which in listOf("sunrise", "sunset")) {
            val convergedJdUt = requireAt(if (which == "sunrise") converged.sunrise else converged.sunset)
            val singleJdUt = requireAt(if (which == "sunrise") singlePass.sunrise else singlePass.sunset)

            val shiftSeconds = abs(convergedJdUt - singleJdUt) * 86_400.0
            assertTrue(shiftSeconds > 10.0) {
                "$which moved only $shiftSeconds s between one pass and convergence; if the " +
                    "iteration were a no-op this test could not distinguish the two"
            }

            val convergedResidual = abs(calculator.sunAltitudeDeg(convergedJdUt, highLatitude) - horizon)
            val singleResidual = abs(calculator.sunAltitudeDeg(singleJdUt, highLatitude) - horizon)
            assertTrue(convergedResidual < 1e-5) { "$which converged residual $convergedResidual" }
            assertTrue(singleResidual > 20.0 * convergedResidual) {
                "$which single-pass residual $singleResidual is not worse than converged " +
                    "$convergedResidual, so the extra passes are doing nothing"
            }
        }
    }

    @Test
    fun `day length grows and shrinks symmetrically about the equinox`() {
        // The symmetry is *not* that the two days are equally long — a day 20 days after the
        // vernal equinox is two hours longer than one 20 days before it. It is that the surplus
        // on one side matches the deficit on the other, so the pair sums to twice the equinox
        // day. Asserting the wrong one of these passes trivially at the equator and fails by
        // hours at 45 N, so it is worth stating carefully.
        val equinoxJdUt = ephemeris.jdUtWhenSunLongitude(0.0, TimeScale.jdUtAtMidnight(2026, 1, 1))
        val equinoxDate = midLatitude.localDate(equinoxJdUt)
        val atEquinox = calculator.sunTimes(equinoxDate, midLatitude).daylightDays!!

        // Refraction and the Sun's semidiameter make the equinox day a little over twelve hours.
        assertTrue((atEquinox * 24.0) in 12.0..12.3) { "equinox daylight ${atEquinox * 24.0} h" }

        var previousAfter = atEquinox
        for (offset in listOf(20L, 40L, 60L)) {
            val before = calculator.sunTimes(equinoxDate.minusDays(offset), midLatitude).daylightDays!!
            val after = calculator.sunTimes(equinoxDate.plusDays(offset), midLatitude).daylightDays!!

            // Direction: this is the vernal equinox, so days lengthen after it. Without this a
            // sign error in the declination term would leave the sum below untouched.
            assertTrue(after > previousAfter) { "days should lengthen after the vernal equinox" }
            assertTrue(before < atEquinox) { "days should be shorter before the vernal equinox" }
            previousAfter = after

            val excessMinutes = (before + after - 2.0 * atEquinox) * 1440.0
            // Not exactly zero. The two dates are symmetric about the equinox only to the
            // nearest civil day, and the day-length curve has a second-order term that does not
            // cancel; both grow with the offset. Five minutes bounds them at 60 days, while a
            // sign or hemisphere error would be hours out.
            assertTrue(abs(excessMinutes) < 5.0) {
                "day lengths $offset days either side of the equinox sum to $excessMinutes min " +
                    "more than twice the equinox day"
            }
        }
    }

    @Test
    fun `day length is longest at the summer solstice and shortest at the winter one`() {
        val summerJdUt = ephemeris.jdUtWhenSunLongitude(90.0, TimeScale.jdUtAtMidnight(2026, 1, 1))
        val winterJdUt = ephemeris.jdUtWhenSunLongitude(270.0, TimeScale.jdUtAtMidnight(2026, 1, 1))

        val summer = calculator.sunTimes(midLatitude.localDate(summerJdUt), midLatitude).daylightDays!!
        val winter = calculator.sunTimes(midLatitude.localDate(winterJdUt), midLatitude).daylightDays!!

        assertTrue(summer > 0.6) { "summer daylight $summer days at 45N is too short" }
        assertTrue(winter < 0.4) { "winter daylight $winter days at 45N is too long" }
        assertEquals(1.0, summer + winter, 0.02) {
            "summer and winter daylight should sum to about a day at any latitude"
        }
    }

    @Test
    fun `arunodaya is exactly 96 minutes before sunrise`() {
        val date = referenceDate
        val times = calculator.sunTimes(date, midLatitude)
        val sunrise = requireAt(times.sunrise)
        val arunodaya = requireAt(times.arunodaya)

        // One double ulp at JD 2.46e6 is 2^-31 days, so a difference of two such numbers cannot
        // be resolved below about 40 microseconds. 1e-5 minutes is 0.6 ms — fifteen ulp, and far
        // tighter than any use this quantity has.
        assertEquals(96.0, (sunrise - arunodaya) * 1440.0, 1e-5)
        assertEquals(4.0 * 24.0, (sunrise - arunodaya) * 1440.0, 1e-5) // four ghatikas
    }

    @Test
    fun `solar noon is the meridian transit`() {
        val date = referenceDate
        val eastern = GeoLocation(20.0, 90.0, ZoneId.of("UTC"))
        val western = GeoLocation(20.0, -90.0, ZoneId.of("UTC"))

        val east = calculator.sunTimes(date, eastern).solarNoonJdUt
        val west = calculator.sunTimes(date, western).solarNoonJdUt

        // 180 degrees of longitude is half a day of rotation, and the eastern site sees it first.
        assertEquals(0.5, west - east, 0.01)
    }

    @Test
    fun `elevation raises the horizon and lengthens the day`() {
        val date = referenceDate
        val seaLevel = GeoLocation(28.6, 77.2, ZoneId.of("Asia/Kolkata"))
        val onAMountain = seaLevel.copy(elevationMeters = 2500.0)

        assertTrue(
            calculator.sunHorizonAltitudeDeg(onAMountain) < calculator.sunHorizonAltitudeDeg(seaLevel),
        )
        val lower = calculator.sunTimes(date, seaLevel).daylightDays!!
        val higher = calculator.sunTimes(date, onAMountain).daylightDays!!
        assertTrue(higher > lower) { "a 2500 m horizon dip should lengthen the day, not shorten it" }
        // 0.0347*sqrt(2500) = 1.735 deg of dip, which is several minutes each side.
        assertTrue((higher - lower) * 1440.0 > 5.0)
    }

    @Test
    fun `no solar quantity is ever NaN`() {
        val date = referenceDate
        for (latitude in listOf(-89.5, -66.0, -23.0, 0.0, 23.0, 66.0, 89.5)) {
            val location = GeoLocation(latitude, 12.0, ZoneId.of("UTC"))
            val times = calculator.sunTimes(date, location)
            assertTrue(times.solarNoonJdUt.isFinite()) { "solar noon not finite at $latitude" }
            for (event in listOf(times.sunrise, times.sunset, times.arunodaya)) {
                val jdUt = event.jdUtOrNull ?: continue
                assertTrue(jdUt.isFinite()) { "rise/set not finite at $latitude" }
            }
        }
    }

    private fun requireAt(event: RiseSet): Double = when (event) {
        is RiseSet.At -> event.jdUt
        else -> throw AssertionError("expected a rise/set event, got $event")
    }
}

