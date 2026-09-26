plugins {
    alias(libs.plugins.kotlin.serialization)
}
dependencies {
    api(project(":core"))
    implementation(libs.kotlinx.serialization.json)
}

// The Gaudiya conformance test reads `verify/golden/vaisnavacalendar-mayapur-2026.json`
// directly out of the repository rather than through a copied resource.
//
// Deliberately a path, not a `processTestResources` copy. A copy would put a second
// physical instance of the oracle in `sampradaya/build`, and a stale copy that silently
// disagrees with the harvested original is exactly the failure mode a golden file exists
// to prevent. It also keeps `sampradaya` free of a compile-time dependency on `:verify`,
// which depends on `:sampradaya` — the reverse edge would be a cycle.
tasks.named<Test>("test") {
    systemProperty(
        "panchang.golden.dir",
        rootProject.layout.projectDirectory.dir("verify/golden").asFile.absolutePath,
    )
}
