package org.panchang.publish

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SiteListTest {

    @Test
    fun `comments and blank lines are ignored and everything else is a spec`() {
        val specs = SiteList.parse(
            """
            # a comment

            coords    mayapur   23.4249  88.3883  Asia/Kolkata  Sri Mayapur Dham
            place-id  mumbai    1275339  Mumbai            # trailing comment
            """.trimIndent(),
        )
        assertEquals(2, specs.size)
        val coords = assertInstanceOf(SiteSpec.Coordinates::class.java, specs[0])
        assertEquals("mayapur", coords.key)
        assertEquals(23.4249, coords.latitude)
        assertEquals("Asia/Kolkata", coords.zoneId)
        assertEquals("Sri Mayapur Dham", coords.title)
        val place = assertInstanceOf(SiteSpec.PlaceId::class.java, specs[1])
        assertEquals(1275339, place.geonameId)
        assertEquals("Mumbai", place.title)
    }

    @Test
    fun `a line that cannot be read becomes a spec rather than disappearing`() {
        // The whole point: `published + skipped == requested` says nothing if a bad line never
        // counted as requested. A publisher that drops line 3000 emits a slightly shorter feed
        // and no evidence.
        val text = """
            coords    good      23.4249  88.3883  Asia/Kolkata  Good
            coords    bad-lat   north    88.3883  Asia/Kolkata  Bad
            coords    short     23.4
            place-id  bad-id    not-a-number
            satellite orbit     1 2 3 4
            coords    UPPER     23.4249  88.3883  Asia/Kolkata  Upper
        """.trimIndent()
        val specs = SiteList.parse(text)
        assertEquals(6, specs.size)
        assertEquals(5, specs.count { it is SiteSpec.Malformed })
        val problems = specs.filterIsInstance<SiteSpec.Malformed>().map { it.problem }
        assertTrue(problems.any { it.contains("latitude") }, problems.toString())
        assertTrue(problems.any { it.contains("GeoNames id") }, problems.toString())
        assertTrue(problems.any { it.contains("unknown site kind") }, problems.toString())
        assertTrue(problems.any { it.contains("site key") }, problems.toString())
        // Line numbers are the operator's only way back to the file.
        assertEquals(
            listOf(2, 3, 4, 5, 6),
            specs.filterIsInstance<SiteSpec.Malformed>().map { it.lineNumber },
        )
    }
}

class PublishReportTest {

    @Test
    fun `a skipped report whose counts do not add up cannot be constructed`() {
        assertThrows<IllegalArgumentException> {
            SkippedReport(requested = 10, published = 8, skipped = 1, sites = emptyList())
        }
        assertThrows<IllegalArgumentException> {
            SkippedReport(
                requested = 10,
                published = 9,
                skipped = 1,
                sites = emptyList(), // says one was skipped, lists none
            )
        }
    }

    @Test
    fun `a skipped site carries exactly one code`() {
        assertThrows<IllegalArgumentException> {
            SkippedSite(key = "k", query = "q", reason = "no code at all")
        }
        assertThrows<IllegalArgumentException> {
            SkippedSite(
                key = "k",
                query = "q",
                siteRejectionCode = org.panchang.wire.SiteRejectionCode.ABOVE_POLAR_LIMIT,
                inputRejectionCode = InputRejectionCode.UNKNOWN_PLACE_ID,
                reason = "both",
            )
        }
    }

    @Test
    fun `a manifest whose legacy counts do not add up cannot be constructed`() {
        fun manifest(published: Int, legacyPublished: Int, legacyWithheld: Int, listed: Int) =
            PublishManifest(
                sampradaya = "iskcon",
                year = 2026,
                requested = published,
                published = published,
                skipped = 0,
                legacyPublished = legacyPublished,
                legacyWithheld = legacyWithheld,
                legacyWithheldSites = List(listed) {
                    LegacyWithheld("k$it", "America/New_York", LegacyWithholdingCode.ZONE_NOT_FIXED_OFFSET, "r")
                },
                legacyParanaOmissions = emptyList(),
                warnings = emptyList(),
                attribution = "test",
            )

        manifest(published = 5, legacyPublished = 4, legacyWithheld = 1, listed = 1)
        assertThrows<IllegalArgumentException> {
            manifest(published = 5, legacyPublished = 5, legacyWithheld = 1, listed = 1)
        }
        assertThrows<IllegalArgumentException> {
            manifest(published = 5, legacyPublished = 4, legacyWithheld = 1, listed = 0)
        }
    }
}
