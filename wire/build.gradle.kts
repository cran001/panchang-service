// The single shared serialisation layer.
//
// `:calc`, `:api` and `:publish` are three front doors onto one engine and must emit
// byte-identical v1 payloads. Duplicated DTOs in three modules would drift silently and nobody
// would notice until two of the doors disagreed about a fasting date in front of a user, so the
// schema lives here once and nowhere else.
//
// `:sampradaya` is `api` rather than `implementation` because this module's public surface takes
// domain types as parameters — a consumer holding a `ResolvedEvent` has to be able to name it to
// hand it over. The serialisation runtime is `api` for the same reason: `WireJson` hands back a
// `Json`, and a consumer that cannot name `Json` cannot use it.
plugins {
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":sampradaya"))
    api(libs.kotlinx.serialization.json)
}
