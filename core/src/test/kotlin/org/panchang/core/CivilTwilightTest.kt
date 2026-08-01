package org.panchang.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.panchang.ephemeris.TimeScale
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * [PanchangCalculator.civilTwilightEnd].
 *
 * Added because the observance rules need a "dusk" that is distinct from sunset: the Gaudiya
 * catalog states "Fast till sunset" for Rama Navami and "Fast till dusk" for Nrsimha Caturdasi,
 * so mapping dusk onto sunset would erase a distinction the source itself drew.
 *
 * What these tests can check is the astronomy — that the instant returned really is the Sun's
 * centre at −6° while descending, that it follows sunset, and that it disappears where the Sun
 * never gets that low. What no test can check is that the tradition means end of civil twilight
 * by "dusk"; no published Gaudiya calendar prints a clock time for it. That reading is flagged
 * INFERRED in the rule layer and is for a pandit to confirm; this file only establishes that the
 * astronomy behind it is the astronomy it claims to be.
 */
class CivilTwilightTest {

    private val ephemeris = LinearEphemeris()
    private val calculator = PanchangCalculator(ephemeris)

    private val referenceDate = LocalDate.of(2026, 5, 12)
    private val midLatitude = GeoLocation(45.0, 0.0, ZoneId.of("UTC"))
    private val tropical = GeoLocation(23.4, 88.4, ZoneId.of("Asia/Kolkata"))

    /** Longyearbyen, Svalbard: no civil twilight at all for months on end. */
    private val svalbard = GeoLocation(78.22, 15.63, ZoneId.of("Arctic/Longyearbyen"))

    @Test
    fun `civil twilight ends after sunset`() {
        for (location in listOf(midLatitude, tropical)) {
            val sunset = requireAt(calculator.sunTimes(referenceDate, location).sunset)
            val dusk = requireAt(calculator.civilTwilightEnd(referenceDate, location))
            assertTrue(dusk > sunset) {
                "dusk $dusk must follow sunset $sunset at ${location.latitude}"
            }
            // Civil twilight runs roughly 21 minutes at the equator and lengthens with latitude;
            // two hours is loose enough to be an assertion about the definition rather than
            // about any one site, and tight enough to catch a solve that found the wrong root.
            assertTrue((dusk - sunset) * 24.0 in 0.0..2.0) {
                "twilight lasted ${(dusk - sunset) * 24.0} h at ${location.latitude}"
            }
        }
    }

    @Test
    fun `the Sun is six degrees down at the returned instant`() {
        val dusk = requireAt(calculator.civilTwilightEnd(referenceDate, midLatitude))
        val altitude = calculator.sunAltitudeDeg(dusk, midLatitude)
        assertEquals(PanchangCalculator.CIVIL_TWILIGHT_ALTITUDE_DEGREES, altitude, 1e-3) {
            "civil twilight is defined at −6°, got $altitude"
        }
    }

    /**
     * The Sun descends through −6° once a day and ascends through it once; only the descending
     * crossing is dusk. A solver asked for the wrong direction would return the dawn crossing,
     * which is a perfectly plausible-looking time several hours wrong.
     */
    @Test
    fun `the descending crossing is taken, not the ascending one`() {
        val dusk = requireAt(calculator.civilTwilightEnd(referenceDate, midLatitude))
        val aMinuteEarlier = dusk - 1.0 / 1440.0
        val aMinuteLater = dusk + 1.0 / 1440.0
        assertTrue(calculator.sunAltitudeDeg(aMinuteEarlier, midLatitude) > -6.0)
        assertTrue(calculator.sunAltitudeDeg(aMinuteLater, midLatitude) < -6.0)
    }

    /**
     * Twilight is defined against the *geometric* horizon, so the observer's elevation must not
     * move it — unlike sunset, where the dip of the visible horizon genuinely does.
     */
    @Test
    fun `elevation moves sunset but not the twilight boundary`() {
        val seaLevel = GeoLocation(45.0, 0.0, ZoneId.of("UTC"))
        val mountain = GeoLocation(45.0, 0.0, ZoneId.of("UTC"), elevationMeters = 2000.0)

        val seaLevelSunset = requireAt(calculator.sunTimes(referenceDate, seaLevel).sunset)
        val mountainSunset = requireAt(calculator.sunTimes(referenceDate, mountain).sunset)
        assertTrue(mountainSunset > seaLevelSunset + 60.0 / 86_400.0) {
            "the dip at 2000 m should delay sunset by minutes, not seconds"
        }

        val seaLevelDusk = requireAt(calculator.civilTwilightEnd(referenceDate, seaLevel))
        val mountainDusk = requireAt(calculator.civilTwilightEnd(referenceDate, mountain))
        assertEquals(seaLevelDusk, mountainDusk, 1e-6) {
            "civil twilight must not take the horizon dip"
        }
    }

    /**
     * A white night. The Sun sets at Longyearbyen in late August, but for weeks either side it
     * never descends to −6°, so there is no dusk — and there must be no number pretending there
     * is one.
     */
    @Test
    fun `no dusk when the Sun never reaches six degrees down`() {
        val midsummer = svalbard.localDate(
            ephemeris.jdUtWhenSunLongitude(90.0, TimeScale.jdUtAtMidnight(2026, 1, 1)),
        )
        assertSame(RiseSet.CircumpolarUp, calculator.civilTwilightEnd(midsummer, svalbard)) {
            "at 78°N in June the Sun stays above −6° all day"
        }
    }

    /** Polar night: the Sun is below −6° for the whole day, so it never crosses it downward. */
    @Test
    fun `no dusk in the polar night either`() {
        val midwinter = svalbard.localDate(
            ephemeris.jdUtWhenSunLongitude(270.0, TimeScale.jdUtAtMidnight(2026, 1, 1)),
        )
        assertSame(RiseSet.CircumpolarDown, calculator.civilTwilightEnd(midwinter, svalbard))
    }

    private fun requireAt(riseSet: RiseSet): Double {
        assertTrue(riseSet is RiseSet.At) { "expected an instant, got $riseSet" }
        val jdUt = (riseSet as RiseSet.At).jdUt
        assertTrue(abs(jdUt) > 0.0 && jdUt.isFinite())
        return jdUt
    }
}
