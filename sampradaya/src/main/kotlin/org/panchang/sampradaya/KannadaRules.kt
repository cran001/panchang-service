package org.panchang.sampradaya

import org.panchang.core.MonthReckoning
import org.panchang.core.Paksha

/**
 * Kannada (Karnataka) calendar rules.
 *
 * **Amanta reckoning**, like the Telugu calendar it shares its new year with: Ugadi on
 * Chaitra Shukla Pratipada.
 *
 * ## What is and is not implemented
 *
 * The festival catalog dates the definitional observances. **Ekadashi fasting and parana
 * timing are not implemented for this tradition**: this service computes Ekadashi observances
 * only for ISKCON.
 */
class KannadaRules : SampradayaRules {

    override val id: String = "kannada"

    override val displayName: String = "Kannada (Karnataka)"

    override val status: VerificationStatus = VerificationStatus.UNVERIFIED

    override val provenanceNote: String =
        "Festival rules are the definitional tithi rules of the Kannada (amanta) calendar — " +
            "Ugadi on Chaitra Shukla Pratipada, Ganesha Chaturthi on Bhadrapada Shukla " +
            "Chaturthi, and so on — as stated in standard Indian calendar references. They " +
            "have NOT been checked against a published Kannada panchang for any year, and no " +
            "local convention is modelled. Ekadashi and parana timing are not computed for " +
            "this tradition; ISKCON is the only tradition this service computes those for."

    /** Deliberately empty; see the class KDoc. */
    override fun ekadashiObservances(
        year: Int,
        ctx: ObservanceContext,
    ): List<ObservanceDecision> = emptyList()

    override fun eventResolution(year: Int, ctx: ObservanceContext): YearResolution =
        KannadaEventCatalog.resolver.resolveYear(year, ctx)
}

/**
 * The Kannada catalog of dated observances.
 *
 * Definitional entries only; [RuleConfidence.INFERRED] for the reasons the Marathi catalog's
 * KDoc sets out.
 */
object KannadaEventCatalog {

    val KNOWN_GAPS: Map<String, String> = linkedMapOf(
        "All Kannada Ekadashi vrata and their parana" to
            "Ekadashi observance is not implemented for this tradition; see " +
                "MarathiEventCatalog's equivalent note.",
        "Kadalekai Parishe and other dated melas" to
            "Gregorian-dated or weekday-dated local observances with no tithi rule; omitted " +
                "rather than tabulated.",
    )

    val definitions: List<EventDefinition> = listOf(

        // ── Chaitra ─────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "ugadi",
            name = "Ugadi (Kannada New Year)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Chaitra", Paksha.SHUKLA, 1),
            sourceNote =
                "Chaitra Shukla Pratipada is the Kannada new year by definition — Yugadi, the " +
                    "same day the Telugu calendar opens on. Not yet checked against a " +
                    "published Kannada panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "sri_rama_navami",
            name = "Sri Rama Navami",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Chaitra", Paksha.SHUKLA, 9),
            sourceNote =
                "Chaitra Shukla Navami — the tithi is the festival's definition. Not yet " +
                    "checked against a published Kannada panchang.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Bhadrapada ──────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "ganesha_chaturthi",
            name = "Ganesha Chaturthi",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Bhadrapada", Paksha.SHUKLA, 4),
            sourceNote =
                "Bhadrapada Shukla Chaturthi — the tithi is in the festival's name, and " +
                    "Karnataka observes it statewide. Not yet checked against a published " +
                    "Kannada panchang.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Ashvina ─────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "navaratri_begins",
            name = "Navaratri begins",
            group = EventGroup.SEASONAL,
            rule = onTithi("Ashvina", Paksha.SHUKLA, 1),
            sourceNote =
                "Ashvina Shukla Pratipada opens the nine-night Navaratri; this entry dates " +
                    "the opening day only. Not yet checked against a published Kannada " +
                    "panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "dasara",
            name = "Dasara (Vijayadashami)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Ashvina", Paksha.SHUKLA, 10),
            sourceNote =
                "Ashvina Shukla Dashami — Vijayadashami's tithi is definitional, and the " +
                    "Mysuru Dasara procession rides on it. Not yet checked against a " +
                    "published Kannada panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "deepavali",
            name = "Deepavali",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Ashvina", Paksha.KRISHNA, 15),
            sourceNote =
                "Ashvina Amavasya carries Deepavali; the Amavasya is the day's definition. " +
                    "Not yet checked against a published Kannada panchang.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Kartika ─────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "kartika_purnima",
            name = "Kartika Purnima",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Kartika", Paksha.SHUKLA, 15),
            sourceNote =
                "Kartika Purnima — the Purnima is the day's definition. Not yet checked " +
                    "against a published Kannada panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
    )

    /** A resolver over [definitions]. Construction validates every cross-reference. */
    val resolver: EventResolver = EventResolver(definitions)

    /** The Kannada calendar is amanta, so every lunar rule in this catalog states that. */
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
