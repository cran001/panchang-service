package org.panchang.verify

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.panchang.verify.drik.DrikPanchangParser
import org.panchang.verify.harvest.ResponseShapeException
import org.panchang.verify.vaisnava.IskconMumbaiParser
import org.panchang.verify.vaisnava.PureBhaktiParser
import java.time.LocalDate

class IskconMumbaiParserTest {

    private fun fragment() = Fixtures.text("iskconmumbai_ekadasi_fragment.html")

    @Test
    fun `reads the Ekadashi name and parana window from the notice`() {
        val parsed = IskconMumbaiParser.parseEkadasiPage(fragment())
        val notice = parsed.records.single()
        assertEquals("Krishna Kamika Ekadasi", notice.ekadasiName)
        assertEquals("2026-08-10", notice.paranaDate)
        assertEquals("Mumbai", notice.city)
        assertTrue(parsed.report.unparsed.isEmpty(), parsed.report.unparsed.toString())
    }

    /**
     * The source writes "6:18 AM" and "08:02 AM" in the same sentence. Both must land on
     * the same 24-hour form or a downstream comparison sorts them wrongly.
     */
    @Test
    fun `normalises inconsistently written clock times`() {
        val notice = IskconMumbaiParser.parseEkadasiPage(fragment()).records.single()
        assertEquals("06:18", notice.paranaStart)
        assertEquals("08:02", notice.paranaEnd)
        assertEquals("12:00", IskconMumbaiParser.normaliseTime("12:00 PM"))
        assertEquals("00:30", IskconMumbaiParser.normaliseTime("12:30 AM"))
    }

    @Test
    fun `fails rather than returning an empty notice when the sentence is gone`() {
        val e = assertThrows<ResponseShapeException> {
            IskconMumbaiParser.parseEkadasiPage("<html><body><p>Under maintenance</p></body></html>")
        }
        assertTrue(e.message!!.contains("Refusing"), e.message)
    }

    /**
     * The month calendar page is JavaScript-rendered. Returning an empty list here would
     * read as "no observances this month" to every caller downstream.
     */
    @Test
    fun `refuses to report the JavaScript-rendered calendar page as empty`() {
        val e = assertThrows<ResponseShapeException> {
            IskconMumbaiParser.parseCalendarPage("""<div class="ui grid"><div id="calendar"></div></div>""")
        }
        assertTrue(e.message!!.contains("client-side"), e.message)
        assertTrue(e.message!!.contains("vaisnavacalendar.info"), e.message)
    }
}

class PureBhaktiParserTest {

    /**
     * As of 2026-08-01 this endpoint serves site chrome and no panjika rows. The parser
     * must say so rather than report a successful harvest of zero observances.
     */
    @Test
    fun `fails explicitly on a page with no calendar rows`() {
        val e = assertThrows<ResponseShapeException> {
            PureBhaktiParser.parse("<html><body><div>Vaisnava Calendar</div></body></html>")
        }
        assertTrue(e.message!!.contains("no calendar entries recognised"), e.message)
    }

    @Test
    fun `extracts dated observance lines when they are present`() {
        val html = """
            <ul>
              <li>10 Aug 2026 - Kamika Ekadasi</li>
              <li>11 Aug 2026 - Parana between sunrise and 10:00</li>
              <li>15 Aug 2026 - Sri Krsna Janmastami appearance</li>
              <li>2024 - copyright notice, not a calendar row</li>
            </ul>
        """.trimIndent()
        val parsed = PureBhaktiParser.parse(html)
        assertEquals(3, parsed.records.size)
        assertEquals("2026-08-10", parsed.records[0].date)
        assertEquals("Ekadasi", parsed.records[0].category)
        assertEquals("Parana", parsed.records[1].category)
        assertEquals("Appearance", parsed.records[2].category)
    }
}

class DrikPanchangParserTest {

    private fun structure() = Fixtures.text("drikpanchang_day_panchang_structure.html")

    /**
     * Structural test only. The fixture is synthetic because drikpanchang.com data is a
     * comparison target we do not redistribute; every value below is a placeholder and
     * none of it is a panchang assertion.
     */
    @Test
    fun `recovers key value pairs across nested markup`() {
        val parsed = DrikPanchangParser.parse(structure(), LocalDate.of(1970, 1, 1))
        val day = parsed.records.single()
        assertEquals("11:11 AM", day.value("Sunrise"))
        assertEquals("22:22 PM", day.value("Sunset"))
        assertEquals("33:33 AM", day.value("Moonrise"))
        assertEquals("44:44 PM", day.value("Moonset"))
        assertTrue(parsed.report.unparsed.isEmpty(), parsed.report.unparsed.toString())
    }

    @Test
    fun `keeps the whole of a value that contains nested spans and anchors`() {
        val day = DrikPanchangParser.parse(structure(), LocalDate.of(1970, 1, 1)).records.single()
        // A regex stopping at the first </div> would truncate this to the anchor text.
        assertEquals("PlaceholderTithiOne upto 55:55 AM", day.value("Tithi"))
        assertEquals("PlaceholderNakshatraOne upto 66:66 AM", day.value("Nakshatra"))
    }

    /**
     * A day with two tithis renders the second in a row with an empty key cell. Dropping
     * it loses the tithi boundary, which is the one thing worth comparing here.
     */
    @Test
    fun `attaches a continuation row to the key above it`() {
        val day = DrikPanchangParser.parse(structure(), LocalDate.of(1970, 1, 1)).records.single()
        assertEquals("PlaceholderTithiTwo", day.value("Tithi (cont)"))
        assertEquals("PlaceholderNakshatraTwo", day.value("Nakshatra (cont)"))
    }

    @Test
    fun `fails on a page with no panchang table, such as a 403 interstitial`() {
        val e = assertThrows<ResponseShapeException> {
            DrikPanchangParser.parse("<html><body>403 Forbidden</body></html>", LocalDate.of(1970, 1, 1))
        }
        assertTrue(e.message!!.contains("403"), e.message)
    }
}
