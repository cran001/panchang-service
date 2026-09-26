// Ground-truth harvesters and the conformance harness. Never shipped.
//
// This module deliberately owns all outbound network access in the project. Nothing
// downstream of it is allowed to fetch anything at test time: reference data is
// harvested here, stamped with provenance, and committed as curated golden files.
plugins {
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":sampradaya"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.clikt)
    runtimeOnly(libs.logback.classic)
}

// Tests that touch the network are tagged "network" and are excluded from `build`.
// Rationale: `./gradlew build` must stay green on a machine with no route to
// ssd.jpl.nasa.gov, aa.usno.navy.mil, or the community calendar hosts. A red build
// caused by someone else's outage teaches developers to ignore red builds.
//
// The root project registers its own `tasks.withType<Test>().configureEach` action
// during root evaluation, i.e. strictly before this file is evaluated, so the
// configuration below runs afterwards and wins.
tasks.named<Test>("test") {
    useJUnitPlatform { excludeTags("network") }
}

// Run the live-source tests explicitly:
//   ./gradlew :verify:networkTest --no-daemon
// These fail if a source is unreachable. That is the point: they are the canary that
// tells us a reference source changed its format or went away.
tasks.register<Test>("networkTest") {
    group = "verification"
    description = "Runs only the @Tag(\"network\") tests. Requires internet access."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    // Never cache a network probe result; the whole point is to re-observe reality.
    outputs.upToDateWhen { false }
}
tasks.named<Test>("networkTest") {
    useJUnitPlatform { includeTags("network") }
    useConfiguredTrustStore()
}

// Harvesting is a deliberate, reviewable act, not a hidden test side-effect.
//   ./gradlew :verify:harvest --args="horizons --body moon --from 2026-01-01 --to 2026-01-31"
tasks.register<JavaExec>("harvest") {
    group = "application"
    description = "Runs the harvest CLI. Pass arguments with --args=\"...\"."
    mainClass.set("org.panchang.verify.cli.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    // The CLI resolves verify/cache and verify/golden relative to this directory.
    workingDir = projectDir
    useConfiguredTrustStore()
}

/**
 * Optional TLS trust store for the two tasks that deliberately touch the network.
 *
 * Some JDK distributions ship a cut-down `cacerts`. The JetBrains Runtime bundled with
 * Android Studio 2024.x carries 112 roots and none of the Sectigo ones, so every fetch of
 * ssd.jpl.nasa.gov — which chains to Sectigo Public Server Authentication Root R46 —
 * fails with "unable to find valid certification path" on a machine where `curl` to the
 * same URL succeeds. The JBR also omits the native SunMSCAPI library, so
 * `-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT` is not available as a fallback.
 *
 * This is a property of the developer's JDK, not of our code, so it is opt-in rather than
 * hard-coded:
 * ```
 * ./gradlew :verify:networkTest -Pharvest.trustStore=/path/to/harvest-truststore.p12
 * ```
 * Build such a store from any PEM CA bundle (Git for Windows ships one at
 * `/usr/ssl/certs/ca-bundle.crt`); `keytool -importcert` reads only the first certificate
 * from a multi-cert PEM, so the bundle has to be split first:
 * ```
 * awk '/BEGIN CERT/{n++} {print > sprintf("ca%04d.pem", n)}' ca-bundle.crt
 * for f in ca*.pem; do keytool -importcert -noprompt -trustcacerts -alias "$f" \
 *     -file "$f" -keystore harvest-truststore.p12 -storetype PKCS12 -storepass changeit; done
 * ```
 * Never point this at a store containing anything but public CA roots, and never apply it
 * to a shipped module: it is a developer convenience for reading public data.
 */
fun JavaForkOptions.useConfiguredTrustStore() {
    val store = providers.gradleProperty("harvest.trustStore").orNull ?: return
    systemProperty("javax.net.ssl.trustStore", store)
    systemProperty("javax.net.ssl.trustStoreType", "PKCS12")
    systemProperty(
        "javax.net.ssl.trustStorePassword",
        providers.gradleProperty("harvest.trustStorePassword").orNull ?: "changeit",
    )
}
