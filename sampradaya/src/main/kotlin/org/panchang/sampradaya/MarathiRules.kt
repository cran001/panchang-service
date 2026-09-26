package org.panchang.sampradaya

import org.panchang.core.MonthReckoning
import org.panchang.core.Paksha

/**
 * Marathi (Maharashtra, Goa) calendar rules.
 *
 * **Amanta reckoning**: the lunar month runs new moon to new moon, and the year opens on
 * Chaitra Shukla Pratipada — Gudi Padwa.
 *
 * ## What is and is not implemented
 *
 * The festival catalog dates the definitional observances — the ones whose tithi or Sankranti
 * *is* the festival's rule. **Ekadashi fasting and parana timing are not implemented for this
 * tradition**: this service computes Ekadashi observances only for ISKCON, whose viddha tests,
 * Mahadvadashi detection and parana bounds are the only ones with a reference implementation.
 * A Marathi Ekadashi rule set would differ on exactly those points, and returning ISKCON's
 * dates here would present Gaudiya rulings as Marathi ones.
 */
class MarathiRules : SampradayaRules {

    override val id: String = "marathi"

    override val displayName: String = "Marathi (Maharashtra, Goa)"

    override val status: VerificationStatus = VerificationStatus.UNVERIFIED

    override val provenanceNote: String =
        "Festival rules are the definitional tithi and Sankranti rules of the Marathi (amanta) " +
            "calendar — Gudi Padwa on Chaitra Shukla Pratipada, Ganesh Chaturthi on Bhadrapada " +
            "Shukla Chaturthi, and so on — as stated in standard Indian calendar references. " +
            "They have NOT been checked against a published Maharashtra panchang for any year, " +
            "and no local convention (a festival shifted for kshaya tithi, a vrata tied to a " +
            "weekday) is modelled. Ekadashi and parana timing are not computed for this " +
            "tradition; ISKCON is the only tradition this service computes those for."

    /**
     * Deliberately empty; see the class KDoc. The interface permits an empty list precisely so
     * a tradition can ship festivals without borrowing another's fasting rulings.
     */
    override fun ekadashiObservances(
        year: Int,
        ctx: ObservanceContext,
    ): List<ObservanceDecision> = emptyList()

    override fun eventResolution(year: Int, ctx: ObservanceContext): YearResolution =
        MarathiEventCatalog.resolver.resolveYear(year, ctx)
}

/**
 * The Marathi catalog of dated observances.
 *
 * Every entry below is *definitional*: the tithi or Sankranti named in the entry is the
 * festival's own rule, not a date inferred from one year of a printed calendar. That keeps the
 * catalog honest while it remains unverified — a definitional rule can be wrong about a local
 * convention but it cannot be silently wrong about nothing.
 *
 * ## Confidence
 *
 * [RuleConfidence] has no UNVERIFIED value; the honest mapping for these entries is
 * [RuleConfidence.INFERRED] — the rule is stated, its output is unconfirmed — combined with the
 * tradition-level [VerificationStatus.UNVERIFIED] every payload carries.
 *
 * ## What is deliberately absent
 *
 * See [KNOWN_GAPS]: anything whose rule this project cannot state from a source, including all
 * weekday-conditional vratas and every Marathi Ekadashi name.
 */
object MarathiEventCatalog {

    val KNOWN_GAPS: Map<String, String> = linkedMapOf(
        "All Marathi Ekadashi vrata and their parana" to
            "Ekadashi observance is not implemented for this tradition. The Marathi viddha " +
                "test, parana bounds and any Mahadvadashi equivalents need a Marathi panchang " +
                "and a stated rule source before they can be computed, and they are omitted " +
                "rather than filled in with ISKCON's rulings.",
        "Hartalika, Mangala Gauri and other weekday-bound vratas" to
            "These are tied to a weekday as well as a tithi, and [EventRule] has no form that " +
                "expresses the conjunction. A tithi-only rule would sometimes place them on " +
                "the wrong weekday.",
    )

    val definitions: List<EventDefinition> = listOf(

        // ── Chaitra ─────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "gudi_padwa",
            name = "Gudi Padwa (Marathi New Year)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Chaitra", Paksha.SHUKLA, 1),
            sourceNote =
                "Chaitra Shukla Pratipada is the Marathi new year by definition — the name and " +
                    "the day are the same thing. Not yet checked against a published " +
                    "Maharashtra panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "ram_navami",
            name = "Rama Navami",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Chaitra", Paksha.SHUKLA, 9),
            sourceNote =
                "Chaitra Shukla Navami — the tithi is the festival's definition across Hindu " +
                    "traditions. Not yet checked against a published Maharashtra panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "hanuman_jayanti",
            name = "Hanuman Jayanti",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Chaitra", Paksha.SHUKLA, 15),
            sourceNote =
                "Maharashtra observes Hanuman Jayanti on Chaitra Purnima, where most of north " +
                    "India uses the same full moon; the Marathi convention is stated here. Not " +
                    "yet checked against a published Maharashtra panchang.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Shravana ────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "nag_panchami",
            name = "Nag Panchami",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Shravana", Paksha.SHUKLA, 5),
            sourceNote =
                "Shravana Shukla Panchami — the tithi is in the festival's name. Not yet " +
                    "checked against a published Maharashtra panchang.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Bhadrapada ──────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "ganesh_chaturthi",
            name = "Ganesh Chaturthi",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Bhadrapada", Paksha.SHUKLA, 4),
            sourceNote =
                "Bhadrapada Shukla Chaturthi — the tithi is in the festival's name, and " +
                    "Ganesh Chaturthi is Maharashtra's principal public festival. Not yet " +
                    "checked against a published Maharashtra panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "ananta_chaturdashi",
            name = "Ananta Chaturdashi (Ganesh Visarjan)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Bhadrapada", Paksha.SHUKLA, 14),
            sourceNote =
                "Bhadrapada Shukla Chaturdashi closes the Ganesh festival — the tithi is in " +
                    "the festival's name. Not yet checked against a published Maharashtra " +
                    "panchang.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Ashvina ─────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "navratri_begins",
            name = "Sharadiya Navratri begins (Ghatasthapana)",
            group = EventGroup.SEASONAL,
            rule = onTithi("Ashvina", Paksha.SHUKLA, 1),
            sourceNote =
                "Ashvina Shukla Pratipada opens the nine-night Navratri; this entry dates the " +
                    "opening day only, and the span itself has no representation. Not yet " +
                    "checked against a published Maharashtra panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "dussehra",
            name = "Dussehra (Dasara)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Ashvina", Paksha.SHUKLA, 10),
            sourceNote =
                "Ashvina Shukla Dashami — Vijayadashami's tithi is definitional. Not yet " +
                    "checked against a published Maharashtra panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "lakshmi_pujan",
            name = "Diwali — Lakshmi Puja",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Ashvina", Paksha.KRISHNA, 15),
            sourceNote =
                "Ashvina Amavasya carries Lakshmi Puja in the Marathi calendar; the Amavasya " +
                    "is the day's definition. The puja itself falls in the evening, which this " +
                    "rule does not express. Not yet checked against a published Maharashtra " +
                    "panchang.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Kartika ─────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "bhai_dooj",
            name = "Bhai Dooj (Bhaubeej)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Kartika", Paksha.SHUKLA, 2),
            sourceNote =
                "Kartika Shukla Dwitiya, two tithis into the bright half — the name and the " +
                    "tithi agree. Not yet checked against a published Maharashtra panchang.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Solar ───────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "makar_sankranti",
            name = "Makar Sankranti",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = EventRule.OnSolarMonth(solarMonthIndex = 10, dayOfMonth = 1),
            sourceNote =
                "The day the Sun enters Makara — a solar rule for a solar festival, and " +
                    "Sankranti's definition. Whether a Maharashtra panchang shifts the " +
                    "observance when the ingress falls late in the civil day is a local " +
                    "convention not yet established; this rule takes the ingress day itself.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Magha / Phalguna ────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "mahashivaratri",
            name = "Maha Shivaratri",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Magha", Paksha.KRISHNA, 14),
            sourceNote =
                "Magha Krishna Chaturdashi under amanta reckoning — the fortnight the " +
                    "purnimanta traditions call Phalguna Krishna Chaturdashi, and the same " +
                    "mid-February night everywhere in India. The vigil itself runs through " +
                    "the night, which this rule does not express. Not yet checked against a " +
                    "published Maharashtra panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "holika_dahan",
            name = "Holika Dahan",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Phalguna", Paksha.SHUKLA, 15),
            sourceNote =
                "Phalguna Purnima carries Holika Dahan, the eve of Holi. Not yet checked " +
                    "against a published Maharashtra panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "rang_panchami",
            name = "Rang Panchami (Holi)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Phalguna", Paksha.KRISHNA, 5),
            sourceNote =
                "Maharashtra plays Holi five tithis after the Purnima, on Phalguna Krishna " +
                    "Panchami — Rang Panchami. Not yet checked against a published Maharashtra " +
                    "panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
    )

    /** A resolver over [definitions]. Construction validates every cross-reference. */
    val resolver: EventResolver = EventResolver(definitions)

    /** The Marathi calendar is amanta, so every lunar rule in this catalog states that. */
    private fun onTithi(
        month: String,
        paksha: Paksha,
        tithiNumberInPaksha: Int,
    ): EventRule.OnTithi = EventRule.OnTithi(
        lunarMonthName = month,
        paksha = paksha,
        tithiNumberInPaksha = tithiNumberInPaksha,
        reckoning = MonthReckoning.AMANTA,
        atSunrise = true,
    )
}
