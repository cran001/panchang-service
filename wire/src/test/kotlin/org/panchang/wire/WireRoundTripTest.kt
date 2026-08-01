package org.panchang.wire

import java.time.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.panchang.sampradaya.FastKind
import org.panchang.sampradaya.MahadvadashiType
import org.panchang.sampradaya.RuleConfidence
import org.panchang.sampradaya.VerificationStatus

/**
 * Every DTO survives a round trip, and the fields that matter most survive it visibly.
 *
 * Three front doors will be pinned to this schema. A field that silently fails to decode is a
 * field that will be missing from one door's output and present in another's.
 */
class WireRoundTripTest {

    private val renderer = WireRenderer(Fixtures.mayapur)

    private fun <T> roundTrip(serializer: KSerializer<T>, value: T): T {
        val text = WireJson.compact.encodeToString(serializer, value)
        val back = WireJson.compact.decodeFromString(serializer, text)
        assertEquals(value, back, "round trip changed the value; encoded form was $text")
        return back
    }

    private fun objectOf(text: String): JsonObject = Json.parseToJsonElement(text) as JsonObject

    @Test
    fun `InstantDto round trips`() {
        roundTrip(InstantDto.serializer(), renderer.instant(Fixtures.JD_A))
    }

    @Test
    fun `TithiOccurrenceDto round trips`() {
        val dto = roundTrip(TithiOccurrenceDto.serializer(), renderer.tithi(Fixtures.tithi))
        assertEquals("Pausha", dto.lunarMonthName)
        assertEquals(org.panchang.core.Paksha.SHUKLA, dto.paksha)
    }

    @Test
    fun `ParanaWindowDto round trips and keeps both of its reasons`() {
        val dto = roundTrip(ParanaWindowDto.serializer(), renderer.parana(Fixtures.parana))
        assertEquals(Fixtures.parana.startReason, dto.startReason)
        assertEquals(Fixtures.parana.endReason, dto.endReason)
        assertEquals(LocalDate.of(2026, 1, 16), dto.date)
    }

    @Test
    fun `ObservanceDecisionDto round trips`() {
        val dto = roundTrip(
            ObservanceDecisionDto.serializer(),
            renderer.decision(Fixtures.decision),
        )
        assertEquals(FastKind.MAHADVADASHI, dto.kind)
        assertEquals(MahadvadashiType.UNMILANI, dto.mahadvadashiType)
        assertNotNull(dto.parana)
    }

    @Test
    fun `ResolvedEventDto round trips with fastUntil and tithi present`() {
        val dto = roundTrip(ResolvedEventDto.serializer(), renderer.event(Fixtures.event))
        assertNotNull(dto.fastUntil)
        assertNotNull(dto.tithi)
        assertTrue(dto.fastUntil is EventTimeDto.Window)
    }

    @Test
    fun `ResolvedEventDto round trips with every optional field absent`() {
        val dto = roundTrip(ResolvedEventDto.serializer(), renderer.event(Fixtures.bareEvent))
        val obj = objectOf(WireJson.compact.encodeToString(ResolvedEventDto.serializer(), dto))

        for (omitted in listOf("fastingNote", "fastUntil", "tithi")) {
            assertFalse(obj.containsKey(omitted), "an absent value must be absent, not null: $obj")
        }
    }

    @Test
    fun `SampradayaDto round trips and carries status and provenance`() {
        val dto = roundTrip(SampradayaDto.serializer(), WireRenderer.sampradaya(Fixtures.rules))
        assertEquals(VerificationStatus.NOT_IMPLEMENTED, dto.status)
        assertTrue(dto.provenanceNote.isNotBlank())
    }

    @Test
    fun `YearResolutionDto round trips and unresolved survives it`() {
        val dto = roundTrip(
            YearResolutionDto.serializer(),
            renderer.yearResolution(2026, Fixtures.mayapur, Fixtures.rules, Fixtures.yearResolution),
        )

        assertEquals(3, dto.unresolved.size)
        assertEquals(
            Fixtures.yearResolution.unresolved.keys.sorted(),
            dto.unresolved.keys.toList().sorted(),
        )
        assertTrue(dto.unresolved["gaura-purnima"] is EventResolutionDto.NoOccurrence)
        assertTrue(dto.unresolved["srila-rupa-gosvami-disappearance"] is EventResolutionDto.TithiSkipped)
        assertTrue(dto.unresolved["acarya-tabulated"] is EventResolutionDto.BeyondTabulatedData)
    }

    @Test
    fun `unresolved is present in the JSON text, not merely in the object model`() {
        val text = WireJson.compact.encodeToString(
            YearResolutionDto.serializer(),
            renderer.yearResolution(2026, Fixtures.mayapur, Fixtures.rules, Fixtures.yearResolution),
        )
        val obj = objectOf(text)

        assertTrue(obj.containsKey("unresolved"), "a payload that drops unresolved makes a missing festival unnoticeable")
        // The reason each entry gave must survive too; a bare key list explains nothing.
        assertTrue(text.contains("kshaya"), "the reason an entry failed must reach the reader: $text")
    }

    @Test
    fun `unresolved keys are emitted in ascending id order regardless of input order`() {
        val text = WireJson.pretty.encodeToString(
            YearResolutionDto.serializer(),
            renderer.yearResolution(2026, Fixtures.mayapur, Fixtures.rules, Fixtures.yearResolution),
        )
        val keys = (objectOf(text)["unresolved"] as JsonObject).keys.toList()

        assertEquals(keys.sorted(), keys, "map iteration order must not leak into a diffable artifact")
    }

    @Test
    fun `EkadashiYearDto round trips`() {
        val dto = roundTrip(
            EkadashiYearDto.serializer(),
            renderer.ekadashiYear(2026, Fixtures.mayapur, Fixtures.rules, listOf(Fixtures.decision)),
        )
        assertEquals(1, dto.observances.size)
    }

    @Test
    fun `SampradayaListDto round trips`() {
        roundTrip(SampradayaListDto.serializer(), WireRenderer.sampradayaList(listOf(Fixtures.rules)))
    }

    @Test
    fun `every payload root states its schema version`() {
        val roots = listOf(
            WireJson.compact.encodeToString(
                YearResolutionDto.serializer(),
                renderer.yearResolution(2026, Fixtures.mayapur, Fixtures.rules, Fixtures.yearResolution),
            ),
            WireJson.compact.encodeToString(
                EkadashiYearDto.serializer(),
                renderer.ekadashiYear(2026, Fixtures.mayapur, Fixtures.rules, emptyList()),
            ),
            WireJson.compact.encodeToString(
                SampradayaListDto.serializer(),
                WireRenderer.sampradayaList(listOf(Fixtures.rules)),
            ),
        )
        for (text in roots) {
            assertEquals(
                WIRE_SCHEMA_VERSION,
                objectOf(text)["schemaVersion"].toString().toInt(),
                "a payload nobody can version is a payload nobody can migrate: $text",
            )
        }
    }

    @Test
    fun `dates are ISO-8601 and enums are their declared names`() {
        val text = WireJson.compact.encodeToString(
            ObservanceDecisionDto.serializer(),
            renderer.decision(Fixtures.decision),
        )

        assertTrue(text.contains("\"date\":\"2026-01-15\""), text)
        assertTrue(text.contains("\"kind\":\"MAHADVADASHI\""), text)
        assertTrue(text.contains("\"mahadvadashiType\":\"UNMILANI\""), text)
        assertTrue(text.contains("\"paksha\":\"SHUKLA\""), text)
        assertTrue(text.contains("\"confidence\":\"CONFIRMED\""), text)
    }

    @Test
    fun `an unknown enum name is refused rather than defaulted`() {
        // A build that does not know a constant must say so. Falling back to a default would
        // turn "I do not understand this Mahadvadashi" into "this is an ordinary Ekadashi".
        assertThrows<kotlinx.serialization.SerializationException> {
            WireJson.compact.decodeFromString(RuleConfidenceSerializer, "\"PROBABLY\"")
        }
    }

    @Test
    fun `all three confidence grades survive`() {
        for (grade in RuleConfidence.entries) {
            assertEquals(grade, roundTrip(RuleConfidenceSerializer, grade))
        }
    }
}
