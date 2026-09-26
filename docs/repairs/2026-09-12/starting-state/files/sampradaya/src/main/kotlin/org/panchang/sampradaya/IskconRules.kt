package org.panchang.sampradaya

import java.time.LocalDate
import org.panchang.core.Nakshatra
import org.panchang.core.Paksha
import org.panchang.core.RiseSet
import org.panchang.core.SunTimes

/**
 * Gaudiya Vaishnava (ISKCON) Ekadashi rules.
 *
 * ## What this reproduces
 *
 * The reference implementation of these rules is GCal 11 Build 5, the GBC Calendar Committee's
 * program, whose published output for Mayapur is this project's oracle
 * (`verify/golden/vaisnavacalendar-mayapur-2026.json`). Everything below was derived by reading
 * GCal's stated rules and then checking each one against seven consecutive years of its actual
 * output; where the two disagreed, the output won and the disagreement is recorded in a comment
 * at the rule concerned.
 *
 * ## The shape of the problem
 *
 * The fast is *not* simply "the day the Ekadashi tithi is running at sunrise". Six distinct
 * conditions move it, and one of them moves it in the opposite direction from the other five.
 * The engine this service replaces modelled none of them: it tested a proxy for a viddha
 * Ekadashi (`elapsedFraction < 1/15`, which asks how far into the tithi sunrise fell rather than
 * whether Dashami survived to arunodaya), and on a positive it added one day and re-emitted the
 * next tithi under the ordinary Ekadashi name. That produces the right date for some deferrals
 * and the wrong name for all of them, since a deferred fast is a *Mahadvadashi* and carries its
 * own name, its own parana rule, and in one case (Trisprsa) is not deferred at all.
 *
 * ## Precedence
 *
 * [classify] applies the conditions in a fixed order. Where two could in principle hold at once
 * the order is a judgement, not an observation: across Mayapur 2021–2027 no two ever co-occurred,
 * so the reference data cannot rank them. The order chosen and the reasoning for it are stated at
 * each branch, and each such branch's [RuleConfidence] reflects it.
 */
class IskconRules : SampradayaRules {

    override val id: String = "iskcon"

    override val displayName: String = "ISKCON / Gaudiya Vaishnava"

    override val status: VerificationStatus = VerificationStatus.VERIFIED

    override val provenanceNote: String =
        "Ekadashi, Mahadvadashi and parana rules follow GCal 11 Build 5 (GBC Calendar Committee), " +
            "as published at vaisnavacalendar.info. Fasting dates, Ekadashi names and parana " +
            "windows are checked against that calendar's Mayapur output for 2026 by an automated " +
            "conformance test, and the rules were derived against its 2021-2027 output. Two " +
            "reservations a reader should carry: the four nakshatra-based Mahadvadashis (Jaya, " +
            "Vijaya, Jayanti, Papanasini) rest on a single observed occurrence and their " +
            "qualifying condition is inferred rather than quoted from a text; and this engine's " +
            "tithi boundaries run up to about two minutes earlier than GCal's, which is harmless " +
            "for a printed time but could in principle move a fasting day when a tithi ends " +
            "within about two minutes of sunrise."

    override fun ekadashiObservances(year: Int, ctx: ObservanceContext): List<ObservanceDecision> {
        val index = LunarDayIndex.build(year, ctx)
        return allObservancesInWindow(index)
            .filter { it.date.year == year }
            .sortedBy { it.date }
    }

    /**
     * Resolves the ISKCON catalog for [year], keeping the entries that produced no date.
     *
     * A caller auditing a year — or a test asking "did the whole catalog resolve?" — gets the
     * failures with the reason each gave. Mayapur 2026 resolves with an empty failure map; a
     * year where it does not is a fact about that year, not a defect to be filtered away.
     */
    override fun eventResolution(year: Int, ctx: ObservanceContext): YearResolution =
        IskconEventCatalog.resolver.resolveYear(year, ctx)

    /**
     * Every Ekadashi observance whose tithi lies in [index]'s window, unfiltered by year.
     *
     * Exposed because the parana for the last fast of December falls in January, so a caller
     * checking a year's parana windows against a published calendar needs the previous year's
     * final decision too. Filtering to the year inside [ekadashiObservances] would hide it.
     */
    fun allObservancesInWindow(index: LunarDayIndex): List<ObservanceDecision> {
        val spans = index.spans()
        val out = ArrayList<ObservanceDecision>()
        for (position in spans.indices) {
            if (spans[position].numberInPaksha != EKADASHI_NUMBER_IN_PAKSHA) continue
            // The closing Purnima or Amavasya is four tithis on: Dvadashi, Trayodashi,
            // Chaturdashi, then the full or new moon. Near the window edge those may not have
            // been built, and a partial fortnight cannot be classified.
            if (position + TITHIS_EKADASHI_TO_FORTNIGHT_END >= spans.size) continue
            out += decide(index, position) ?: continue
        }
        return out
    }

    // ---------------------------------------------------------------- classification

    /** The fortnight around one Ekadashi, in the only three tithis whose length can move the fast. */
    private class Fortnight(
        val ekadashi: TithiSpan,
        val dvadashi: TithiSpan,
        /** The Purnima or Amavasya that closes the fortnight. */
        val closing: TithiSpan,
        /** Civil dates whose sunrise fell inside [ekadashi]. Empty when the Ekadashi is kshaya. */
        val ekadashiDays: List<LocalDate>,
        val dvadashiDays: List<LocalDate>,
        val closingDays: List<LocalDate>,
    )

    /** What [classify] decided, before the parana window is derived. */
    private class Classification(
        val fastDate: LocalDate,
        val type: MahadvadashiType?,
        val reason: String,
        val confidence: RuleConfidence,
    )

    private fun decide(index: LunarDayIndex, position: Int): ObservanceDecision? {
        val spans = index.spans()
        val ekadashi = spans[position]
        val dvadashi = spans[position + 1]
        val closing = spans[position + TITHIS_EKADASHI_TO_FORTNIGHT_END]
        // A defensive check, not a formality: every branch below reads `closing` as the full or
        // new moon, and if the span walk ever skipped an occurrence the arithmetic would quietly
        // point at a Chaturdashi and Paksavardhini would stop firing rather than fail.
        check(closing.numberInPaksha == FORTNIGHT_END_NUMBER_IN_PAKSHA) {
            "expected the tithi $TITHIS_EKADASHI_TO_FORTNIGHT_END after the Ekadashi beginning at " +
                "${ekadashi.startJdUt} to close the fortnight, found number " +
                "${closing.numberInPaksha}"
        }

        val fortnight = Fortnight(
            ekadashi = ekadashi,
            dvadashi = dvadashi,
            closing = closing,
            ekadashiDays = index.sunriseDatesAt(position),
            dvadashiDays = index.sunriseDatesAt(position + 1),
            closingDays = index.sunriseDatesAt(position + TITHIS_EKADASHI_TO_FORTNIGHT_END),
        )
        val classified = classify(index, fortnight) ?: return null
        val parana = derivePotentialParana(index, fortnight, classified)

        return ObservanceDecision(
            date = classified.fastDate,
            name = "${ekadashiName(ekadashi)} Ekadashi",
            kind = if (classified.type == null) FastKind.EKADASHI else FastKind.MAHADVADASHI,
            mahadvadashiType = classified.type,
            tithi = toOccurrence(ekadashi),
            parana = parana.window,
            reason = listOfNotNull(classified.reason, parana.note).joinToString(" "),
            // maxOf, not minOf: the enum is ordered strongest-first, so the *later* constant is
            // the weaker claim and a decision can be no more confident than its weakest part.
            confidence = maxOf(classified.confidence, parana.confidence),
        )
    }

    /**
     * Which day the fast falls on, and which of the eight names it takes.
     *
     * Returns null only when the site has no sunrise on a day the decision depends on. Inside the
     * polar circles "the tithi at sunrise" has no referent, and which instant a Gaudiya ruling
     * substitutes there is not something this phase has a source for; producing a date anyway
     * would be inventing a religious ruling.
     */
    private fun classify(index: LunarDayIndex, f: Fortnight): Classification? {
        val ekadashiVriddhi = f.ekadashiDays.size >= 2
        val dvadashiVriddhi = f.dvadashiDays.size >= 2
        val closingVriddhi = f.closingDays.size >= 2
        val closingName = if (f.closing.paksha == Paksha.SHUKLA) "Purnima" else "Amavasya"

        // 1. Ekadashi kshaya. It began after one sunrise and ended before the next, so no day
        //    carries it and the fast necessarily falls on the Dvadashi.
        //
        //    GCal's published rule list says this case produces "Unmillani Mahadvadasi". Its own
        //    output says otherwise: 2022-09-07, 2023-09-26, 2024-03-07, 2024-06-03, 2025-06-22,
        //    2025-12-31 and 2027-10-26 at Mayapur are all this case and all print the fast on the
        //    Dvadashi with *no* Mahadvadashi line, while every day GCal does label Unmilani has the
        //    Ekadashi at sunrise on two successive days. The output is the oracle, so the fast is
        //    deferred here without a Mahadvadashi name.
        if (f.ekadashiDays.isEmpty()) {
            val day = f.dvadashiDays.firstOrNull() ?: return null
            return Classification(
                fastDate = day,
                type = null,
                reason = "The Ekadashi began after sunrise and ended before the next sunrise, so " +
                    "no day carries it; the fast is kept on the Dvadashi.",
                confidence = RuleConfidence.CONFIRMED,
            )
        }

        // 2. Dvadashi kshaya - Trisprsa, the one condition that does *not* move the fast. Ekadashi,
        //    Dvadashi and Trayodashi all touch the fasting day, which is what the name says.
        if (f.dvadashiDays.isEmpty()) {
            return Classification(
                fastDate = f.ekadashiDays.last(),
                type = MahadvadashiType.TRISPRSA,
                reason = "The Dvadashi begins and ends between two sunrises, so Ekadashi, " +
                    "Dvadashi and Trayodashi all touch this day; the fast is kept on the " +
                    "Ekadashi as Trisprsa Mahadvadashi and broken on the Trayodashi.",
                confidence = RuleConfidence.CONFIRMED,
            )
        }

        // The direct arunodaya test, and the thing the old engine only had a proxy for: had the
        // Ekadashi begun by four ghatikas (96 minutes) before sunrise, or was Dashami still
        // running then? An Ekadashi that arrives after arunodaya is viddha - mixed with Dashami,
        // impure - and is not fasted on. It is needed here and not only at branch 7, because
        // whether the Ekadashi was pure decides whether branch 3 has a *name*.
        val arunodaya = arunodayaOf(index, f.ekadashiDays.first()) ?: return null
        val viddha = f.ekadashi.startJdUt > arunodaya

        // 3. Ekadashi vriddhi - the Ekadashi is running at sunrise on two successive days. GCal:
        //    "If Ekadasi falls on sunrise two days in a row, fasting is observed on the second
        //    day." Placed above the Dvadashi and fortnight-end tests because it is the only one of
        //    the three that changes *which tithi* the fast sits on; the others only choose between
        //    two candidate days of the same Dvadashi.
        //
        //    Whether this is *Unmilani* is a second question, and both published descriptions of
        //    Unmilani are contradicted by GCal's output. vaisnavacalendar.info says Unmilani is a
        //    lost Ekadashi (it is not: see branch 1). Three other sources say it is simply an
        //    Ekadashi surviving to the Dvadashi day's sunrise, which is this branch - but of the
        //    nine such fortnights at Mayapur 2021-2027 GCal names only two, 2022-08-23 and
        //    2025-06-07. What separates those two from the other seven is exactly purity: in both
        //    the Ekadashi began before arunodaya, and in all seven unnamed ones it began after.
        //    Nine cases, no exceptions.
        //
        //    Read as doctrine that is coherent: a *viddha* Ekadashi moving to the second day is
        //    the ordinary impure-Ekadashi deferral of branch 7 and earns no special name, whereas
        //    a pure Ekadashi that nonetheless touches two sunrises is the genuine Unmilani.
        if (ekadashiVriddhi) {
            val day = f.ekadashiDays.last()
            return if (viddha) {
                Classification(
                    fastDate = day,
                    type = null,
                    reason = "The Ekadashi had not begun by arunodaya, so it is viddha; it is " +
                        "still running at sunrise on the following day, and the fast is kept on " +
                        "that second day.",
                    confidence = RuleConfidence.CONFIRMED,
                )
            } else {
                Classification(
                    fastDate = day,
                    type = MahadvadashiType.UNMILANI,
                    reason = "The Ekadashi began before arunodaya and is still running at " +
                        "sunrise on the following day, so the fast is kept on that second day " +
                        "as Unmilani Mahadvadashi.",
                    confidence = RuleConfidence.CONFIRMED,
                )
            }
        }

        // 4. The closing Purnima or Amavasya spans two sunrises, lengthening the fortnight -
        //    Paksavardhini. Ranked above Vyanjuli because it is stated as a property of the
        //    fortnight as a whole rather than of the Dvadashi, and a fortnight-level ruling
        //    that lost to a tithi-level one would be hard to justify to a reviewer. The two
        //    never co-occur in 2021-2027, so this ordering is untested.
        if (closingVriddhi) {
            val day = f.dvadashiDays.first()
            return Classification(
                fastDate = day,
                type = MahadvadashiType.PAKSAVARDHINI,
                reason = "The $closingName closing this fortnight is running at sunrise on two " +
                    "successive days, so the fortnight is lengthened and the fast is kept on the " +
                    "Dvadashi as Paksavardhini Mahadvadashi.",
                confidence = RuleConfidence.CONFIRMED,
            )
        }

        // 5. Dvadashi vriddhi - Vyanjuli. GCal: "If Dvadasi falls on the sunrise two days in a row
        //    the first Dvadasi becomes Vyanjuli Mahadvadasi." Note the fast is on the *first* of
        //    the two, and the parana then falls on the second, before the Dvadashi ends - which is
        //    why those parana windows are short.
        if (dvadashiVriddhi) {
            return Classification(
                fastDate = f.dvadashiDays.first(),
                type = MahadvadashiType.VANJULI,
                reason = "The Dvadashi is running at sunrise on two successive days, so the fast " +
                    "is kept on the first of them as Vyanjuli Mahadvadashi.",
                confidence = RuleConfidence.CONFIRMED,
            )
        }

        // 6. The four nakshatra-based Mahadvadashis. See [nakshatraMahadvadashi] for what is
        //    established and what is inferred.
        val dvadashiDay = f.dvadashiDays.first()
        val nakshatra = nakshatraMahadvadashi(index, f, dvadashiDay)
        if (nakshatra != null) {
            return Classification(
                fastDate = dvadashiDay,
                type = nakshatra.type,
                reason = "${nakshatra.nakshatra.name} nakshatra is running at sunrise on the " +
                    "bright-fortnight Dvadashi and continues past the following sunrise, so the " +
                    "fast is kept on the Dvadashi as ${nakshatra.type.displayName} Mahadvadashi.",
                confidence = RuleConfidence.INFERRED,
            )
        }

        // 7. Dashami-viddha. The direct form of the test the old engine only approximated: was the
        //    Dashami still running at arunodaya, four ghatikas (96 minutes) before sunrise? If the
        //    Ekadashi had not yet begun by then it is mixed with Dashami and impure, and the fast
        //    moves to the Dvadashi.
        //
        //    GCal's published rule says an impure Ekadashi is "to be observed on the Dvadasi
        //    (making Mahadvadasi or compounded)". Its output again disagrees about the name: at
        //    Mayapur 2022-04-12, 2022-12-03, 2023-02-16, 2023-06-29, 2025-03-25, 2025-09-03 and
        //    2027-03-18 the Ekadashi is printed "not suitable for fasting", the fast is on the
        //    following Dvadashi, and no Mahadvadashi line appears. Deferred, unnamed.
        if (viddha) {
            return Classification(
                fastDate = f.dvadashiDays.first(),
                type = null,
                reason = "The Ekadashi had not begun by arunodaya, so Dashami was still running " +
                    "in the last four ghatikas before sunrise and the Ekadashi is viddha; the " +
                    "fast is kept on the Dvadashi.",
                confidence = RuleConfidence.CONFIRMED,
            )
        }

        return Classification(
            fastDate = f.ekadashiDays.single(),
            type = null,
            reason = "The Ekadashi began before arunodaya and was running at sunrise, so the fast " +
                "is kept on the Ekadashi itself.",
            confidence = RuleConfidence.CONFIRMED,
        )
    }

    private class NakshatraMatch(val type: MahadvadashiType, val nakshatra: Nakshatra)

    /**
     * Jaya, Vijaya, Jayanti and Papanasini: a Dvadashi of the bright fortnight joined to one of
     * four nakshatras.
     *
     * ## What is established
     *
     * The pairing is stated identically by three independent Vaishnava sources (ISKCON Raichur,
     * Bhagavat Dharma Samaj, Institute for Pure Devotional Service): Punarvasu gives Jaya,
     * Shravana Vijaya, Rohini Jayanti and Pushya Papanasini, all in the bright fortnight, and in
     * each case the Ekadashi fast moves to the Dvadashi.
     *
     * ## What is inferred, and why it had to be
     *
     * Stated that plainly, the rule is wrong: it fires far too often. Over Mayapur 2021-2027 there
     * are twenty-three days on which one of the four nakshatras is running at sunrise on a
     * Dvadashi - ten of them in the bright fortnight - and GCal marks exactly one of them, the
     * Vijaya Mahadvadashi of 2027-09-12. Some further condition must be separating them, and none
     * of the sources consulted states one.
     *
     * The condition below - that the nakshatra must still be running at the *next* sunrise -
     * separates the twenty-three cleanly: it is true for 2027-09-12 and false for the other
     * twenty-two. No competing candidate does. "The nakshatra outlasts the Dvadashi tithi", the
     * most natural alternative, is true of thirteen of the twenty-two negatives.
     *
     * It also has a reading that makes sense of the rest of GCal's behaviour. On 2027-09-13 GCal
     * closes the parana with the basis `end of naksatra`, so the fast must be broken while the
     * nakshatra still runs. Where the nakshatra has already ended before the parana day's sunrise
     * that is impossible, and the observance is not declared.
     *
     * This is one positive example. It is marked [RuleConfidence.INFERRED] and belongs on any
     * pandit's review list; a second observed occurrence, or a citation of the qualifying
     * condition from Hari-bhakti-vilasa, would settle it.
     */
    private fun nakshatraMahadvadashi(
        index: LunarDayIndex,
        f: Fortnight,
        dvadashiDay: LocalDate,
    ): NakshatraMatch? {
        if (f.dvadashi.paksha != Paksha.SHUKLA) return null
        val sunrise = index.sunriseOf(dvadashiDay) ?: return null
        val nakshatra = index.ctx.calculator.nakshatraAt(sunrise)
        val type = NAKSHATRA_MAHADVADASHI[nakshatra.name] ?: return null
        val nextSunrise = index.sunriseOf(dvadashiDay.plusDays(1)) ?: return null
        if (nakshatra.endJdUt <= nextSunrise) return null
        return NakshatraMatch(type, nakshatra)
    }

    // ---------------------------------------------------------------- parana

    /** A parana window, or the explanation for there not being one. */
    private class ParanaOutcome(
        val window: ParanaWindow?,
        /** Appended to the decision's reason. Never null when [window] is null. */
        val note: String?,
        val confidence: RuleConfidence,
    )

    /**
     * The interval in which the fast is broken.
     *
     * Three constraints, each of which can bind, and all three derived rather than assumed. The
     * engine this replaces hardcoded the parana to `jd + 1` and assumed the tithi at that sunrise
     * was the Dvadashi; on a Trisprsa it is the Trayodashi and on a Vyanjuli it is the *second*
     * Dvadashi, and in both cases the assumption produces a window bounded by the wrong tithi.
     *
     * - **Start.** Sunrise on the parana day, unless Hari Vasara - the first quarter of the
     *   Dvadashi, during which Hari is said to remain in the tithi - is still running. GCal prints
     *   this basis as `1/4 of tithi`.
     * - **End, first bound.** The Dvadashi must not have ended: "one should break the fast ...
     *   before the dvadasi tithi has ended". Only binding when the Dvadashi survives past sunrise
     *   on the parana day, which for a Trisprsa it does not.
     * - **End, second bound.** The first third of the daylight period, GCal's `1/3 of daylight`,
     *   which is what caps the great majority of windows.
     * - **End, third bound.** For a nakshatra Mahadvadashi only, the end of that nakshatra.
     *
     * When the constraints leave nothing, this returns no window and says so. Constructing one
     * anyway is not available: [ParanaWindow] refuses to hold an inverted interval, because a
     * window rendered as "07:12 - 06:34" reads as ordinary to everyone except the person trying
     * to follow it.
     */
    private fun derivePotentialParana(
        index: LunarDayIndex,
        f: Fortnight,
        classified: Classification,
    ): ParanaOutcome {
        val paranaDate = classified.fastDate.plusDays(1)
        val sunTimes = index.ctx.calculator.sunTimes(paranaDate, index.location)
        val sunrise = (sunTimes.sunrise as? RiseSet.At)?.jdUt
            ?: return ParanaOutcome(
                window = null,
                note = "No parana window is given because the Sun does not rise at this site on " +
                    "$paranaDate, and the tradition's ruling for that case is not known here.",
                confidence = RuleConfidence.INFERRED,
            )

        var start = sunrise
        var startReason = ParanaBoundReason.SUNRISE
        val hariVasaraEnd = f.dvadashi.startJdUt + (f.dvadashi.endJdUt - f.dvadashi.startJdUt) / 4.0
        if (hariVasaraEnd > start) {
            start = hariVasaraEnd
            startReason = ParanaBoundReason.HARI_VASARA_END
        }

        // The reason is nullable so that it cannot be recorded without the bound that justifies
        // it. Each of the three caps below assigns instant and reason together or not at all;
        // the previous form asserted ONE_THIRD_DAYLIGHT up front, so a site with no usable
        // daylight silently produced a window bounded by something else and labelled with this.
        var end = Double.MAX_VALUE
        var endReason: ParanaBoundReason? = null
        val daylight = sunTimes.daylightDays
        if (daylight != null && daylight > 0.0) {
            end = sunrise + daylight / 3.0
            endReason = ParanaBoundReason.ONE_THIRD_DAYLIGHT
        }
        if (f.dvadashi.endJdUt > sunrise && f.dvadashi.endJdUt < end) {
            end = f.dvadashi.endJdUt
            endReason = ParanaBoundReason.DVADASHI_END
        }
        if (classified.type in NAKSHATRA_MAHADVADASHI.values) {
            val nakshatra = index.ctx.calculator.nakshatraAt(sunrise)
            if (nakshatra.endJdUt < end) {
                end = nakshatra.endJdUt
                endReason = ParanaBoundReason.NAKSHATRA_END
            }
        }

        // No cap could be established. `SunTimes.daylightDays` documents both ways the first one
        // fails, and they are different facts about the site, so they get different sentences: a
        // reader who cannot tell "the Sun never set" from "the sunset in this civil day came
        // before its sunrise" cannot act on either. Reaching here is an explicit refusal rather
        // than a window capped at MAX_VALUE, which would be an invented bound.
        val boundReason = endReason ?: return ParanaOutcome(
            window = null,
            note = "No parana window is given because " +
                if (daylight == null) {
                    "the Sun does not set at this site on $paranaDate, so the daylight period " +
                        "has no end and no first third to take"
                } else {
                    "this site's civil sunset on $paranaDate precedes its sunrise on the same " +
                        "day, so the interval between them is not a length of daylight"
                } +
                ", and neither the end of the Dvadashi nor the end of a qualifying nakshatra " +
                "falls after that sunrise to bound the window in its place. The tradition's " +
                "ruling for this case is not known here.",
            confidence = RuleConfidence.INFERRED,
        )

        if (end <= start) {
            return ParanaOutcome(
                window = null,
                note = "No parana window is given: the fast may not be broken before " +
                    "${describe(startReason)} on $paranaDate, and must be broken before " +
                    "${describe(boundReason)}, which comes first. This needs a pandit's ruling " +
                    "rather than a computed answer.",
                confidence = RuleConfidence.INFERRED,
            )
        }

        return ParanaOutcome(
            window = ParanaWindow(
                date = paranaDate,
                startJdUt = start,
                endJdUt = end,
                startReason = startReason,
                endReason = boundReason,
            ),
            note = null,
            confidence = RuleConfidence.CONFIRMED,
        )
    }

    private fun describe(reason: ParanaBoundReason): String = when (reason) {
        ParanaBoundReason.SUNRISE -> "sunrise"
        ParanaBoundReason.HARI_VASARA_END -> "the end of Hari Vasara, the first quarter of Dvadashi"
        ParanaBoundReason.DVADASHI_END -> "the end of the Dvadashi"
        ParanaBoundReason.ONE_THIRD_DAYLIGHT -> "the end of the first third of daylight"
        ParanaBoundReason.FAST_TITHI_END -> "the end of the fasting tithi"
        ParanaBoundReason.NAKSHATRA_END -> "the end of the nakshatra"
        ParanaBoundReason.SUNSET -> "sunset"
    }

    // ---------------------------------------------------------------- naming

    /**
     * Arunodaya on [date] - sunrise minus four ghatikas - or null where the Sun does not rise.
     *
     * Read from [SunTimes] rather than subtracted from the index's cached sunrise so that the
     * definition of arunodaya lives in exactly one place.
     */
    private fun arunodayaOf(index: LunarDayIndex, date: LocalDate): Double? =
        (index.ctx.calculator.sunTimes(date, index.location).arunodaya as? RiseSet.At)?.jdUt

    private fun toOccurrence(span: TithiSpan): TithiOccurrence = TithiOccurrence(
        index = span.tithiIndex,
        name = org.panchang.core.Tithi.nameOf(span.tithiIndex),
        numberInPaksha = span.numberInPaksha,
        paksha = span.paksha,
        lunarMonthName = span.monthName(org.panchang.core.MonthReckoning.AMANTA),
        isAdhikaMonth = span.isAdhika,
        startJdUt = span.startJdUt,
        endJdUt = span.endJdUt,
    )

    /**
     * The proper name of an Ekadashi, from its amanta month and paksha.
     *
     * Amanta, not purnimanta. The two disagree about the month name of every Krishna fortnight, so
     * the same table read under the other convention names half the year's Ekadashis wrongly - and
     * wrongly by one month, which looks plausible. The intercalary month overrides both: Purushottama
     * carries Padmini and Parama, and its Ekadashis are not named for the month it doubles.
     *
     * Spellings are GCal's own, because GCal is the oracle this is checked against; "Amalaki vrata"
     * really is how it prints that one.
     */
    private fun ekadashiName(span: TithiSpan): String {
        if (span.isAdhika) {
            return if (span.paksha == Paksha.SHUKLA) "Padmini" else "Parama"
        }
        val names = EKADASHI_NAMES[span.amantaMonthIndex]
        return if (span.paksha == Paksha.SHUKLA) names.first else names.second
    }

    companion object {

        private const val EKADASHI_NUMBER_IN_PAKSHA = 11
        private const val FORTNIGHT_END_NUMBER_IN_PAKSHA = 15

        /** Ekadashi, Dvadashi, Trayodashi, Chaturdashi, Purnima or Amavasya. */
        private const val TITHIS_EKADASHI_TO_FORTNIGHT_END = 4

        /**
         * Shukla and Krishna Ekadashi names by amanta month index, 0 = Chaitra.
         *
         * Verified against every named Ekadashi GCal printed for Mayapur 2021-2027 - 168
         * occurrences, all 26 names including the two intercalary ones.
         */
        private val EKADASHI_NAMES: List<Pair<String, String>> = listOf(
            "Kamada" to "Varuthini", // Chaitra
            "Mohini" to "Apara", // Vaishakha
            "Pandava Nirjala" to "Yogini", // Jyeshtha
            "Sayana" to "Kamika", // Ashadha
            "Pavitraropana" to "Annada", // Shravana
            "Parsva" to "Indira", // Bhadrapada
            "Pasankusa" to "Rama", // Ashvina
            "Utthana" to "Utpanna", // Kartika
            "Moksada" to "Saphala", // Margashirsha
            "Putrada" to "Sat-tila", // Pausha
            "Bhaimi" to "Vijaya", // Magha
            "Amalaki vrata" to "Papamocani", // Phalguna
        )

        /**
         * Nakshatra name to the Mahadvadashi it names, when the further condition in
         * [nakshatraMahadvadashi] also holds.
         *
         * Keyed by `core.Nakshatra` name rather than index so that a reordering of the nakshatra
         * table could not silently repoint Rohini's rule at Mrigashira.
         */
        private val NAKSHATRA_MAHADVADASHI: Map<String, MahadvadashiType> = mapOf(
            "Punarvasu" to MahadvadashiType.JAYA,
            "Shravana" to MahadvadashiType.VIJAYA,
            "Rohini" to MahadvadashiType.JAYANTI,
            "Pushya" to MahadvadashiType.PAPANASINI,
        )
    }
}
