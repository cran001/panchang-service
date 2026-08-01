package org.panchang.sampradaya

import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.panchang.core.GeoLocation
import org.panchang.core.MoonTimes
import org.panchang.core.PanchangCalculator
import org.panchang.core.Paksha
import org.panchang.core.RiseSet
import org.panchang.ephemeris.Vsop87Ephemeris

/**
 * Time of day for observances: [ObservanceAnchor], [EventTime] and their arrival in
 * [ResolvedEvent.fastUntil].
 *
 * ## What is and is not being asserted
 *
 * There is **no oracle for these times.** The published Gaudiya calendars name the anchors in
 * words — "Fast till noon", "Fast till dusk" — and print no clock time for any of them, across
 * all 365 days of the reference data. So nothing below claims that a computed instant matches a
 * tradition's published one; no such published instant exists to match.
 *
 * What is asserted is of two kinds, and they are kept apart on purpose:
 *
 * 1. **Arithmetic.** Solar noon is the transit the calculator reports; the Nisita muhurta really
 *    does contain solar midnight; a dusk really is after sunset. This is ordinary astronomy and
 *    is checkable.
 * 2. **Structure.** Every "Fast till" note has an anchor; an absent moonrise arrives as
 *    [EventTime.Absent] and never as a fabricated number; the two anchors resting on a judgement
 *    call are labelled [RuleConfidence.INFERRED] and carry the judgement in their own `basis`.
 *
 * The mapping from the tradition's word to the astronomical anchor is *not* tested here, because
 * it cannot be. It is flagged for pandit review instead.
 */
class EventTimeTest {

    // ── The catalog cannot silently lose a time ──────────────────────────────────────────────

    /**
     * The prose is not a fixed vocabulary — the source writes things like "Fast till noon for
     * Varahadeva, with feast tomorrow" — so the check reads the word after "till" and tolerates
     * anything around it.
     *
     * This is the guard that matters most for the future. A catalog entry added next year with
     * "Fast till sunset" in its note and no `fastUntil` would render a sentence the client cannot
     * turn into a time, which is exactly the gap this feature closed.
     */
    @Test
    fun `every fast-till note names an anchor, and the right one`() {
        val expectedByKeyword = mapOf(
            "sunrise" to ObservanceAnchor.SUNRISE,
            "noon" to ObservanceAnchor.SOLAR_NOON,
            "sunset" to ObservanceAnchor.SUNSET,
            "moonrise" to ObservanceAnchor.MOONRISE,
            "dusk" to ObservanceAnchor.DUSK,
            "midnight" to ObservanceAnchor.NISITA_KALA,
        )

        val anchored = IskconEventCatalog.definitions.filter {
            it.fastingNote?.startsWith(FAST_UNTIL_PREFIX) == true
        }
        assertTrue(anchored.size >= 10) {
            "expected the catalog to still hold its anchored fasts, found ${anchored.size}"
        }

        for (definition in anchored) {
            val note = definition.fastingNote!!
            val keyword = note.removePrefix(FAST_UNTIL_PREFIX)
                .substringBefore(' ')
                .trimEnd('.', ',', ';')
                .lowercase()
            val expected = expectedByKeyword[keyword]
            assertNotNull(expected) {
                "'${definition.id}' says \"$note\" but '$keyword' names no known anchor; either " +
                    "add one to ObservanceAnchor or reword the note"
            }
            assertEquals(expected, definition.fastUntil) {
                "'${definition.id}' says \"$note\" but is anchored to ${definition.fastUntil}"
            }
        }
    }

    /** The converse: nothing carries an anchor its note does not support. */
    @Test
    fun `no anchor without a fast to anchor`() {
        for (definition in IskconEventCatalog.definitions) {
            if (definition.fastUntil != null) {
                assertNotNull(definition.fastingNote) {
                    "'${definition.id}' anchors a fast it never declared"
                }
            }
        }

        val error = assertThrows<IllegalArgumentException> {
            EventDefinition(
                id = "probe",
                name = "probe",
                group = EventGroup.MAJOR_FESTIVAL,
                rule = EventRule.OnTithi("Magha", Paksha.SHUKLA, 5),
                sourceNote = "test fixture",
                confidence = RuleConfidence.INFERRED,
                fastUntil = ObservanceAnchor.SOLAR_NOON,
            )
        }
        assertMentions(error.message.orEmpty(), "fastingNote")
    }

    // ── Confidence grades the reading, not the arithmetic ────────────────────────────────────

    @Test
    fun `the two judgement-call anchors are INFERRED and the plain ones are not`() {
        assertEquals(RuleConfidence.INFERRED, ObservanceAnchor.DUSK.mappingConfidence)
        assertEquals(RuleConfidence.INFERRED, ObservanceAnchor.NISITA_KALA.mappingConfidence)
        for (anchor in listOf(
            ObservanceAnchor.SUNRISE,
            ObservanceAnchor.SOLAR_NOON,
            ObservanceAnchor.SUNSET,
            ObservanceAnchor.MOONRISE,
        )) {
            assertEquals(RuleConfidence.CONFIRMED, anchor.mappingConfidence) { "$anchor" }
        }
    }

    /**
     * The confidence on an [EventTime] must be the anchor's, never the event's. Janmastami's
     * *date* is CONFIRMED and the Nisita reading of "fast till midnight" is INFERRED, and the
     * payload has to be able to say both at once.
     */
    @Test
    fun `an event's date and its fast time are graded independently`() {
        val janmastami = resolved("janmastami")
        assertEquals(RuleConfidence.CONFIRMED, janmastami.confidence)
        assertEquals(RuleConfidence.INFERRED, janmastami.fastUntil!!.confidence)
    }

    /** The assumption has to reach the payload, not stop at a code comment. */
    @Test
    fun `the inferred anchors carry their definition in basis`() {
        val nisita = resolved("janmastami").fastUntil!!
        assertEquals(RuleConfidence.INFERRED, nisita.confidence)
        assertFalse(nisita.basis.isBlank())
        assertMentions(nisita.basis, "solar midnight")
        assertMentions(nisita.basis, "DST")

        val dusk = resolved("nrsimha_caturdasi").fastUntil!!
        assertEquals(RuleConfidence.INFERRED, dusk.confidence)
        assertFalse(dusk.basis.isBlank())
        assertMentions(dusk.basis, "civil twilight")
    }

    /** Every anchor states its definition, whether or not an instant came out of it. */
    @Test
    fun `no EventTime is ever produced without a basis`() {
        for (event in MAYAPUR_EVENTS) {
            val time = event.fastUntil ?: continue
            assertFalse(time.basis.isBlank()) { "'${event.id}' produced a time with no basis" }
        }
    }

    // ── The arithmetic ──────────────────────────────────────────────────────────────────────

    @Test
    fun `a noon fast lands on the solar transit, not on twelve o'clock`() {
        val advaita = resolved("advaita_acarya_appearance")
        val time = assertIs<EventTime.At>(advaita.fastUntil)
        assertEquals(ObservanceAnchor.SOLAR_NOON, time.anchor)

        val sun = CALCULATOR.sunTimes(advaita.date, MAYAPUR)
        assertEquals(sun.solarNoonJdUt, time.jdUt, 1e-9)

        // Mayapur sits 6.9° east of the 82.5°E meridian IST is defined on, so solar noon there
        // is about 24 minutes before 12:00 local. If these agreed, the anchor would be wrong.
        val civilNoon = MAYAPUR.jdUtAtStartOfDay(advaita.date) + 0.5
        assertTrue((civilNoon - time.jdUt) * 1440.0 > 5.0) {
            "solar noon and civil noon should differ measurably at Mayapur"
        }
    }

    @Test
    fun `a sunset fast lands on sunset and a dusk fast strictly after it`() {
        val ramaNavami = resolved("rama_navami")
        val sunsetTime = assertIs<EventTime.At>(ramaNavami.fastUntil)
        assertEquals(
            CALCULATOR.sunTimes(ramaNavami.date, MAYAPUR).sunset.jdUtOrNull!!,
            sunsetTime.jdUt,
            1e-9,
        )

        val nrsimha = resolved("nrsimha_caturdasi")
        val duskTime = assertIs<EventTime.At>(nrsimha.fastUntil)
        assertEquals(ObservanceAnchor.DUSK, duskTime.anchor)
        val sunsetThatDay = CALCULATOR.sunTimes(nrsimha.date, MAYAPUR).sunset.jdUtOrNull!!
        assertTrue(duskTime.jdUt > sunsetThatDay) {
            "dusk must follow sunset; the catalog distinguishes the two deliberately"
        }
        assertTrue((duskTime.jdUt - sunsetThatDay) * 1440.0 in 10.0..60.0) {
            "civil twilight at 23°N should be tens of minutes, was " +
                "${(duskTime.jdUt - sunsetThatDay) * 1440.0} min"
        }
    }

    @Test
    fun `a moonrise fast lands on moonrise`() {
        val gauraPurnima = resolved("gaura_purnima")
        val time = assertIs<EventTime.At>(gauraPurnima.fastUntil)
        assertEquals(ObservanceAnchor.MOONRISE, time.anchor)
        assertEquals(
            CALCULATOR.moonTimes(gauraPurnima.date, MAYAPUR).moonrise.jdUtOrNull!!,
            time.jdUt,
            1e-9,
        )
    }

    /**
     * Nisita-kala is the muhurta *containing* solar midnight, which is the whole reason it is
     * preferred over civil 00:00. Solar midnight is the lower transit, taken here as the midpoint
     * between consecutive upper transits.
     */
    @Test
    fun `the Nisita window is a window and it contains solar midnight`() {
        val janmastami = resolved("janmastami")
        val window = assertIs<EventTime.Window>(janmastami.fastUntil)
        assertEquals(ObservanceAnchor.NISITA_KALA, window.anchor)

        val noonToday = CALCULATOR.sunTimes(janmastami.date, MAYAPUR).solarNoonJdUt
        val noonTomorrow = CALCULATOR.sunTimes(janmastami.date.plusDays(1), MAYAPUR).solarNoonJdUt
        val solarMidnight = (noonToday + noonTomorrow) / 2.0

        assertTrue(solarMidnight > window.startJdUt && solarMidnight < window.endJdUt) {
            "solar midnight $solarMidnight is outside the Nisita muhurta " +
                "${window.startJdUt}..${window.endJdUt}"
        }

        // One fifteenth of a night of roughly twelve hours.
        assertTrue(window.durationMinutes in 30.0..70.0) {
            "a night muhurta at Mayapur should be around 45 min, was ${window.durationMinutes}"
        }

        // And it is genuinely in the night that follows the festival day, not the one before it.
        val sunset = CALCULATOR.sunTimes(janmastami.date, MAYAPUR).sunset.jdUtOrNull!!
        val sunriseNextDay =
            CALCULATOR.sunTimes(janmastami.date.plusDays(1), MAYAPUR).sunrise.jdUtOrNull!!
        assertTrue(window.startJdUt > sunset && window.endJdUt < sunriseNextDay)
    }

    // ── Absence is a first-class answer ─────────────────────────────────────────────────────

    /**
     * The reason this type is sealed. At 78°N in midsummer there is no sunset, no dusk and no
     * night to divide — and the user must be told that, not handed a plausible-looking clock
     * time derived from some fallback.
     */
    @Test
    fun `no fabricated instants at high latitude`() {
        val midsummer = LocalDate.of(2026, 6, 21)
        val sun = CALCULATOR.sunTimes(midsummer, SVALBARD)

        val sunset = assertIs<EventTime.Absent>(EventTimes.sunset(sun))
        assertEquals(AbsenceReason.CIRCUMPOLAR_UP, sunset.reason)

        // Not CIRCUMPOLAR_UP: the twilight solve says nothing about the rise/set horizon, only
        // that the Sun never got to −6°.
        val dusk = assertIs<EventTime.Absent>(
            EventTimes.dusk(CALCULATOR.civilTwilightEnd(midsummer, SVALBARD)),
        )
        assertEquals(AbsenceReason.TWILIGHT_NOT_REACHED, dusk.reason)

        val nisita = assertIs<EventTime.Absent>(
            EventTimes.nisitaKala(
                sunsetOfDay = sun.sunset,
                sunriseOfNextDay = CALCULATOR.sunTimes(midsummer.plusDays(1), SVALBARD).sunrise,
            ),
        )
        assertEquals(AbsenceReason.NIGHT_NOT_WELL_DEFINED, nisita.reason)

        // Solar noon survives all of it: the Sun transits every day at every latitude, which is
        // one of the three reasons core's RiseSet was the wrong type to reuse here.
        val noon = assertIs<EventTime.At>(EventTimes.solarNoon(sun))
        assertTrue(noon.jdUt.isFinite())

        for (time in listOf(sunset, dusk, nisita)) {
            assertNull(time.jdUtOrNull) { "an absent anchor must expose no instant" }
        }
    }

    /**
     * The non-polar absence. Moonrise runs about 50 minutes later each day, so roughly once a
     * lunar month a civil day carries a moonset and no moonrise — anywhere on Earth, including
     * Mayapur.
     */
    @Test
    fun `a day with no moonrise reports absence rather than a substitute`() {
        val missing = (0L until 40L)
            .map { LocalDate.of(2026, 3, 1).plusDays(it) }
            .map { EventTimes.moonrise(CALCULATOR.moonTimes(it, MAYAPUR)) }
            .filterIsInstance<EventTime.Absent>()
        assertTrue(missing.isNotEmpty()) {
            "expected at least one moonrise-less day in a 40-day span at Mayapur"
        }
        for (absent in missing) {
            assertEquals(AbsenceReason.NO_EVENT_IN_WINDOW, absent.reason)
            assertNull(absent.jdUtOrNull)
        }
    }

    /** A `RiseSet` that is a polar case must never become an instant, whatever the anchor. */
    @Test
    fun `the RiseSet adapter maps every absent case without inventing one`() {
        val down = assertIs<EventTime.Absent>(
            EventTimes.moonrise(MoonTimes(RiseSet.CircumpolarDown, RiseSet.CircumpolarDown)),
        )
        assertEquals(AbsenceReason.CIRCUMPOLAR_DOWN, down.reason)

        val up = assertIs<EventTime.Absent>(
            EventTimes.moonrise(MoonTimes(RiseSet.CircumpolarUp, RiseSet.CircumpolarUp)),
        )
        assertEquals(AbsenceReason.CIRCUMPOLAR_UP, up.reason)

        // A night that comes out backwards — possible at a site whose civil zone is far from its
        // longitude — is not divided into muhurtas anyway.
        val inverted = assertIs<EventTime.Absent>(
            EventTimes.nisitaKala(RiseSet.At(2_461_000.9), RiseSet.At(2_461_000.1)),
        )
        assertEquals(AbsenceReason.NIGHT_NOT_WELL_DEFINED, inverted.reason)
    }

    // ── The qualifying tithi is retained ────────────────────────────────────────────────────

    /**
     * Retained, not recomputed: it is the very occurrence the resolver matched on, so a client
     * can show the festival's tithi start and end at the user's own location.
     */
    @Test
    fun `a tithi-ruled event carries the occurrence that qualified it`() {
        val gauraPurnima = resolved("gaura_purnima")
        val tithi = assertIs<TithiOccurrence>(gauraPurnima.tithi)
        assertEquals(15, tithi.numberInPaksha)
        assertEquals(Paksha.SHUKLA, tithi.paksha)
        // Named under the rule's own reckoning, which this catalog states purnimanta.
        assertEquals("Phalguna", tithi.lunarMonthName)
        assertFalse(tithi.isAdhikaMonth)

        // The occurrence must actually contain the date's sunrise, which is what qualified it.
        val sunrise = CALCULATOR.sunTimes(gauraPurnima.date, MAYAPUR).sunrise.jdUtOrNull!!
        assertTrue(sunrise >= tithi.startJdUt && sunrise < tithi.endJdUt) {
            "the retained tithi does not contain the sunrise that selected the date"
        }
    }

    /**
     * Null where nothing tithi-shaped did the qualifying. An offset event's date was chosen by
     * counting days from another event, so neither that event's tithi nor the offset day's own
     * one "qualified" it, and reporting either would misdescribe the rule.
     */
    @Test
    fun `an offset event reports no qualifying tithi rather than an invented one`() {
        assertNull(resolved("jagannatha_misra_festival").tithi)
    }

    /** An event with no fast has no time, and that is not the same as a time that is absent. */
    @Test
    fun `events without a fast carry no anchor at all`() {
        val vasanta = resolved("vasanta_pancami")
        assertNull(vasanta.fastingNote)
        assertNull(vasanta.fastUntil)
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────

    private fun resolved(id: String): ResolvedEvent =
        MAYAPUR_EVENTS.firstOrNull { it.id == id }
            ?: error("'$id' did not resolve in 2026 at Mayapur")

    private fun assertMentions(text: String, fragment: String) =
        assertTrue(text.contains(fragment), "expected \"$fragment\" in: $text")

    private inline fun <reified T> assertIs(value: Any?): T {
        assertTrue(value is T, "expected ${T::class.simpleName} but was $value")
        return value as T
    }

    companion object {
        private const val FAST_UNTIL_PREFIX = "Fast till "

        private val CALCULATOR = PanchangCalculator(Vsop87Ephemeris())

        private val MAYAPUR = GeoLocation(
            GaudiyaGoldenCalendar.MAYAPUR_LATITUDE,
            GaudiyaGoldenCalendar.MAYAPUR_LONGITUDE,
            ZoneId.of(GaudiyaGoldenCalendar.MAYAPUR_ZONE),
        )

        /** Longyearbyen, Svalbard. Every absent case this type has is reachable there. */
        private val SVALBARD = GeoLocation(78.22, 15.63, ZoneId.of("Arctic/Longyearbyen"))

        /** Built once: an index costs about 500 tithi searches and 500 sunrise searches. */
        private val MAYAPUR_EVENTS: List<ResolvedEvent> by lazy {
            EventResolver(IskconEventCatalog.definitions)
                .resolveYear(2026, ObservanceContext(CALCULATOR, MAYAPUR))
                .events
        }
    }
}
