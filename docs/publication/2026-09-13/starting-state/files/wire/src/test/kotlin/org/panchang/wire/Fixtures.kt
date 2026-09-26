package org.panchang.wire

import java.time.LocalDate
import org.panchang.core.GeoLocation
import org.panchang.core.Paksha
import org.panchang.sampradaya.AbsenceReason
import org.panchang.sampradaya.EventGroup
import org.panchang.sampradaya.EventResolution
import org.panchang.sampradaya.EventTime
import org.panchang.sampradaya.FastKind
import org.panchang.sampradaya.MahadvadashiType
import org.panchang.sampradaya.ObservanceAnchor
import org.panchang.sampradaya.ObservanceDecision
import org.panchang.sampradaya.ParanaBoundReason
import org.panchang.sampradaya.ParanaWindow
import org.panchang.sampradaya.ResolvedEvent
import org.panchang.sampradaya.RuleConfidence
import org.panchang.sampradaya.TithiOccurrence
import org.panchang.sampradaya.UnimplementedSampradaya
import org.panchang.sampradaya.YearResolution

/**
 * Hand-built domain values for the serialisation tests.
 *
 * Deliberately literal rather than computed by running the engine: these tests are about the
 * *shape* of the payload, and a fixture that came out of the resolver would make a rule change
 * look like a schema failure. Nothing here is reference data and nothing here claims astronomical
 * accuracy — the instants are round numbers chosen to be easy to read in a diff.
 */
object Fixtures {

    /** 2026-01-15 00:50:30.912 UTC, which is 06:20:31 in Kolkata. */
    const val JD_A: Double = 2461055.53508

    /** Rather later the same day. */
    const val JD_B: Double = 2461055.91250

    val mayapur: GeoLocation = GeoLocation.of(24.4258, 88.3897, "Asia/Kolkata")

    /** Same instants, a very different clock: used to prove `local` moves and `jdUt` does not. */
    val newYork: GeoLocation = GeoLocation.of(40.7128, -74.0060, "America/New_York")

    val rules = UnimplementedSampradaya(
        id = "test-tradition",
        displayName = "Test Tradition",
        provenanceNote = "A fixture. Not a tradition, and not to be presented as one.",
    )

    val tithi = TithiOccurrence(
        index = 10,
        name = "Ekadashi",
        numberInPaksha = 11,
        paksha = Paksha.SHUKLA,
        lunarMonthName = "Pausha",
        isAdhikaMonth = false,
        startJdUt = JD_A,
        endJdUt = JD_B,
    )

    val parana = ParanaWindow(
        date = LocalDate.of(2026, 1, 16),
        startJdUt = JD_A,
        endJdUt = JD_A + 0.02,
        startReason = ParanaBoundReason.HARI_VASARA_END,
        endReason = ParanaBoundReason.ONE_THIRD_DAYLIGHT,
    )

    val at = EventTime.At(
        anchor = ObservanceAnchor.MOONRISE,
        jdUt = JD_B,
        basis = "moonrise: the Moon's centre on the horizon while rising, including its parallax",
    )

    val window = EventTime.Window(
        anchor = ObservanceAnchor.NISITA_KALA,
        startJdUt = JD_A,
        endJdUt = JD_A + 0.0333333,
        basis = "Nisita-kala: the 8th of the 15 equal muhurtas of the night. Flagged INFERRED " +
            "for pandit review.",
    )

    val absent = EventTime.Absent(
        anchor = ObservanceAnchor.MOONRISE,
        reason = AbsenceReason.NO_EVENT_IN_WINDOW,
        basis = "moonrise: the Moon's centre on the horizon while rising, including its parallax",
    )

    val decision = ObservanceDecision(
        date = LocalDate.of(2026, 1, 15),
        name = "Putrada Ekadashi",
        kind = FastKind.MAHADVADASHI,
        mahadvadashiType = MahadvadashiType.UNMILANI,
        tithi = tithi,
        parana = parana,
        reason = "Dashami was still running at arunodaya, so the fast is deferred.",
        confidence = RuleConfidence.CONFIRMED,
    )

    val event = ResolvedEvent(
        id = "janmastami",
        name = "Sri Krsna Janmastami",
        group = EventGroup.MAJOR_FESTIVAL,
        date = LocalDate.of(2026, 9, 4),
        fastingNote = "Fasting till midnight",
        reason = "Astami of Bhadra Krsna paksa at sunrise.",
        confidence = RuleConfidence.CONFIRMED,
        fastUntil = window,
        tithi = tithi,
    )

    /** An event with every optional field absent, to exercise the omit-nulls rule. */
    val bareEvent = ResolvedEvent(
        id = "ratha-yatra",
        name = "Ratha Yatra",
        group = EventGroup.MAJOR_FESTIVAL,
        date = LocalDate.of(2026, 7, 16),
        fastingNote = null,
        reason = "Dvitiya of Asadha Sukla paksa at sunrise.",
        confidence = RuleConfidence.CONFIRMED,
        fastUntil = null,
        tithi = null,
    )

    /**
     * A year with failures in it, because the failures are the part most likely to be dropped.
     *
     * The keys are deliberately not in sorted order, so a renderer that leaks the input map's
     * iteration order into the payload is caught.
     */
    val yearResolution = YearResolution(
        events = listOf(event, bareEvent),
        unresolved = linkedMapOf(
            "srila-rupa-gosvami-disappearance" to
                EventResolution.TithiSkipped("Dvadasi is kshaya in 2026; which day the tradition shifts it to is not something we have a source for."),
            "gaura-purnima" to
                EventResolution.NoOccurrence("Phalguna Purnima does not fall in the requested year at this site."),
            "acarya-tabulated" to
                EventResolution.BeyondTabulatedData("The tabulated dates end in 2030."),
        ),
    )
}
