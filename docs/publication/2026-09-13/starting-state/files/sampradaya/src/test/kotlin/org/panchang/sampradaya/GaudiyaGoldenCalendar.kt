package org.panchang.sampradaya

import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The harvested Gaudiya calendar used as ground truth, loaded straight from `verify/golden`.
 *
 * These types mirror `org.panchang.verify.vaisnava.VaisnavaDay` field-for-field but are
 * declared here rather than imported: `:verify` depends on `:sampradaya`, so importing them
 * would create a dependency cycle. The duplication is two dozen lines and it keeps the
 * direction of the build graph honest — the module under test cannot reach into the module
 * that harvests its oracle.
 *
 * Nothing here normalises the source's vocabulary. `tithi` stays "Ekadasi (suitable for
 * fasting)" and `startBasis` stays "1/4 of tithi", because a later disagreement has to be
 * traceable to a real difference rather than to a translation this file performed.
 */
object GaudiyaGoldenCalendar {

    /**
     * Coordinates as the source itself prints them in its header: `23N25 88E23`, i.e.
     * 23°25′N 88°23′E. Deliberately *not* `ReferenceCities.MAYAPUR` (23.4242, 88.3888).
     *
     * The two differ by about 0.006° of longitude, which is 1.5 seconds of sunrise — well
     * below the one-minute resolution the calendar prints. Using the source's own datum
     * removes even that as a candidate explanation when a printed time disagrees, so any
     * remaining disagreement is about the rules, which is what this suite is measuring.
     */
    const val MAYAPUR_LATITUDE: Double = 23.0 + 25.0 / 60.0
    const val MAYAPUR_LONGITUDE: Double = 88.0 + 23.0 / 60.0
    const val MAYAPUR_ZONE: String = "Asia/Kolkata"

    private val json = Json { ignoreUnknownKeys = true }

    /** Directory supplied by the build; see the `panchang.golden.dir` wiring in the build file. */
    fun goldenDir(): File {
        val configured = System.getProperty("panchang.golden.dir")
        checkNotNull(configured) {
            "system property 'panchang.golden.dir' is not set. The conformance suite reads its " +
                "oracle out of verify/golden; run it through Gradle, which sets the property."
        }
        val dir = File(configured)
        check(dir.isDirectory) { "golden directory does not exist: $dir" }
        return dir
    }

    /** Delegates to [load] so the Mayapur suites keep working unchanged. */
    fun mayapur2026(): List<GoldenDay> = load("mayapur", 2026)

    /**
     * One city's harvested calendar, e.g. `load("delhi", 2026)`.
     *
     * The city id is our reference-grid id, not the publisher's file stem: the source
     * files Mumbai as "Bombay [India]" and Moscow as "Moskva [Russia]", and the printed
     * name it used is preserved in the file's own site block rather than in its name.
     */
    fun load(cityId: String, year: Int): List<GoldenDay> =
        load("vaisnavacalendar-$cityId-$year.json")

    fun load(fileName: String): List<GoldenDay> = document(fileName).records

    /**
     * The site the golden file describes, or null if it does not say.
     *
     * Nullable rather than defaulted. A location-general test needs the coordinates the
     * reference was actually computed at, and quietly substituting the grid's own
     * coordinates for a file that declines to state them would reintroduce exactly the
     * datum-versus-rule ambiguity the site block exists to remove. The Mayapur file
     * predates the block and has none; [MAYAPUR_LATITUDE] and friends cover that case.
     */
    fun site(cityId: String, year: Int): GoldenSite? =
        document("vaisnavacalendar-$cityId-$year.json").site

    private fun document(fileName: String): GoldenDocument {
        val file = File(goldenDir(), fileName)
        check(file.isFile) { "golden calendar file missing: $file" }
        val doc = json.decodeFromString<GoldenDocument>(file.readText())
        check(doc.records.isNotEmpty()) { "golden calendar $fileName has no records" }
        return doc
    }

    @Serializable
    private data class GoldenDocument(
        val writtenAtUtc: String? = null,
        val site: GoldenSite? = null,
        val records: List<GoldenDay> = emptyList(),
    )

    /**
     * Where the golden file's times were computed, as the source itself printed it.
     *
     * [latitudeDeg]/[longitudeDeg] come from the calendar's own header (Delhi is printed
     * as `28N40 77E13`, i.e. 28.667/77.217, while our grid carries 28.6139/77.2090).
     * Evaluating a reference at coordinates it was not computed for makes a datum
     * disagreement indistinguishable from a rule disagreement once the time has been
     * rounded to the printed minute.
     */
    @Serializable
    data class GoldenSite(
        val city: String,
        val coordinates: String,
        val utcOffset: String,
        val generator: String? = null,
        val cityId: String = "",
        val latitudeDeg: Double,
        val longitudeDeg: Double,
        val ianaZone: String,
    )

    @Serializable
    data class GoldenDay(
        @SerialName("date") val dateText: String,
        val weekdayAbbrev: String = "",
        val tithi: String = "",
        val paksa: String = "",
        val naksatra: String = "",
        val fastMarked: Boolean = false,
        val masa: String? = null,
        val gaurabda: Int? = null,
        val events: List<String> = emptyList(),
        val fastingFor: String? = null,
        val parana: GoldenParana? = null,
    ) {
        val date: LocalDate get() = LocalDate.parse(dateText)

        /** The tithi name with the source's parenthetical fasting marker stripped. */
        val tithiName: String get() = tithi.substringBefore(" (").trim()
    }

    @Serializable
    data class GoldenParana(
        val start: String,
        val startBasis: String,
        val end: String? = null,
        val endBasis: String? = null,
        val clock: String = "LT",
    ) {
        val startTime: LocalTime get() = LocalTime.parse(start)
        val endTime: LocalTime? get() = end?.let { LocalTime.parse(it) }
    }
}
