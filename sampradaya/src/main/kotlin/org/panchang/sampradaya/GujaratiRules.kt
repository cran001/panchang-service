package org.panchang.sampradaya

import org.panchang.core.MonthReckoning
import org.panchang.core.Paksha

/**
 * Gujarati (Gujarat) calendar rules.
 *
 * **Amanta reckoning**, with a new year that does *not* open at Chaitra: Bestu Varas falls on
 * Kartika Shukla Pratipada, the day after Diwali. The months are the same amanta months every
 * other Gujarat-adjacent tradition uses; only the year boundary sits at the other end.
 *
 * ## What is and is not implemented
 *
 * The festival catalog dates the definitional observances. **Ekadashi fasting and parana
 * timing are not implemented for this tradition**: this service computes Ekadashi observances
 * only for ISKCON.
 */
class GujaratiRules : SampradayaRules {

    override val id: String = "gujarati"

    override val displayName: String = "Gujarati (Gujarat)"

    override val status: VerificationStatus = VerificationStatus.UNVERIFIED

    override val provenanceNote: String =
        "Festival rules are the definitional tithi and Sankranti rules of the Gujarati " +
            "(amanta) calendar — Bestu Varas on Kartika Shukla Pratipada, Uttarayan on the " +
            "Makara Sankranti, and so on — as stated in standard Indian calendar references. " +
            "They have NOT been checked against a published Gujarati panchang for any year, " +
            "and no local convention is modelled. Ekadashi and parana timing are not computed " +
            "for this tradition; ISKCON is the only tradition this service computes those for."

    /** Deliberately empty; see the class KDoc. */
    override fun ekadashiObservances(
        year: Int,
        ctx: ObservanceContext,
    ): List<ObservanceDecision> = emptyList()

    override fun eventResolution(year: Int, ctx: ObservanceContext): YearResolution =
        GujaratiEventCatalog.resolver.resolveYear(year, ctx)
}

/**
 * The Gujarati catalog of dated observances.
 *
 * Definitional entries only; [RuleConfidence.INFERRED] for the reasons the Marathi catalog's
 * KDoc sets out.
 */
object GujaratiEventCatalog {

    val KNOWN_GAPS: Map<String, String> = linkedMapOf(
        "All Gujarati Ekadashi vrata and their parana" to
            "Ekadashi observance is not implemented for this tradition; see " +
                "MarathiEventCatalog's equivalent note.",
        "Vasant Panchami and other widely-kept observances of the north Indian belt" to
            "Included only where a Gujarati-specific statement of the rule was available; the " +
                "belt's shared festivals are catalogued under the north Indian tradition, " +
                "whose purnimanta month names must not be read as Gujarati ones.",
    )

    val definitions: List<EventDefinition> = listOf(

        // ── Ashvina ─────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "navratri_begins",
            name = "Navratri begins",
            group = EventGroup.SEASONAL,
            rule = onTithi("Ashvina", Paksha.SHUKLA, 1),
            sourceNote =
                "Ashvina Shukla Pratipada opens the nine nights of garba; this entry dates " +
                    "the opening day only. Not yet checked against a published Gujarati " +
                    "panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "dassera",
            name = "Dassera (Vijayadashami)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Ashvina", Paksha.SHUKLA, 10),
            sourceNote =
                "Ashvina Shukla Dashami — Vijayadashami's tithi is definitional. Not yet " +
                    "checked against a published Gujarati panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "diwali",
            name = "Diwali (Lakshmi Puja)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Ashvina", Paksha.KRISHNA, 15),
            sourceNote =
                "Ashvina Amavasya carries Lakshmi Puja; the Amavasya is the day's " +
                    "definition. The puja falls in the evening, which this rule does not " +
                    "express. Not yet checked against a published Gujarati panchang.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Kartika ─────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "bestu_varas",
            name = "Bestu Varas (Gujarati New Year)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Kartika", Paksha.SHUKLA, 1),
            sourceNote =
                "Kartika Shukla Pratipada, the day after Diwali, is the Gujarati new year by " +
                    "definition. Not yet checked against a published Gujarati panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "bhai_bij",
            name = "Bhai Bij (Bhai Dooj)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Kartika", Paksha.SHUKLA, 2),
            sourceNote =
                "Kartika Shukla Dwitiya — the name and the tithi agree. Not yet checked " +
                    "against a published Gujarati panchang.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Solar ───────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "uttarayan",
            name = "Uttarayan (Makar Sankranti)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = EventRule.OnSolarMonth(solarMonthIndex = 10, dayOfMonth = 1),
            sourceNote =
                "Gujarat calls the Makara Sankranti Uttarayan and keeps it as its major " +
                    "kite-flying festival; the ingress day is the definition. Whether a " +
                    "Gujarati panchang shifts the observance when the ingress falls late in " +
                    "the civil day is not yet established; this rule takes the ingress day.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Magha ───────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "vasant_panchami",
            name = "Vasant Panchami",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Magha", Paksha.SHUKLA, 5),
            sourceNote =
                "Magha Shukla Panchami — the tithi is in the festival's name. Not yet " +
                    "checked against a published Gujarati panchang.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Magha ───────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "mahashivaratri",
            name = "Maha Shivaratri",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Magha", Paksha.KRISHNA, 14),
            sourceNote =
                "Magha Krishna Chaturdashi under amanta reckoning — the fortnight purnimanta " +
                    "traditions call Phalguna Krishna, and the same mid-February night " +
                    "everywhere in India. The vigil runs through the night, which this rule " +
                    "does not express. Not yet checked against a published Gujarati panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
    )

    /** A resolver over [definitions]. Construction validates every cross-reference. */
    val resolver: EventResolver = EventResolver(definitions)

    /** The Gujarati calendar is amanta, so every lunar rule in this catalog states that. */
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
