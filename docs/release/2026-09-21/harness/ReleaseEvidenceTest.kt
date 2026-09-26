package org.panchang.verify.release

import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.panchang.calc.*
import org.panchang.core.*
import org.panchang.ephemeris.Vsop87Ephemeris
import org.panchang.publication.*
import org.panchang.sampradaya.*
import org.panchang.verify.vaisnava.*

/** Offline measurement collection only. Passing means complete collection, never agreement/approval. */
class ReleaseEvidenceTest {
    private val root = File(System.getProperty("release.root"))
    private val out = File(root, "docs/release/2026-09-21")
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    private fun write(file: File, value: JsonElement) { file.parentFile.mkdirs(); file.writeText(json.encodeToString(JsonElement.serializer(), value)) }

    @Test fun collectCurrentReferenceComparisons() {
        val files = File(out, "comparison-inputs").listFiles()!!.filter { it.name.startsWith("calendar-") && it.extension == "json" }.sortedBy { it.name }
        assertEquals(45, files.size, "The declared 15-site, three-year research sample must not silently shrink")
        for (metadata in files) {
            val meta = json.parseToJsonElement(metadata.readText()).jsonObject
            check(meta["status"]?.jsonPrimitive?.int == 200) { "Unavailable source: ${metadata.name}" }
            val bytes = File(root, meta.getValue("file").jsonPrimitive.content).readBytes()
            check(sha256(bytes) == meta.getValue("sha256").jsonPrimitive.content)
            val (reference, report) = VaisnavaCalendarTxtParser.parseCalendar(bytes.toString(Charsets.UTF_8))
            val year = meta.getValue("year").jsonPrimitive.int
            val city = meta.getValue("cityId").jsonPrimitive.content
            val expectedDates = generateSequence(LocalDate.of(year, 1, 1)) { it.plusDays(1) }.takeWhile { it.year == year }.map { it.toString() }.toList()
            assertEquals(expectedDates, reference.days.map { it.date }, "Reference must cover every civil day exactly once: $city $year")
            check(report.unparsed.isEmpty()) { "Unparsed source lines: $report" }
            // Hyderabad is an explicit research mapping, not a newly supported release location.
            val sourceSite = if (city == "hyderabad") {
                check(reference.header.city == "Hyderabad [India]")
                val h = reference.header
                VaisnavaSiteZones.siteFor(city, h.copy(city = "Mayapur [India]"), year).copy(city = h.city)
            } else VaisnavaSiteZones.siteFor(city, reference.header, year)
            val location = GeoLocation(sourceSite.latitudeDeg, sourceSite.longitudeDeg, ZoneId.of(sourceSite.ianaZone), 0.0)
            val result = CalcEngine().compute(LocationResolver().byCoordinates(location, "release evidence research"), IskconRules(), Scope.Year(year))
            val member = PublicationService.member(result)
            val calculator = PanchangCalculator(Vsop87Ephemeris())
            val index = LunarDayIndex.build(year, ObservanceContext(calculator, location))
            val daily = reference.days.map { d ->
                val date = LocalDate.parse(d.date)
                val span = index.spanAtSunriseOf(date)
                val nak = index.sunriseOf(date)?.let { calculator.nakshatraAt(it) }
                buildJsonObject {
                    put("date", d.date); put("tithi", span?.let { Tithi.nameOf(it.tithiIndex) }); put("paksha", span?.paksha?.name)
                    put("nakshatra", nak?.name); put("month", span?.monthName(MonthReckoning.PURNIMANTA))
                    put("referenceClockMatchesZone", d.parana?.let { (it.clock == "DST") == location.zone.rules.isDaylightSavings(date.atTime(12, 0).atZone(location.zone).toInstant()) })
                }
            }
            val diagnostics = if (year == 2026) {
                val disputeDates = DisputeRegistry.current().disputes.filter {
                    it.place.latitude == location.latitude && it.place.longitude == location.longitude && it.place.timeZone == location.zone.id
                }.flatMap { listOf(LocalDate.parse(it.coverage.from), LocalDate.parse(it.coverage.through)) }
                val dates = (disputeDates + result.ekadashiYear.observances.map { it.date } +
                    listOf(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), LocalDate.of(year, 6, 30)))
                    .flatMap { d -> (-1L..1L).map { d.plusDays(it) } }.distinct().sorted()
                dates.map { date ->
                    val sun = index.sunTimesOf(date)
                    val rise = sun.sunrise.jdUtOrNull!!
                    val arunodaya = rise - 96.0 / 1440.0
                    val spans = index.spans().filter { kotlin.math.abs(it.endJdUt - rise) < 2 || kotlin.math.abs(it.startJdUt - rise) < 2 }
                    buildJsonObject {
                        put("date", date.toString())
                        put("sunrise", location.zonedDateTime(rise).toString())
                        put("arunodaya", location.zonedDateTime(arunodaya).toString())
                        put("sunriseTithi", spans.singleOrNull { rise >= it.startJdUt && rise < it.endJdUt }?.tithiIndex)
                        put("arunodayaTithi", spans.singleOrNull { arunodaya >= it.startJdUt && arunodaya < it.endJdUt }?.tithiIndex)
                        put("sunset", sun.sunset.jdUtOrNull?.let { location.zonedDateTime(it).toString() })
                        put("daylightThirdEnd", sun.daylightDays?.let { location.zonedDateTime(rise + it / 3).toString() })
                        put("spans", JsonArray(spans.map { span -> buildJsonObject {
                            put("index", span.tithiIndex)
                            put("start", location.zonedDateTime(span.startJdUt).toString())
                            put("end", location.zonedDateTime(span.endJdUt).toString())
                            put("endMinusSunriseSeconds", (span.endJdUt - rise) * 86400)
                            put("hariVasaraEnd", location.zonedDateTime(span.startJdUt + (span.endJdUt - span.startJdUt) / 4).toString())
                        } }))
                    }
                }
            } else emptyList()
            write(File(out, "measurements/$city-$year.json"), buildJsonObject {
                put("purpose", "MEASURED_EVIDENCE_NOT_APPROVAL"); put("source", meta)
                put("site", json.encodeToJsonElement(VaisnavaSite.serializer(), sourceSite))
                put("elevationAssumption", "0 metres for comparison; source does not publish elevation")
                put("parseWarnings", JsonArray(report.warnings.map { JsonPrimitive(it) }))
                put("reference", json.encodeToJsonElement(ListSerializer(VaisnavaDay.serializer()), reference.days))
                put("member", RecordJson.encodeToJsonElement(BundleMember.serializer(), member))
                put("calculated", CalcJson.document(result)); put("daily", JsonArray(daily))
                put("ruleDiagnostics", JsonArray(diagnostics))
            })
            println("Collected $city $year: ${reference.days.size} days; no approval created")
        }
    }
}
