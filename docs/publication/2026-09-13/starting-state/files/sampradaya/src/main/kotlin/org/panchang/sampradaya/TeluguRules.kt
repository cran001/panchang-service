package org.panchang.sampradaya

import org.panchang.core.MonthReckoning
import org.panchang.core.Paksha

/**
 * Telugu (Andhra Pradesh, Telangana) calendar rules.
 *
 * **Amanta reckoning**: the lunar month runs new moon to new moon, and the year opens on
 * Chaitra Shukla Pratipada — Ugadi.
 *
 * ## What is and is not implemented
 *
 * The festival catalog dates the definitional observances. **Ekadashi fasting and parana
 * timing are not implemented for this tradition**: this service computes Ekadashi observances
 * only for ISKCON, and a Telugu Ekadashi rule set would differ on the viddha test, the parana
 * bounds and the treatment of deferrals.
 */
class TeluguRules : SampradayaRules {

    override val id: String = "telugu"

    override val displayName: String = "Telugu (Andhra Pradesh, Telangana)"

    override val status: VerificationStatus = VerificationStatus.UNVERIFIED

    override val provenanceNote: String =
        "Festival rules are the definitional tithi rules of the Telugu (amanta) calendar — " +
            "Ugadi on Chaitra Shukla Pratipada, Vinayaka Chavithi on Bhadrapada Shukla " +
            "Chaturthi, and so on — as stated in standard Indian calendar references. They " +
            "have NOT been checked against a published Telugu panchang (Ugadi, Durmukhi or " +
            "other publisher) for any year, and no local convention is modelled. Ekadashi and " +
            "parana timing are not computed for this tradition; ISKCON is the only tradition " +
            "this service computes those for."

    /**
     * Deliberately empty; see the class KDoc. Returning ISKCON's Ekadashi dates here would
     * present Gaudiya rulings as Telugu ones.
     */
    override fun ekadashiObservances(
        year: Int,
        ctx: ObservanceContext,
    ): List<ObservanceDecision> = emptyList()

    override fun eventResolution(year: Int, ctx: ObservanceContext): YearResolution =
        TeluguEventCatalog.resolver.resolveYear(year, ctx)
}

/**
 * The Telugu catalog of dated observances.
 *
 * Definitional entries only: the tithi named in the entry is the festival's own rule. Mapped to
 * [RuleConfidence.INFERRED] — see the Marathi catalog's KDoc for why that is the honest value
 * for an unverified definitional rule.
 */
object TeluguEventCatalog {

    val KNOWN_GAPS: Map<String, String> = linkedMapOf(
        "All Telugu Ekadashi vrata and their parana" to
            "Ekadashi observance is not implemented for this tradition; see MarathiEventCatalog's " +
                "equivalent note for why borrowing ISKCON's rulings is not an option here.",
        "Varalakshmi Vratam" to
            "Held on the Friday before the Shravana full moon. The weekday is part of the rule " +
                "and [EventRule] has no form that expresses a tithi-weekday conjunction.",
        "Bathukamma" to
            "A nine-night observance tied to the Bhadrapada–Ashvina lunar structure whose exact " +
                "per-day rules have not been sourced; omitted rather than approximated.",
    )

    val definitions: List<EventDefinition> = listOf(

        // ── Chaitra ─────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "ugadi",
            name = "Ugadi (Telugu New Year)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Chaitra", Paksha.SHUKLA, 1),
            sourceNote =
                "Chaitra Shukla Pratipada is the Telugu new year by definition — the name and " +
                    "the day are the same thing. Not yet checked against a published Telugu " +
                    "panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "sri_rama_navami",
            name = "Sri Rama Navami",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Chaitra", Paksha.SHUKLA, 9),
            sourceNote =
                "Chaitra Shukla Navami — the tithi is the festival's definition, and the " +
                    "wedding of Rama and Sita is celebrated on it in the Telugu states. Not " +
                    "yet checked against a published Telugu panchang.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Shravana ────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "nagula_chavithi",
            name = "Nagula Chavithi",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Shravana", Paksha.SHUKLA, 4),
            sourceNote =
                "Shravana Shukla Chavithi — the tithi is in the festival's name. Observed one " +
                    "tithi earlier than the northern Nag Panchami; the Telugu convention is " +
                    "stated here. Not yet checked against a published Telugu panchang.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Bhadrapada ──────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "vinayaka_chavithi",
            name = "Vinayaka Chavithi",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Bhadrapada", Paksha.SHUKLA, 4),
            sourceNote =
                "Bhadrapada Shukla Chaturthi — the tithi is in the festival's name. Not yet " +
                    "checked against a published Telugu panchang.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Ashvina ─────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "sharannavaratri_begins",
            name = "Sharannavaratri begins",
            group = EventGroup.SEASONAL,
            rule = onTithi("Ashvina", Paksha.SHUKLA, 1),
            sourceNote =
                "Ashvina Shukla Pratipada opens the nine-night Sharannavaratri; this entry " +
                    "dates the opening day only. Not yet checked against a published Telugu " +
                    "panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "dasara",
            name = "Dasara (Vijayadashami)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Ashvina", Paksha.SHUKLA, 10),
            sourceNote =
                "Ashvina Shukla Dashami — Vijayadashami's tithi is definitional. Not yet " +
                    "checked against a published Telugu panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "deepavali",
            name = "Deepavali",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Ashvina", Paksha.KRISHNA, 15),
            sourceNote =
                "Ashvina Amavasya carries Deepavali in the Telugu calendar; the Amavasya is " +
                    "the day's definition. Not yet checked against a published Telugu panchang.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Kartika ─────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "karthika_purnima",
            name = "Karthika Purnima (Karthika Deepotsavam)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Kartika", Paksha.SHUKLA, 15),
            sourceNote =
                "Kartika Purnima closes the month of lamps in the Telugu states — the Purnima " +
                    "is the day's definition. Not yet checked against a published Telugu " +
                    "panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
    )

    /** A resolver over [definitions]. Construction validates every cross-reference. */
    val resolver: EventResolver = EventResolver(definitions)

    /** The Telugu calendar is amanta, so every lunar rule in this catalog states that. */
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
