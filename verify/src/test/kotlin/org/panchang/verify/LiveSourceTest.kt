package org.panchang.verify

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.panchang.verify.drik.DrikPanchangHarvester
import org.panchang.verify.drik.DrikQuery
import org.panchang.verify.grid.ReferenceCities
import org.panchang.verify.harvest.ArtifactStore
import org.panchang.verify.harvest.HarvestContext
import org.panchang.verify.harvest.HostRateLimiter
import org.panchang.verify.harvest.HttpFetcher
import org.panchang.verify.horizons.HorizonsBody
import org.panchang.verify.horizons.HorizonsHarvester
import org.panchang.verify.horizons.HorizonsQuery
import org.panchang.verify.usno.UsnoHarvester
import org.panchang.verify.vaisnava.IskconMumbaiHarvester
import org.panchang.verify.vaisnava.IskconMumbaiQuery
import org.panchang.verify.vaisnava.VaisnavaCalendarQuery
import org.panchang.verify.vaisnava.VaisnavaCalendarTxtHarvester
import java.nio.file.Path
import java.time.LocalDate

/**
 * Live probes against the real sources.
 *
 * Excluded from `./gradlew :verify:build`; run them deliberately:
 * ```
 * ./gradlew :verify:networkTest --no-daemon
 * ```
 *
 * Their purpose is not to check our arithmetic — the offline parser tests do that against
 * committed captures. Their purpose is to notice when a source changes its format, moves,
 * or disappears, which is the failure that would otherwise be discovered as a mysterious
 * conformance regression months later.
 *
 * They are correspondingly polite: a handful of requests, the shared rate limiter, and no
 * loops over the grid. Do not add a test here that sweeps a year of dates.
 */
@Tag("network")
class LiveSourceTest {

    private fun context(root: Path): Pair<HarvestContext, HttpFetcher> {
        val fetcher = HttpFetcher(HttpFetcher.defaultClient(), HostRateLimiter())
        return HarvestContext(ArtifactStore(root), fetcher) to fetcher
    }

    /**
     * The strongest available check: the committed Horizons fixture is a real capture, so
     * re-requesting the same instant must reproduce the same apparent longitude. A
     * mismatch means either the API changed its framing or our request parameters no
     * longer mean what they meant.
     */
    @Test
    fun `JPL Horizons still returns the committed regression anchor`(@TempDir root: Path) = runBlocking {
        val (ctx, fetcher) = context(root)
        fetcher.use {
            val result = HorizonsHarvester().harvest(
                HorizonsQuery(HorizonsBody.MOON, "2026-04-14", "2026-04-15", "1 d"),
                ctx,
            )
            val first = result.parsed.records.first()
            assertEquals("2026-04-14T00:00:00Z", first.utc)
            // Tolerance covers a kernel or nutation-model revision, not a framing change.
            assertEquals(338.2914490, first.apparentEclipticLongitudeDeg, 1e-4)
            assertTrue(result.parsed.report.unparsed.isEmpty(), result.parsed.report.unparsed.toString())
        }
    }

    @Test
    fun `USNO still answers for a grid city`(@TempDir root: Path) = runBlocking {
        val (ctx, fetcher) = context(root)
        fetcher.use {
            val query = UsnoHarvester.queryFor(ReferenceCities.MAYAPUR, LocalDate.of(2026, 4, 14))
            val day = UsnoHarvester().harvest(query, ctx).parsed.records.single()
            assertEquals("2026-04-14", day.date)
            assertEquals(listOf("05:17"), day.sunRises)
            assertEquals(listOf("17:57"), day.sunSets)
        }
    }

    /**
     * The polar path is the one most likely to break silently, because a parser that
     * mishandles it still produces a plausible-looking record.
     */
    @Test
    fun `USNO still reports polar day in the shape we parse`(@TempDir root: Path) = runBlocking {
        val (ctx, fetcher) = context(root)
        fetcher.use {
            val probe = ReferenceCities.POLAR_PROBES.first { it.id == "longyearbyen" }
            val query = UsnoHarvester.queryFor(probe, LocalDate.of(2026, 6, 21))
            val day = UsnoHarvester().harvest(query, ctx).parsed.records.single()
            assertTrue(day.sunHasNoRiseOrSet, "expected no sunrise or sunset at 78N on the solstice")
            assertTrue(day.sunCondition != null, "polar condition was not recognised")
        }
    }

    /**
     * A format canary for the fixed-width text export. The parser reads column positions
     * out of the emitted header, so a layout change shows up as unparsed lines rather
     * than as wrong values — but only if someone looks, which is what this does.
     */
    @Test
    fun `vaisnavacalendar text export still parses cleanly`(@TempDir root: Path) = runBlocking {
        val (ctx, fetcher) = context(root)
        fetcher.use {
            val query = VaisnavaCalendarQuery.forCity(ReferenceCities.MAYAPUR, 2026)!!
            val result = VaisnavaCalendarTxtHarvester().harvest(query, ctx)
            assertTrue(result.parsed.records.size > 350, "expected a full year, got ${result.parsed.records.size}")
            assertTrue(result.parsed.report.unparsed.isEmpty(), result.parsed.report.unparsed.toString())
            assertTrue(result.parsed.records.any { it.tithi.startsWith("Ekadasi") })
        }
    }

    @Test
    fun `every grid city still has a published calendar for the current year`(@TempDir root: Path) = runBlocking {
        val (ctx, fetcher) = context(root)
        val harvester = VaisnavaCalendarTxtHarvester()
        fetcher.use {
            // Ten requests at a three-second gap. Deliberately the largest thing here.
            ReferenceCities.ALL.forEach { city ->
                val query = VaisnavaCalendarQuery.forCity(city, 2026)
                    ?: error("${city.id} has no vaisnavaCalendarCity stem")
                val result = harvester.harvest(query, ctx)
                assertTrue(
                    result.parsed.records.size > 350,
                    "${city.id}: expected a full year, got ${result.parsed.records.size}",
                )
            }
        }
    }

    @Test
    fun `ISKCON Mumbai still publishes a parseable Ekadasi notice`(@TempDir root: Path) = runBlocking {
        val (ctx, fetcher) = context(root)
        fetcher.use {
            val notice = IskconMumbaiHarvester().harvest(IskconMumbaiQuery(), ctx).parsed.records.single()
            assertTrue(
                notice.ekadasiName?.contains("Ekadasi", ignoreCase = true) == true,
                "no Ekadashi name recovered: ${notice.sourceSentence}",
            )
            assertTrue(
                notice.paranaStart?.matches(Regex("""\d{2}:\d{2}""")) == true,
                "parana start not normalised: ${notice.paranaStart}",
            )
            assertTrue(
                notice.paranaEnd?.matches(Regex("""\d{2}:\d{2}""")) == true,
                "parana end not normalised: ${notice.paranaEnd}",
            )
        }
    }

    /**
     * Reachability and structure only. This test asserts that the page still contains the
     * rows we know how to read; it deliberately asserts nothing about their values,
     * because drikpanchang.com data is a comparison target we do not redistribute and a
     * committed expected value would be redistributing it.
     */
    @Test
    fun `drikpanchang day panchang still exposes the rows we read`(@TempDir root: Path) = runBlocking {
        val (ctx, fetcher) = context(root)
        fetcher.use {
            val result = DrikPanchangHarvester().harvest(DrikQuery(LocalDate.of(2026, 4, 14)), ctx)
            val day = result.parsed.records.single()
            listOf("Sunrise", "Sunset", "Moonrise", "Tithi", "Nakshatra").forEach { key ->
                assertTrue(day.value(key) != null, "row '$key' is gone from the page")
            }
        }
    }
}
