package org.panchang.sampradaya

import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.panchang.core.GeoLocation
import org.panchang.core.PanchangCalculator
import org.panchang.ephemeris.Vsop87Ephemeris

/**
 * Resolves the whole [IskconEventCatalog] for 2026 at Mayapur and compares it, entry by entry,
 * with the harvested `vaisnavacalendar-mayapur-2026.json`.
 *
 * ## How an entry is matched to the oracle
 *
 * By the *exact* event string the source prints, not by fuzzy name similarity. Fuzzy matching is
 * how a suite like this quietly starts marking Sri Krsna Rasayatra correct because it found Sri
 * Krsna Saradiya Rasayatra six weeks earlier. Each marker is additionally asserted to occur on
 * exactly one day of the year, so a marker that has become ambiguous fails loudly rather than
 * matching whichever day happens to come first.
 *
 * Entries with no marker cannot be checked at all and are named in [UNVERIFIABLE_IN_2026] with the
 * reason. A definition that is in neither map fails this suite: the point is that adding a
 * catalog entry forces an explicit statement about whether it is checkable.
 *
 * ## What this suite does not prove
 *
 * One year. A rule that reproduces 2026 has been shown consistent with 2026 and nothing more —
 * `hera_pancami` is in the catalog precisely because a rule that looked right in most years is
 * two days wrong in this one. Multi-year confirmation needs more harvested calendars and is
 * Phase 4's work.
 */
class IskconEventCatalogConformanceTest {

    @Test
    fun `every catalog entry is either checkable against the golden calendar or named as not`() {
        val ids = IskconEventCatalog.definitions.map { it.id }.toSet()
        val accountedFor = GOLDEN_MARKERS.keys + UNVERIFIABLE_IN_2026.keys
        assertEquals(
            emptySet<String>(),
            ids - accountedFor,
            "catalog entries with neither a golden marker nor a stated reason they lack one",
        )
        assertEquals(
            emptySet<String>(),
            accountedFor - ids,
            "this suite refers to catalog entries that no longer exist",
        )
    }

    /** A marker that matched two days would make a mismatch look like a match. */
    @Test
    fun `every golden marker identifies exactly one day of 2026`() {
        val ambiguous = GOLDEN_MARKERS.mapValues { (_, marker) -> datesCarrying(marker) }
            .filterValues { it.size != 1 }
        assertEquals(emptyMap<String, List<LocalDate>>(), ambiguous)
    }

    @Test
    fun `the catalog reproduces the official Mayapur 2026 dates`() {
        val resolution = IskconEventCatalog.resolver.resolveYear(index)
        val resolvedById = resolution.events.associateBy { it.id }

        val mismatches = ArrayList<String>()
        var checked = 0
        var agreed = 0

        for (definition in IskconEventCatalog.definitions) {
            val marker = GOLDEN_MARKERS[definition.id] ?: continue
            checked++
            val expected = datesCarrying(marker).singleOrNull()
            val actual = resolvedById[definition.id]?.date
            when {
                expected == null ->
                    mismatches += "${definition.id}: marker \"$marker\" is not in the golden year"

                actual == null -> {
                    val why = resolution.unresolved[definition.id]
                    mismatches += "${definition.id}: golden says $expected, the catalog produced " +
                        "no date at all ($why)"
                }

                actual != expected ->
                    mismatches += "${definition.id}: golden says $expected, the catalog says " +
                        "$actual (${definition.rule})"

                else -> agreed++
            }
        }

        // Printed whether or not the suite passes, because the number is the deliverable.
        println(
            "Mayapur 2026 conformance: $agreed/$checked checkable entries agree " +
                "(${IskconEventCatalog.definitions.size} entries total, " +
                "${UNVERIFIABLE_IN_2026.size} not checkable). " +
                "Unresolved: ${resolution.unresolved.keys}",
        )

        assertTrue(
            mismatches.isEmpty(),
            "${mismatches.size} of $checked checkable entries disagree with the official " +
                "Mayapur 2026 calendar:\n" + mismatches.joinToString("\n"),
        )
    }

    /**
     * Nothing in the catalog may silently fail to produce a date for an ordinary year at an
     * ordinary latitude. A festival that resolves to nothing is invisible in the output, and a
     * calendar that is missing Janmastami looks exactly like a calendar in which Janmastami is
     * not celebrated.
     */
    @Test
    fun `no catalog entry fails to resolve in 2026`() {
        val resolution = IskconEventCatalog.resolver.resolveYear(index)
        assertEquals(
            emptyMap<String, EventResolution>(),
            resolution.unresolved,
            "these entries produced no date for 2026 at Mayapur",
        )
        assertEquals(
            IskconEventCatalog.definitions.size,
            resolution.events.size,
            "one resolved event per definition",
        )
    }

    /**
     * Confidence has to mean something, so it is asserted rather than merely documented:
     * CONFIRMED entries must be exactly the ones that reproduce the golden year. An entry cannot
     * claim CONFIRMED while sitting on a date the reference calendar disagrees with, and an entry
     * with no oracle at all cannot claim it either.
     */
    @Test
    fun `no entry claims CONFIRMED without an oracle that agrees`() {
        val resolvedById =
            IskconEventCatalog.resolver.resolveYear(index).events.associateBy { it.id }
        val overclaimed = IskconEventCatalog.definitions
            .filter { it.confidence == RuleConfidence.CONFIRMED }
            .filter { definition ->
                val marker = GOLDEN_MARKERS[definition.id]
                marker == null || datesCarrying(marker).singleOrNull() !=
                    resolvedById[definition.id]?.date
            }
            .map { it.id }
        assertEquals(emptyList<String>(), overclaimed)
    }

    @Test
    fun `every entry records where its rule came from`() {
        val thin = IskconEventCatalog.definitions
            .filter { it.sourceNote.length < 40 }
            .map { it.id }
        assertEquals(
            emptyList<String>(),
            thin,
            "a source note too short to name a source is not a source note",
        )
    }

    /**
     * Not an assertion — a printout of what the reference calendar carries that this catalog does
     * not, so the size of the remaining gap is visible in the build log rather than only in a
     * report somebody has to remember to read.
     */
    @Test
    fun `report golden events this catalog does not cover`() {
        val covered = GOLDEN_MARKERS.values.toSet()
        val coveredDates = golden.filter { day -> day.events.any { it in covered } }
            .map { it.date }
            .toSet()
        val uncovered = golden
            .flatMap { day -> day.events.map { day.date to it } }
            // Parenthetical rows are the source's fasting annotations, not observances.
            .filterNot { (_, event) -> event.startsWith("(") }
            .filterNot { (_, event) -> event in covered }
        val acharyaDays = uncovered.count { (_, event) -> " -- " in event }
        val (aliases, absent) = uncovered
            .filterNot { " -- " in it.second }
            .partition { (date, _) -> date in coveredDates }
        println(
            "Golden 2026 rows not matched by a catalog marker: ${uncovered.size} " +
                "($acharyaDays acharya appearance/disappearance rows, deferred to Phase 4; " +
                "${aliases.size} further names for a day this catalog already dates; " +
                "${absent.size} rows on days this catalog does not date at all)",
        )
        for ((date, event) in absent) println("  not dated at all  $date  $event")
        for ((date, event) in aliases) println("  alias of a dated day  $date  $event")
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────

    private fun datesCarrying(marker: String): List<LocalDate> =
        golden.filter { marker in it.events }.map { it.date }

    private val golden: List<GaudiyaGoldenCalendar.GoldenDay> get() = GOLDEN
    private val index: LunarDayIndex get() = MAYAPUR_2026

    companion object {

        private val GOLDEN: List<GaudiyaGoldenCalendar.GoldenDay> by lazy {
            GaudiyaGoldenCalendar.mayapur2026()
        }

        private val MAYAPUR_2026: LunarDayIndex by lazy {
            LunarDayIndex.build(
                2026,
                ObservanceContext(
                    calculator = PanchangCalculator(Vsop87Ephemeris()),
                    location = GeoLocation(
                        GaudiyaGoldenCalendar.MAYAPUR_LATITUDE,
                        GaudiyaGoldenCalendar.MAYAPUR_LONGITUDE,
                        ZoneId.of(GaudiyaGoldenCalendar.MAYAPUR_ZONE),
                    ),
                ),
            )
        }

        /**
         * Catalog id to the exact string the source prints. Copied verbatim, spelling and all:
         * "Odana sasthi" is lowercase in the source and stays lowercase here, because normalising
         * it would mean this file and the oracle no longer agree on what the oracle says.
         */
        private val GOLDEN_MARKERS: Map<String, String> = linkedMapOf(
            "krsna_pusya_abhiseka" to "Sri Krsna Pusya Abhiseka",
            "vasanta_pancami" to "Vasanta Pancami",
            "advaita_acarya_appearance" to "Sri Advaita Acarya -- Appearance",
            "bhismastami" to "Bhismastami",
            "varaha_dvadasi" to "Varaha Dvadasi: Appearance of Lord Varahadeva",
            "nityananda_trayodasi" to
                "Nityananda Trayodasi: Appearance of Sri Nityananda Prabhu",
            "krsna_madhura_utsava" to "Sri Krsna Madhura Utsava",
            "siva_ratri" to "Siva Ratri",
            "gaura_purnima" to "Gaura Purnima: Appearance of Sri Caitanya Mahaprabhu",
            "jagannatha_misra_festival" to "Festival of Jagannatha Misra",
            "ramanujacarya_appearance" to "Sri Ramanujacarya -- Appearance",
            "rama_navami" to "Rama Navami: Appearance of Lord Sri Ramacandra",
            "damanakaropana_dvadasi" to "Damanakaropana Dvadasi",
            "krsna_vasanta_rasa" to "Sri Krsna Vasanta Rasa",
            "aksaya_trtiya" to "Aksaya Trtiya. Candana Yatra starts. (Continues for 21 days)",
            "jahnu_saptami" to "Jahnu Saptami",
            "sita_devi_appearance" to
                "Srimati Sita Devi (consort of Lord Sri Rama) -- Appearance",
            "rukmini_dvadasi" to "Rukmini Dvadasi",
            "nrsimha_caturdasi" to "Nrsimha Caturdasi: Appearance of Lord Nrsimhadeva",
            "krsna_phula_dola" to "Krsna Phula Dola, Salila Vihara",
            "ganga_puja" to "Ganga Puja",
            "panihati_cida_dahi_utsava" to "Panihati Cida Dahi Utsava",
            "snana_yatra" to "Snana Yatra",
            "gundica_marjana" to "Gundica Marjana",
            "ratha_yatra" to "Ratha Yatra",
            "hera_pancami" to "Hera Pancami (4 days after Ratha Yatra)",
            "return_ratha_yatra" to "Return Ratha (8 days after Ratha Yatra)",
            "guru_purnima" to "Guru (Vyasa) Purnima",
            "sanatana_gosvami_disappearance" to "Srila Sanatana Gosvami -- Disappearance",
            "caturmasya_first_month_begins" to
                "First month of Caturmasya begins [PURNIMA SYSTEM]",
            "caturmasya_first_month_last_day" to
                "Last day of the first Caturmasya month [PURNIMA SYSTEM]",
            "jhulana_yatra_begins" to "Radha Govinda Jhulana Yatra begins",
            "rupa_gosvami_disappearance" to "Srila Rupa Gosvami -- Disappearance",
            "balarama_purnima" to "Lord Balarama -- Appearance",
            "jhulana_yatra_ends" to "Jhulana Yatra ends",
            "caturmasya_second_month_begins" to
                "Second month of Caturmasya begins [PURNIMA SYSTEM]",
            "caturmasya_second_month_last_day" to
                "Last day of the second Caturmasya month [PURNIMA SYSTEM]",
            "janmastami" to "Sri Krsna Janmastami: Appearance of Lord Sri Krsna",
            "nandotsava" to "Nandotsava",
            "srila_prabhupada_appearance" to "Srila Prabhupada -- Appearance",
            "radhastami" to "Radhastami: Appearance of Srimati Radharani",
            "vamana_dvadasi" to "Sri Vamana Dvadasi: Appearance of Lord Vamanadeva",
            "ananta_caturdasi_vrata" to "Ananta Caturdasi Vrata",
            "haridasa_thakura_disappearance" to "Srila Haridasa Thakura -- Disappearance",
            "visvarupa_mahotsava" to "Sri Visvarupa Mahotsava",
            "caturmasya_third_month_begins" to
                "Third month of Caturmasya begins [PURNIMA SYSTEM]",
            "caturmasya_third_month_last_day" to
                "Last day of the third Caturmasya month [PURNIMA SYSTEM]",
            "durga_puja" to "Durga Puja",
            "ramacandra_vijayotsava" to "Ramacandra Vijayotsava",
            "madhvacarya_appearance" to "Sri Madhvacarya -- Appearance",
            "saradiya_rasa_yatra" to "Sri Krsna Saradiya Rasayatra",
            "caturmasya_fourth_month_begins" to
                "Fourth month of Caturmasya begins [PURNIMA SYSTEM]",
            "caturmasya_fourth_month_last_day" to
                "Last day of the fourth Caturmasya month [PURNIMA SYSTEM]",
            "bahulastami" to "Bahulastami",
            "dipavali" to "Dipa dana, Dipavali, (Kali Puja)",
            "govardhana_puja" to "Go Puja. Go Krda. Govardhana Puja.",
            "srila_prabhupada_disappearance" to "Srila Prabhupada -- Disappearance",
            "gopastami" to "Gopastami, Gosthastami",
            "jagaddhatri_puja" to "Jagaddhatri Puja",
            "bhisma_pancaka_begins" to "First day of Bhisma Pancaka",
            "bhisma_pancaka_ends" to "Last day of Bhisma Pancaka",
            "kartika_purnima" to "Sri Krsna Rasayatra",
            "nimbarkacarya_appearance" to "Sri Nimbarkacarya -- Appearance",
            "katyayani_vrata_begins" to "Katyayani vrata begins",
            "odana_sasthi" to "Odana sasthi",
            "gita_jayanti" to "Advent of Srimad Bhagavad-gita",
            "katyayani_vrata_ends" to "Katyayani vrata ends",
        )

        /** Catalog entries the 2026 oracle simply does not name, and why. */
        private val UNVERIFIABLE_IN_2026: Map<String, String> = linkedMapOf(
            "kartika_vrata_begins" to
                "The source names only 'Fourth month of Caturmasya begins' on 26 October and " +
                    "never prints Kartika or Damodara vrata as an event, so only the coincidence " +
                    "of dates can be checked, not the name.",
            "kartika_vrata_ends" to
                "Likewise unnamed, and genuinely uncertain at the boundary: the source closes " +
                    "the fourth Caturmasya month on 23 November (the Caturdasi) while this " +
                    "catalog ends the Damodara vrata on Kartika Purnima, 24 November. Both " +
                    "readings are current. Flagged for pandit review.",
        )
    }
}
