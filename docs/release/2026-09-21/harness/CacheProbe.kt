package org.panchang.releaseprobe

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.time.ZoneId
import kotlin.system.measureNanoTime
import kotlinx.serialization.json.*
import org.panchang.calc.*
import org.panchang.core.GeoLocation
import org.panchang.publication.*

/** Local measurement executable, outside production source sets. No authority or expected dates. */
object CacheProbe {
    @JvmStatic fun main(args: Array<String>) {
        val root = Path.of(args[0])
        Files.createDirectories(root)
        val site = LocationResolver().byCoordinates(GeoLocation(23.416666666666668, 88.38333333333334, ZoneId.of("Asia/Kolkata")), "cache verification")
        val rules = Sampradayas["iskcon"]!!
        val cache = PersistentCalculationCache(root.resolve("cache"), YearCalculation { s, r, year ->
            Files.writeString(root.resolve("computations.log"), "computed\n", CREATE, APPEND)
            CalcEngine().compute(s, r, Scope.Year(year))
        })
        lateinit var result: CalcResult
        val nanos = measureNanoTime { result = cache.calculate(site, rules, 2026) }
        val member = PublicationService.member(result)
        val published = PublicationService(calculations = cache).publish(site, rules, Scope.Year(2026))
        Files.writeString(Path.of(args[1]), buildJsonObject {
            put("elapsedMillis", nanos / 1_000_000.0)
            put("resultSha256", member.resultSha256)
            put("context", RecordJson.encodeToJsonElement(Coverage.serializer(), member.context))
            put("versions", RecordJson.encodeToJsonElement(BundleMember.serializer(), member).jsonObject["versions"]!!)
            put("public", published.document)
        }.toString(), CREATE_NEW)
    }
}
