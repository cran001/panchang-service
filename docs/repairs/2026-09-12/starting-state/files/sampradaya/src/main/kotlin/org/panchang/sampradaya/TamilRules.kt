package org.panchang.sampradaya

/**
 * Tamil (Tamil Nadu) calendar rules.
 *
 * **Solar reckoning**: months open on the day the Sun enters a rashi (Sankranti), with names
 * of their own — Chithirai, Vaikasi, Aani … — and the year opens on the day the Sun enters
 * Mesha: Puthandu. The sixty-year cycle (Prabhava, Vibhava, …) names the years; [SixtyYearCycle]
 * holds the arithmetic and its anchor, and [EventRule.In60YearCycle] holds rules reserved to a
 * cycle year.
 *
 * ## What is and is not implemented
 *
 * The festival catalog dates the definitional observances that a *solar* rule can express.
 * **Ekadashi fasting and parana timing are not implemented for this tradition**: this service
 * computes Ekadashi observances only for ISKCON. Several major Tamil festivals are dated by a
 * nakshatra *within* a solar month (Thai Poosam, Panguni Uthiram, Karthigai Deepam), and no
 * rule form expresses that conjunction yet — see [TamilEventCatalog.KNOWN_GAPS].
 */
class TamilRules : SampradayaRules {

    override val id: String = "tamil"

    override val displayName: String = "Tamil (Tamil Nadu)"

    override val status: VerificationStatus = VerificationStatus.UNVERIFIED

    override val provenanceNote: String =
        "Festival rules are the definitional solar (Sankranti-opening) rules of the Tamil " +
            "calendar — Puthandu on the day the Sun enters Mesha, Thai Pongal on the day it " +
            "enters Makara — as stated in standard Indian calendar references. They have NOT " +
            "been checked against a published Tamil panchang for any year. The sixty-year " +
            "cycle position is anchored to the published cycle years 2024–2026 (Krodhi, " +
            "Vishvavasu, Parabhava); the cycle's sixty names themselves are not carried in " +
            "this software. Ekadashi and parana timing are not computed for this tradition; " +
            "ISKCON is the only tradition this service computes those for."

    /** Deliberately empty; see the class KDoc. */
    override fun ekadashiObservances(
        year: Int,
        ctx: ObservanceContext,
    ): List<ObservanceDecision> = emptyList()

    override fun eventResolution(year: Int, ctx: ObservanceContext): YearResolution =
        TamilEventCatalog.resolver.resolveYear(year, ctx)
}

/**
 * The Tamil catalog of dated observances.
 *
 * Solar entries use [EventRule.OnSolarMonth] with the Tamil month-opening convention — the
 * civil day containing the Sankranti is day 1, whatever the clock says ([SolarTransition.SANKRANTI_START]).
 * Tamil months are named for their own tradition below the entry name; the rule speaks rashi
 * names because those are what the Sun visits.
 *
 * Definitional entries only; [RuleConfidence.INFERRED] for the reasons the Marathi catalog's
 * KDoc sets out.
 */
object TamilEventCatalog {

    val KNOWN_GAPS: Map<String, String> = linkedMapOf(
        "All Tamil Ekadashi vrata and their parana" to
            "Ekadashi observance is not implemented for this tradition; see " +
                "MarathiEventCatalog's equivalent note.",
        "Thai Poosam, Panguni Uthiram, Karthigai Deepam, Thiruvathirai" to
            "Dated by a nakshatra running within a solar month — Poosam in Thai, Uthiram in " +
                "Panguni, Karthigai in Kartikai. [EventRule.OnNakshatraInMonth] matches a " +
                "nakshatra within a *lunar* month and would misdate these; no form expresses " +
                "the solar-month conjunction, so they are omitted rather than approximated.",
        "Deepavali" to
            "Kept a day before the north Indian Amavasya observance on the Tamil Aippasi " +
                "Krishna Chaturdashi–Amavasya cusp with a pre-dawn convention; the exact " +
                "local rule has not been sourced, so the date is omitted rather than guessed.",
        "Onam and other Chingam observances" to
            "Onam is Thiruvonam nakshatra in the solar month Chingam — the same missing " +
                "solar-month-nakshatra form; catalogued under the Malayalam tradition's gaps.",
    )

    val definitions: List<EventDefinition> = listOf(

        // ── Mesha (Chithirai) ───────────────────────────────────────────────────────────────
        EventDefinition(
            id = "puthandu",
            name = "Puthandu (Tamil New Year)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = EventRule.OnSolarMonth(solarMonthIndex = 1, dayOfMonth = 1),
            sourceNote =
                "The day the Sun enters Mesha opens the Tamil month Chithirai and the Tamil " +
                    "year — Puthandu is the Mesha Sankranti day by definition, and the Tamil " +
                    "month opens on the ingress day itself whatever the clock time. Not yet " +
                    "checked against a published Tamil panchang.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Karka (Aadi) ────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "aadi_pirappu",
            name = "Aadi Pirappu (Aadi 1, Dakshinayana begins)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = EventRule.OnSolarMonth(solarMonthIndex = 4, dayOfMonth = 1),
            sourceNote =
                "The Karka Sankranti opens the month of Aadi and the sun's southern course; " +
                    "the Tamil month opens on the ingress day. Not yet checked against a " +
                    "published Tamil panchang.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Makara (Thai) ───────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "thai_pongal",
            name = "Thai Pongal",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = EventRule.OnSolarMonth(solarMonthIndex = 10, dayOfMonth = 1),
            sourceNote =
                "The Makara Sankranti opens the month of Thai, and Thai 1 is Thai Pongal — " +
                    "the harvest festival is the Sankranti day by definition. Not yet checked " +
                    "against a published Tamil panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "mattu_pongal",
            name = "Mattu Pongal",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = EventRule.OnSolarMonth(solarMonthIndex = 10, dayOfMonth = 2),
            sourceNote =
                "Mattu Pongal falls the day after Thai Pongal — Thai 2 by definition. Not " +
                    "yet checked against a published Tamil panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
    )

    /** A resolver over [definitions]. Construction validates every cross-reference. */
    val resolver: EventResolver = EventResolver(definitions)
}
