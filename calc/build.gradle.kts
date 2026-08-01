// The calculation façade: the command the whole project exists to serve.
//
//     ./gradlew :calc:run --args="--place Nadia --sampradaya iskcon --year 2026"
//
// It assembles, and computes nothing of its own. The astronomy is `:core`, the rulings are
// `:sampradaya`, the place table is `:gazetteer`, and every Julian Day → civil time conversion
// and every serialised field is `:wire`. This module owns exactly two things: how a location is
// chosen from the arguments, and how the result is laid out for a human to read.
//
// `:wire` and `:gazetteer` are `implementation` and not `api` because nothing depends on
// `:calc` — it is a front door, not a library.
plugins {
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    implementation(project(":wire"))
    implementation(project(":gazetteer"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.clikt)
}

application {
    mainClass.set("org.panchang.calc.MainKt")
}

// `./gradlew :calc:run --args="..." > out.json` only produces clean JSON when Gradle is quiet;
// at the default log level Gradle writes its own task banner to the same stdout. Everything this
// CLI prints that is *not* the payload already goes to stderr, so `-q` is enough:
//
//     ./gradlew -q :calc:run --args="--lat 19.076 --lon 72.8777 --tz Asia/Kolkata --year 2026 \
//         --format json" > mum.json
