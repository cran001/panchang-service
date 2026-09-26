package org.panchang.sampradaya

/**
 * Malayalam (Kerala) calendar rules — Kolla Varsham.
 *
 * **Solar reckoning**: months open on the Sankranti day, under their own names — Metam,
 * Edavam, Mithunam, Karkatakam, Chingam … The era year (Kolla Varsham, epoch 825 CE) turns
 * over at Chingam 1 in August–September, while Vishu in April — the day the Sun enters Mesha —
 * is the astronomical new year of the tradition's own reckoning. Both are dated here, and the
 * difference between them is the difference between an era boundary and a new year.
 *
 * ## What is and is not implemented
 *
 * The festival catalog dates the definitional observances a *solar* rule can express.
 * **Ekadashi fasting and parana timing are not implemented for this tradition**: this service
 * computes Ekadashi observances only for ISKCON. Onam — the tradition's principal festival —
 * is dated by the Thiruvonam nakshatra within the solar month Chingam, and no rule form
 * expresses that conjunction yet; see [MalayalamEventCatalog.KNOWN_GAPS].
 */
class MalayalamRules : SampradayaRules {

    override val id: String = "malayalam"

    override val displayName: String = "Malayalam (Kerala, Kolla Varsham)"

    override val status: VerificationStatus = VerificationStatus.UNVERIFIED

    override val provenanceNote: String =
        "Festival rules are the definitional solar (Sankranti-opening) rules of the Malayalam " +
            "calendar — Chingam 1 when the Sun enters Simha, Vishu when it enters Mesha — as " +
            "stated in standard Indian calendar references. They have NOT been checked " +
            "against a published Malayalam panchang for any year. The Kolla Varsham year " +
            "number is not computed. Ekadashi and parana timing are not computed for this " +
            "tradition; ISKCON is the only tradition this service computes those for."

    /** Deliberately empty; see the class KDoc. */
    override fun ekadashiObservances(
        year: Int,
        ctx: ObservanceContext,
    ): List<ObservanceDecision> = emptyList()

    override fun eventResolution(year: Int, ctx: ObservanceContext): YearResolution =
        MalayalamEventCatalog.resolver.resolveYear(year, ctx)
}

/**
 * The Malayalam catalog of dated observances.
 *
 * Solar entries use [EventRule.OnSolarMonth] with [SolarTransition.SANKRANTI_START] — the
 * Malayalam month opens on the civil day containing the ingress.
 *
 * Definitional entries only; [RuleConfidence.INFERRED] for the reasons the Marathi catalog's
 * KDoc sets out.
 */
object MalayalamEventCatalog {

    val KNOWN_GAPS: Map<String, String> = linkedMapOf(
        "All Malayalam Ekadashi vrata and their parana" to
            "Ekadashi observance is not implemented for this tradition; see " +
                "MarathiEventCatalog's equivalent note.",
        "Onam" to
            "Onam falls on the Thiruvonam nakshatra within the solar month Chingam — the " +
                "tradition's principal festival, and one whose rule no [EventRule] form " +
                "expresses (a nakshatra matched within a *solar* month). It is omitted " +
                "rather than dated by a proxy that would sometimes be right.",
        "Thiruvathira" to
            "The Thiruvathira nakshatra in the solar month Dhanu; same missing form as Onam.",
        "Vishu Kani" to
            "The pre-dawn viewing is a time-of-day convention on the Vishu date; the date is " +
                "carried, the convention is not.",
    )

    val definitions: List<EventDefinition> = listOf(

        // ── Mesha (Metam) ───────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "vishu",
            name = "Vishu (Metam 1)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = EventRule.OnSolarMonth(solarMonthIndex = 1, dayOfMonth = 1),
            sourceNote =
                "The day the Sun enters Mesha opens the Malayalam month Metam; Vishu — the " +
                    "astronomical new year — is that day by definition. Not yet checked " +
                    "against a published Malayalam panchang.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Simha (Chingam) ─────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "chingam_1",
            name = "Chingam 1 (Kolla Varsham New Year)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = EventRule.OnSolarMonth(solarMonthIndex = 5, dayOfMonth = 1),
            sourceNote =
                "The day the Sun enters Simha opens the month Chingam and turns over the " +
                    "Kolla Varsham era year (epoch 825 CE) — Chingam 1 is the era new year " +
                    "by definition, a month the era counts from that Vishu does not. Not yet " +
                    "checked against a published Malayalam panchang.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Karka (Karkatakam) ──────────────────────────────────────────────────────────────
        EventDefinition(
            id = "karkidakam_1",
            name = "Karkidakam 1 (Dakshinayana begins)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = EventRule.OnSolarMonth(solarMonthIndex = 4, dayOfMonth = 1),
            sourceNote =
                "The Karka Sankranti opens the month of Karkidakam and the sun's southern " +
                    "course, kept in Kerala as the month of the Ramayana. Not yet checked " +
                    "against a published Malayalam panchang.",
            confidence = RuleConfidence.INFERRED,
        ),
    )

    /** A resolver over [definitions]. Construction validates every cross-reference. */
    val resolver: EventResolver = EventResolver(definitions)
}
