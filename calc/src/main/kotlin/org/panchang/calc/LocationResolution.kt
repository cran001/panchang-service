package org.panchang.calc

import org.panchang.core.GeoLocation
import org.panchang.gazetteer.Gazetteer
import org.panchang.gazetteer.Place
import org.panchang.gazetteer.normalizePlaceName
import org.panchang.wire.SiteAcceptance
import org.panchang.wire.acceptSite

/**
 * Where the coordinates that were actually computed with came from.
 *
 * Reported in every output, human and JSON alike. A user who believes they were given their own
 * district's sunrise has no way to check that belief unless the answer says which point it used,
 * and the app this project replaces got exactly this wrong: it showed a nearby city's name and
 * silently computed at the city's coordinates instead of the user's.
 */
enum class CoordinateSource {
    /** `--lat/--lon/--tz`. The caller's own point, used verbatim. */
    CALLER_COORDINATES,

    /**
     * `--place`. The gazetteer record's *representative* point.
     *
     * For a district that is somewhere near the administrative centre and can sit tens of
     * kilometres from where the user is standing. Sunrise moves about four minutes per degree of
     * longitude, and a tithi boundary landing inside that window decides which civil day a fast
     * is kept on, so this is a real difference and not a rounding one.
     */
    GAZETTEER_PLACE_POINT,
}

/**
 * A resolved site: the point, where it came from, and everything the user needs to check it.
 *
 * @param place the gazetteer record the point came from, when [source] is
 *   [CoordinateSource.GAZETTEER_PLACE_POINT].
 * @param nearestLabel a nearby gazetteer record when [source] is
 *   [CoordinateSource.CALLER_COORDINATES] — a **label only**. Nothing is computed at it.
 * @param notes sentences the caller must show. Never advisory formatting: each one exists
 *   because something about this resolution could otherwise be misread.
 */
data class ResolvedSite(
    val location: GeoLocation,
    val source: CoordinateSource,
    val query: String,
    val place: Place? = null,
    val nearestLabel: NearestLabel? = null,
    val notes: List<String> = emptyList(),
)

/**
 * A place name for a set of coordinates, and how far away that place actually is.
 *
 * Carries the distance because a label 140 km away is a different kind of statement from one
 * 2 km away, and a user shown only the name cannot tell which they got. Never used as a position.
 */
data class NearestLabel(val place: Place, val distanceKm: Double)

/** Why a `--place` could not be turned into a single site. Each case exits non-zero. */
sealed interface PlaceFailure {

    /** The message to print. Complete: the caller adds nothing to it. */
    val message: String

    /** More than one record carries this exact name. We refuse to choose. */
    data class Ambiguous(val query: String, val candidates: List<Place>) : PlaceFailure {
        override val message: String = buildString {
            append("'").append(query).append("' matches ").append(candidates.size)
            append(" records in the gazetteer, and choosing one of them is not this program's ")
            append("call. India has two distinct Raigarh districts in two different states; a ")
            append("front door that silently picked the more populous one would give a devotee ")
            append("in the other state a sunrise from 900 km away and no way to notice.\n\n")
            append("Candidates:\n")
            candidates.forEach { append("  ").append(describe(it)).append('\n') }
            append("\nRe-run naming one of them exactly: --place-id <geonameId>.")
        }
    }

    /** No record carries this name, and nothing else is going to be substituted for it. */
    data class NotFound(
        val query: String,
        val hint: String?,
        val prefixMatches: List<Place>,
    ) : PlaceFailure {
        override val message: String = buildString {
            append("No gazetteer record is named '").append(query).append("'.")
            if (hint != null) append("\n\n").append(hint)
            if (prefixMatches.isNotEmpty()) {
                append("\n\nRecords whose name begins with '").append(query).append("':\n")
                prefixMatches.forEach { append("  ").append(describe(it)).append('\n') }
            }
            append(
                "\nThis program does not fuzzy-match place names. A wrong city is worse than no " +
                    "city: it produces a complete, plausible, authoritative-looking answer for " +
                    "somewhere the user has never been. Pass an exact name, a --place-id, or " +
                    "your own --lat/--lon/--tz.",
            )
        }
    }

    /** A `--place-id` that is not in the table. */
    data class UnknownId(val id: Int) : PlaceFailure {
        override val message: String =
            "No gazetteer record has GeoNames id $id. Ids are printed beside every candidate " +
                "when a name is ambiguous; they are not invented here."
    }

    companion object {
        internal fun describe(p: Place): String =
            "${p.name} - ${p.kind}, ${p.admin1}, ${p.country}, " +
                "population ${p.population}, ${"%.5f".format(p.latitude)}, " +
                "${"%.5f".format(p.longitude)}, ${p.zone.id}, geonameId ${p.geonameId}"
    }
}

/**
 * Turns the location arguments into one site, or into a refusal that says why.
 *
 * ## The alias decision, stated plainly
 *
 * The gazetteer's ingest deliberately drops GeoNames' `alternatenames` column, so `--place
 * Bombay` finds nothing — while the reference calendars this project is validated against print
 * "Bombay" and "Moskva" as their own city names. The brief allowed two answers: fail helpfully,
 * or carry a small alias table here covering the ten reference cities.
 *
 * **This module fails helpfully. It does not alias.** Three reasons, in order of weight:
 *
 * 1. `Calcutta` already resolves — to a town in South Africa, unambiguously, with a real
 *    GeoNames id. An alias table mapping `Calcutta → Kolkata` would therefore have to *override*
 *    a genuine exact match on the strength of a guess about what the user meant. That is a fuzzy
 *    match wearing a lookup table's clothes, and it is the one thing the brief forbids outright.
 * 2. A name table in `:calc` is a second name-resolution path that `:api` and `:publish` do not
 *    share, so the same `--place Bombay` would mean one thing at the CLI and another at the HTTP
 *    door. Keeping one resolution path is the same argument that put the renderer in `:wire`.
 * 3. Ten hand-typed aliases imply a coverage the table would not have. `Madras`, `Poona`,
 *    `Bangalore` and `Trivandrum` would still fail, and they would fail *differently* from
 *    `Bombay`, which teaches the user a rule that is not true. Historical names are real data;
 *    they belong in the gazetteer's ingest, read from GeoNames' own `alternatenames` column,
 *    where all three front doors get them at once. That is a `:gazetteer` change, not a `:calc`
 *    workaround, and it is flagged as such here rather than papered over.
 *
 * What this module *does* carry is [HISTORICAL_NAME_HINTS]: a table of *messages*, not of
 * coordinates. A hint never resolves anything. The user must retype the modern name, so no
 * computation can ever happen at a place they did not explicitly ask for.
 */
class LocationResolver(private val gazetteer: Gazetteer = Gazetteer.default) {

    /** Resolve `--place`. Left as a `Result`-shaped pair so the CLI owns the exit. */
    fun byName(query: String): Pair<ResolvedSite?, PlaceFailure?> {
        val matches = gazetteer.byName(query)
        return when {
            matches.size == 1 -> fromPlace(matches.single(), query) to null
            matches.size > 1 -> null to PlaceFailure.Ambiguous(query, matches)
            else -> null to PlaceFailure.NotFound(
                query = query,
                hint = hintFor(query),
                prefixMatches = gazetteer.startingWith(query, limit = 8),
            )
        }
    }

    /** Resolve `--place-id`. Unambiguous by construction. */
    fun byId(id: Int): Pair<ResolvedSite?, PlaceFailure?> {
        val place = gazetteer.byGeonameId(id) ?: return null to PlaceFailure.UnknownId(id)
        return fromPlace(place, "--place-id $id") to null
    }

    /** The caller's own coordinates, used verbatim; the gazetteer is consulted only for a label. */
    fun byCoordinates(location: GeoLocation, query: String): ResolvedSite {
        val near = gazetteer.nearest(location.latitude, location.longitude)
        val label = near?.let {
            NearestLabel(it, location.distanceKmTo(it.toGeoLocation()))
        }
        val notes = buildList {
            add(
                "Computed at the coordinates you supplied, not at any place record. The place " +
                    "name below, if any, is a label only.",
            )
            if (label == null) {
                add(
                    "No gazetteer record lies within 150 km of this point, so this program will " +
                        "not put a place name on it. Naming a record 900 km away would be a " +
                        "fabrication; the computation itself is unaffected.",
                )
            }
        }
        return ResolvedSite(
            location = location,
            source = CoordinateSource.CALLER_COORDINATES,
            query = query,
            nearestLabel = label,
            notes = notes,
        )
    }

    private fun fromPlace(place: Place, query: String): ResolvedSite {
        val notes = buildList {
            add(
                "Computed at the gazetteer's representative point for ${place.name}, which is " +
                    "not your position. For a district that point sits near the administrative " +
                    "centre and can be tens of kilometres away; four minutes of sunrise per " +
                    "degree of longitude is enough to move a fast onto a different civil day. " +
                    "Pass --lat/--lon/--tz for your own site.",
            )
            collisionNoteFor(query, place)?.let(::add)
        }
        return ResolvedSite(
            location = place.toGeoLocation(),
            source = CoordinateSource.GAZETTEER_PLACE_POINT,
            query = query,
            place = place,
            notes = notes,
        )
    }

    /** The site as `:wire` judges it. `:calc` states no limit of its own. */
    fun accept(site: ResolvedSite): SiteAcceptance = acceptSite(site.location)

    companion object {

        /**
         * Historical and transliterated names that resolve to **nothing**, and what to say.
         *
         * A message table. Nothing here is ever substituted for what the user typed. Every entry
         * is a name that a reference calendar prints or that a user of that generation would
         * reasonably type, checked against the shipped table as producing zero matches — if one
         * of these ever starts resolving, the entry becomes unreachable rather than harmful.
         */
        val HISTORICAL_NAME_HINTS: Map<String, String> = listOf(
            "bombay" to
                "Bombay was renamed Mumbai in 1995. The vaisnavacalendar.info calendars still " +
                    "file the city under the old name, which is why you may have typed it. This " +
                    "gazetteer carries only current GeoNames names: try --place Mumbai (note " +
                    "that it matches both the city and the district, so you will be asked to " +
                    "pick one).",
            "moskva" to
                "Moskva is the Russian name; GeoNames files the city as Moscow. Try --place " +
                    "Moscow - it matches Moscow, Russia and Moscow, Idaho, so you will be asked " +
                    "to pick one.",
            "madras" to "Madras was renamed Chennai in 1996. Try --place Chennai.",
            "bangalore" to "Bangalore was renamed Bengaluru in 2014. Try --place Bengaluru.",
            "poona" to "Poona is the older spelling of Pune. Try --place Pune.",
            "new york" to
                "GeoNames files the city as 'New York City'. Try --place \"New York City\".",
            "mayapur" to
                "Mayapur has no record of its own in this gazetteer: GeoNames' populated-place " +
                    "table starts at 15 000 inhabitants and Mayapur is below it. The adjoining " +
                    "town is Navadwip (--place Navadwip), about 3 km away - near enough that " +
                    "sunrise differs by a few seconds, which is not near enough to be called " +
                    "Mayapur. If you need Mayapur itself, pass its coordinates with " +
                    "--lat/--lon/--tz; this program will not put someone else's town under that " +
                    "name.",
        ).associate { (k, v) -> normalizePlaceName(k) to v }

        /**
         * Names that resolve, but to a record most people asking for them do not mean.
         *
         * The resolution is not changed — the record is a real place and the match is exact.
         * A note is added so the user sees the collision instead of discovering it from a
         * sunrise that is four hours out.
         */
        val COLLISION_NOTES: Map<String, Pair<String, String>> = mapOf(
            normalizePlaceName("calcutta") to (
                "ZA" to
                    "'Calcutta' matched a town in South Africa, which is a real place and an " +
                        "exact match. Kolkata, India carried the name Calcutta until 2001; if " +
                        "that is what you meant, use --place Kolkata."
                ),
        )

        private fun collisionNoteFor(query: String, place: Place): String? {
            val (country, note) = COLLISION_NOTES[normalizePlaceName(query)] ?: return null
            return note.takeIf { place.country == country }
        }

        private fun hintFor(query: String): String? =
            HISTORICAL_NAME_HINTS[normalizePlaceName(query)]
    }
}
