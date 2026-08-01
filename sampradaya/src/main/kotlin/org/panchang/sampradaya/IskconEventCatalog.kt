package org.panchang.sampradaya

import org.panchang.core.MonthReckoning
import org.panchang.core.Paksha

/**
 * The ISKCON / Gaudiya Vaisnava catalog of non-Ekadasi observances.
 *
 * ## Month names in this file are purnimanta
 *
 * The Gaudiya calendar starts each month directly after the full moon, so its Krishna paksha
 * precedes its Shukla paksha and every Krishna fortnight carries the *next* amanta month's name.
 * Janmastami is "Bhadra Krsna Astami" in this tradition and "Shravana Krsna Astami" under amanta
 * reckoning — the same fortnight under two names. Every rule below therefore states
 * [MonthReckoning.PURNIMANTA] explicitly, including the Shukla ones where the two conventions
 * agree, so that nothing here can be read as amanta by accident.
 *
 * This is checkable against the reference export directly: `Mayapur [India] 2026` labels
 * 4 March 2026 (the day after Gaura Purnima, a Krishna Pratipada) as "Caitra", which is the
 * purnimanta name; amanta would call it Phalguna.
 *
 * ## What [RuleConfidence] means here, precisely
 *
 * - [RuleConfidence.CONFIRMED] — the tithi rule is published independently of any one calendar
 *   (the tradition cites the observance by its tithi, often in its very name: *Nityananda
 *   Trayodasi*, *Advaita Saptami*, *Nrsimha Caturdasi*) **and** resolving it reproduces the date
 *   in the harvested Mayapur 2026 calendar.
 * - [RuleConfidence.INFERRED] — the date reproduces Mayapur 2026, but the rule behind it was read
 *   off that single year rather than found stated anywhere. One year is consistent with many
 *   rules; see [org.panchang.sampradaya.IskconEventCatalog.KNOWN_GAPS] for two observances in the
 *   same export that look tithi-ruled for exactly one year and are in fact solar.
 *
 * Note that "confirmed" is therefore confirmed against **one** harvested year. That is weaker
 * than the enum's own wording invites; it is the honest state of this phase. Multi-year
 * confirmation is Phase 4's work.
 *
 * ## What is deliberately absent
 *
 * The roughly 177 acharya appearance and disappearance days are Phase 4's systematic recovery
 * from four to six harvested years, and are not back-solved here from one. The ten that do
 * appear below — six [EventGroup.APPEARANCE] and four [EventGroup.DISAPPEARANCE] — are the ones
 * whose tithi is stated in the tradition's own literature, independent of any calendar file. No entry in this catalog uses [EventRule.FixedGregorian]: nothing here is
 * a bare date, and the whole catalog extrapolates to any year.
 *
 * Ekadasi names, dates, Mahadvadasi detection and parana windows belong to the sampradaya's
 * Ekadasi rules and are not duplicated here. Three entries below *fall on* an Ekadasi tithi —
 * Jhulana Yatra, Bhisma Pancaka and the advent of the Bhagavad-gita — which is not the same
 * thing: the festival tracks the tithi even in a year when the fast itself is deferred to the
 * Dvadasi. Mayapur 2026 shows both halves of that: 21 November is a Trisprsa Mahadvadasi *and*
 * the first day of Bhisma Pancaka.
 */
object IskconEventCatalog {

    /**
     * Observances the reference calendar carries that this catalog deliberately does not.
     *
     * Kept as data rather than prose because it is the list a reviewing pandit most needs, and
     * because two of them are the standing warning against inferring a rule from one year.
     */
    val KNOWN_GAPS: Map<String, String> = linkedMapOf(
        "Ganga Sagara Mela" to
            "Solar, not lunar: it falls on Makara Sankranti. The Mayapur 2026 export puts it on " +
            "15 January, a Magha Krsna Dvadasi, and prints the Sankranti on the same page. " +
            "No EventRule form expresses a solar ingress, so it is omitted rather than given a " +
            "tithi rule that would be right once and wrong thereafter.",
        "Tulasi Jala Dan (begins / ends)" to
            "Solar. The 2026 export begins it 14 April and ends it 14 May — Mesa and Vrsabha " +
            "Sankranti. Both days happen to be a Krsna Dvadasi, which is exactly the coincidence " +
            "that would make a one-year inference look like a rule.",
        "Candana Yatra (21 days from Aksaya Trtiya)" to
            "The start day is in the catalog as part of aksaya_trtiya; the 21-day span itself " +
            "has no representation, because EventDefinition carries no duration.",
        "Srila Prabhupada's biographical anniversaries" to
            "The export observes his departure for the USA, arrival in the USA, acceptance of " +
            "sannyasa and the incorporation of ISKCON on tithis. The tithi rule for each was not " +
            "located independently and is not inferred from one year.",
        "The ~177 acharya appearance and disappearance days" to
            "Phase 4. Recovering them needs four to six harvested years; one is not enough to " +
            "separate a tithi rule from a coincidence.",
    )

    /**
     * Every non-Ekadasi observance this phase can date.
     *
     * Ordered by the Gaudiya year rather than alphabetically, so a reviewer can read it against a
     * printed calendar top to bottom.
     */
    val definitions: List<EventDefinition> = listOf(

        // ── Pausa ──────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "krsna_pusya_abhiseka",
            name = "Sri Krsna Pusya Abhiseka",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Pausha", Paksha.SHUKLA, 15),
            sourceNote =
                "Pausa Purnima, read from the Mayapur 2026 GCal export (3 January 2026). Flagged " +
                    "for review: the name points at Pusya naksatra, but the export's naksatra on " +
                    "that day is Ardra, so the calendar is plainly using the Purnima and not the " +
                    "naksatra. Whether the tradition's rule is the Purnima or the naksatra was " +
                    "not established.",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Magha ──────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "vasanta_pancami",
            name = "Vasanta Pancami (Sarasvati Puja)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Magha", Paksha.SHUKLA, 5),
            sourceNote =
                "Magha sukla pancami — the tithi is the festival's definition across Hindu " +
                    "traditions. Matches Mayapur 2026 (23 January).",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "advaita_acarya_appearance",
            name = "Sri Advaita Acarya — Appearance",
            group = EventGroup.APPEARANCE,
            rule = onTithi("Magha", Paksha.SHUKLA, 7),
            sourceNote =
                "Magha sukla saptami, the day the tradition calls Advaita Saptami; stated as " +
                    "such in Gaudiya sources independent of any calendar file. Matches Mayapur " +
                    "2026 (25 January).",
            confidence = RuleConfidence.CONFIRMED,
            fastingNote = "Fast till noon",
        ),
        EventDefinition(
            id = "bhismastami",
            name = "Bhismastami",
            group = EventGroup.OPTIONAL_FAST,
            rule = onTithi("Magha", Paksha.SHUKLA, 8),
            sourceNote =
                "Magha sukla astami, the day of Bhisma's passing and of Bhisma tarpana; the tithi " +
                    "is in the name. Matches Mayapur 2026 (26 January).",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "varaha_dvadasi",
            name = "Varaha Dvadasi: Appearance of Lord Varahadeva",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Magha", Paksha.SHUKLA, 12),
            sourceNote =
                "Magha sukla dvadasi, read from the Mayapur 2026 export (30 January). The fast is " +
                    "kept on the preceding Ekadasi and the Dvadasi is the feast, so this entry " +
                    "carries no fasting note; the Ekadasi side belongs to the Ekadasi rules.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "nityananda_trayodasi",
            name = "Nityananda Trayodasi: Appearance of Sri Nityananda Prabhu",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Magha", Paksha.SHUKLA, 13),
            sourceNote =
                "Magha sukla trayodasi — the tithi is in the festival's name and is stated so in " +
                    "Gaudiya sources. Matches Mayapur 2026 (31 January).",
            confidence = RuleConfidence.CONFIRMED,
            fastingNote = "Fast till noon",
        ),
        EventDefinition(
            id = "krsna_madhura_utsava",
            name = "Sri Krsna Madhura Utsava",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Magha", Paksha.SHUKLA, 15),
            sourceNote = "Magha Purnima, read from the Mayapur 2026 export (1 February).",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Phalguna ───────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "siva_ratri",
            name = "Siva Ratri",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Phalguna", Paksha.KRISHNA, 14),
            sourceNote =
                "Maha Sivaratri is Phalguna krsna caturdasi purnimanta, which is Magha krsna " +
                    "caturdasi under amanta reckoning — the same night. Matches Mayapur 2026 " +
                    "(16 February), which prints the month as Phalguna.",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "gaura_purnima",
            name = "Gaura Purnima: Appearance of Sri Caitanya Mahaprabhu",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Phalguna", Paksha.SHUKLA, 15),
            sourceNote =
                "Phalguna Purnima. The single best-attested date in the tradition and the anchor " +
                    "of the Gaurabda year. Matches Mayapur 2026 (3 March).",
            confidence = RuleConfidence.CONFIRMED,
            fastingNote = "Fast till moonrise",
        ),
        EventDefinition(
            id = "jagannatha_misra_festival",
            name = "Festival of Jagannatha Misra",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = EventRule.RelativeTo("gaura_purnima", 1),
            sourceNote =
                "The day after Gaura Purnima — Jagannatha Misra's celebration of his son's " +
                    "birth. Stated as an offset rather than as Caitra krsna pratipada on purpose: " +
                    "the tradition states it as the following day, and the offset survives a " +
                    "skipped Pratipada, which the tithi form would not.",
            confidence = RuleConfidence.CONFIRMED,
        ),

        // ── Caitra ─────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "ramanujacarya_appearance",
            name = "Sri Ramanujacarya — Appearance",
            group = EventGroup.APPEARANCE,
            rule = onTithi("Chaitra", Paksha.SHUKLA, 5),
            sourceNote =
                "Caitra sukla pancami as the Mayapur 2026 export has it (23 March). **Flagged " +
                    "for review**: the Sri Vaisnava tradition reckons Ramanuja's appearance by " +
                    "Ardra naksatra in Cittirai, and the export's naksatra on 23 March 2026 is " +
                    "Krittika — so the two rules are not the same rule and will diverge in other " +
                    "years. The Gaudiya calendar's tithi form is used here because that is the " +
                    "calendar this service is reproducing.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "rama_navami",
            name = "Rama Navami: Appearance of Lord Sri Ramacandra",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Chaitra", Paksha.SHUKLA, 9),
            sourceNote =
                "Caitra sukla navami — the tithi is in the name and is universal across " +
                    "traditions. Matches Mayapur 2026 (27 March).",
            confidence = RuleConfidence.CONFIRMED,
            fastingNote = "Fast till sunset",
        ),
        EventDefinition(
            id = "damanakaropana_dvadasi",
            name = "Damanakaropana Dvadasi",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Chaitra", Paksha.SHUKLA, 12),
            sourceNote = "Caitra sukla dvadasi, read from the Mayapur 2026 export (30 March).",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "krsna_vasanta_rasa",
            name = "Sri Krsna Vasanta Rasa, Sri Balarama Rasayatra",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Chaitra", Paksha.SHUKLA, 15),
            sourceNote =
                "Caitra Purnima, the spring rasa. The same day carries the appearance of Radha " +
                    "Kunda and snana-dana in the Mayapur 2026 export (2 April); those are not " +
                    "separate entries here because the export gives them no separate rule.",
            confidence = RuleConfidence.CONFIRMED,
        ),

        // ── Vaisakha ───────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "aksaya_trtiya",
            name = "Aksaya Trtiya (Candana Yatra starts)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Vaishakha", Paksha.SHUKLA, 3),
            sourceNote =
                "Vaisakha sukla trtiya — the tithi is the festival's definition. Candana Yatra " +
                    "starts the same day and runs 21 days, which this catalog cannot express; " +
                    "see KNOWN_GAPS. Matches Mayapur 2026 (20 April).",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "jahnu_saptami",
            name = "Jahnu Saptami",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Vaishakha", Paksha.SHUKLA, 7),
            sourceNote =
                "Vaisakha sukla saptami; the tithi is in the name, but the month was taken from " +
                    "the Mayapur 2026 export (23 April) rather than from an independent statement.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "sita_devi_appearance",
            name = "Srimati Sita Devi (consort of Lord Sri Rama) — Appearance",
            group = EventGroup.APPEARANCE,
            rule = onTithi("Vaishakha", Paksha.SHUKLA, 9),
            sourceNote =
                "Vaisakha sukla navami, the day known as Sita Navami; stated by tithi across " +
                    "traditions. Matches Mayapur 2026 (25 April).",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "rukmini_dvadasi",
            name = "Rukmini Dvadasi",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Vaishakha", Paksha.SHUKLA, 12),
            sourceNote = "Vaisakha sukla dvadasi, read from the Mayapur 2026 export (28 April).",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "nrsimha_caturdasi",
            name = "Nrsimha Caturdasi: Appearance of Lord Nrsimhadeva",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Vaishakha", Paksha.SHUKLA, 14),
            sourceNote =
                "Vaisakha sukla caturdasi — the tithi is in the name. Matches Mayapur 2026 " +
                    "(30 April).",
            confidence = RuleConfidence.CONFIRMED,
            fastingNote = "Fast till dusk",
        ),
        EventDefinition(
            id = "krsna_phula_dola",
            name = "Krsna Phula Dola, Salila Vihara",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Vaishakha", Paksha.SHUKLA, 15),
            sourceNote = "Vaisakha Purnima, read from the Mayapur 2026 export (1 May).",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Jyestha ────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "ganga_puja",
            name = "Ganga Puja (Ganga Dussehra)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Jyeshtha", Paksha.SHUKLA, 10),
            sourceNote =
                "Jyestha sukla dasami, the descent of the Ganga; stated by tithi across " +
                    "traditions. Matches Mayapur 2026 (24 June) — in the *nija* Jyestha, not the " +
                    "Purusottama-adhika Jyestha that precedes it.",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "panihati_cida_dahi_utsava",
            name = "Panihati Cida Dahi Utsava",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Jyeshtha", Paksha.SHUKLA, 13),
            sourceNote =
                "Jyestha sukla trayodasi, the chipped-rice festival at Panihati; stated by tithi " +
                    "in Gaudiya sources. Matches Mayapur 2026 (27 June).",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "snana_yatra",
            name = "Snana Yatra",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Jyeshtha", Paksha.SHUKLA, 15),
            sourceNote =
                "Jyestha Purnima, the bathing festival of Lord Jagannatha; stated by tithi in the " +
                    "Jagannatha tradition. Matches Mayapur 2026 (29 June) — a year in which " +
                    "Purnima also runs at sunrise on the 30th and the export keeps the festival " +
                    "on the earlier day.",
            confidence = RuleConfidence.CONFIRMED,
        ),

        // ── Asadha ─────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "gundica_marjana",
            name = "Gundica Marjana",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = EventRule.RelativeTo("ratha_yatra", -1),
            sourceNote =
                "The cleansing of the Gundica temple on the day before Ratha Yatra, as described " +
                    "in Caitanya-caritamrta. Stated as an offset because the tradition states it " +
                    "as the preceding day. Matches Mayapur 2026 (15 July).",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "ratha_yatra",
            name = "Ratha Yatra",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Ashadha", Paksha.SHUKLA, 2),
            sourceNote =
                "Asadha sukla dvitiya — the tithi is the festival's definition at Puri and " +
                    "everywhere that follows it. Matches Mayapur 2026 (16 July).",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "hera_pancami",
            name = "Hera Pancami",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = EventRule.RelativeTo("ratha_yatra", 4),
            sourceNote =
                "Four days after Ratha Yatra — the rule the Mayapur export itself prints beside " +
                    "the entry, and the reason this is an offset and not Asadha sukla pancami. " +
                    "2026 separates the two: Asadha's Caturthi is skipped, so sukla pancami is at " +
                    "sunrise on 18 July while the official calendar observes Hera Pancami on the " +
                    "20th, a day it labels Saptami. A tithi rule would be a day and a half early.",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "return_ratha_yatra",
            name = "Return Ratha Yatra",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = EventRule.RelativeTo("ratha_yatra", 8),
            sourceNote =
                "Eight days after Ratha Yatra, as the Mayapur export prints beside the entry. " +
                    "Matches Mayapur 2026 (24 July).",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "guru_purnima",
            name = "Guru (Vyasa) Purnima",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Ashadha", Paksha.SHUKLA, 15),
            sourceNote =
                "Asadha Purnima — the tithi is in the name. Matches Mayapur 2026 (29 July).",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "sanatana_gosvami_disappearance",
            name = "Srila Sanatana Gosvami — Disappearance",
            group = EventGroup.DISAPPEARANCE,
            rule = onTithi("Ashadha", Paksha.SHUKLA, 15),
            sourceNote =
                "Guru Purnima, Asadha Purnima; stated by tithi in Gaudiya sources independent of " +
                    "any calendar file. Matches Mayapur 2026 (29 July).",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "caturmasya_first_month_begins",
            name = "First month of Caturmasya begins (Purnima system)",
            group = EventGroup.SEASONAL,
            rule = onTithi("Ashadha", Paksha.SHUKLA, 15),
            sourceNote =
                "Under the Purnima system the four Caturmasya months run Purnima to Purnima " +
                    "starting at Asadha Purnima. Matches Mayapur 2026 (29 July).",
            confidence = RuleConfidence.CONFIRMED,
            fastingNote = "Green leafy vegetables are given up for one month",
        ),
        EventDefinition(
            id = "caturmasya_first_month_last_day",
            name = "Last day of the first Caturmasya month (Purnima system)",
            group = EventGroup.SEASONAL,
            rule = EventRule.RelativeTo("caturmasya_second_month_begins", -1),
            sourceNote =
                "The day before the next Caturmasya month opens, by definition of a " +
                    "Purnima-to-Purnima month. Expressed as an offset so a skipped or repeated " +
                    "Caturdasi cannot open a gap or an overlap between the two months. Matches " +
                    "Mayapur 2026 (27 August).",
            confidence = RuleConfidence.CONFIRMED,
        ),

        // ── Sravana ────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "jhulana_yatra_begins",
            name = "Radha Govinda Jhulana Yatra begins",
            group = EventGroup.SEASONAL,
            rule = onTithi("Shravana", Paksha.SHUKLA, 11),
            sourceNote =
                "The swing festival runs from Sravana sukla ekadasi to Sravana Purnima. This " +
                    "entry dates the festival, not the fast: in Mayapur 2026 (23 August) the " +
                    "export marks the same day 'Ekadasi (not suitable for fasting)'. Ekadasi " +
                    "fasting and parana are the Ekadasi rules' business.",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "rupa_gosvami_disappearance",
            name = "Srila Rupa Gosvami — Disappearance",
            group = EventGroup.DISAPPEARANCE,
            rule = onTithi("Shravana", Paksha.SHUKLA, 12),
            sourceNote =
                "Sravana sukla dvadasi, 1564; stated by tithi in Gaudiya biographical sources " +
                    "independent of any calendar file. Matches Mayapur 2026 (24 August) — a year " +
                    "in which Dvadasi also runs at sunrise on the 25th and the export keeps the " +
                    "day on the earlier of the two.",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "balarama_purnima",
            name = "Lord Balarama — Appearance (Balarama Purnima)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Shravana", Paksha.SHUKLA, 15),
            sourceNote =
                "Sravana Purnima — the tithi is in the festival's usual name. Matches Mayapur " +
                    "2026 (28 August).",
            confidence = RuleConfidence.CONFIRMED,
            fastingNote = "Fast till noon",
        ),
        EventDefinition(
            id = "jhulana_yatra_ends",
            name = "Jhulana Yatra ends",
            group = EventGroup.SEASONAL,
            rule = onTithi("Shravana", Paksha.SHUKLA, 15),
            sourceNote =
                "Sravana Purnima closes the swing festival. Matches Mayapur 2026 (28 August).",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "caturmasya_second_month_begins",
            name = "Second month of Caturmasya begins (Purnima system)",
            group = EventGroup.SEASONAL,
            rule = onTithi("Shravana", Paksha.SHUKLA, 15),
            sourceNote = "Sravana Purnima. Matches Mayapur 2026 (28 August).",
            confidence = RuleConfidence.CONFIRMED,
            fastingNote = "Yogurt is given up for one month",
        ),
        EventDefinition(
            id = "caturmasya_second_month_last_day",
            name = "Last day of the second Caturmasya month (Purnima system)",
            group = EventGroup.SEASONAL,
            rule = EventRule.RelativeTo("caturmasya_third_month_begins", -1),
            sourceNote =
                "The day before the third month opens. Matches Mayapur 2026 (25 September).",
            confidence = RuleConfidence.CONFIRMED,
        ),

        // ── Bhadra ─────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "janmastami",
            name = "Sri Krsna Janmastami: Appearance of Lord Sri Krsna",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Bhadrapada", Paksha.KRISHNA, 8),
            sourceNote =
                "Bhadra krsna astami purnimanta, which is Sravana krsna astami under amanta " +
                    "reckoning — the same fortnight. Vaisnava practice gives precedence to the " +
                    "Astami tithi rather than to Nisita Kala, which is why the Vaisnava date can " +
                    "differ from the Smarta one. Matches Mayapur 2026 (4 September).",
            confidence = RuleConfidence.CONFIRMED,
            fastingNote = "Fast till midnight",
        ),
        EventDefinition(
            id = "nandotsava",
            name = "Nandotsava",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = EventRule.RelativeTo("janmastami", 1),
            sourceNote =
                "Nanda Maharaja's festival on the day after Janmastami. An offset, because that " +
                    "is how the tradition states it. Matches Mayapur 2026 (5 September).",
            confidence = RuleConfidence.CONFIRMED,
            fastingNote = "Fast till noon",
        ),
        EventDefinition(
            id = "srila_prabhupada_appearance",
            name = "Srila Prabhupada — Appearance",
            group = EventGroup.APPEARANCE,
            rule = EventRule.RelativeTo("janmastami", 1),
            sourceNote =
                "A. C. Bhaktivedanta Swami Prabhupada appeared on Nandotsava, 1 September 1896 — " +
                    "the day after Janmastami — and ISKCON observes it there every year. Matches " +
                    "Mayapur 2026 (5 September).",
            confidence = RuleConfidence.CONFIRMED,
            fastingNote = "Fast till noon",
        ),
        EventDefinition(
            id = "radhastami",
            name = "Radhastami: Appearance of Srimati Radharani",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Bhadrapada", Paksha.SHUKLA, 8),
            sourceNote =
                "Bhadra sukla astami — the tithi is in the name, and it falls a fortnight after " +
                    "Janmastami. Matches Mayapur 2026 (19 September).",
            confidence = RuleConfidence.CONFIRMED,
            fastingNote = "Fast till noon",
        ),
        EventDefinition(
            id = "vamana_dvadasi",
            name = "Sri Vamana Dvadasi: Appearance of Lord Vamanadeva",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Bhadrapada", Paksha.SHUKLA, 12),
            sourceNote =
                "Bhadra sukla dvadasi — the tithi is in the name. The fast is kept on the " +
                    "preceding Ekadasi and this day is the feast, so no fasting note is carried " +
                    "here. Matches Mayapur 2026 (23 September).",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "ananta_caturdasi_vrata",
            name = "Ananta Caturdasi Vrata",
            group = EventGroup.OPTIONAL_FAST,
            rule = onTithi("Bhadrapada", Paksha.SHUKLA, 14),
            sourceNote =
                "Bhadra sukla caturdasi — the tithi is in the name. Matches Mayapur 2026 " +
                    "(25 September).",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "haridasa_thakura_disappearance",
            name = "Srila Haridasa Thakura — Disappearance",
            group = EventGroup.DISAPPEARANCE,
            rule = onTithi("Bhadrapada", Paksha.SHUKLA, 14),
            sourceNote =
                "Ananta Caturdasi, Bhadra sukla caturdasi; stated by tithi in Gaudiya sources " +
                    "independent of any calendar file. Matches Mayapur 2026 (25 September).",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "visvarupa_mahotsava",
            name = "Sri Visvarupa Mahotsava (Bhadra Purnima)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Bhadrapada", Paksha.SHUKLA, 15),
            sourceNote =
                "Bhadra Purnima, read from the Mayapur 2026 export (26 September). The same day " +
                    "carries Srila Prabhupada's acceptance of sannyasa in the export; that " +
                    "anniversary is not a separate entry here — see KNOWN_GAPS.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "caturmasya_third_month_begins",
            name = "Third month of Caturmasya begins (Purnima system)",
            group = EventGroup.SEASONAL,
            rule = onTithi("Bhadrapada", Paksha.SHUKLA, 15),
            sourceNote = "Bhadra Purnima. Matches Mayapur 2026 (26 September).",
            confidence = RuleConfidence.CONFIRMED,
            fastingNote = "Milk is given up for one month",
        ),
        EventDefinition(
            id = "caturmasya_third_month_last_day",
            name = "Last day of the third Caturmasya month (Purnima system)",
            group = EventGroup.SEASONAL,
            rule = EventRule.RelativeTo("caturmasya_fourth_month_begins", -1),
            sourceNote =
                "The day before the fourth month opens. Matches Mayapur 2026 (25 October).",
            confidence = RuleConfidence.CONFIRMED,
        ),

        // ── Asvina ─────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "durga_puja",
            name = "Durga Puja",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Ashvina", Paksha.SHUKLA, 7),
            sourceNote =
                "The Mayapur 2026 export marks Durga Puja on Asvina sukla saptami (18 October), " +
                    "i.e. Maha Saptami. The observance itself spans Saptami to Dasami and this " +
                    "catalog carries only its first day, because EventDefinition has no duration.",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "ramacandra_vijayotsava",
            name = "Ramacandra Vijayotsava (Vijaya Dasami)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Ashvina", Paksha.SHUKLA, 10),
            sourceNote =
                "Asvina sukla dasami, Vijaya Dasami — the tithi is in the name. Matches Mayapur " +
                    "2026 (21 October).",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "madhvacarya_appearance",
            name = "Sri Madhvacarya — Appearance",
            group = EventGroup.APPEARANCE,
            rule = onTithi("Ashvina", Paksha.SHUKLA, 10),
            sourceNote =
                "Vijaya Dasami, Asvina sukla dasami; the Madhva tradition states his appearance " +
                    "on that day independent of any calendar file. Matches Mayapur 2026 " +
                    "(21 October).",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "saradiya_rasa_yatra",
            name = "Sri Krsna Saradiya Rasayatra (Laksmi Puja)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Ashvina", Paksha.SHUKLA, 15),
            sourceNote =
                "Asvina Purnima, the autumn rasa and Kojagari Laksmi Puja; stated by tithi across " +
                    "traditions. Matches Mayapur 2026 (26 October).",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "caturmasya_fourth_month_begins",
            name = "Fourth month of Caturmasya begins (Purnima system)",
            group = EventGroup.SEASONAL,
            rule = onTithi("Ashvina", Paksha.SHUKLA, 15),
            sourceNote = "Asvina Purnima. Matches Mayapur 2026 (26 October).",
            confidence = RuleConfidence.CONFIRMED,
            fastingNote = "Urad dal is given up for one month",
        ),
        EventDefinition(
            id = "caturmasya_fourth_month_last_day",
            name = "Last day of the fourth Caturmasya month (Purnima system)",
            group = EventGroup.SEASONAL,
            rule = EventRule.RelativeTo("kartika_purnima", -1),
            sourceNote =
                "The day before Kartika Purnima closes Caturmasya. Matches Mayapur 2026 " +
                    "(23 November).",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "kartika_vrata_begins",
            name = "Kartika (Damodara) vrata begins",
            group = EventGroup.SEASONAL,
            rule = onTithi("Ashvina", Paksha.SHUKLA, 15),
            sourceNote =
                "The month of Damodara runs Asvina Purnima to Kartika Purnima under the Purnima " +
                    "system, coinciding exactly with the fourth Caturmasya month. Held as a " +
                    "separate entry because a client filtering on SEASONAL wants Kartika by name; " +
                    "the Mayapur export names only the Caturmasya month on this day, so the " +
                    "coincidence — not the name — is what was checked (26 October 2026).",
            confidence = RuleConfidence.INFERRED,
        ),

        // ── Kartika ────────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "bahulastami",
            name = "Bahulastami",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Kartika", Paksha.KRISHNA, 8),
            sourceNote =
                "Kartika krsna astami purnimanta, which is Asvina krsna astami under amanta " +
                    "reckoning. Widely observed as Radha Kunda's appearance; note that the " +
                    "Mayapur export attaches the Radha Kunda gloss to Caitra Purnima instead and " +
                    "prints this day as 'Bahulastami' alone. Matches Mayapur 2026 (2 November).",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "dipavali",
            name = "Dipa dana, Dipavali",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Kartika", Paksha.KRISHNA, 15),
            sourceNote =
                "Kartika krsna amavasya purnimanta — the new moon of the dark fortnight preceding " +
                    "Kartika sukla, which amanta reckoning calls Asvina amavasya. Matches Mayapur " +
                    "2026 (9 November).",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "govardhana_puja",
            name = "Govardhana Puja, Go Puja, Bali Daityaraja Puja",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Kartika", Paksha.SHUKLA, 1),
            sourceNote =
                "Kartika sukla pratipada, the day after Dipavali — Annakuta. Stated by tithi " +
                    "across traditions. Matches Mayapur 2026 (10 November).",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "srila_prabhupada_disappearance",
            name = "Srila Prabhupada — Disappearance",
            group = EventGroup.DISAPPEARANCE,
            rule = onTithi("Kartika", Paksha.SHUKLA, 4),
            sourceNote =
                "Srila Prabhupada departed on 14 November 1977, the Caturthi of the bright " +
                    "fortnight of Kartika; ISKCON states the tithi rather than the Gregorian " +
                    "date. Matches Mayapur 2026 (13 November).",
            confidence = RuleConfidence.CONFIRMED,
            fastingNote = "Fast till noon",
        ),
        EventDefinition(
            id = "gopastami",
            name = "Gopastami, Gosthastami",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Kartika", Paksha.SHUKLA, 8),
            sourceNote =
                "Kartika sukla astami — the tithi is in the name. Matches Mayapur 2026 " +
                    "(17 November) — a year in which Astami also runs at sunrise on the 18th and " +
                    "the export keeps the festival on the earlier day.",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "jagaddhatri_puja",
            name = "Jagaddhatri Puja",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Kartika", Paksha.SHUKLA, 9),
            sourceNote =
                "Kartika sukla navami, the Bengali observance following Gopastami. Matches " +
                    "Mayapur 2026 (19 November).",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "bhisma_pancaka_begins",
            name = "First day of Bhisma Pancaka",
            group = EventGroup.SEASONAL,
            rule = onTithi("Kartika", Paksha.SHUKLA, 11),
            sourceNote =
                "The last five days of Kartika, Kartika sukla ekadasi to Purnima. Dates the " +
                    "observance and not the fast: in Mayapur 2026 the same day (21 November) is a " +
                    "Trisprsa Mahadvadasi, so the Ekadasi fast itself has moved and the Bhisma " +
                    "Pancaka has not.",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "bhisma_pancaka_ends",
            name = "Last day of Bhisma Pancaka",
            group = EventGroup.SEASONAL,
            rule = onTithi("Kartika", Paksha.SHUKLA, 15),
            sourceNote = "Kartika Purnima closes the five days. Matches Mayapur 2026 (24 November).",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "kartika_purnima",
            name = "Sri Krsna Rasayatra, Tulasi-Saligrama Vivaha",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Kartika", Paksha.SHUKLA, 15),
            sourceNote =
                "Kartika Purnima, the Rasa Purnima and the marriage of Tulasi and Saligrama. " +
                    "Matches Mayapur 2026 (24 November).",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "kartika_vrata_ends",
            name = "Kartika (Damodara) vrata ends",
            group = EventGroup.SEASONAL,
            rule = onTithi("Kartika", Paksha.SHUKLA, 15),
            sourceNote =
                "Kartika Purnima closes the month of Damodara. The Mayapur export names only the " +
                    "close of the fourth Caturmasya month on the preceding day and the Purnima " +
                    "festivals on this one, so the name was not checked against it — only the " +
                    "date (24 November 2026).",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "nimbarkacarya_appearance",
            name = "Sri Nimbarkacarya — Appearance",
            group = EventGroup.APPEARANCE,
            rule = onTithi("Kartika", Paksha.SHUKLA, 15),
            sourceNote =
                "Kartika Purnima; the Nimbarka sampradaya states his appearance on that day " +
                    "independent of any calendar file. Matches Mayapur 2026 (24 November).",
            confidence = RuleConfidence.CONFIRMED,
        ),

        // ── Margasirsa ─────────────────────────────────────────────────────────────────────
        EventDefinition(
            id = "katyayani_vrata_begins",
            name = "Katyayani vrata begins",
            group = EventGroup.SEASONAL,
            rule = onTithi("Margashirsha", Paksha.KRISHNA, 1),
            sourceNote =
                "The gopis' month-long vrata runs the whole purnimanta month of Margasirsa, so it " +
                    "opens on Margasirsa krsna pratipada — the day after Kartika Purnima. Matches " +
                    "Mayapur 2026 (25 November).",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "odana_sasthi",
            name = "Odana Sasthi",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Margashirsha", Paksha.SHUKLA, 6),
            sourceNote =
                "Margasirsa sukla sasthi at Puri — the tithi is in the name. Matches Mayapur 2026 " +
                    "(15 December).",
            confidence = RuleConfidence.INFERRED,
        ),
        EventDefinition(
            id = "gita_jayanti",
            name = "Advent of Srimad Bhagavad-gita",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Margashirsha", Paksha.SHUKLA, 11),
            sourceNote =
                "Margasirsa sukla ekadasi, the day known as Gita Jayanti. This entry dates the " +
                    "festival only; the Ekadasi fast and its parana are the Ekadasi rules' " +
                    "business. Matches Mayapur 2026 (20 December).",
            confidence = RuleConfidence.CONFIRMED,
        ),
        EventDefinition(
            id = "katyayani_vrata_ends",
            name = "Katyayani vrata ends",
            group = EventGroup.SEASONAL,
            rule = onTithi("Margashirsha", Paksha.SHUKLA, 15),
            sourceNote =
                "Margasirsa Purnima closes the vrata. Matches Mayapur 2026 (24 December).",
            confidence = RuleConfidence.INFERRED,
        ),
    )

    /** A resolver over [definitions]. Construction validates every cross-reference. */
    val resolver: EventResolver = EventResolver(definitions)

    /**
     * Every rule in this catalog is stated in Gaudiya (purnimanta) month names, including the
     * Shukla ones where amanta agrees, so that no entry can be misread later.
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
