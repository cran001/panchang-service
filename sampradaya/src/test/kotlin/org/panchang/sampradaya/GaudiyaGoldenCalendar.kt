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

    fun mayapur2026(): List<GoldenDay> = load("vaisnavacalendar-mayapur-2026.json")

    fun load(fileName: String): List<GoldenDay> {
        val file = File(goldenDir(), fileName)
        check(file.isFile) { "golden calendar file missing: $file" }
        val doc = json.decodeFromString<GoldenDocument>(file.readText())
        check(doc.records.isNotEmpty()) { "golden calendar $fileName has no records" }
        return doc.records
    }

    @Serializable
    private data class GoldenDocument(
        val writtenAtUtc: String? = null,
        val records: List<GoldenDay> = emptyList(),
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
