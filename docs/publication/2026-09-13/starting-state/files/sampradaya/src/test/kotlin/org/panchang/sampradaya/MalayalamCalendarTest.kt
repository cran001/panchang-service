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
 * Registration, identity and plausibility for the Malayalam tradition, at Thiruvananthapuram in 2026.
 *
 * **These are plausibility tests, not conformance tests.** The tradition is
 * [VerificationStatus.UNVERIFIED] — no published Malayalam panchang has been compared against
 * this output — so what can honestly be asserted is that the rules exist, that the new year
 * lands where a Simha-Sankranti solar new year must land, and that the catalog resolves. Asserting a specific
 * day here would manufacture a reference this project does not have.
 */
class MalayalamCalendarTest {

    @Test
    fun `the rules identify themselves and stay unverified`() {
        val rules = MalayalamRules()
        assertEquals("malayalam", rules.id)
        assertTrue(rules.displayName.contains("Malayalam"))
        assertEquals(VerificationStatus.UNVERIFIED, rules.status)
        assertTrue(
            rules.provenanceNote.contains("NOT"),
            "an unverified tradition must say so in its provenance note",
        )
    }

    @Test
    fun `Chingam 1 2026 resolves inside the Simha window`() {
        val resolution = rules().eventResolution(YEAR, ctx())
        val newYear = resolution.events.firstOrNull { it.id == "chingam_1" }
        assertNotNull(
            newYear,
            "Chingam 1 must resolve; unresolved was ${resolution.unresolved.keys}",
        )
        val date = newYear!!.date
        assertTrue(
            date in LocalDate.of(2026, Month.AUGUST, 14)..LocalDate.of(2026, Month.AUGUST, 21),
            "the Sun enters Simha in the second half of August, not $date",
        )
    }

    @Test
    fun `ekadashi timing is absent and the rest of the catalog resolves`() {
        val rules = rules()
        val ctx = ctx()
        assertEquals(
            emptyList<ObservanceDecision>(),
            rules.ekadashiObservances(YEAR, ctx),
            "no tradition but ISKCON computes Ekadashi timing; borrowing Gaudiya rulings " +
                "would mislabel Malayalam observances",
        )
        val resolution = rules.eventResolution(YEAR, ctx)
        assertTrue(
            resolution.events.size >= 3,
            "the Malayalam catalog carries definitional entries; ${resolution.events.size} " +
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

    private fun rules() = MalayalamRules()

    private fun ctx() = ObservanceContext(CALCULATOR, THIRUVANANTHAPURAM)

    private companion object {
        const val YEAR = 2026
        private val THIRUVANANTHAPURAM = GeoLocation(8.5241, 76.9366, ZoneId.of("Asia/Kolkata"))
        private val CALCULATOR = org.panchang.core.PanchangCalculator(Vsop87Ephemeris())
    }
}
