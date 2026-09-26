package org.panchang.wire

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The two-field instant, which is the reason this module renders anything at all.
 *
 * The legacy Android feed transmitted only a rendered string, so a timezone bug in it could not
 * be detected from the payload — there was nothing to check the string against. These tests pin
 * the property that fixes that: the zone changes what a human reads and cannot change the number
 * a diagnosis is done with.
 */
class InstantRenderingTest {

    @Test
    fun `renders the local string and the Julian Day side by side`() {
        val dto = WireRenderer(Fixtures.mayapur).instant(Fixtures.JD_A)

        assertEquals("2026-01-15T06:20:31+05:30", dto.local)
        assertEquals(2461055.53508, dto.jdUt)
    }

    @Test
    fun `the same instant at two zones gives two local strings and one Julian Day`() {
        val india = WireRenderer(Fixtures.mayapur).instant(Fixtures.JD_A)
        val newYork = WireRenderer(Fixtures.newYork).instant(Fixtures.JD_A)

        assertEquals("2026-01-15T06:20:31+05:30", india.local)
        assertEquals("2026-01-14T19:50:31-05:00", newYork.local)
        assertTrue(india.local != newYork.local, "the zone must change what a human reads")
        assertEquals(
            india.jdUt,
            newYork.jdUt,
            "the zone must not change the number a disagreement is diagnosed with",
        )
    }

    @Test
    fun `the offset is rendered, not just implied`() {
        val dto = WireRenderer(Fixtures.newYork).instant(Fixtures.JD_B)
        assertTrue(
            dto.local.endsWith("-05:00"),
            "a local string with no offset is exactly the legacy bug: ${dto.local}",
        )
    }

    @Test
    fun `Julian Days are rounded to a fixed number of places`() {
        val renderer = WireRenderer(Fixtures.mayapur)

        // Two inputs a nanometre of a day apart must not produce two different payload bytes.
        assertEquals(
            renderer.instant(2461055.535080001).jdUt,
            renderer.instant(2461055.535079999).jdUt,
        )
        assertEquals(5, WireRenderer.JD_DECIMALS)
    }

    @Test
    fun `seconds are rounded rather than truncated`() {
        // 00:00:00.900 UTC on 2026-01-15, which is 00:00:01 to the nearest second.
        val jd = 2461055.5 + 0.9 / 86_400.0
        val dto = WireRenderer(java.time.ZoneId.of("UTC")).instant(jd)
        assertEquals("2026-01-15T00:00:01Z", dto.local)
    }

    @Test
    fun `a non-finite Julian Day is refused rather than rendered`() {
        val renderer = WireRenderer(Fixtures.mayapur)
        assertThrows<IllegalArgumentException> { renderer.instant(Double.NaN) }
        assertThrows<IllegalArgumentException> { renderer.instant(Double.POSITIVE_INFINITY) }
    }
}
