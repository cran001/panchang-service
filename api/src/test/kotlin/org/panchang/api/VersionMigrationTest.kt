package org.panchang.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VersionMigrationTest {
    @Test fun `v1 calendar and day are explicit non-cacheable migration refusals`() = testApplication {
        application { panchangModule() }
        for (path in listOf("day/iskcon/2026-01-01", "calendar/iskcon/2026", "day/marathi/2026-01-01")) {
            val response = client.get("/v1/$path?lat=23.4&lon=88.4&tz=Asia/Kolkata")
            assertEquals(410, response.status.value)
            assertEquals("no-store", response.headers["Cache-Control"])
            val root = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("API_VERSION_RETIRED", root["code"]!!.jsonPrimitive.content)
            assertEquals("WITHHELD", root["guidance"]!!.jsonPrimitive.content)
            assertFalse(root.containsKey("ekadashiYear"))
        }
    }
}
