package org.panchang.verify

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.panchang.verify.grid.ReferenceCities
import java.time.LocalDate
import java.time.ZoneId

class ReferenceCitiesTest {

    @Test
    fun `ids are unique and lowercase`() {
        val ids = (ReferenceCities.ALL + ReferenceCities.POLAR_PROBES).map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate city id")
        assertTrue(ids.all { it == it.lowercase() && it.isNotBlank() })
    }

    @Test
    fun `coordinates are within range and time zones resolve`() {
        (ReferenceCities.ALL + ReferenceCities.POLAR_PROBES).forEach { c ->
            assertTrue(c.latitudeDeg in -90.0..90.0, "${c.id} latitude out of range")
            assertTrue(c.longitudeDeg in -180.0..180.0, "${c.id} longitude out of range")
            // ZoneId.of throws on an unknown or abbreviated zone; that must fail here, not
            // halfway through a harvest run.
            ZoneId.of(c.ianaZone)
        }
    }

    /**
     * The grid exists to make specific failures reachable. If one of these properties
     * stops holding, the corresponding class of bug becomes untestable and nothing else
     * would say so.
     */
    @Test
    fun `the grid still covers every case it was chosen for`() {
        val all = ReferenceCities.ALL
        assertTrue(all.any { it.longitudeDeg < 0 }, "no western-longitude city")
        assertTrue(all.any { it.latitudeDeg < 0 }, "no southern-hemisphere city")
        assertTrue(all.any { it.latitudeDeg > 50 }, "no high-latitude city")

        val jan = LocalDate.of(2026, 1, 15)
        val jun = LocalDate.of(2026, 6, 15)
        val dstCities = all.filter { c ->
            val zone = ZoneId.of(c.ianaZone)
            zone.rules.getOffset(jan.atTime(12, 0)) != zone.rules.getOffset(jun.atTime(12, 0))
        }
        assertTrue(
            dstCities.any { it.latitudeDeg > 0 } && dstCities.any { it.latitudeDeg < 0 },
            "the grid must contain a DST-observing city in each hemisphere; found: " +
                dstCities.joinToString { it.id },
        )

        assertTrue(all.any { ZoneId.of(it.ianaZone).rules.getOffset(jan.atTime(12, 0)).totalSeconds % 3600 != 0 },
            "no half-hour-offset zone in the grid")

        // Polar probes are the only way to provoke USNO's no-rise responses.
        assertTrue(ReferenceCities.POLAR_PROBES.any { it.latitudeDeg > 66.5 })
        assertTrue(ReferenceCities.POLAR_PROBES.any { it.latitudeDeg < -66.5 })
    }

    /**
     * The community calendar files cities under its own names, several of which are not
     * the modern ones. A wrong stem is a 404 at harvest time, so record here that these
     * are deliberate rather than typos.
     */
    @Test
    fun `community calendar city stems are recorded in the source's own spelling`() {
        assertEquals("Bombay [India]", ReferenceCities.MUMBAI.vaisnavaCalendarCity)
        assertEquals("Moskva [Russia]", ReferenceCities.MOSCOW.vaisnavaCalendarCity)
        assertEquals("New York City [United States of America]", ReferenceCities.NEW_YORK.vaisnavaCalendarCity)
        assertTrue(
            ReferenceCities.ALL.all { it.vaisnavaCalendarCity != null },
            "every grid city was verified present on vaisnavacalendar.info on 2026-08-01",
        )
        assertTrue(
            ReferenceCities.ALL.all { it.vaisnavaCalendarCity!!.matches(Regex("""^[^\[\]]+ \[[^\[\]]+]$""")) },
            "stems must be 'City [Country]' or the URL will not resolve",
        )
    }

    @Test
    fun `lookup by id is case insensitive and fails helpfully`() {
        assertEquals(ReferenceCities.MAYAPUR, ReferenceCities.byId("MAYAPUR"))
        val e = assertThrows<IllegalArgumentException> { ReferenceCities.byId("atlantis") }
        assertTrue(e.message!!.contains("mayapur"), e.message)
    }
}
