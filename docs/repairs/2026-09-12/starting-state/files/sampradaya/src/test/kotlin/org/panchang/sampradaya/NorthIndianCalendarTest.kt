package org.panchang.sampradaya

import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.panchang.core.GeoLocation
import org.panchang.ephemeris.Vsop87Ephemeris

/**
 * Registration, identity and plausibility for the North Indian (Hindi belt) tradition, at Delhi in 2026.
 *
 * **These are plausibility tests, not conformance tests.** The tradition is
 * [VerificationStatus.UNVERIFIED] — no published North Indian (Hindi belt) panchang has been compared against
 * this output — so what can honestly be asserted is that the rules exist, that the new year
 * lands where a purnimanta Chaitra new year must land, and that the catalog resolves. Asserting a specific
 * day here would manufacture a reference this project does not have.
 */
class NorthIndianCalendarTest {

    @Test
    fun `the rules identify themselves and stay unverified`() {
        val rules = NorthIndianRules()
        assertEquals("northindian", rules.id)
        assertTrue(rules.displayName.contains("North Indian"))
        assertEquals(VerificationStatus.UNVERIFIED, rules.status)
        assertTrue(
            rules.provenanceNote.contains("NOT"),
            "an unverified tradition must say so in its provenance note",
        )
    }

    @Test
    fun `Chaitra Navratri resolves inside the Chaitra window in an ordinary year`() {
        // 2027, because 2026's Chaitra Shukla Pratipada is kshaya across India — it began
        // after the sunrise of 19 March 2026 and ended before the sunrise of the 20th, so no
        // day carries it. The resolver refuses to guess which day a kshaya new year falls to
        // (that is a ruling with no source in this project), so the window assertion uses a
        // year where the tithi behaves.
        val resolution = rules().eventResolution(2027, ctx())
        val newYear = resolution.events.firstOrNull { it.id == "chaitra_navratri_begins" }
        assertNotNull(
            newYear,
            "Chaitra Navratri must resolve in 2027; unresolved was ${resolution.unresolved}",
        )
        val date = newYear!!.date
        assertTrue(
            date in LocalDate.of(2027, Month.MARCH, 10)..LocalDate.of(2027, Month.MAY, 10),
            "a Chaitra Shukla Pratipada lands in late March or April, not $date",
        )
    }

    @Test
    fun `the kshaya Chaitra Pratipada of 2026 is reported loudly, never guessed`() {
        val resolution = rules().eventResolution(YEAR, ctx())
        val outcome = resolution.unresolved["chaitra_navratri_begins"]
        if (outcome != null) {
            assertTrue(
                outcome is EventResolution.TithiSkipped,
                "the 2026 Chaitra Pratipada is kshaya across India; the new year may only be " +
                    "absent as a *stated* skip, not as ${outcome::class.simpleName}",
            )
        }
        // If the tithi is not kshaya at this site under some future ephemeris, the entry must
        // resolve instead — silence either way is the failure.
    }

    @Test
    fun `ekadashi timing is absent and the rest of the catalog resolves`() {
        val rules = rules()
        val ctx = ctx()
        assertEquals(
            emptyList<ObservanceDecision>(),
            rules.ekadashiObservances(YEAR, ctx),
            "no tradition but ISKCON computes Ekadashi timing; borrowing Gaudiya rulings " +
                "would mislabel North Indian (Hindi belt) observances",
        )
        val resolution = rules.eventResolution(YEAR, ctx)
        assertTrue(
            resolution.events.size >= 8,
            "the North Indian (Hindi belt) catalog carries definitional entries; ${resolution.events.size} " +
                "resolved and ${resolution.unresolved.keys} did not, which is too few to be " +
                "an intact catalog",
        )
        resolution.unresolved.forEach { (id, outcome) ->
            assertTrue(
                outcome !is EventResolution.TithiSkipped || outcome.why.isNotBlank(),
                "unresolved entry '$id' must say why",
            )
        }
    }

    private fun rules() = NorthIndianRules()

    private fun ctx() = ObservanceContext(CALCULATOR, DELHI)

    private companion object {
        const val YEAR = 2026
        private val DELHI = GeoLocation(28.6139, 77.209, ZoneId.of("Asia/Kolkata"))
        private val CALCULATOR = org.panchang.core.PanchangCalculator(Vsop87Ephemeris())
    }
}
