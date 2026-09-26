package org.panchang.publish

import java.time.ZoneOffset
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The legacy shape's guards, tested against the failures they exist to prevent rather than against
 * a restatement of their own regexes.
 */
class LegacyFeedTest {

    private fun location(timezone: String) = LegacyLocation(
        title = "Sri Mayapur",
        coordinates = "23N25 088E23",
        timezone = timezone,
        option = "mayapur",
        file = "mayapur",
    )

    // ── timezone ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an IANA zone id in the timezone field is refused`() {
        // LiveCalendarDataSource.parseZoneOffset does parts[0].toInt() and catches its own failure
        // into ZoneOffset.ofHoursMinutes(5, 30). Every one of these would publish as IST.
        listOf("Asia/Kolkata", "America/New_York", "Europe/London", "UTC", "IST").forEach {
            val error = assertThrows<IllegalArgumentException> { location(it) }
            assertTrue(
                error.message!!.contains("silently fall back to IST"),
                "the refusal for '$it' does not name the failure it prevents: ${error.message}",
            )
        }
    }

    @Test
    fun `the string Z is refused, and timezoneField never produces it`() {
        // ZoneOffset.UTC.getId() is "Z". It is not an IANA name, it looks harmless, and it fails
        // the app's numeric parser exactly like one: this is the trap a Kotlin author walks into.
        assertEquals("Z", ZoneOffset.UTC.id)
        assertThrows<IllegalArgumentException> { location("Z") }
        assertEquals("+00:00", LegacyFeed.timezoneField(ZoneOffset.UTC))
        location(LegacyFeed.timezoneField(ZoneOffset.UTC))
    }

    @Test
    fun `numeric offsets are accepted and rendered with two digits and a sign`() {
        assertEquals("+05:30", LegacyFeed.timezoneField(ZoneOffset.ofHoursMinutes(5, 30)))
        assertEquals("-05:00", LegacyFeed.timezoneField(ZoneOffset.ofHours(-5)))
        assertEquals("+05:45", LegacyFeed.timezoneField(ZoneOffset.ofHoursMinutes(5, 45)))
        assertEquals("-03:30", LegacyFeed.timezoneField(ZoneOffset.ofHoursMinutes(-3, -30)))
        listOf("+05:30", "-05:00", "+00:00", "+05:45", "-03:30", "+14:00").forEach { location(it) }
    }

    @Test
    fun `the emitted offset survives the app's own parser unchanged`() {
        // A transcription of LiveCalendarDataSource.parseZoneOffset lines 53-65, applied to what
        // this publisher emits. If the two ever disagree the fasting alarm is wrong, silently.
        fun appParse(tz: String): ZoneOffset = try {
            val cleaned = tz.trim()
            val sign = if (cleaned.startsWith("-")) -1 else 1
            val parts = cleaned.removePrefix("+").removePrefix("-").split(":")
            ZoneOffset.ofHoursMinutes(
                sign * parts[0].toInt(),
                sign * (parts.getOrNull(1)?.toInt() ?: 0),
            )
        } catch (e: Exception) {
            ZoneOffset.ofHoursMinutes(5, 30)
        }

        listOf(
            ZoneOffset.ofHoursMinutes(5, 30),
            ZoneOffset.ofHoursMinutes(5, 45),
            ZoneOffset.UTC,
            ZoneOffset.ofHours(-5),
            ZoneOffset.ofHoursMinutes(-3, -30),
            ZoneOffset.ofHours(8),
            ZoneOffset.ofHours(14),
        ).forEach { offset ->
            assertEquals(offset, appParse(LegacyFeed.timezoneField(offset)), "offset $offset")
        }

        // And the failure mode itself, so the claim in the KDoc is measured and not asserted.
        assertEquals(ZoneOffset.ofHoursMinutes(5, 30), appParse("America/New_York"))
        assertEquals(ZoneOffset.ofHoursMinutes(5, 30), appParse("Z"))
    }

    // ── zone dir ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `zoneDir matches CalendarSyncRepository formatOffset`() {
        // formatOffset (line 311): String.format("%s%02d%02d", sign, hours, minutes), sign P or N.
        fun appFormat(offset: ZoneOffset): String {
            val totalMinutes = offset.totalSeconds / 60
            val sign = if (totalMinutes < 0) "N" else "P"
            val magnitude = abs(totalMinutes)
            return "%s%02d%02d".format(sign, magnitude / 60, magnitude % 60)
        }
        listOf(
            ZoneOffset.ofHoursMinutes(5, 30),
            ZoneOffset.UTC,
            ZoneOffset.ofHours(-5),
            ZoneOffset.ofHoursMinutes(-9, -30),
            ZoneOffset.ofHoursMinutes(5, 45),
        ).forEach { assertEquals(appFormat(it), LegacyFeed.zoneDir(it), "offset $it") }

        assertEquals("P0000", LegacyFeed.zoneDir(ZoneOffset.UTC))
        assertEquals("N0500", LegacyFeed.zoneDir(ZoneOffset.ofHours(-5)))
    }

    @Test
    fun `a zone dir the app could never match is refused`() {
        assertThrows<IllegalArgumentException> { LegacyZone("UTC+05:30", "+05:30", "+05:30") }
        assertThrows<IllegalArgumentException> { LegacyZone("UTC", "+00:00", "Asia/Kolkata") }
        LegacyZone("UTC+05:30", "+05:30", "P0530")
    }

    // ── coordinates ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `coordinates are emitted in the form the app's parser reads`() {
        // parseCoordinates (CalendarSyncRepository:282-302): one space, split on N/S then E/W,
        // deg + min/60. Anything else makes the entry unreachable as "nearest".
        fun appParse(text: String): Pair<Double, Double>? {
            val parts = text.trim().split(" ")
            if (parts.size != 2) return null
            fun one(part: String, positive: String, negative: String): Double? {
                val negativeSide = part.contains(negative)
                val bits = part.split(if (negativeSide) negative else positive)
                if (bits.size != 2) return null
                val degrees = bits[0].toDoubleOrNull() ?: return null
                val minutes = bits[1].toDoubleOrNull() ?: return null
                val value = degrees + minutes / 60.0
                return if (negativeSide) -value else value
            }
            val lat = one(parts[0], "N", "S") ?: return null
            val lon = one(parts[1], "E", "W") ?: return null
            return lat to lon
        }

        listOf(
            23.4249 to 88.3883,
            -26.2041 to 28.0473,
            1.3521 to 103.8198,
            51.5074 to -0.1278,
        ).forEach { (lat, lon) ->
            val text = LegacyFeed.coordinates(lat, lon)
            assertTrue(LegacyFeed.COORDINATES.matches(text), "'$text' is not the app's form")
            val parsed = appParse(text)
            assertNotNull(parsed, "the app's own parser cannot read '$text'")
            // An arc-minute is about 1.85 km; the tolerance is the resolution of the format, not
            // of the computation, and every published time used the full-precision point.
            assertTrue(abs(parsed!!.first - lat) < 0.02, "$text latitude $parsed vs $lat")
            assertTrue(abs(parsed.second - lon) < 0.02, "$text longitude $parsed vs $lon")
        }
    }

    @Test
    fun `an arc-minute that rounds to sixty is carried into the degrees`() {
        // 23.99999 -> 23 deg 60.0 min. "23N60" is arithmetically absurd and the app would parse it
        // as 24 degrees anyway; emitting it is still a defect in the file.
        assertEquals("24N00 000E00", LegacyFeed.coordinates(23.99999, 0.0))
        assertTrue(LegacyFeed.COORDINATES.matches(LegacyFeed.coordinates(23.99999, 179.99999)))
    }

    // ── parana title ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the break-fast title matches the app's own regex and yields both bounds`() {
        val title = LegacyFeed.breakFastTitle("06:12", "09:47")
        val match = LegacyFeed.APP_BREAK_FAST_REGEX.find(title.trimStart('+').trim())
        assertNotNull(match, "the app's regex does not match '$title'")
        assertEquals("06:12", match!!.groupValues[1])
        assertEquals("09:47", match.groupValues[2])
    }

    @Test
    fun `two break-fast items on one day are refused`() {
        // processAllDays keys paranaByDvadashiDate by date, so the second silently replaces the
        // first and one Ekadashi's window is quietly shown for another's.
        assertThrows<IllegalArgumentException> {
            LegacyDay(
                date = "2026-01-01",
                tithi = "Shukla Dvadashi",
                events = listOf(
                    LegacyEvent(LegacyFeed.breakFastTitle("06:12", "09:47")),
                    LegacyEvent(LegacyFeed.breakFastTitle("06:13", "09:48")),
                ),
            )
        }
    }

    // ── the fields that kill the whole array ────────────────────────────────────────────────

    @Test
    fun `a day with an unparseable date is refused`() {
        // Moshi decodes the file as one List<LiveCalendarDay>; one bad element throws out of
        // fromJson and the user loses the entire year, not the day.
        listOf("", "2026-1-1", "01-01-2026", "2026/01/01", "next tuesday").forEach {
            assertThrows<IllegalArgumentException>("date '$it' should have been refused") {
                LegacyDay(date = it, tithi = "Shukla Pratipada")
            }
        }
    }

    @Test
    fun `a day with a blank tithi is refused`() {
        assertThrows<IllegalArgumentException> { LegacyDay(date = "2026-01-01", tithi = " ") }
    }

    @Test
    fun `an event with a blank title is refused`() {
        assertThrows<IllegalArgumentException> { LegacyEvent(" ") }
    }

    // ── the fasting predicate ───────────────────────────────────────────────────────────────

    @Test
    fun `the fasting predicate matches what processAllDays looks for`() {
        assertTrue(LegacyFeed.appWouldTreatAsFastingTitle("Papamocani Ekadashi"))
        assertTrue(LegacyFeed.appWouldTreatAsFastingTitle("Pandava Nirjala Ekadashi"))
        assertTrue(LegacyFeed.appWouldTreatAsFastingTitle("Ekādaśī"))
        assertTrue(LegacyFeed.appWouldTreatAsFastingTitle("Nirjala fast"))
        assertFalse(LegacyFeed.appWouldTreatAsFastingTitle("Gaura Purnima"))
        assertFalse(LegacyFeed.appWouldTreatAsFastingTitle("Sri Krishna Janmastami"))
    }
}
