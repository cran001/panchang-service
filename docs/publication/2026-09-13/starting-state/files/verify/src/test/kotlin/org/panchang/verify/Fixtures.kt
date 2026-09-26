package org.panchang.verify

/**
 * Committed fixture files.
 *
 * PROVENANCE OF THESE FIXTURES — keep this table accurate.
 *
 * | file                                          | origin                                  |
 * |-----------------------------------------------|-----------------------------------------|
 * | horizons_moon_geocentric_2026-04-14.txt       | real capture, ssd.jpl.nasa.gov, 2026-08-01 |
 * | horizons_sun_geocentric_2026-04-14.txt        | real capture, ssd.jpl.nasa.gov, 2026-08-01 |
 * | usno_oneday_mayapur_2026-04-14.json           | real capture, aa.usno.navy.mil, 2026-08-01 |
 * | usno_oneday_svalbard_2026-06-21.json          | real capture, aa.usno.navy.mil, 2026-08-01 |
 * | vaisnavacalendar_mayapur_2026_head.txt        | real capture (lines 1-45 verbatim), 2026-08-01 |
 * | vaisnavacalendar_mayapur_2026_jun_slice.txt   | real capture (lines 312-346 verbatim), 2026-08-01 |
 * | vaisnavacalendar_auckland_2026_head.txt       | real capture (lines 1-42 verbatim), 2026-08-01 |
 * | vaisnavacalendar_auckland_2026_sep_slice.txt  | real capture (lines 463-514 verbatim), 2026-08-01 |
 * | vaisnavacalendar_notes_tail.txt               | real capture (trailer, verbatim), 2026-08-01 |
 * | iskconmumbai_ekadasi_fragment.html            | real capture (fragment), 2026-08-01     |
 * | drikpanchang_day_panchang_structure.html      | SYNTHETIC. Structure real, values placeholder. |
 *
 * The Drik fixture is synthetic on purpose: drikpanchang.com is a comparison target whose
 * data we do not redistribute, and committing a captured page would be redistributing it.
 * Its test therefore proves structural recovery only, never a numerical value.
 */
object Fixtures {

    fun text(name: String): String = bytes(name).toString(Charsets.UTF_8)

    /**
     * The Gaurabda Calendar text exports are not UTF-8 clean in every city file, and
     * mis-decoding a byte would corrupt a naksatra name rather than fail. ISO-8859-1
     * round-trips every byte, which is what the harvester uses too.
     */
    fun latin1(name: String): String = bytes(name).toString(Charsets.ISO_8859_1)

    fun bytes(name: String): ByteArray =
        Fixtures::class.java.getResourceAsStream("/fixtures/$name")?.readBytes()
            ?: error(
                "Missing fixture 'fixtures/$name'. Fixtures are captured by the harvest CLI; " +
                    "see Fixtures.kt for the provenance table.",
            )
}
