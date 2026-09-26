package org.panchang.gazetteer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.ZoneId

class GazetteerTest {

    private val g = Gazetteer.default

    @Test
    fun `the table loads and both kinds are present`() {
        assertEquals(34826, g.places.size, "row count changed; if that was intentional, update this test")
        assertEquals(763, g.places.count { it.kind == PlaceKind.DISTRICT }, "India ADM2 district count")
        assertEquals(34063, g.places.count { it.kind == PlaceKind.CITY }, "cities15000 row count")
    }

    @Test
    fun `every row is well formed`() {
        val ids = HashSet<Int>(g.places.size * 2)
        for (p in g.places) {
            assertTrue(ids.add(p.geonameId), "duplicate geonameid ${p.geonameId}")
            assertTrue(p.name.isNotBlank(), "blank name for ${p.geonameId}")
            assertTrue(p.latitude in -90.0..90.0, "latitude out of range for ${p.geonameId}")
            assertTrue(p.longitude in -180.0..180.0, "longitude out of range for ${p.geonameId}")
            assertTrue(p.population >= 0, "negative population for ${p.geonameId}")
            assertEquals(2, p.country.length, "country code for ${p.geonameId}")
        }
    }

    @Test
    fun `districts are all Indian and carry a resolved state name`() {
        val districts = g.places.filter { it.kind == PlaceKind.DISTRICT }
        assertTrue(districts.all { it.country == "IN" }, "the district set is India-only by construction")
        assertTrue(districts.all { it.zone == ZoneId.of("Asia/Kolkata") }, "Indian districts keep IST")
        // Resolved from the country file's own ADM1 records, so no district should be left
        // showing a bare numeric GeoNames code.
        assertTrue(
            districts.none { it.admin1.toIntOrNull() != null },
            "some districts still carry an unresolved numeric admin1 code",
        )
    }

    @Test
    fun `name lookup is exact after normalisation and diacritic-insensitive`() {
        val asciiHit = g.byName("Navadwip")
        assertTrue(asciiHit.any { it.geonameId == 1261669 }, "Navadwip should be found by its ASCII name")
        assertEquals(asciiHit, g.byName("navadwīp"), "diacritics and case must not change the result")
        assertEquals(asciiHit, g.byName("  NAVADWIP  "), "surrounding whitespace must not change the result")
    }

    @Test
    fun `name lookup returns nothing rather than a guess`() {
        assertTrue(g.byName("Nvadwip").isEmpty(), "a typo must not fuzzy-match")
        assertTrue(g.byName("").isEmpty())
        assertTrue(g.byName("   ").isEmpty())
    }

    @Test
    fun `ambiguous names return every match, most populous first`() {
        // Two real districts named Raigarh, in Chhattisgarh and Maharashtra. The gazetteer's
        // job is to surface both so the caller can disambiguate, never to silently pick one.
        val raigarh = g.byName("Raigarh").filter { it.kind == PlaceKind.DISTRICT }
        assertEquals(2, raigarh.size, "both Raigarh districts should be returned")
        assertEquals(
            raigarh.map { it.population }.sortedDescending(),
            raigarh.map { it.population },
            "results must be ordered most populous first",
        )
        assertEquals(2, raigarh.map { it.admin1 }.distinct().size, "they are in different states")
    }

    @Test
    fun `prefix search is for typeahead and never matches everything`() {
        val hits = g.startingWith("Mathur", limit = 5)
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.size <= 5)
        assertTrue(hits.all { normalizePlaceName(it.name).startsWith("mathur") || normalizePlaceName(it.asciiName).startsWith("mathur") })
        assertTrue(g.startingWith("").isEmpty(), "a blank prefix must match nothing, not everything")
        assertThrows<IllegalArgumentException> { g.startingWith("Mathura", limit = 0) }
    }

    @Test
    fun `byGeonameId finds a known record`() {
        val nadia = g.byGeonameId(1262293)
        assertNotNull(nadia)
        assertEquals(PlaceKind.DISTRICT, nadia!!.kind)
        assertEquals("West Bengal", nadia.admin1)
        assertNull(g.byGeonameId(-1))
    }

    // ---- nearest -----------------------------------------------------------------------

    @Test
    fun `nearest finds the closest record`() {
        // ISKCON Mayapur. Navadwip sits about 3 km across the river.
        val hit = g.nearest(23.4247, 88.3903)
        assertNotNull(hit)
        assertEquals(1261669, hit!!.geonameId, "expected Navadwip")
        assertTrue(
            Gazetteer.haversineKm(23.4247, 88.3903, hit.latitude, hit.longitude) < 5.0,
            "Navadwip should be within 5 km of Mayapur",
        )
    }

    /**
     * The one that matters. A point in the South Pacific has a nearest record 1 119 km away;
     * naming it would be a fabrication, and any caller that then computed sunrise at it would
     * be wrong by most of an hour. Beyond the radius the answer is "I don't know".
     */
    @Test
    fun `nearest returns null beyond the radius rather than snapping`() {
        assertNull(g.nearest(-30.0, -140.0), "must not snap to a place 1119 km away")
        // And prove the null came from the radius, not from an empty table or a broken grid:
        // widen it and the same query finds the record it was correctly refusing to return.
        val faraway = g.nearest(-30.0, -140.0, withinKm = 2000.0)
        assertNotNull(faraway, "widening the radius should find Adamstown")
        assertTrue(
            Gazetteer.haversineKm(-30.0, -140.0, faraway!!.latitude, faraway.longitude) > 1000.0,
            "the record the default radius rejected really is over 1000 km away",
        )
    }

    @Test
    fun `the radius is honoured exactly`() {
        val mayapurLat = 23.4247
        val mayapurLon = 88.3903
        val hit = g.nearest(mayapurLat, mayapurLon)!!
        val km = Gazetteer.haversineKm(mayapurLat, mayapurLon, hit.latitude, hit.longitude)
        assertNotNull(g.nearest(mayapurLat, mayapurLon, withinKm = km * 1.001), "just inside")
        assertNull(
            g.nearest(mayapurLat, mayapurLon, withinKm = km * 0.5),
            "a radius smaller than the true distance must reject the record",
        )
    }

    /**
     * The bucket grid is keyed on integer degrees, so the cell east of 179°E is 180°W. A
     * caller at 179.9°W is 78 km from Labasa at 179.36°E; a grid that clamped instead of
     * wrapping would return null there and nobody would notice, because "no place nearby in
     * the middle of the Pacific" is exactly what you would expect to see.
     */
    @Test
    fun `nearest wraps across the antimeridian`() {
        val hit = g.nearest(-16.4332, -179.9)
        assertNotNull(hit, "the grid must wrap in longitude")
        assertEquals(2204582, hit!!.geonameId, "expected Labasa, Fiji")
    }

    @Test
    fun `nearest works at the poles where longitude cells collapse`() {
        // No record within 150 km of either pole, but the query must not throw or run away
        // scanning; at high latitude the longitude band widens to the whole globe by design.
        assertNull(g.nearest(90.0, 0.0))
        assertNull(g.nearest(-90.0, 0.0))
        assertNotNull(g.nearest(90.0, 0.0, withinKm = 20_000.0), "a global radius always finds something")
    }

    @Test
    fun `nearest rejects nonsense input at the edge`() {
        assertThrows<IllegalArgumentException> { g.nearest(91.0, 0.0) }
        assertThrows<IllegalArgumentException> { g.nearest(0.0, 181.0) }
        assertThrows<IllegalArgumentException> { g.nearest(Double.NaN, 0.0) }
        assertThrows<IllegalArgumentException> { g.nearest(0.0, 0.0, withinKm = 0.0) }
        assertThrows<IllegalArgumentException> { g.nearest(0.0, 0.0, withinKm = -1.0) }
    }

    /**
     * The bound the grid was chosen for. `nearest` scans at most
     * `(2·latCells+1) · (2·lonCells+1) · maxCellOccupancy` records; pinning the occupancy is
     * what turns "a grid is fast enough" from a hope into a stated worst case. If a future
     * data refresh blows past this, the right response is to look at the cell, not to raise
     * the number reflexively.
     */
    @Test
    fun `no one-degree cell is pathologically crowded`() {
        assertTrue(
            g.maxCellOccupancy <= 400,
            "densest 1x1 degree cell holds ${g.maxCellOccupancy} records; the nearest() " +
                "worst case is proportional to this",
        )
        assertTrue(g.occupiedCellCount > 3_000, "occupied cells: ${g.occupiedCellCount}")
    }

    /** Brute force over the whole table must agree with the grid, at random points. */
    @Test
    fun `grid search agrees with brute force`() {
        val rng = java.util.Random(20260801L)
        repeat(100) {
            val lat = rng.nextDouble() * 180.0 - 90.0
            val lon = rng.nextDouble() * 360.0 - 180.0
            var expected: Place? = null
            var bestKm = Double.MAX_VALUE
            for (p in g.places) {
                val km = Gazetteer.haversineKm(lat, lon, p.latitude, p.longitude)
                if (km <= 150.0 && km < bestKm) {
                    bestKm = km
                    expected = p
                }
            }
            val actual = g.nearest(lat, lon)
            if (expected == null) {
                assertNull(actual, "grid found a record brute force did not, at ($lat, $lon)")
            } else {
                assertNotNull(actual, "grid missed a record at ($lat, $lon): ${expected.name}")
                assertEquals(
                    Gazetteer.haversineKm(lat, lon, expected.latitude, expected.longitude),
                    Gazetteer.haversineKm(lat, lon, actual!!.latitude, actual.longitude),
                    1e-9,
                    "grid returned a farther record than brute force at ($lat, $lon)",
                )
            }
        }
    }

    @Test
    fun `zone ids are interned rather than duplicated per row`() {
        val distinct = g.places.map { it.zone }.distinct()
        assertTrue(distinct.size < 500, "expected a few hundred distinct zones, got ${distinct.size}")
        val first = g.places.first { it.zone.id == "Asia/Kolkata" }.zone
        val last = g.places.last { it.zone.id == "Asia/Kolkata" }.zone
        assertSame(first, last, "identical zone ids should share one ZoneId instance")
    }

    @Test
    fun `toGeoLocation defaults to sea level and carries the record's zone`() {
        val nadia = g.byGeonameId(1262293)!!
        val loc = nadia.toGeoLocation()
        assertEquals(0.0, loc.elevationMeters, "elevation must default to zero; see PROVENANCE.md")
        assertEquals(nadia.zone, loc.zone)
        assertEquals(nadia.latitude, loc.latitude)
        assertEquals(nadia.longitude, loc.longitude)
    }
}
