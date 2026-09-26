package org.panchang.api

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.panchang.calc.*
import org.panchang.core.GeoLocation
import org.panchang.publication.*
import org.panchang.publish.*

class PublicationControlsApiTest {
    @TempDir lateinit var directory: Path
    private val query = "lat=23.416666666666668&lon=88.38333333333334&tz=Asia/Kolkata"
    private val specs get() = SiteList.parse("coords mayapur 23.416666666666668 88.38333333333334 Asia/Kolkata Mayapur")
    private fun get(url: String, service: PublicationService = PublicationService()): JsonObject {
        var result: JsonObject? = null
        testApplication {
            application { panchangModule(publication = service) }
            val response = client.get(url)
            assertEquals(200, response.status.value, response.bodyAsText())
            assertEquals("no-store", response.headers["Cache-Control"])
            result = RecordJson.parseToJsonElement(response.bodyAsText()).jsonObject
        }
        return result!!
    }
    private fun state(root: JsonObject, field: String = "ekadashiYear") = root.getValue(field).jsonObject.getValue("publication").jsonObject.getValue("state").jsonPrimitive.content
    private fun member(): BundleMember {
        val site = LocationResolver().byCoordinates(GeoLocation(23.416666666666668, 88.38333333333334, ZoneId.of("Asia/Kolkata")), "test")
        return PublicationService.member(CalcEngine().compute(site, Sampradayas["iskcon"]!!, Scope.Year(2026)))
    }

    @Test fun `unconfigured public day and year explicitly withhold calculated guidance`() {
        for (url in listOf("/v2/day/iskcon/2026-01-01?$query", "/v2/calendar/iskcon/2026?$query")) {
            val root = get(url)
            assertTrue(state(root) in setOf("MISSING_EVIDENCE", "DISPUTED"))
            assertEquals(JsonNull, root.getValue("ekadashiYear").jsonObject["observances"])
            assertEquals(JsonNull, root.getValue("yearResolution").jsonObject["events"])
            assertFalse(root.toString().contains("jdUt"))
            assertFalse(root.toString().contains("CONFIRMED"))
            assertEquals("GUIDANCE_WITHHELD_NOT_NO_EVENT", root.getValue("ekadashiYear").jsonObject["absenceMeaning"]!!.jsonPrimitive.content)
        }
    }

    @Test fun `known dispute is visible even when computed observance is absent on the reference date`() {
        val root = get("/v2/day/iskcon/2026-06-27?lat=27.583333333333332&lon=77.7&tz=Asia/Kolkata")
        assertEquals("DISPUTED", state(root))
        assertTrue(root.toString().contains("OBSERVANCE-01"))
        assertEquals(JsonNull, root.getValue("ekadashiYear").jsonObject["observances"])
    }

    @Test fun `Reykjavik sunrise dispute remains separate from corrected daylight delivery`() {
        val root = get("/v2/day/iskcon/2026-06-27?lat=64.1466&lon=-21.9426&tz=Atlantic/Reykjavik")
        assertEquals("DISPUTED", state(root))
        assertTrue(root.toString().contains("ASTRONOMY-01"))
        assertFalse(root.toString().contains("DELIVERY-02"))
    }

    @Test fun `approved empty day and withheld day cannot be confused`() {
        val root = get("/v2/day/iskcon/2026-01-02?$query", TestApprovals.service())
        assertEquals("APPROVED", state(root))
        assertEquals(JsonArray(emptyList()), root.getValue("ekadashiYear").jsonObject["observances"])
        val withheld = get("/v2/day/iskcon/2026-01-02?$query")
        assertEquals(JsonNull, withheld.getValue("ekadashiYear").jsonObject["observances"])
    }

    @Test fun `test-only approved January carryover retains previous fasting date and Parana`() {
        val root = get("/v2/day/iskcon/2026-01-01?$query", TestApprovals.service())
        val obs = root.getValue("ekadashiYear").jsonObject.getValue("observances").jsonArray.single().jsonObject
        assertEquals("2025-12-31", obs["date"]!!.jsonPrimitive.content)
        assertEquals("2026-01-01", obs["parana"]!!.jsonObject["date"]!!.jsonPrimitive.content)
        assertEquals("APPROVED", state(root))
    }

    @Test fun `API structured files and legacy agree that unapproved guidance cannot publish`() {
        val root = get("/v2/calendar/iskcon/2026?$query")
        val publisher = FeedPublisher()
        val result = publisher.run(specs, 2026)
        publisher.write(result, directory.resolve("new-release"))
        for ((file, field) in listOf("ekadashi-year" to "ekadashiYear", "year-resolution" to "yearResolution")) {
            val output = Files.readString(directory.resolve("new-release/v2/mayapur/$file.json"))
            assertEquals(root[field], RecordJson.parseToJsonElement(output))
        }
        assertEquals(0, result.manifest.legacyPublished)
        assertEquals(1, result.manifest.legacyWithheld)
        assertFalse(result.files.keys.any { it.startsWith("legacy/") })
        assertFalse(Files.exists(directory.resolve("new-release/legacy")))
        assertTrue(Files.exists(directory.resolve("new-release/COMPLETE")))
        val migration = RecordJson.parseToJsonElement(Files.readString(directory.resolve("new-release/v1/mayapur/ekadashi-year.json"))).jsonObject
        assertEquals("API_VERSION_RETIRED", migration["code"]!!.jsonPrimitive.content)
        assertFalse(migration.containsKey("observances"))
        assertFalse(result.files.values.any { it.contains("jdUt") })
    }

    @Test fun `approved API and structured files agree while legacy refuses unsupported contract`() {
        val service = TestApprovals.service()
        val root = get("/v2/calendar/iskcon/2026?$query", service)
        val result = FeedPublisher(publication = service).run(specs, 2026)
        assertEquals(root["ekadashiYear"], RecordJson.parseToJsonElement(result.files.getValue("v2/mayapur/ekadashi-year.json")))
        assertEquals("APPROVED", state(root))
        assertEquals(LegacyWithholdingCode.PUBLICATION_CONTRACT_REQUIRED, result.manifest.legacyWithheldSites.single().code)
        assertNull(result.manifest.utcFallback)
    }

    @Test fun `revocation blocks next API response and already prepared file publication`() {
        val f = TestApprovals(directory.resolve("test-journal"))
        val approval = f.approve(member())
        val service = PublicationService(policyFor = { f.policy },
            calculations = PersistentCalculationCache(directory.resolve("isolated-calculation-cache")))
        assertEquals("APPROVED", state(get("/v2/day/iskcon/2026-01-01?$query", service)))
        val publisher = FeedPublisher(publication = service)
        val prepared = publisher.run(specs, 2026)
        f.record(approval.bundle, Action.REVOKE, target = approval.id)
        assertEquals("REVOKED", state(get("/v2/day/iskcon/2026-01-01?$query", service)))
        assertThrows(IllegalArgumentException::class.java) { publisher.write(prepared, directory.resolve("revoked-output")) }
        assertFalse(Files.exists(directory.resolve("revoked-output")))
        val next = publisher.run(specs, 2026)
        assertFalse(next.files.getValue("v2/mayapur/ekadashi-year.json").contains("jdUt"))
    }

    @Test fun `query header and anonymous body cannot forge approval or enable a review API`() {
        testApplication {
            application { panchangModule() }
            val forged = client.get("/v2/day/iskcon/2026-01-01?$query&approved=true&reviewer=owner") {
                header("Authorization", "Bearer owner")
                header("X-Approver", "owner")
            }
            assertFalse(forged.bodyAsText().contains("jdUt"))
            for (path in listOf("/v2/approve", "/v2/admin/approvals", "/review/v2/day/iskcon/2026-01-01")) {
                val response = client.post(path) { setBody("{\"approved\":true,\"owner\":true}") }
                assertTrue(response.status.value in setOf(404, 405))
            }
        }
    }

    @Test fun `public output refuses reuse of a directory containing old artifacts`() {
        val old = directory.resolve("old")
        Files.createDirectories(old.resolve("legacy"))
        Files.writeString(old.resolve("legacy/old.json"), "old deployment artifact")
        val publisher = FeedPublisher()
        val result = publisher.run(specs, 2026)
        assertThrows(IllegalArgumentException::class.java) { publisher.write(result, old) }
        assertEquals("old deployment artifact", Files.readString(old.resolve("legacy/old.json")))
        val tampered = result.copy(files = result.files + ("legacy/forged.json" to "forged timing"))
        assertThrows(IllegalArgumentException::class.java) { publisher.write(tampered, directory.resolve("forged")) }
        assertFalse(Files.exists(directory.resolve("forged")))
    }

    @Test fun `all other traditions are refused by both public routes and publisher while calculators remain`() {
        val others = org.panchang.calc.Sampradayas.knownIds().filter { it != "iskcon" }
        assertEquals(9, others.size)
        for (id in others) {
            assertNotNull(org.panchang.calc.Sampradayas[id])
            for (path in listOf("day/$id/2026-01-02", "calendar/$id/2026")) testApplication {
                application { panchangModule(publication = TestApprovals.service()) }
                val response = client.get("/v2/$path?$query")
                assertEquals(422, response.status.value)
                val root = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                assertEquals("SAMPRADAYA_OUTSIDE_RELEASE", root["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
                assertEquals("WITHHELD", root["publication"]!!.jsonObject["guidance"]!!.jsonPrimitive.content)
                assertFalse(root.containsKey("ekadashiYear"))
            }
            assertThrows(IllegalArgumentException::class.java) { FeedPublisher(TestApprovals.service()).run(specs, 2026, id) }
        }
    }
}
