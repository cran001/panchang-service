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
 * configurable: there is no database, no credential and no tuning knob in this service, and a
 * configuration file that held only a port number would be a place for one to appear.
 *
 * The whole engine is constructed once, at startup, and shared by every request.
 * `PanchangCalculator` and `Vsop87Ephemeris` hold no mutable state — every table in `:ephemeris`
 * is immutable and nothing is cached across calls — so one instance is safe for concurrent use and
 * a per-request instance would only re-do the constructor.
 */
fun main() {
    val host = System.getenv("HOST") ?: "0.0.0.0"
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = host) { panchangModule() }
        .start(wait = true)
}
