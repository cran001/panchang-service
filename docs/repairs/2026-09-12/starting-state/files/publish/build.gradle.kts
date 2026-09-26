// The publisher: generates the static artifacts a client downloads, into a local directory.
//
//     ./gradlew :publish:run --args="--sites publish/sites/example.tsv --year 2026 --out build/feed"
//
// It computes nothing of its own. `:calc` owns the request→answer path and the sampradaya
// registration, and this module reuses both rather than standing up a parallel copy — two front
// doors that each assemble a year are two chances to disagree about a fasting time. Reusing
// `org.panchang.calc.Sampradayas` is also load-bearing at runtime: `SampradayaRegistry.register`
// throws if two different `IskconRules` instances claim the same id, which is exactly what a
// second registration in this module would produce the moment both are loaded in one JVM.
//
// `:wire`, `:gazetteer` and `:core` are declared here because `:calc` exposes their types
// (`YearResolutionDto`, `Place`, `GeoLocation`) through `implementation` dependencies, which do
// not reach this module's compile classpath transitively.
//
// Two outputs, and only one of them is this project's schema:
//   * `legacy/` reproduces the Android app's existing feed contract, so installs in the field
//     keep working. That shape is the app's, documented in `docs/legacy-contract.md`, and is
//     rendered here rather than by `:wire`.
//   * `v1/` is `:wire`'s document roots verbatim, `unresolved` map included.
plugins {
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    implementation(project(":sampradaya"))
    implementation(project(":calc"))
    implementation(project(":wire"))
    implementation(project(":gazetteer"))
    implementation(project(":core"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.clikt)
}

application {
    mainClass.set("org.panchang.publish.MainKt")
}
