package org.panchang.wire

import java.util.Locale
import java.util.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.panchang.sampradaya.EventResolution
import org.panchang.sampradaya.YearResolution

/**
 * A caller must be able to diff two runs.
 *
 * `:publish` writes these payloads to files that go into version control and into an app bundle.
 * If the same input produces different bytes on Tuesday, every diff is noise and nobody reads any
 * of them — including the one where a fasting date moved.
 */
class DeterminismTest {

    private val renderer = WireRenderer(Fixtures.mayapur)

    private fun encodeYear(resolution: YearResolution = Fixtures.yearResolution): String =
        WireJson.pretty.encodeToString(
            YearResolutionDto.serializer(),
            renderer.yearResolution(2026, Fixtures.mayapur, Fixtures.rules, resolution),
        )

    @Test
    fun `two runs of the same input produce byte-identical output`() {
        assertEquals(encodeYear(), encodeYear())
    }

    @Test
    fun `output does not depend on the order the unresolved map was built in`() {
        val forwards = Fixtures.yearResolution
        val backwards = YearResolution(
            events = forwards.events,
            unresolved = LinkedHashMap<String, EventResolution>().apply {
                forwards.unresolved.entries.reversed().forEach { put(it.key, it.value) }
            },
        )

        assertEquals(encodeYear(forwards), encodeYear(backwards))
    }

    @Test
    fun `output does not depend on the default locale`() {
        // Turkish lowercases 'I' to a dotless 'ı' and Arabic can render digits in Eastern
        // numerals. A formatter that consults the default locale breaks in exactly one country's
        // build agent, which is the hardest kind of bug to be told about.
        val original = Locale.getDefault()
        val baseline = encodeYear()
        try {
            for (locale in listOf(Locale.forLanguageTag("tr-TR"), Locale.forLanguageTag("ar-EG-u-nu-arab"))) {
                Locale.setDefault(locale)
                assertEquals(baseline, encodeYear(), "output changed under locale $locale")
            }
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `output does not depend on the default time zone`() {
        val original = TimeZone.getDefault()
        val baseline = encodeYear()
        try {
            for (id in listOf("Pacific/Kiritimati", "America/Anchorage", "UTC")) {
                TimeZone.setDefault(TimeZone.getTimeZone(id))
                assertEquals(baseline, encodeYear(), "output changed under default zone $id")
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `a Julian Day renders as the same characters every time`() {
        // Guards the fixed-scale rounding: an unrounded Double would let the last bits of a float
        // wander between runs of a different build of the engine and make every diff dirty.
        val text = WireJson.compact.encodeToString(
            InstantDto.serializer(),
            renderer.instant(Fixtures.JD_A),
        )
        assertEquals("{\"local\":\"2026-01-15T06:20:31+05:30\",\"jdUt\":2461055.53508}", text)
    }
}
