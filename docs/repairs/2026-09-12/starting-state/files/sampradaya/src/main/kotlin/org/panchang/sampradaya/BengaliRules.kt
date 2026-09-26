package org.panchang.sampradaya

import org.panchang.core.MonthReckoning
import org.panchang.core.Paksha

/**
 * Bengali (West Bengal, Tripura) calendar rules.
 *
 * **Solar reckoning for the era**: the Bengali year turns over on Pohela Boishakh, which
 * follows the Mesha Sankranti — the Bengali convention is that an ingress before sunrise opens
 * the month that day and a later ingress opens it the next, which is
 * [SolarTransition.SANKRANTI_AT_SUNRISE] and makes this the tradition that exercises that
 * branch. **Lunar reckoning for the festivals**: the Durga Puja cycle is dated by amanta tithis
 * exactly as the pan-Indian lunar panchang dates them, and those entries state
 * [MonthReckoning.AMANTA].
 *
 * ## What is and is not implemented
 *
 * The festival catalog dates the definitional observances. **Ekadashi fasting and parana
 * timing are not implemented for this tradition**: this service computes Ekadashi observances
 * only for ISKCON.
 */
class BengaliRules : SampradayaRules {

    override val id: String = "bengali"

    override val displayName: String = "Bengali (West Bengal, Tripura)"

    override val status: VerificationStatus = VerificationStatus.UNVERIFIED

    override val provenanceNote: String =
        "Festival rules are the definitional solar and tithi rules of the Bengali calendar — " +
            "Pohela Boishakh on the first sunrise after the Mesha Sankranti, Durga Ashtami " +
            "on Ashvina Shukla Ashtami — as stated in standard Indian calendar references. " +
            "They have NOT been checked against a published Bengali panjika (Viswakosh, " +
            "Gupta Press or other publisher) for any year, and the revised (1963-era) " +
            "Bengali calendar's regional date adjustments are not modelled. Ekadashi and " +
            "parana timing are not computed for this tradition; ISKCON is the only tradition " +
            "this service computes those for."

    /** Deliberately empty; see the class KDoc. */
    override fun ekadashiObservances(
        year: Int,
        ctx: ObservanceContext,
    ): List<ObservanceDecision> = emptyList()

    override fun eventResolution(year: Int, ctx: ObservanceContext): YearResolution =
        BengaliEventCatalog.resolver.resolveYear(year, ctx)
}

/**
 * The Bengali catalog of dated observances.
 *
 * The New Year entry is the one that exercises [SolarTransition.SANKRANTI_AT_SUNRISE]: a
 * Mesha ingress that falls after sunrise opens Boishakh the following day, which is why
 * Pohela Boishakh commonly prints one day after the Odia and Tamil new years.
 *
 * Definitional entries only; [RuleConfidence.INFERRED] for the reasons the Marathi catalog's
 * KDoc sets out.
 */
object BengaliEventCatalog {

    val KNOWN_GAPS: Map<String, String> = linkedMapOf(
        "All Bengali Ekadashi vrata and their parana" to
            "Ekadashi observance is not implemented for this tradition; see " +
                "MarathiEventCatalog's equivalent note.",
        "Mahalaya's pre-dawn Mahishasuramardini convention" to
            "The Amavasya date is not itself in dispute, but the convention of beginning " +
                "Devipaksha on it is a period rule this catalog does not carry; the " +
                "pitripaksha opening is dated by the Amavasya entry only if a rule is " +
                "sourced, and until then it is omitted.",
        "Dol Purnima's street convention and Holi" to
            "The Purnima is dated; the dawn processions of Dol are a time-of-day matter the " +
                "rule does not express.",
    )

    val definitions: List<EventDefinition> = listOf(

        // ── Solar ───────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "pohela_boishakh",
            name = "Pohela Boishakh (Bengali New Year)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = EventRule.OnSolarMonth(
                solarMonthIndex = 1,
                dayOfMonth = 1,
                transitionPoint = SolarTransition.SANKRANTI_AT_SUNRISE,
            ),
            sourceNote =
                "Boishakh opens on the first day whose sunrise follows the Sun's entry into " +
                    "Mesha — the Bengali month-opening convention — so an afternoon ingress " +
                    "pushes Pohela Boishakh to the next civil day. Not yet checked against a " +
                    "published Bengali panjika.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Bhadrapada (lunar, amanta) ──────────────────────────────────────────────────────
        EventDefinition(
            id = "mahalaya",
            name = "Mahalaya (Devipaksha begins)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Bhadrapada", Paksha.KRISHNA, 15),
            sourceNote =
                "The Bhadrapada Amavasya is Mahalaya and opens Devipaksha; the Amavasya is " +
                    "the day's definition. Not yet checked against a published Bengali " +
                    "panjika.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Ashvina (lunar, amanta) ─────────────────────────────────────────────────────────
        EventDefinition(
            id = "durga_ashtami",
            name = "Maha Ashtami (Durga Puja)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Ashvina", Paksha.SHUKLA, 8),
            sourceNote =
                "Ashvina Shukla Ashtami is Maha Ashtami, the central day of Durga Puja; the " +
                    "tithi is in the festival's own name. The puja's hours (sandhi puja " +
                    "straddling Ashtami and Navami) are time-of-day matters this rule does " +
                    "not carry. Not yet checked against a published Bengali panjika.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "vijaya_dashami",
            name = "Vijaya Dashami",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Ashvina", Paksha.SHUKLA, 10),
            sourceNote =
                "Ashvina Shukla Dashami closes Durga Puja — Vijayadashami's tithi is " +
                    "definitional. Not yet checked against a published Bengali panjika.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "kojagori_lakshmi_puja",
            name = "Kojagori Lakshmi Puja",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Ashvina", Paksha.SHUKLA, 15),
            sourceNote =
                "The Ashvina Purnima is Kojagori Lakshmi Puja in Bengal, on the full-moon " +
                    "night the tradition names 'who is awake'; the Purnima is the day's " +
                    "definition. Not yet checked against a published Bengali panjika.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "kali_puja",
            name = "Kali Puja (Diwali)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Ashvina", Paksha.KRISHNA, 15),
            sourceNote =
                "Bengal keeps Kali Puja on the Ashvina Amavasya night, where the north " +
                    "keeps Lakshmi Puja; the Amavasya is the day's definition. The " +
                    "midnight-worship convention is a time-of-day matter this rule does not " +
                    "carry. Not yet checked against a published Bengali panjika.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Phalguna (lunar, amanta) ────────────────────────────────────────────────────────
        EventDefinition(
            id = "dol_purnima",
            name = "Dol Purnima (Holi)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Phalguna", Paksha.SHUKLA, 15),
            sourceNote =
                "The Phalguna Purnima is Dol Purnima, Bengal's Holi; the Purnima is the " +
                    "day's definition. Not yet checked against a published Bengali panjika.",
            confidence = RuleConfidence.INFERRED,
        ),
    )

    /** A resolver over [definitions]. Construction validates every cross-reference. */
    val resolver: EventResolver = EventResolver(definitions)

    /** The Bengali lunar panchang is amanta, so every lunar rule in this catalog states that. */
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
