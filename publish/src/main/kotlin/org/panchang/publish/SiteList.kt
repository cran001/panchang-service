package org.panchang.publish

/**
 * One site the caller asked for.
 *
 * A malformed line is a [Malformed] spec rather than a dropped line, because the counting
 * invariant this module asserts — `published + skipped == requested` — is only worth anything if
 * "requested" means every line the operator wrote, including the ones that were wrong. A
 * publisher that quietly ignores line 3000 of a site list produces a slightly shorter feed and no
 * evidence, which is how this class of bug survives for a year.
 */
sealed interface SiteSpec {

    /** Stable identifier; becomes a directory and file name in the output. */
    val key: String

    /** The request as the operator wrote it, echoed into `skipped.json`. */
    val query: String

    /** The caller's own coordinates, used verbatim. Preferred: exact, and names its own zone. */
    data class Coordinates(
        override val key: String,
        val title: String,
        val latitude: Double,
        val longitude: Double,
        val zoneId: String,
    ) : SiteSpec {
        override val query: String get() = "--lat $latitude --lon $longitude --tz $zoneId"
    }

    /**
     * A gazetteer record by GeoNames id.
     *
     * By id and not by name: `:calc` refuses an ambiguous name rather than choosing, and a
     * publisher is exactly the caller that cannot answer an interactive "which Raigarh?".
     */
    data class PlaceId(
        override val key: String,
        val geonameId: Int,
        val title: String?,
    ) : SiteSpec {
        override val query: String get() = "--place-id $geonameId"
    }

    /** A line that could not be read. Carried so it can be counted and reported, never dropped. */
    data class Malformed(
        override val key: String,
        val lineNumber: Int,
        val text: String,
        val problem: String,
    ) : SiteSpec {
        override val query: String get() = "line $lineNumber: $text"
    }
}

/**
 * The site-list file format.
 *
 * Whitespace-separated, one site per line, `#` comments and blank lines ignored:
 *
 * ```
 * coords    mayapur   23.4249  88.3883  Asia/Kolkata  Sri Mayapur
 * place-id  mumbai    1275339                         Mumbai
 * ```
 *
 * Deliberately not JSON or YAML: this file is edited by whoever maintains the published set, is
 * reviewed in a diff, and gaining a parser dependency to read six columns would be a poor trade.
 */
object SiteList {

    fun parse(text: String): List<SiteSpec> {
        val specs = ArrayList<SiteSpec>()
        text.lineSequence().forEachIndexed { index, raw ->
            val lineNumber = index + 1
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEachIndexed
            specs += parseLine(line, lineNumber)
        }
        return specs
    }

    private fun parseLine(line: String, lineNumber: Int): SiteSpec {
        val fields = line.split(Regex("""\s+"""))
        val malformed = { problem: String ->
            SiteSpec.Malformed("line-$lineNumber", lineNumber, line, problem)
        }
        if (fields.size < 3) return malformed("expected at least 3 whitespace-separated fields")

        val key = fields[1]
        if (!LegacyFeed.SITE_KEY.matches(key)) {
            return malformed(
                "site key '$key' is not lowercase letters, digits and dashes; it becomes a " +
                    "directory and a URL path segment, so anything else is not portable",
            )
        }

        return when (fields[0]) {
            "coords" -> {
                if (fields.size < 6) return malformed("coords needs: coords <key> <lat> <lon> <tz> <title>")
                val lat = fields[2].toDoubleOrNull()
                    ?: return malformed("latitude '${fields[2]}' is not a number")
                val lon = fields[3].toDoubleOrNull()
                    ?: return malformed("longitude '${fields[3]}' is not a number")
                SiteSpec.Coordinates(
                    key = key,
                    title = fields.drop(5).joinToString(" "),
                    latitude = lat,
                    longitude = lon,
                    zoneId = fields[4],
                )
            }

            "place-id" -> {
                val id = fields[2].toIntOrNull()
                    ?: return malformed("GeoNames id '${fields[2]}' is not an integer")
                SiteSpec.PlaceId(
                    key = key,
                    geonameId = id,
                    title = fields.drop(3).joinToString(" ").takeIf { it.isNotBlank() },
                )
            }

            else -> malformed("unknown site kind '${fields[0]}'; expected 'coords' or 'place-id'")
        }
    }
}
