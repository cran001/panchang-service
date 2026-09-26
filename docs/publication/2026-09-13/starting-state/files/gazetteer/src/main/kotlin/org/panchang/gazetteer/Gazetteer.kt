package org.panchang.gazetteer

import java.io.BufferedReader
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * An in-memory place table with a name index and a nearest-point search.
 *
 * ## What this module is for
 *
 * The Android app this service replaces resolved a user's position by calling a
 * third-party host for a nearest-city list and then **computing the panchang at the city's
 * coordinates instead of the user's**. That host is now dead, which is the visible failure;
 * the invisible one is worse. Snapping moved sunrise by as much as several minutes, and a
 * tithi boundary that lands within those minutes of sunrise decides which civil day a fast
 * is kept on. A user 60 km from the city whose name they were shown could be given the
 * wrong Ekadasi date and have no way to tell.
 *
 * So this module holds names and points, and nothing here ever hands a caller a coordinate
 * to compute with. See [nearest].
 *
 * ## Attribution
 *
 * The table is derived from GeoNames under CC BY 4.0. [ATTRIBUTION] must be displayed
 * wherever this data is surfaced; a test asserts it survives into the shipped artifact.
 */
class Gazetteer internal constructor(
    /** Every record, in the order the derived table lists them. */
    val places: List<Place>,
    /** The `#`-prefixed provenance lines from the head of the derived table, `#` stripped. */
    val provenance: List<String>,
) {
    /** Normalised name (native and ASCII forms both) to the records carrying it. */
    private val byNormalizedName: Map<String, List<Place>> = buildMap<String, MutableList<Place>> {
        for (place in places) {
            for (form in setOf(normalizePlaceName(place.name), normalizePlaceName(place.asciiName))) {
                if (form.isNotEmpty()) getOrPut(form) { ArrayList(1) }.add(place)
            }
        }
    }.mapValues { (_, v) -> v.sortedWith(POPULOUS_FIRST) }

    /**
     * One-degree-by-one-degree bucket grid, keyed by [cellKey].
     *
     * Deliberately not a k-d tree. A grid is about twenty lines whose worst case can be
     * *stated and then asserted* — see `GazetteerTest`, which pins the largest cell's
     * occupancy — whereas a hand-rolled tree is a few hundred lines of balancing and
     * backtracking that nobody on this project would be able to bound or review. At 35k
     * records the grid's constant factor is irrelevant and its auditability is not.
     */
    private val grid: Map<Int, List<Place>> = places.groupBy { cellKey(it.latitude, it.longitude) }

    /** The largest number of records in any single one-degree cell. A bound on [nearest]'s scan. */
    val maxCellOccupancy: Int get() = grid.values.maxOf { it.size }

    /** Number of occupied one-degree cells. */
    val occupiedCellCount: Int get() = grid.size

    /**
     * Every record whose name matches [query] exactly after [normalizePlaceName], most
     * populous first. Empty when nothing matches — never a "closest guess".
     *
     * Both the native-script and ASCII forms of each name are indexed, so `Mahārāshtra`,
     * `Maharashtra` and `maharashtra` all reach the same records.
     */
    fun byName(query: String): List<Place> = byNormalizedName[normalizePlaceName(query)] ?: emptyList()

    /**
     * Records whose normalised name starts with [query], most populous first, capped at
     * [limit]. For typeahead. A blank query matches nothing rather than everything.
     */
    fun startingWith(query: String, limit: Int = 20): List<Place> {
        require(limit > 0) { "limit must be positive, was $limit" }
        val key = normalizePlaceName(query)
        if (key.isEmpty()) return emptyList()
        return byNormalizedName.asSequence()
            .filter { it.key.startsWith(key) }
            .flatMap { it.value }
            .distinct()
            .sortedWith(POPULOUS_FIRST)
            .take(limit)
            .toList()
    }

    /** The record with this GeoNames id, or null. */
    fun byGeonameId(id: Int): Place? = places.firstOrNull { it.geonameId == id }

    /**
     * The nearest record to ([latitude], [longitude]) within [withinKm], or **null**.
     *
     * ### Read this before changing the radius
     *
     * Returning null is the feature. Snapping a computation to the coordinates of whatever
     * record happens to be closest is the exact defect this module exists to remove: the
     * app that came before called a nearest-city service and then computed sunrise at the
     * city, silently relocating the user by up to hundreds of kilometres and shifting the
     * tithi-at-sunrise test that decides a fasting date. Nobody saw it happen, because a
     * wrong sunrise still looks like a sunrise.
     *
     * The contract, therefore:
     *  - **This result is a label, not a position.** Use it to tell the user where they
     *    are. Compute with the coordinates *they* gave you — `GeoLocation(userLat, userLon,
     *    place.zone)`, never `place.toGeoLocation()` — so the only thing borrowed from the
     *    record is its time zone, which is a property of the region and not of the point.
     *  - **Beyond [withinKm] the honest answer is "I don't know".** Mid-ocean, or in a
     *    sparsely gazetteered region, the nearest record can be 900 km away; naming it
     *    would be a fabrication, and a caller that then computed at it would be wrong by
     *    an hour of longitude. Widening or removing this radius reintroduces the bug.
     *
     * @param withinKm great-circle cut-off in kilometres. The default of 150 km is roughly
     *   the scale at which a place name stops being a truthful description of where someone
     *   is. Must be positive and finite.
     */
    fun nearest(latitude: Double, longitude: Double, withinKm: Double = 150.0): Place? {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "latitude must be a finite value in [-90, 90], was $latitude"
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "longitude must be a finite value in [-180, 180], was $longitude"
        }
        require(withinKm.isFinite() && withinKm > 0.0) {
            "withinKm must be a finite positive distance, was $withinKm"
        }

        // How many one-degree cells can hold a point within withinKm? Latitude is easy: one
        // degree is always ~111.2 km. Longitude shrinks with cos(lat), so near the poles the
        // band widens without limit — clamp it to "every longitude cell" rather than letting
        // the span overflow. +1 covers the caller sitting anywhere inside its own cell.
        val latCells = ceil(withinKm / KM_PER_DEG_LAT).toInt() + 1
        val cosLat = cos(abs(latitude) * DEG).coerceAtLeast(1e-9)
        val lonSpanDeg = withinKm / (KM_PER_DEG_LAT * cosLat)
        val lonCells = if (lonSpanDeg >= 180.0) 180 else ceil(lonSpanDeg).toInt() + 1

        val latIdx = latIndex(latitude)
        val lonIdx = lonIndex(longitude)

        var best: Place? = null
        var bestKm = Double.MAX_VALUE
        for (dLat in -latCells..latCells) {
            val li = latIdx + dLat
            if (li < 0 || li >= LAT_CELLS) continue
            for (dLon in -lonCells..lonCells) {
                // Longitude wraps: the cell east of 179°E is 180°W, and a caller at 179.9°E
                // is 20 km from a place at 179.9°W. Modular arithmetic, not a clamp.
                val gi = Math.floorMod(lonIdx + dLon, LON_CELLS)
                val bucket = grid[li * LON_CELLS + gi] ?: continue
                for (place in bucket) {
                    val km = haversineKm(latitude, longitude, place.latitude, place.longitude)
                    if (km <= withinKm && km < bestKm) {
                        bestKm = km
                        best = place
                    }
                }
            }
        }
        return best
    }

    companion object {
        /**
         * The attribution CC BY 4.0 requires, and the reason this source was chosen.
         *
         * CC BY is attribution-only: no share-alike, no network clause. That mattered.
         * The obvious alternatives carry copyleft that reaches through a network service,
         * which would have obliged us to publish the sampradaya rule set — material that is
         * not ours to relicense. The cost of that choice is precisely this string, so it is
         * not decorative and it is not optional: it ships, and `GazetteerProvenanceTest`
         * fails if it stops appearing in the derived table.
         */
        const val ATTRIBUTION: String =
            "Place data © GeoNames (https://www.geonames.org/), " +
                "used under CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/)."

        /** Classpath location of the derived table. */
        const val RESOURCE_PATH: String = "/org/panchang/gazetteer/gazetteer-v1.tsv"

        /** Column order of the derived table's data lines. */
        internal val COLUMNS = listOf(
            "geonameid", "kind", "name", "asciiname",
            "country", "admin1", "population", "latitude", "longitude", "timezone",
        )

        /** The shipped gazetteer, parsed once on first use. */
        val default: Gazetteer by lazy { load() }

        /** Parses the gazetteer from the classpath. */
        fun load(): Gazetteer {
            val stream = Gazetteer::class.java.getResourceAsStream(RESOURCE_PATH)
                ?: error("gazetteer resource $RESOURCE_PATH is missing from the classpath")
            return stream.bufferedReader(Charsets.UTF_8).use { parse(it) }
        }

        /**
         * Parses the TSV form. `#`-prefixed lines are provenance and are retained verbatim
         * (minus the marker) so callers and tests can read the source hashes and counts back
         * out of the artifact itself.
         */
        fun parse(reader: BufferedReader): Gazetteer {
            val provenance = ArrayList<String>()
            val places = ArrayList<Place>()
            val zones = HashMap<String, ZoneId>()
            var lineNo = 0
            reader.forEachLine { line ->
                lineNo++
                when {
                    line.isEmpty() -> Unit
                    line.startsWith("#") -> provenance.add(line.removePrefix("#").trim())
                    else -> {
                        val f = line.split('\t')
                        require(f.size == COLUMNS.size) {
                            "line $lineNo has ${f.size} fields, expected ${COLUMNS.size}: $line"
                        }
                        places.add(
                            Place(
                                geonameId = f[0].toInt(),
                                kind = PlaceKind.valueOf(f[1]),
                                name = f[2],
                                asciiName = f[3],
                                country = f[4],
                                admin1 = f[5],
                                population = f[6].toLong(),
                                latitude = f[7].toDouble(),
                                longitude = f[8].toDouble(),
                                // Zone ids repeat tens of thousands of times over ~350 distinct
                                // values; intern them so the table holds 350 ZoneIds, not 35 000.
                                zone = zones.getOrPut(f[9]) { ZoneId.of(f[9]) },
                            ),
                        )
                    }
                }
            }
            return Gazetteer(places, provenance)
        }

        private const val LAT_CELLS = 180
        private const val LON_CELLS = 360
        private const val DEG = Math.PI / 180.0
        private const val EARTH_RADIUS_KM = 6371.0088

        /** Mean kilometres per degree of latitude on the sphere used here. */
        private const val KM_PER_DEG_LAT = EARTH_RADIUS_KM * DEG

        private val POPULOUS_FIRST =
            compareByDescending<Place> { it.population }.thenBy { it.geonameId }

        private fun latIndex(lat: Double): Int =
            (floor(lat).toInt() + 90).coerceIn(0, LAT_CELLS - 1)

        private fun lonIndex(lon: Double): Int =
            Math.floorMod(floor(lon).toInt() + 180, LON_CELLS)

        internal fun cellKey(lat: Double, lon: Double): Int =
            latIndex(lat) * LON_CELLS + lonIndex(lon)

        /**
         * Great-circle distance in kilometres on a sphere. Same formula and same radius as
         * `GeoLocation.distanceKmTo`; kept here as a free function taking raw doubles because
         * [nearest] evaluates it across a whole bucket and constructing a validated
         * `GeoLocation` per candidate would dominate the cost. `GazetteerGeometryTest` asserts
         * the two agree, so this cannot quietly drift into a different Earth.
         */
        internal fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = (lat2 - lat1) * DEG
            val dLon = (lon2 - lon1) * DEG
            val sinLat = sin(dLat / 2)
            val sinLon = sin(dLon / 2)
            val a = sinLat * sinLat + cos(lat1 * DEG) * cos(lat2 * DEG) * sinLon * sinLon
            return 2.0 * EARTH_RADIUS_KM * asin(sqrt(a).coerceAtMost(1.0))
        }
    }
}
