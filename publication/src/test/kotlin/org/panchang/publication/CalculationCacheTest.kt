package org.panchang.publication

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.panchang.calc.*
import org.panchang.core.GeoLocation

class CalculationCacheTest {
    @TempDir lateinit var directory: Path
    private val site get() = result.site
    private val rules get() = result.rules
    private fun files() = Files.list(directory).use { s -> s.filter { it.toString().endsWith(".json") }.toList() }
    private fun counted(count: AtomicInteger) = YearCalculation { _, _, _ -> count.incrementAndGet(); result }
    private fun state(output: PublicResult) = output.decisions.single { it.field == Field.OBSERVANCES }.state

    @Test fun `restart reuses verified yearly bytes and preserves request provenance`() {
        val count = AtomicInteger()
        val cold = PersistentCalculationCache(directory, counted(count)).calculate(site, rules, 2026)
        val caller = site.copy(query = "a different caller", notes = listOf("request-specific note"))
        val warm = PersistentCalculationCache(directory, YearCalculation { _, _, _ -> error("must hit disk") }).calculate(caller, rules, 2026)
        assertEquals(1, count.get())
        assertEquals(caller, warm.site)
        assertEquals(cold.yearResolution, warm.yearResolution)
        assertEquals(cold.ekadashiYear, warm.ekadashiYear)
        assertEquals(PublicationService.member(cold), PublicationService.member(warm))
        assertTrue(warm.ekadashiYear.observances.any { it.date == LocalDate.of(2025, 12, 31) && it.parana?.date == LocalDate.of(2026, 1, 1) })
    }

    @Test fun `concurrent instances compute one complete entry`() {
        val count = AtomicInteger()
        val workers = Executors.newFixedThreadPool(6)
        try {
            val futures = workers.invokeAll((1..12).map { Callable {
                PersistentCalculationCache(directory, counted(count)).calculate(site, rules, 2026)
            } })
            assertTrue(futures.all { it.get().ekadashiYear == result.ekadashiYear })
            assertEquals(1, count.get())
            assertEquals(1, files().size)
        } finally { workers.shutdownNow() }
    }

    @Test fun `exact input and all version changes invalidate cache identity`() {
        val key = CalculationKey(place = PublicationService.place(site), year = 2026, tradition = rules.id,
            versions = CalculationVersions.at(site.location.zone.id))
        val changes = listOf(key.copy(year = 2027), key.copy(tradition = "marathi"),
            key.copy(place = key.place.copy(latitude = key.place.latitude + 0.000001)),
            key.copy(place = key.place.copy(longitude = key.place.longitude + 0.000001)),
            key.copy(place = key.place.copy(elevationMeters = 1.0)),
            key.copy(place = key.place.copy(timeZone = "Asia/Calcutta")), key.copy(format = "future")) +
            key.versions.keys.map { key.copy(versions = key.versions + (it to "future")) }
        for (changed in changes) assertNotEquals(key.fingerprint(), changed.fingerprint())
        assertEquals(changes.size, changes.map { it.fingerprint() }.distinct().size)
    }

    @Test fun `changed location actually recomputes and cannot borrow cached scope`() {
        val count = AtomicInteger()
        val delegate = YearCalculation { requested, _, year ->
            count.incrementAndGet()
            val p = requested.location
            val dto = org.panchang.wire.SiteDto(p.latitude, p.longitude, p.zone.id, p.elevationMeters)
            result.copy(site = requested, scope = Scope.Year(year),
                yearResolution = result.yearResolution.copy(site = dto), ekadashiYear = result.ekadashiYear.copy(site = dto))
        }
        val cache = PersistentCalculationCache(directory, delegate)
        cache.calculate(site, rules, 2026)
        cache.calculate(site.copy(location = site.location.copy(elevationMeters = 1.0)), rules, 2026)
        assertEquals(2, count.get())
        assertEquals(2, files().size)
    }

    @Test fun `corrupted or swapped payload cannot be read as a cache hit`() {
        val cache = PersistentCalculationCache(directory, counted(AtomicInteger()))
        cache.calculate(site, rules, 2026)
        val path = files().single()
        val original = Files.readString(path)
        Files.writeString(path, original.replace("2025-12-31", "2025-12-30"))
        assertThrows(IllegalArgumentException::class.java) { cache.calculate(site, rules, 2026) }
        Files.writeString(path, original.take(100))
        assertThrows(Exception::class.java) { cache.calculate(site, rules, 2026) }
    }

    @Test fun `failed computation and abandoned partial files never become hits`() {
        Files.writeString(directory.resolve(".interrupted.pending"), "partial bytes")
        assertThrows(IllegalStateException::class.java) {
            PersistentCalculationCache(directory, YearCalculation { _, _, _ -> error("injected crash") }).calculate(site, rules, 2026)
        }
        assertTrue(files().isEmpty())
        assertEquals(result.ekadashiYear, PersistentCalculationCache(directory, counted(AtomicInteger())).calculate(site, rules, 2026).ekadashiYear)
        assertEquals(1, files().size)
    }

    @Test fun `warm reads recheck revocation supersession unavailable store and disputes`() {
        val cache = PersistentCalculationCache(directory.resolve("cache"), counted(AtomicInteger()))
        val f = TestApprovals(directory.resolve("TEST-ONLY-journal"))
        val member = PublicationService.member(result)
        val approval = f.approve(member)
        var outage = false
        var registry = f.registry
        val source = RecordSource { if (outage) error("test outage") else f.journal.read() }
        val service = PublicationService(policyFor = { PublicationPolicy(registry, f.referencePolicy, source) }, calculations = cache)
        fun read() = service.publish(site, rules, Scope.Day(LocalDate.of(2026, 1, 2)))
        val emptyDay = read()
        assertEquals(PublicationState.APPROVED, state(emptyDay))
        assertEquals(JsonArray(emptyList()), emptyDay.document["ekadashiYear"]!!.jsonObject["observances"])
        outage = true
        val withheld = read()
        assertEquals(PublicationState.INVALID_RECORDS, state(withheld))
        assertEquals(JsonNull, withheld.document["ekadashiYear"]!!.jsonObject["observances"])
        outage = false
        registry = registry.copy(revision = "changed-disputes-test-only")
        assertNotEquals(PublicationState.APPROVED, state(read()))
        registry = f.registry
        f.record(approval.bundle, Action.SUPERSEDE, target = approval.id)
        assertEquals(PublicationState.SUPERSEDED, state(read()))
        val next = f.approve(member)
        val prepared = read()
        assertEquals(PublicationState.APPROVED, state(prepared))
        f.record(next.bundle, Action.REVOKE, target = next.id)
        assertFalse(prepared.isCurrent())
        assertEquals(PublicationState.REVOKED, state(read()))
    }

    @Test fun `runtime configuration never activates approval through environment`() {
        assertThrows(IllegalArgumentException::class.java) {
            PublicationRuntime.service(mapOf("PANCHANG_APPROVAL_MODE" to "production"))
        }
        val runtime = PublicationRuntime.service(mapOf("PANCHANG_CALC_CACHE_DIR" to directory.toString()))
        assertNotEquals(PublicationState.APPROVED, state(runtime.publish(site, rules, Scope.Day(LocalDate.of(2026, 1, 2)))))
        assertEquals(1, files().size)
    }

    companion object {
        private val result by lazy {
            val site = LocationResolver().byCoordinates(GeoLocation(23.416666666666668, 88.38333333333334, ZoneId.of("Asia/Kolkata")), "TEST ONLY")
            CalcEngine().compute(site, Sampradayas["iskcon"]!!, Scope.Year(2026))
        }
    }
}
