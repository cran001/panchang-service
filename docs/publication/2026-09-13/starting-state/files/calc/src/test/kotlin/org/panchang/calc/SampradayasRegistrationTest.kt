package org.panchang.calc

import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.panchang.core.GeoLocation
import org.panchang.core.PanchangCalculator
import org.panchang.ephemeris.Vsop87Ephemeris
import org.panchang.sampradaya.ObservanceContext
import org.panchang.sampradaya.VerificationStatus

/**
 * What this front door claims to know, pinned.
 *
 * Registration is a statement a front door makes — "these rules exist and we stand behind
 * computing them" — so the list is asserted in full. A silent addition to it would put an
 * unreviewed tradition in front of users; a silent removal would break clients that had been
 * told an id exists. Both should be loud.
 */
class SampradayasRegistrationTest {

    @Test
    fun `iskcon plus the nine regional traditions are registered, in order`() {
        assertEquals(
            listOf(
                "iskcon",
                "marathi",
                "telugu",
                "kannada",
                "gujarati",
                "northindian",
                "tamil",
                "malayalam",
                "bengali",
                "odia",
            ),
            Sampradayas.knownIds(),
        )
    }

    @Test
    fun `only iskcon computes ekadashi and parana timing`() {
        val ctx = ObservanceContext(
            PanchangCalculator(Vsop87Ephemeris()),
            GeoLocation(23.4249, 88.3883, ZoneId.of("Asia/Kolkata")),
        )

        val iskcon = Sampradayas["iskcon"]!!
        assertTrue(
            iskcon.ekadashiObservances(2026, ctx).isNotEmpty(),
            "ISKCON is the one tradition whose Ekadashi timing is implemented; if this fails " +
                "the engine has lost its conformance-tested core",
        )

        for (id in Sampradayas.knownIds().filter { it != "iskcon" }) {
            val rules = Sampradayas[id]!!
            assertEquals(
                emptyList<org.panchang.sampradaya.ObservanceDecision>(),
                rules.ekadashiObservances(2026, ctx),
                "$id must not borrow ISKCON's Ekadashi rulings; regional fasting rules are " +
                    "unimplemented and must say so by absence",
            )
        }
    }

    @Test
    fun `every regional tradition carries festivals and an honest unverified status`() {
        val ctx = ObservanceContext(
            PanchangCalculator(Vsop87Ephemeris()),
            GeoLocation(23.4249, 88.3883, ZoneId.of("Asia/Kolkata")),
        )
        for (id in Sampradayas.knownIds().filter { it != "iskcon" }) {
            val rules = Sampradayas[id]!!
            assertEquals(VerificationStatus.UNVERIFIED, rules.status, "for $id")
            assertTrue(
                rules.eventResolution(2026, ctx).events.isNotEmpty(),
                "$id resolved no festivals at all in 2026; a registered tradition with an " +
                    "empty year is a listing that answers nothing",
            )
        }
    }
}
