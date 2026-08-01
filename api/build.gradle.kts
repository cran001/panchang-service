// The HTTP front door.
//
//     ./gradlew :api:run
//     curl 'http://localhost:8080/v1/calendar/iskcon/2026?lat=19.076&lon=72.8777&tz=Asia/Kolkata'
//
// This module owns routing, status codes and query-parameter parsing. It owns no rule, no
// coordinate, no time conversion and no DTO.
//
// ## Why it depends on `:calc`
//
// `:calc` is not "the CLI". Only `Main.kt` in that module is clikt-specific; `CalcEngine`,
// `LocationResolver`/`ResolvedSite` and `CalcJson` are the request→answer path itself, and the
// acceptance criterion for this module is that its payload is byte-identical to what `:calc
// --format json` writes. The surest way to be byte-identical to something is to be the same code,
// so the two `:wire` roots and the `location` block emitted here are literally the objects
// `:calc` builds.
//
// It also avoids a concrete hazard: `SampradayaRegistry` is a process-wide object whose
// `register` throws if two different rule instances claim one id. `:calc`'s `Sampradayas` already
// registers `IskconRules`; a parallel registration here would fail the moment both modules met in
// one JVM, which is exactly what the byte-identity test arranges.
//
// `:wire`, `:gazetteer` and `:sampradaya` are named explicitly because `:calc` declares its own
// copies `implementation`, so they do not arrive transitively — and that is right: this module
// uses `WireJson`, `acceptSite`, `Gazetteer` and `SampradayaRules` in its own routes, and a
// dependency you use is a dependency you declare.
//
// Content negotiation is deliberately absent. Every response here is built as a `JsonObject` and
// written through `WireJson`, because the byte-identity requirement means this module must
// control the exact bytes rather than delegate them to a plugin's own `Json` instance — two
// serialiser configurations is precisely the drift `:wire` exists to prevent.
plugins {
    alias(libs.plugins.kotlin.serialization)
    application
}
dependencies {
    implementation(project(":calc"))
    implementation(project(":wire"))
    implementation(project(":gazetteer"))
    implementation(project(":sampradaya"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.logback.classic)
    testImplementation(libs.ktor.server.test.host)
    // The acceptance test drives the real `:calc` CLI object, not just its engine, so that the
    // bytes it compares against are the bytes `--format json` actually writes to stdout. `:calc`
    // declares clikt `implementation`, so the test source set has to name it.
    testImplementation(libs.clikt)
}
application {
    mainClass.set("org.panchang.api.MainKt")
}

// `/v1/meta` and `/v1/health` report an engine version. It is generated from the Gradle project
// version rather than typed into a Kotlin constant, because a hand-maintained copy is a second
// place for the version to live and will be wrong within one release — and a version number a
// service reports is only worth reading if it cannot drift from the thing it names.
val generateVersionResource by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/version")
    val projectVersion = version.toString()
    inputs.property("version", projectVersion)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("org/panchang/api/version.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=$projectVersion\n", Charsets.UTF_8)
    }
}

sourceSets.named("main") { resources.srcDir(generateVersionResource) }
