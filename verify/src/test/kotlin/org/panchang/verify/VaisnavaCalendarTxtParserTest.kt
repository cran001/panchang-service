package org.panchang.verify

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.panchang.verify.harvest.ResponseShapeException
import org.panchang.verify.vaisnava.VaisnavaCalendarTxtParser

class VaisnavaCalendarTxtParserTest {

    private fun mayapurHead() = Fixtures.latin1("vaisnavacalendar_mayapur_2026_head.txt")
    private fun mayapurJune() = Fixtures.latin1("vaisnavacalendar_mayapur_2026_jun_slice.txt")
    private fun aucklandHead() = Fixtures.latin1("vaisnavacalendar_auckland_2026_head.txt")

    @Test
    fun `reads the city header including coordinates and offset`() {
        val (calendar, _) = VaisnavaCalendarTxtParser.parseCalendar(mayapurHead())
        assertEquals("Mayapur [India]", calendar.header.city)
        assertEquals("23N25 88E23", calendar.header.coordinates)
        assertEquals("+5:30", calendar.header.utcOffset)
        assertEquals("GCal 11, Build 5", calendar.header.generator)
    }

    @Test
    fun `parses every day line in the excerpt with nothing unparsed`() {
        val (calendar, report) = VaisnavaCalendarTxtParser.parseCalendar(mayapurHead())
        assertEquals(21, calendar.days.size)
        assertEquals("2026-01-01", calendar.days.first().date)
        assertTrue(report.unparsed.isEmpty(), "unparsed: ${report.unparsed}")
        assertTrue(report.warnings.isEmpty(), "warnings: ${report.warnings}")
    }

    @Test
    fun `carries the masa heading forward onto the days beneath it`() {
        val (calendar, _) = VaisnavaCalendarTxtParser.parseCalendar(mayapurHead())
        assertEquals("Pausa (Narayana)", calendar.days.first { it.date == "2026-01-01" }.masa)
        assertEquals(539, calendar.days.first { it.date == "2026-01-01" }.gaurabda)
        assertEquals("Magha (Madhava)", calendar.days.first { it.date == "2026-01-04" }.masa)
    }

    @Test
    fun `reads the Ekadasi row and its fasting marker`() {
        val (calendar, _) = VaisnavaCalendarTxtParser.parseCalendar(mayapurHead())
        val ekadasi = calendar.days.single { it.date == "2026-01-14" }
        assertEquals("Ekadasi (suitable for fasting)", ekadasi.tithi)
        assertEquals("K", ekadasi.paksa)
        assertEquals("Anuradha", ekadasi.naksatra)
        assertTrue(ekadasi.fastMarked)
        assertEquals("Sat-tila Ekadasi", ekadasi.fastingFor)
    }

    /**
     * The parana window is the single most consequential field this source publishes, and
     * both of its bounds are rules, not clock times. Keeping only "06:20 to 09:57" would
     * make a sunrise-bounded window indistinguishable from a quarter-tithi-bounded one.
     */
    @Test
    fun `reads a parana window with both bounding rules and its clock basis`() {
        val (calendar, _) = VaisnavaCalendarTxtParser.parseCalendar(mayapurHead())
        val parana = calendar.days.single { it.date == "2026-01-15" }.parana
        assertNotNull(parana)
        assertEquals("06:20", parana!!.start)
        assertEquals("sunrise", parana.startBasis)
        assertEquals("09:57", parana.end)
        assertEquals("1/3 of daylight", parana.endBasis)
        assertEquals("LT", parana.clock)
    }

    /**
     * The DST stress case in its raw form. Auckland's January lines are stamped DST, not
     * LT; a parser that drops the marker is an hour wrong for half the southern year.
     */
    @Test
    fun `distinguishes DST from local standard time on parana lines`() {
        val (calendar, _) = VaisnavaCalendarTxtParser.parseCalendar(aucklandHead())
        assertEquals("Auckland [New Zealand]", calendar.header.city)
        assertEquals("+12:00", calendar.header.utcOffset)
        val dstWindows = calendar.days.mapNotNull { it.parana }.filter { it.clock == "DST" }
        assertTrue(dstWindows.isNotEmpty(), "expected at least one DST-stamped parana window")
        assertEquals("06:04", dstWindows.first().start)
        assertEquals("end of tithi", dstWindows.first().endBasis)
    }

    /**
     * The source occasionally states only a lower bound: "Break fast after 10:31
     * (1/4 of tithi) LT". Three days across the ten grid cities look like this in 2026.
     * Inventing an upper bound would turn a rule the source declined to state into one we
     * made up, and dropping the line would lose the parana entirely.
     */
    @Test
    fun `reads an open-ended parana window without inventing an upper bound`() {
        val (calendar, report) = VaisnavaCalendarTxtParser.parseCalendar(
            Fixtures.latin1("vaisnavacalendar_auckland_2026_sep_slice.txt"),
        )
        val parana = calendar.days.single { it.date == "2026-09-23" }.parana
        assertNotNull(parana)
        assertEquals("10:31", parana!!.start)
        assertEquals("1/4 of tithi", parana.startBasis)
        assertEquals(null, parana.end)
        assertEquals(null, parana.endBasis)
        assertEquals("LT", parana.clock)
        assertTrue(report.unparsed.isEmpty(), "unparsed: ${report.unparsed}")
    }

    /**
     * Real 2026 data: `25 Jun 2026 Th   Ekadasi (not suitable for fasting)G Swati` has no
     * space between the tithi text and the paksa column. Three lines in the Mayapur file
     * are like this and all three are Ekadasi rows. A whitespace-splitting parser loses
     * exactly the rows this module exists to check.
     */
    @Test
    fun `parses a row whose tithi text overflows into the paksa column`() {
        val (calendar, report) = VaisnavaCalendarTxtParser.parseCalendar(mayapurJune())
        val overflow = calendar.days.single { it.date == "2026-06-25" }
        assertEquals("Ekadasi (not suitable for fasting)", overflow.tithi)
        assertEquals("G", overflow.paksa)
        assertEquals("Swati", overflow.naksatra)
        assertTrue(report.unparsed.isEmpty(), "unparsed: ${report.unparsed}")
    }

    /**
     * On a Mahadvadasi the fast moves off Ekadasi onto Dvadasi. Attaching the Ekadashi
     * name to the tithi rather than to the day the source marked for fasting would erase
     * that, and Mahadvadasi handling is one of the harder things to get right downstream.
     */
    @Test
    fun `keeps the fasting day distinct from the Ekadasi tithi on a Mahadvadasi`() {
        val (calendar, _) = VaisnavaCalendarTxtParser.parseCalendar(mayapurJune())
        val ekadasiTithi = calendar.days.single { it.date == "2026-06-25" }
        val fastingDay = calendar.days.single { it.date == "2026-06-26" }

        assertTrue(ekadasiTithi.tithi.startsWith("Ekadasi"))
        assertTrue(!ekadasiTithi.fastMarked, "the Ekadasi tithi itself is not the fasting day here")

        assertEquals("Dvadasi (suitable for fasting)", fastingDay.tithi)
        assertTrue(fastingDay.fastMarked)
        assertEquals("Pandava Nirjala Ekadasi", fastingDay.fastingFor)
        assertTrue(fastingDay.events.contains("Paksa vardhini Mahadvadasi"))

        // The parana then falls on the day after the fast, i.e. trayodasi.
        assertEquals("04:52", calendar.days.single { it.date == "2026-06-27" }.parana!!.start)
    }

    @Test
    fun `collects appearance and disappearance entries verbatim`() {
        val (calendar, _) = VaisnavaCalendarTxtParser.parseCalendar(mayapurJune())
        val day = calendar.days.single { it.date == "2026-06-24" }
        assertEquals(
            listOf(
                "Ganga Puja",
                "Sri Baladeva Vidyabhusana -- Disappearance",
                "Srimati Gangamata Gosvamini -- Appearance",
            ),
            day.events,
        )
    }

    @Test
    fun `collects sankranti rules without treating them as day rows`() {
        val (calendar, _) = VaisnavaCalendarTxtParser.parseCalendar(mayapurHead())
        val makara = calendar.sankrantis.single()
        assertEquals("Makara Sankranti", makara.name)
        assertTrue(makara.text.contains("Sun enters Capricorn on 14 Jan, 14:54 LT"))
    }

    @Test
    fun `reports an unrecognised body line instead of dropping it`() {
        val tampered = mayapurHead().replace(
            " 2 Jan 2026 Fr   Caturdasi                         G Mrigasira",
            " 2 Jan 2026 Fr   ???",
        )
        val (_, report) = VaisnavaCalendarTxtParser.parseCalendar(tampered)
        assertEquals(1, report.unparsed.size)
        assertTrue(report.unparsed.first().reason.contains("columns could not be split"))
    }

    /**
     * A cheap independent check that the fixed-width assumption still holds: the printed
     * weekday must agree with the printed date.
     */
    @Test
    fun `warns when the printed weekday contradicts the printed date`() {
        val tampered = mayapurHead().replace(" 1 Jan 2026 Th ", " 1 Jan 2026 Mo ")
        val (_, report) = VaisnavaCalendarTxtParser.parseCalendar(tampered)
        assertTrue(report.warnings.any { it.contains("weekday") }, report.warnings.toString())
    }

    @Test
    fun `refuses a document that is not a Gaurabda Calendar export`() {
        val e = assertThrows<ResponseShapeException> {
            VaisnavaCalendarTxtParser.parseCalendar("<html><body>404 Not Found</body></html>")
        }
        assertTrue(e.message!!.contains("city header"), e.message)
    }

    @Test
    fun `stops at the trailer instead of reporting the notes as unparsed`() {
        val withNotes = mayapurHead() + "\n" + Fixtures.latin1("vaisnavacalendar_notes_tail.txt")
        val (_, report) = VaisnavaCalendarTxtParser.parseCalendar(withNotes)
        assertTrue(report.unparsed.isEmpty(), "unparsed: ${report.unparsed}")
    }
}
