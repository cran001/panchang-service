package org.panchang.sampradaya

import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.panchang.core.GeoLocation
import org.panchang.core.MonthReckoning
import org.panchang.core.Paksha
import org.panchang.ephemeris.Vsop87Ephemeris
import org.panchang.core.PanchangCalculator

/**
 * Behaviour of [EventResolver] itself, exercised at Mayapur in 2026.
 *
 * 2026 was chosen because it is an awkward year rather than a convenient one. It contains an
 * intercalary Jyestha, a Caturthi that no day carries, and three tithis that two consecutive
 * sunrises both fall inside — the three cases where a resolver that "mostly works" starts
 * inventing dates. The expected dates below come from the harvested Mayapur 2026 calendar in
 * `verify/golden`, not from this resolver's own output.
 *
 * Conformance of the *catalog* is a separate suite; this one is about the machinery.
 */
class EventResolverTest {

    // ── Reckoning ───────────────────────────────────────────────────────────────────────────

    /**
     * The trap [EventRule.OnTithi.reckoning] exists for: a krishna-paksha rule that omits the
     * convention is ambiguous by one whole month, and both readings look plausible.
     */
    @Test
    fun `krishna paksha rules name different fortnights under the two reckonings`() {
        // Janmastami. The Gaudiya calendar calls it Bhadra krsna astami; amanta reckoning calls
        // the same fortnight Sravana krsna astami. Both must land on the golden 4 September.
        assertEquals(
            LocalDate.of(2026, 9, 4),
            dateOf(onTithi("Bhadrapada", Paksha.KRISHNA, 8, MonthReckoning.PURNIMANTA)),
        )
        assertEquals(
            LocalDate.of(2026, 9, 4),
            dateOf(onTithi("Shravana", Paksha.KRISHNA, 8, MonthReckoning.AMANTA)),
        )

        // Reading the Gaudiya month name as amanta silently moves the festival a month late —
        // to the fortnight the golden calendar labels Asvina krsna.
        assertEquals(
            LocalDate.of(2026, 10, 4),
            dateOf(onTithi("Bhadrapada", Paksha.KRISHNA, 8, MonthReckoning.AMANTA)),
        )
    }

    @Test
    fun `shukla paksha rules are identical under both reckonings`() {
        // Radhastami. The two conventions differ only in how they name a krishna fortnight, so a
        // bright-fortnight rule must be reckoning-independent.
        val purnimanta = dateOf(onTithi("Bhadrapada", Paksha.SHUKLA, 8, MonthReckoning.PURNIMANTA))
        val amanta = dateOf(onTithi("Bhadrapada", Paksha.SHUKLA, 8, MonthReckoning.AMANTA))
        assertEquals(LocalDate.of(2026, 9, 19), purnimanta)
        assertEquals(purnimanta, amanta)
    }

    // ── Adhika maasa ────────────────────────────────────────────────────────────────────────

    /**
     * 2026 has a Purusottama-adhika Jyestha, and the reference calendar keeps nothing in it.
     *
     * The month is real in the index — the assertions below check it is actually there, so this
     * test cannot pass by the adhika month simply having been missed — and the resolver skips it
     * anyway. Ganga Puja's Dasami occurs twice under the name "Jyestha sukla dasami": 25 May in
     * the adhika month and 24 June in the nija month. The resolver returns the earliest match in
     * the year, so if adhika suppression were removed this test would fail with the May date
     * rather than passing by accident.
     */
    @Test
    fun `an intercalary month keeps no observances`() {
        val adhikaDasami = index.spans().withIndex().filter { (_, span) ->
            span.isAdhika && span.tithiIndex == 9
        }
        assertEquals(1, adhikaDasami.size, "2026 should hold exactly one adhika Dasami")
        val (position, span) = adhikaDasami.single()
        assertEquals("Adhika Jyeshtha", span.monthName(MonthReckoning.PURNIMANTA))
        assertEquals(
            LocalDate.of(2026, 5, 25),
            index.sunriseDatesAt(position).first(),
            "the adhika Dasami is the day the golden calendar labels Purusottama-adhika",
        )

        assertEquals(
            LocalDate.of(2026, 6, 24),
            dateOf(onTithi("Jyeshtha", Paksha.SHUKLA, 10, MonthReckoning.PURNIMANTA)),
            "Ganga Puja belongs to the nija Jyestha, not to the adhika month that precedes it",
        )
    }

    // ── Skipped and repeated tithis ─────────────────────────────────────────────────────────

    /**
     * Asadha sukla caturthi 2026 begins after sunrise on 17 July and ends before sunrise on the
     * 18th, so no day carries it. The resolver refuses rather than nudging it to a neighbour.
     */
    @Test
    fun `a skipped tithi resolves to nothing and says so`() {
        // The neighbours resolve normally, which is what makes the gap a gap and not a bug.
        assertEquals(
            LocalDate.of(2026, 7, 17),
            dateOf(onTithi("Ashadha", Paksha.SHUKLA, 3, MonthReckoning.PURNIMANTA)),
        )
        assertEquals(
            LocalDate.of(2026, 7, 18),
            dateOf(onTithi("Ashadha", Paksha.SHUKLA, 5, MonthReckoning.PURNIMANTA)),
        )

        val outcome = resolver(onTithi("Ashadha", Paksha.SHUKLA, 4, MonthReckoning.PURNIMANTA))
            .resolve("probe", index)
        val skipped = assertIs<EventResolution.TithiSkipped>(outcome)
        assertMentions(skipped.why, "without touching a sunrise")
        assertMentions(skipped.why, "Ashadha Shukla 4")
    }

    /**
     * Jyestha Purnima 2026 is running at sunrise on both 29 and 30 June. The golden calendar puts
     * Snana Yatra on the 29th, so a repeated tithi is taken on the first of its days.
     */
    @Test
    fun `a repeated tithi is taken on the first of its two days`() {
        val purnima = onTithi("Jyeshtha", Paksha.SHUKLA, 15, MonthReckoning.PURNIMANTA)
        assertEquals(LocalDate.of(2026, 6, 29), dateOf(purnima))

        // The same tithi actually is at sunrise on both days; the resolver is choosing, not
        // failing to notice the second.
        val spans = index.spans().withIndex().single { (_, span) ->
            span.tithiIndex == 14 &&
                span.monthIndex(MonthReckoning.PURNIMANTA) == 2 &&
                !span.isAdhika
        }
        assertEquals(
            listOf(LocalDate.of(2026, 6, 29), LocalDate.of(2026, 6, 30)),
            index.sunriseDatesAt(spans.index),
        )
    }

    /** `atSunrise = false` selects the day the tithi *ends*, which for a vriddhi tithi differs. */
    @Test
    fun `atSunrise false selects the day the tithi ends`() {
        val rule = onTithi("Jyeshtha", Paksha.SHUKLA, 15, MonthReckoning.PURNIMANTA)
            .copy(atSunrise = false)
        assertEquals(LocalDate.of(2026, 6, 30), dateOf(rule))
    }

    /** A skipped tithi still *ends* on a day, so the `atSunrise = false` form can date it. */
    @Test
    fun `atSunrise false can date a tithi that no sunrise touches`() {
        val rule = onTithi("Ashadha", Paksha.SHUKLA, 4, MonthReckoning.PURNIMANTA)
            .copy(atSunrise = false)
        assertEquals(LocalDate.of(2026, 7, 18), dateOf(rule))
    }

    // ── RelativeTo ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `relative events apply offsets in both directions`() {
        val catalog = EventResolver(IskconEventCatalog.definitions)
        assertEquals(LocalDate.of(2026, 7, 16), resolvedDate(catalog, "ratha_yatra"))
        assertEquals(LocalDate.of(2026, 7, 15), resolvedDate(catalog, "gundica_marjana"))
        assertEquals(LocalDate.of(2026, 7, 20), resolvedDate(catalog, "hera_pancami"))
        assertEquals(LocalDate.of(2026, 7, 24), resolvedDate(catalog, "return_ratha_yatra"))
    }

    /**
     * Hera Pancami is the reason `RelativeTo` exists at all.
     *
     * Read as a tithi rule it would be Asadha sukla pancami. In 2026 the intervening Caturthi is
     * skipped, so Pancami is at sunrise on 18 July while the official calendar observes Hera
     * Pancami on the 20th — a two-day error that only shows up in years with a kshaya tithi.
     */
    @Test
    fun `Hera Pancami disagrees with the tithi rule it superficially resembles`() {
        val asTithi = dateOf(onTithi("Ashadha", Paksha.SHUKLA, 5, MonthReckoning.PURNIMANTA))
        val asOffset = resolvedDate(EventResolver(IskconEventCatalog.definitions), "hera_pancami")
        assertNotEquals(asTithi, asOffset)
        assertEquals(LocalDate.of(2026, 7, 20), asOffset)
    }

    @Test
    fun `relative events chain through other relative events`() {
        val chain = EventResolver(
            listOf(
                definition("base", onTithi("Ashadha", Paksha.SHUKLA, 2, MonthReckoning.PURNIMANTA)),
                definition("middle", EventRule.RelativeTo("base", 4)),
                definition("tip", EventRule.RelativeTo("middle", 4)),
            ),
        )
        assertEquals(LocalDate.of(2026, 7, 24), resolvedDate(chain, "tip"))
    }

    /**
     * A base event in the *previous* year must still be reachable, or every offset event that
     * lands in January silently vanishes.
     *
     * Kartika Purnima occurs twice inside the resolver's 2026 window: 5 November 2025 and the
     * golden 24 November 2026. Sixty days past the second is 2027 and gets filtered out, so the
     * only route to the asserted 4 January 2026 is through the 2025 occurrence — which lies
     * outside the requested year entirely. (Kartika Purnima 2025 fell on 5 November 2025; that
     * date is widely published and is not being taken from this resolver's own output.)
     */
    @Test
    fun `a relative event reaches back across the year boundary for its base`() {
        val catalog = EventResolver(
            listOf(
                definition(
                    "anchor",
                    onTithi("Kartika", Paksha.SHUKLA, 15, MonthReckoning.PURNIMANTA),
                ),
                definition("offset", EventRule.RelativeTo("anchor", 60)),
            ),
        )
        assertEquals(LocalDate.of(2026, 11, 24), resolvedDate(catalog, "anchor"))
        assertEquals(LocalDate.of(2026, 1, 4), resolvedDate(catalog, "offset"))
        assertEquals(Month.JANUARY, resolvedDate(catalog, "offset").month)
    }

    /**
     * A skip in the base propagates, named, instead of becoming a silent absence.
     *
     * Margasirsa Purnima 2025 is itself a kshaya tithi at Mayapur — it ran from 08:38 on 4
     * December to 04:44 on the 5th, clearing both sunrises — so an event defined thirty days
     * after it has no base to stand on. The resolver says which base failed and why.
     */
    @Test
    fun `a skipped base is reported against the event that depends on it`() {
        val catalog = EventResolver(
            listOf(
                definition(
                    "anchor",
                    onTithi("Margashirsha", Paksha.SHUKLA, 15, MonthReckoning.PURNIMANTA),
                ),
                definition("dependent", EventRule.RelativeTo("anchor", 30)),
            ),
        )
        // The anchor's own 2026 occurrence is fine; it is the December 2025 one that is missing.
        assertEquals(LocalDate.of(2026, 12, 24), resolvedDate(catalog, "anchor"))

        val outcome = catalog.resolve("dependent", index)
        val skipped = assertIs<EventResolution.TithiSkipped>(outcome)
        assertMentions(skipped.why, "base event 'anchor'")
        assertMentions(skipped.why, "Margashirsha Shukla 15")
    }

    @Test
    fun `a relative event naming an unknown id fails when the catalog is built`() {
        val failure = assertThrows<IllegalArgumentException> {
            EventResolver(listOf(definition("orphan", EventRule.RelativeTo("no_such_event", 1))))
        }
        assertMentions(failure.message.orEmpty(), "no_such_event")
    }

    @Test
    fun `a cycle of relative events fails when the catalog is built`() {
        val mutual = assertThrows<IllegalArgumentException> {
            EventResolver(
                listOf(
                    definition("first", EventRule.RelativeTo("second", 1)),
                    definition("second", EventRule.RelativeTo("first", -1)),
                ),
            )
        }
        assertMentions(mutual.message.orEmpty(), "cycle")

        val selfReference = assertThrows<IllegalArgumentException> {
            EventResolver(listOf(definition("loop", EventRule.RelativeTo("loop", 0))))
        }
        assertMentions(selfReference.message.orEmpty(), "cycle")
    }

    // ── Nakshatra rules ─────────────────────────────────────────────────────────────────────

    @Test
    fun `a nakshatra rule matches the day that nakshatra is running at sunrise`() {
        val rule = EventRule.OnNakshatraInMonth(
            lunarMonthName = "Chaitra",
            nakshatraName = "Krittika",
            reckoning = MonthReckoning.PURNIMANTA,
        )
        // The golden calendar prints Krittika at sunrise on 23 March 2026, inside Caitra.
        assertEquals(LocalDate.of(2026, 3, 23), dateOf(rule))
    }

    // ── FixedGregorian ──────────────────────────────────────────────────────────────────────

    @Test
    fun `a tabulated event resolves inside its table and refuses outside it`() {
        val inTable = EventResolver(
            listOf(
                definition(
                    "tabulated",
                    EventRule.FixedGregorian(mapOf(2026 to LocalDate.of(2026, 4, 30))),
                    RuleConfidence.TABULATED,
                ),
            ),
        )
        assertEquals(LocalDate.of(2026, 4, 30), resolvedDate(inTable, "tabulated"))

        val outOfTable = EventResolver(
            listOf(
                definition(
                    "tabulated",
                    EventRule.FixedGregorian(mapOf(2024 to LocalDate.of(2024, 5, 21))),
                    RuleConfidence.TABULATED,
                ),
            ),
        )
        val outcome = outOfTable.resolve("tabulated", index)
        val exhausted = assertIs<EventResolution.BeyondTabulatedData>(outcome)
        assertMentions(exhausted.why, "cannot be extrapolated")
    }

    // ── Catalog validation ──────────────────────────────────────────────────────────────────

    @Test
    fun `structurally impossible catalogs are rejected at construction`() {
        assertMentions(
            assertThrows<IllegalArgumentException> {
                EventResolver(
                    listOf(
                        definition("twice", onTithi("Magha", Paksha.SHUKLA, 5)),
                        definition("twice", onTithi("Magha", Paksha.SHUKLA, 6)),
                    ),
                )
            }.message.orEmpty(),
            "duplicate event ids",
        )

        assertMentions(
            assertThrows<IllegalArgumentException> {
                EventResolver(listOf(definition("bad_month", onTithi("Sravan", Paksha.SHUKLA, 5))))
            }.message.orEmpty(),
            "Sravan",
        )

        assertMentions(
            assertThrows<IllegalArgumentException> {
                EventResolver(listOf(definition("bad_tithi", onTithi("Magha", Paksha.KRISHNA, 16))))
            }.message.orEmpty(),
            "1..15",
        )
    }

    // ── Whole-year resolution ───────────────────────────────────────────────────────────────

    /**
     * A festival that fails to resolve must not simply be absent: nobody goes looking for a
     * calendar entry that was never printed.
     */
    @Test
    fun `resolveYear reports failures beside successes`() {
        val mixed = EventResolver(
            listOf(
                definition("works", onTithi("Ashadha", Paksha.SHUKLA, 2, MonthReckoning.PURNIMANTA)),
                definition(
                    "vanishes",
                    onTithi("Ashadha", Paksha.SHUKLA, 4, MonthReckoning.PURNIMANTA),
                ),
            ),
        )
        val year = mixed.resolveYear(index)
        assertEquals(listOf("works"), year.events.map { it.id })
        assertEquals(setOf("vanishes"), year.unresolved.keys)
        assertIs<EventResolution.TithiSkipped>(year.unresolved.getValue("vanishes"))
    }

    /** The resolved event carries the rule that produced it, so a wrong date is diagnosable. */
    @Test
    fun `a resolved event explains itself`() {
        val resolved = resolver(onTithi("Phalguna", Paksha.SHUKLA, 15, MonthReckoning.PURNIMANTA))
            .resolve("probe", index)
        val event = assertIs<EventResolution.Resolved>(resolved).event
        assertEquals(LocalDate.of(2026, 3, 3), event.date)
        assertMentions(event.reason, "Phalguna Shukla 15")
        assertMentions(event.reason, "purnimanta")
        assertMentions(event.reason, "the tithi running at sunrise")
    }

    /** The real catalog must at least be structurally sound; conformance is a separate suite. */
    @Test
    fun `the shipped catalog constructs and every rule is well formed`() {
        assertTrue(IskconEventCatalog.definitions.isNotEmpty())
        assertTrue(IskconEventCatalog.resolver.definitions.isNotEmpty())
        assertTrue(
            IskconEventCatalog.definitions.none { it.rule is EventRule.FixedGregorian },
            "no entry in this catalog is a bare date; if that changes, say so in the phase report",
        )
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────

    private fun onTithi(
        month: String,
        paksha: Paksha,
        number: Int,
        reckoning: MonthReckoning = MonthReckoning.PURNIMANTA,
    ) = EventRule.OnTithi(month, paksha, number, reckoning)

    private fun definition(
        id: String,
        rule: EventRule,
        confidence: RuleConfidence = RuleConfidence.INFERRED,
    ) = EventDefinition(
        id = id,
        name = id,
        group = EventGroup.MAJOR_FESTIVAL,
        rule = rule,
        sourceNote = "test fixture",
        confidence = confidence,
    )

    private fun resolver(rule: EventRule) = EventResolver(listOf(definition("probe", rule)))

    private fun dateOf(rule: EventRule): LocalDate =
        resolvedDate(resolver(rule), "probe")

    private fun resolvedDate(resolver: EventResolver, id: String): LocalDate {
        val outcome = resolver.resolve(id, index)
        return assertIs<EventResolution.Resolved>(outcome).event.date
    }

    /** Asserts on a diagnostic string's substance without pinning its exact wording. */
    private fun assertMentions(text: String, fragment: String) =
        assertTrue(text.contains(fragment), "expected \"$fragment\" in: $text")

    private inline fun <reified T> assertIs(value: Any?): T {
        assertTrue(value is T, "expected ${T::class.simpleName} but was $value")
        return value as T
    }

    private val index: LunarDayIndex get() = MAYAPUR_2026

    companion object {
        /**
         * Built once. An index costs about 500 tithi searches and 500 sunrise searches; paying
         * that per test method would make this suite the slowest thing in the build for no gain,
         * and the index is immutable once constructed.
         */
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
    }
}
