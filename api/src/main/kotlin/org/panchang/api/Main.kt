package org.panchang.api

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

/**
 * Entry point.
 *
 * ```
 * ./gradlew :api:run
 * curl 'http://localhost:8080/v1/calendar/iskcon/2026?lat=19.0760&lon=72.8777&tz=Asia/Kolkata'
 * curl 'http://localhost:8080/v1/day/iskcon/2026-01-15?place=Nadia&pretty=1'
 * ```
 *
 * `PORT` and `HOST` are read from the environment because that is how a process manager passes
 * them, and defaulted so that `:api:run` works with no configuration at all. Nothing else is
 * configurable for approval. PANCHANG_CALC_CACHE_DIR optionally enables an operator-owned
 * persistent calculation cache; PANCHANG_APPROVAL_MODE accepts only disabled.
 *
 * The whole engine is constructed once, at startup, and shared by every request.
 * `PanchangCalculator` and `Vsop87Ephemeris` hold no mutable state — every table in `:ephemeris`
 * is immutable. The optional yearly cache coordinates concurrent requests and processes through
 * local filesystem locks. Every public response evaluates current publication policy.
 */
fun main() {
    val host = System.getenv("HOST") ?: "0.0.0.0"
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val publication = org.panchang.publication.PublicationRuntime.service()
    embeddedServer(Netty, port = port, host = host) { panchangModule(publication = publication) }
        .start(wait = true)
}
