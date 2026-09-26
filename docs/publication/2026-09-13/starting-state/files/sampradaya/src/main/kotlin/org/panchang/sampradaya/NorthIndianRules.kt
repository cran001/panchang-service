package org.panchang.sampradaya

import org.panchang.core.MonthReckoning
import org.panchang.core.Paksha

/**
 * North Indian Hindi-belt (Purnimanta) calendar rules — UP, MP, Bihar, Rajasthan, Haryana,
 * Himachal and the adjoining Hindi-speaking regions.
 *
 * **Purnimanta reckoning**: the lunar month runs full moon to full moon, so the Krishna paksha
 * *precedes* the Shukla paksha and carries the next amanta month's name. The year is counted
 * from Chaitra Shukla Pratipada — Chaitra Navratri — exactly like the amanta traditions; only
 * the month *naming* differs, and for exactly half the fortnights.
 *
 * ## What is and is not implemented
 *
 * The festival catalog dates the definitional observances. **Ekadashi fasting and parana
 * timing are not implemented for this tradition**: this service computes Ekadashi observances
 * only for ISKCON.
 */
class NorthIndianRules : SampradayaRules {

    override val id: String = "northindian"

    override val displayName: String = "North Indian Hindi (Purnimanta)"

    override val status: VerificationStatus = VerificationStatus.UNVERIFIED

    override val provenanceNote: String =
        "Festival rules are the definitional tithi and Sankranti rules of the purnimanta " +
            "Hindi-belt calendar — Ram Navami on Chaitra Shukla Navami, Krishna Janmashtami " +
            "on Bhadra Krishna Ashtami under purnimanta naming, and so on — as stated in " +
            "standard Indian calendar references. They have NOT been checked against a " +
            "published Hindi panchang for any year, and no local convention is modelled. " +
            "Ekadashi and parana timing are not computed for this tradition; ISKCON is the " +
            "only tradition this service computes those for."

    /** Deliberately empty; see the class KDoc. */
    override fun ekadashiObservances(
        year: Int,
        ctx: ObservanceContext,
    ): List<ObservanceDecision> = emptyList()

    override fun eventResolution(year: Int, ctx: ObservanceContext): YearResolution =
        NorthIndianEventCatalog.resolver.resolveYear(year, ctx)
}

/**
 * The North Indian catalog of dated observances.
 *
 * ## Month names in this file are purnimanta
 *
 * Every lunar rule below states [MonthReckoning.PURNIMANTA] explicitly, including the Shukla
 * ones where the two conventions agree, exactly as the Gaudiya catalog does — so that no entry
 * can be misread as amanta. The practical consequence: a Krishna-paksha rule names the month
 * *after* the amanta month the same fortnight belongs to. Mahashivaratri appears below as
 * Magha Krishna Chaturdashi, which the amanta traditions call Phalguna Krishna Chaturdashi.
 *
 * Definitional entries only; [RuleConfidence.INFERRED] for the reasons the Marathi catalog's
 * KDoc sets out.
 */
object NorthIndianEventCatalog {

    val KNOWN_GAPS: Map<String, String> = linkedMapOf(
        "All north Indian Ekadashi vrata and their parana" to
            "Ekadashi observance is not implemented for this tradition; see " +
                "MarathiEventCatalog's equivalent note.",
        "Karva Chauth's moonrise fast-breaking" to
            "The fast is dated (Kartika Krishna Chaturthi, purnimanta) but breaking it at " +
                "moonrise is a time-of-day rule this catalog does not carry; the date alone " +
                "is what is asserted here.",
        "Hartalika Teej, Ahoi Ashtami and other weekday-conditional vratas" to
            "Tied to a weekday as well as a tithi; [EventRule] has no form for the " +
                "conjunction.",
    )

    val definitions: List<EventDefinition> = listOf(

        // ── Chaitra ─────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "chaitra_navratri_begins",
            name = "Chaitra Navratri begins (Hindi New Year)",
            group = EventGroup.SEASONAL,
            rule = onTithi("Chaitra", Paksha.SHUKLA, 1),
            sourceNote =
                "Chaitra Shukla Pratipada opens the Chaitra Navratri and the purnimanta year; " +
                    "this entry dates the opening day only. Not yet checked against a " +
                    "published Hindi panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "ram_navami",
            name = "Rama Navami",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Chaitra", Paksha.SHUKLA, 9),
            sourceNote =
                "Chaitra Shukla Navami — the tithi is the festival's definition. Not yet " +
                    "checked against a published Hindi panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "hanuman_jayanti",
            name = "Hanuman Jayanti",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Chaitra", Paksha.SHUKLA, 15),
            sourceNote =
                "The Hindi belt observes Hanuman Jayanti on the Chaitra full moon; the " +
                    "Maharashtrian full-moon convention is the same day stated amanta. Not " +
                    "yet checked against a published Hindi panchang.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Ashadha ─────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "guru_purnima",
            name = "Guru Purnima",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Ashadha", Paksha.SHUKLA, 15),
            sourceNote =
                "Ashadha Purnima — the Purnima is the day's definition. Not yet checked " +
                    "against a published Hindi panchang.",
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
                    "checked against a published Hindi panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "raksha_bandhan",
            name = "Raksha Bandhan",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Shravana", Paksha.SHUKLA, 15),
            sourceNote =
                "Shravana Purnima carries Raksha Bandhan; the tying itself belongs to the " +
                    "day's morning, which this rule does not express. Not yet checked against " +
                    "a published Hindi panchang.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Bhadra (purnimanta; the fortnight amanta calls Shravana Krishna) ────────────────
        EventDefinition(
            id = "krishna_janmashtami",
            name = "Krishna Janmashtami",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Bhadrapada", Paksha.KRISHNA, 8),
            sourceNote =
                "Bhadra Krishna Ashtami under purnimanta naming — the tithi is the " +
                    "festival's definition, and the midnight birth is a time-of-day matter " +
                    "this rule does not carry. Not yet checked against a published Hindi " +
                    "panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "ganesh_chaturthi",
            name = "Ganesh Chaturthi",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Bhadrapada", Paksha.SHUKLA, 4),
            sourceNote =
                "Bhadrapada Shukla Chaturthi — the tithi is in the festival's name. Not yet " +
                    "checked against a published Hindi panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "ananta_chaturdashi",
            name = "Ananta Chaturdashi",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Bhadrapada", Paksha.SHUKLA, 14),
            sourceNote =
                "Bhadrapada Shukla Chaturdashi — the tithi is in the festival's name. Not " +
                    "yet checked against a published Hindi panchang.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Ashvina ─────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "sharadiya_navratri_begins",
            name = "Sharadiya Navratri begins",
            group = EventGroup.SEASONAL,
            rule = onTithi("Ashvina", Paksha.SHUKLA, 1),
            sourceNote =
                "Ashvina Shukla Pratipada opens the nine-night Sharadiya Navratri; this " +
                    "entry dates the opening day only. Not yet checked against a published " +
                    "Hindi panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "dussehra",
            name = "Dussehra (Vijayadashami)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Ashvina", Paksha.SHUKLA, 10),
            sourceNote =
                "Ashvina Shukla Dashami — Vijayadashami's tithi is definitional. Not yet " +
                    "checked against a published Hindi panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "diwali",
            name = "Diwali (Lakshmi Puja)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Kartika", Paksha.KRISHNA, 15),
            sourceNote =
                "The Amavasya that carries Diwali — Kartika Krishna 15 under purnimanta " +
                    "naming, the fortnight amanta reckoning calls Ashvina Krishna; north " +
                    "Indian panchangs cite Diwali as Kartik Amavasya. The puja falls in the " +
                    "evening, which this rule does not express. Not yet checked against a " +
                    "published Hindi panchang.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Kartika ─────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "karva_chauth",
            name = "Karva Chauth",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Kartika", Paksha.KRISHNA, 4),
            sourceNote =
                "Kartika Krishna Chaturthi under purnimanta naming — the amanta tradition " +
                    "calls the same fortnight Ashvina Krishna. The fast runs to moonrise, " +
                    "which this rule does not express. Not yet checked against a published " +
                    "Hindi panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "bhai_dooj",
            name = "Bhai Dooj",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Kartika", Paksha.SHUKLA, 2),
            sourceNote =
                "Kartika Shukla Dwitiya — the name and the tithi agree. Not yet checked " +
                    "against a published Hindi panchang.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Solar ───────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "makar_sankranti",
            name = "Makar Sankranti",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = EventRule.OnSolarMonth(solarMonthIndex = 10, dayOfMonth = 1),
            sourceNote =
                "The day the Sun enters Makara — a solar rule for a solar festival. The " +
                    "widely-printed 'Sankranti before sunrise' shift convention is not yet " +
                    "established against a published panchang; this rule takes the ingress " +
                    "day itself.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Magha ───────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "vasant_panchami",
            name = "Vasant Panchami (Saraswati Puja)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Magha", Paksha.SHUKLA, 5),
            sourceNote =
                "Magha Shukla Panchami — the tithi is in the festival's name. Not yet " +
                    "checked against a published Hindi panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        // ── Phalguna (purnimanta; the fortnight amanta calls Magha Krishna) ────────────────
        EventDefinition(
            id = "mahashivaratri",
            name = "Maha Shivaratri",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Phalguna", Paksha.KRISHNA, 14),
            sourceNote =
                "Phalguna Krishna Chaturdashi under purnimanta naming — the fortnight amanta " +
                    "reckoning calls Magha Krishna Chaturdashi, and the same mid-February " +
                    "night everywhere in India. The vigil runs through the night, which this " +
                    "rule does not express. Not yet checked against a published Hindi " +
                    "panchang.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Phalguna Shukla ─────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "holi",
            name = "Holi (Holika Dahan)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Phalguna", Paksha.SHUKLA, 15),
            sourceNote =
                "Phalguna Purnima carries Holika Dahan, the eve of Holi; the Purnima is the " +
                    "day's definition. Not yet checked against a published Hindi panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
    )

    /** A resolver over [definitions]. Construction validates every cross-reference. */
    val resolver: EventResolver = EventResolver(definitions)

    /**
     * Every rule in this catalog is stated in purnimanta month names, including the Shukla
     * ones where amanta agrees, so that no entry can be misread later.
     */
    private fun onTithi(
        month: String,
        paksha: Paksha,
        tithiNumberInPaksha: Int,
    ): EventRule.OnTithi = EventRule.OnTithi(
        lunarMonthName = month,
        paksha = paksha,
        tithiNumberInPaksha = tithiNumberInPaksha,
        reckoning = MonthReckoning.PURNIMANTA,
        atSunrise = true,
    )
}
