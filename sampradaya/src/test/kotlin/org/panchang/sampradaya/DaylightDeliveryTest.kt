package org.panchang.sampradaya

import java.io.File
import java.time.*
import kotlinx.serialization.json.*
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.panchang.core.GeoLocation
import org.panchang.core.PanchangCalculator
import org.panchang.ephemeris.Vsop87Ephemeris

class DaylightDeliveryTest {
    @Test
    fun `Reykjavik June 26 uses the independently evidenced following sunset cap`() {
        val root = File(System.getProperty("panchang.golden.dir")).parentFile.parentFile
        val evidence = File(root, "docs/audits/2026-09-12/new-astronomy")
        fun record(name: String) = Json.parseToJsonElement(File(evidence, name).readText())
            .jsonObject["records"]!!.jsonArray.single().jsonObject
        val day = record("usno-reykjavik-2026-06-26.json")
        val next = record("retry-usno-reykjavik-2026-06-26.json")
        fun instant(record: JsonObject, key: String): Instant = LocalDate.parse(record["date"]!!.jsonPrimitive.content)
            .atTime(LocalTime.parse(record[key]!!.jsonArray.single().jsonPrimitive.content)).toInstant(ZoneOffset.UTC)
        val rise = instant(day, "sunRises")
        val set = instant(next, "sunSets")
        val expectedThird = rise.plusSeconds(Duration.between(rise, set).seconds / 3)
        assertEquals(Instant.parse("2026-06-26T09:59:20Z"), expectedThird)
        val location = GeoLocation.of(64.1466, -21.9426, "Atlantic/Reykjavik")
        val calculator = PanchangCalculator(Vsop87Ephemeris())
        val decision = IskconRules().ekadashiObservances(2026, ObservanceContext(calculator, location))
            .single { it.date == LocalDate.of(2026, 6, 25) }
        val window = decision.parana!!
        assertEquals(LocalDate.of(2026, 6, 26), window.date)
        val actualEnd = location.zonedDateTime(window.endJdUt).toInstant()
        // Both USNO endpoints are minute-rounded. A weighted third preserves the +/-30s band.
        assertTrue(abs(Duration.between(expectedThird, actualEnd).toMillis()) <= 30_000,
            "USNO daylight third=$expectedThird; actual=$actualEnd")
        assertEquals(ParanaBoundReason.ONE_THIRD_DAYLIGHT, window.endReason)
    }
}
