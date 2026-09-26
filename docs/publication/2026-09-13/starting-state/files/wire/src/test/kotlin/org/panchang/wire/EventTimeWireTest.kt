package org.panchang.wire

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The three shapes of an anchored time, and in particular the shape that has no time.
 *
 * An absent moonrise must reach the client as "the Moon does not rise here today", never as a
 * blank field a careless renderer will format into a plausible-looking clock time.
 */
class EventTimeWireTest {

    private val renderer = WireRenderer(Fixtures.mayapur)

    @Test
    fun `At carries one instant`() {
        val dto = renderer.eventTime(Fixtures.at) as EventTimeDto.At

        assertEquals(2461055.9125, dto.at.jdUt)
        assertEquals("2026-01-15T15:24:00+05:30", dto.at.local)
        assertEquals("moonrise", dto.anchorDisplayName)
    }

    @Test
    fun `Window carries both bounds because a muhurta is not a point`() {
        val dto = renderer.eventTime(Fixtures.window) as EventTimeDto.Window

        assertTrue(dto.end.jdUt > dto.start.jdUt)
        assertEquals(48.0, dto.durationMinutes, 0.01)
    }

    @Test
    fun `Absent emits a reason and no time-shaped field whatsoever`() {
        val encoded = WireJson.compact.encodeToString(
            EventTimeDto.serializer(),
            renderer.eventTime(Fixtures.absent),
        )
        val obj = Json.parseToJsonElement(encoded) as JsonObject

        assertEquals("absent", obj["type"].toString().trim('"'))
        assertTrue(obj.containsKey("reason"), "an absence that does not say why is a blank: $encoded")

        for (forbidden in listOf("at", "start", "end", "jdUt", "local", "time", "durationMinutes")) {
            assertFalse(
                obj.containsKey(forbidden),
                "Absent must carry no time-shaped field, found '$forbidden' in $encoded",
            )
        }
        // Not a null and not an empty string sitting where a time would go, either.
        assertFalse(encoded.contains("null"), "a null where a time belongs is still a blank: $encoded")
        assertFalse(encoded.contains("\"\""), "an empty string where a time belongs is still a blank: $encoded")
    }

    @Test
    fun `Absent states the absence in a sentence a client can show unmodified`() {
        val dto = renderer.eventTime(Fixtures.absent) as EventTimeDto.Absent

        assertEquals("There is no moonrise here today: no such crossing falls inside this civil day.", dto.reasonText)
    }

    @Test
    fun `basis and confidence cross the module boundary verbatim`() {
        val dto = renderer.eventTime(Fixtures.window)

        assertEquals(Fixtures.window.basis, dto.basis)
        assertTrue(
            dto.basis.contains("INFERRED"),
            "the reasoning behind an inferred anchor is what a pandit reviews; it must travel",
        )
        assertEquals(Fixtures.window.confidence, dto.confidence)
    }

    @Test
    fun `all three shapes round trip`() {
        for (time in listOf(Fixtures.at, Fixtures.window, Fixtures.absent)) {
            val dto = renderer.eventTime(time)
            val text = WireJson.compact.encodeToString(EventTimeDto.serializer(), dto)
            assertEquals(dto, WireJson.compact.decodeFromString(EventTimeDto.serializer(), text))
        }
    }
}
