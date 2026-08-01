package org.panchang.gazetteer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.panchang.core.GeoLocation
import java.time.ZoneId

/**
 * The gazetteer carries its own allocation-free copy of the great-circle formula because
 * [Gazetteer.nearest] evaluates it across a whole bucket. Two copies of a formula is two
 * chances to be on a different Earth, so this pins them together: if someone changes the
 * radius or the formula on either side, this fails.
 */
class GazetteerGeometryTest {

    private val utc = ZoneId.of("UTC")

    private fun core(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
        GeoLocation(lat1, lon1, utc).distanceKmTo(GeoLocation(lat2, lon2, utc))

    @Test
    fun `agrees with GeoLocation distanceKmTo`() {
        val rng = java.util.Random(88_390_3L)
        repeat(2_000) {
            val lat1 = rng.nextDouble() * 180.0 - 90.0
            val lon1 = rng.nextDouble() * 360.0 - 180.0
            val lat2 = rng.nextDouble() * 180.0 - 90.0
            val lon2 = rng.nextDouble() * 360.0 - 180.0
            assertEquals(
                core(lat1, lon1, lat2, lon2),
                Gazetteer.haversineKm(lat1, lon1, lat2, lon2),
                1e-9,
                "gazetteer and core disagree at ($lat1, $lon1) -> ($lat2, $lon2)",
            )
        }
    }

    /**
     * The scale is checked against arithmetic on the sphere's own radius, not against
     * remembered city-to-city figures. A half-recalled "about 550 km" would only ever have
     * tested my memory; `R·π/180` tests the formula.
     */
    @Test
    fun `the scale of the sphere is right`() {
        val kmPerDegree = 6371.0088 * Math.PI / 180.0 // 111.1949...
        // One degree along a meridian, at three latitudes. Independent of latitude.
        assertEquals(kmPerDegree, Gazetteer.haversineKm(0.0, 0.0, 1.0, 0.0), 1e-6)
        assertEquals(kmPerDegree, Gazetteer.haversineKm(45.0, 17.0, 46.0, 17.0), 1e-6)
        assertEquals(kmPerDegree, Gazetteer.haversineKm(-89.0, 100.0, -88.0, 100.0), 1e-6)
        // One degree along the equator is the same; along the 60th parallel it is halved,
        // because cos 60 = 1/2.
        assertEquals(kmPerDegree, Gazetteer.haversineKm(0.0, 0.0, 0.0, 1.0), 1e-6)
        assertEquals(kmPerDegree * 0.5, Gazetteer.haversineKm(60.0, 0.0, 60.0, 1.0), 0.01)
        // A quarter of the way round is a quarter of the circumference.
        assertEquals(0.25 * 2.0 * Math.PI * 6371.0088, Gazetteer.haversineKm(0.0, 0.0, 0.0, 90.0), 1e-6)
    }

    /**
     * The 150 km default radius, expressed in the units the grid reasons about: it is about
     * 1.35 degrees of latitude, which is why [Gazetteer.nearest] scans a couple of cells
     * either side. If this stops holding, the cell-span arithmetic in `nearest` is wrong.
     */
    @Test
    fun `the default radius spans less than two degrees of latitude`() {
        val degrees = 150.0 / (6371.0088 * Math.PI / 180.0)
        assertTrue(degrees > 1.0 && degrees < 2.0, "150 km is $degrees degrees of latitude")
        assertEquals(150.0, Gazetteer.haversineKm(20.0, 88.0, 20.0 + degrees, 88.0), 1e-6)
    }

    @Test
    fun `degenerate and antipodal cases do not blow up`() {
        assertEquals(0.0, Gazetteer.haversineKm(23.4, 88.4, 23.4, 88.4), 0.0)
        val halfCircumference = Math.PI * 6371.0088
        assertEquals(halfCircumference, Gazetteer.haversineKm(0.0, 0.0, 0.0, 180.0), 1e-6)
        assertEquals(halfCircumference, Gazetteer.haversineKm(90.0, 0.0, -90.0, 0.0), 1e-6)
        assertTrue(Gazetteer.haversineKm(0.0, -179.999, 0.0, 179.999).isFinite())
    }

    @Test
    fun `the antimeridian is not a wall for the raw formula`() {
        // 0.002 degrees of longitude at the equator, straddling +-180.
        assertEquals(0.2224, Gazetteer.haversineKm(0.0, 179.999, 0.0, -179.999), 0.001)
    }
}
