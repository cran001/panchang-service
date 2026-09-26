package org.panchang.sampradaya

import org.panchang.core.MonthReckoning
import org.panchang.core.Paksha

/**
 * Odia (Odisha) calendar rules.
 *
 * **Solar reckoning for the year**: the Odia year opens on Pana Sankranti (Maha Bishuba
 * Sankranti), the day the Sun enters Mesha — with the Odia convention, like the Tamil and
 * Malayalam one, that the month opens on the ingress day itself
 * ([SolarTransition.SANKRANTI_START]). **Lunar reckoning for the festivals**: Ratha Yatra and
 * the rest are dated by amanta tithis, stated as [MonthReckoning.AMANTA].
 *
 * ## What is and is not implemented
 *
 * The festival catalog dates the definitional observances. **Ekadashi fasting and parana
 * timing are not implemented for this tradition**: this service computes Ekadashi observances
 * only for ISKCON.
 */
class OdiaRules : SampradayaRules {

    override val id: String = "odia"

    override val displayName: String = "Odia (Odisha)"

    override val status: VerificationStatus = VerificationStatus.UNVERIFIED

    override val provenanceNote: String =
        "Festival rules are the definitional solar and tithi rules of the Odia calendar — " +
            "Pana Sankranti on the Mesha ingress day, Ratha Yatra on Ashadha Shukla " +
            "Dwitiya — as stated in standard Indian calendar references. They have NOT been " +
            "checked against a published Odia panji (Birajabanit Panjika, Khadiratna or " +
            "other publisher) for any year, and no local convention is modelled. Ekadashi " +
            "and parana timing are not computed for this tradition; ISKCON is the only " +
            "tradition this service computes those for."

    /** Deliberately empty; see the class KDoc. */
    override fun ekadashiObservances(
        year: Int,
        ctx: ObservanceContext,
    ): List<ObservanceDecision> = emptyList()

    override fun eventResolution(year: Int, ctx: ObservanceContext): YearResolution =
        OdiaEventCatalog.resolver.resolveYear(year, ctx)
}

/**
 * The Odia catalog of dated observances.
 *
 * Definitional entries only; [RuleConfidence.INFERRED] for the reasons the Marathi catalog's
 * KDoc sets out.
 */
object OdiaEventCatalog {

    val KNOWN_GAPS: Map<String, String> = linkedMapOf(
        "All Odia Ekadashi vrata and their parana" to
            "Ekadashi observance is not implemented for this tradition; see " +
                "MarathiEventCatalog's equivalent note.",
        "Kumar Purnima's pre-dawn convention" to
            "The Ashvina Purnima date is carried; the early-morning Kumar puja is a " +
                "time-of-day matter the rule does not express.",
        "Chandan Yatra, Snana Yatra and the Ratha Yatra cycle beyond the opening day" to
            "The opening day is definitional (Ashadha Shukla Dwitiya); the subsequent yatras " +
                "run on temple calendars whose lunar rules are not yet sourced, and ISKCON's " +
                    "Gaudiya catalog carries its own Snana Yatra entry under its own reckoning.",
    )

    val definitions: List<EventDefinition> = listOf(

        // ── Solar ───────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "pana_sankranti",
            name = "Pana Sankranti (Odia New Year, Maha Bishuba Sankranti)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = EventRule.OnSolarMonth(solarMonthIndex = 1, dayOfMonth = 1),
            sourceNote =
                "The day the Sun enters Mesha is Pana Sankranti and opens the Odia year; the " +
                    "Odia month opens on the ingress day itself. Not yet checked against a " +
                    "published Odia panji.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "makara_sankranti",
            name = "Makara Sankranti",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = EventRule.OnSolarMonth(solarMonthIndex = 10, dayOfMonth = 1),
            sourceNote =
                "The day the Sun enters Makara; a solar rule for a solar festival. Not yet " +
                    "checked against a published Odia panji.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Ashadha (lunar, amanta) ─────────────────────────────────────────────────────────
        EventDefinition(
            id = "ratha_yatra",
            name = "Ratha Yatra (Puri)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Ashadha", Paksha.SHUKLA, 2),
            sourceNote =
                "Ashadha Shukla Dwitiya is the day the Puri chariots roll — the tithi is " +
                    "stated by the Shree Jagannath Temple's own calendar tradition and is " +
                    "definitional. Not yet checked against a published Odia panji.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Ashvina (lunar, amanta) ─────────────────────────────────────────────────────────
        EventDefinition(
            id = "kumar_purnima",
            name = "Kumar Purnima",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Ashvina", Paksha.SHUKLA, 15),
            sourceNote =
                "The Ashvina Purnima is Kumar Purnima in Odisha; the Purnima is the day's " +
                    "definition. Not yet checked against a published Odia panji.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "diwali_badabadua_daka",
            name = "Diwali (Badabadua Daka)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Ashvina", Paksha.KRISHNA, 15),
            sourceNote =
                "The Ashvina Amavasya carries Diwali and Odisha's Badabadua Daka; the " +
                    "Amavasya is the day's definition. The evening jute-torch convention is " +
                    "a time-of-day matter this rule does not carry. Not yet checked against " +
                    "a published Odia panji.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Margashirsha (lunar, amanta) ────────────────────────────────────────────────────
        EventDefinition(
            id = "prathamastami",
            name = "Prathamastami",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Margashirsha", Paksha.KRISHNA, 8),
            sourceNote =
                "Margashirsha Krishna Ashtami — 'the first eighth' of the dark half; the " +
                    "tithi is in the festival's name. Not yet checked against a published " +
                    "Odia panji.",
            confidence = RuleConfidence.INFERRED,
        ),
    )

    /** A resolver over [definitions]. Construction validates every cross-reference. */
    val resolver: EventResolver = EventResolver(definitions)

    /** The Odia lunar panchang is amanta, so every lunar rule in this catalog states that. */
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
