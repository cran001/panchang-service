// The place gazetteer: a static table of world cities and Indian districts, with a name
// index and a nearest-point search.
//
// Its own module on purpose. It is not `:core` — a 26k-row place table is data entry, not
// astronomy, and putting it there would make every core test drag it in. It is not
// `:verify`, which is never shipped. It is not a resource inside `:api`, because
// `:publish` and `:calc` need the same table and a second copy is a second thing to drift.
dependencies {
    // For `Place.toGeoLocation()`. The edge points downstream: `:core` knows nothing of places.
    api(project(":core"))
}

// The provenance test checks that the hashes recorded in the derived `gazetteer-v1.tsv`
// header still match `vendor/MANIFEST.sha256`, so a re-vendored source archive cannot
// silently leave a stale derived table behind.
//
// Deliberately a path to the repository file rather than a `processTestResources` copy,
// for the same reason `:sampradaya` reads the golden calendar in place: a copied manifest
// that disagrees with the real one defeats the entire purpose of having a manifest.
tasks.named<Test>("test") {
    systemProperty(
        "panchang.vendor.dir",
        rootProject.layout.projectDirectory.dir("vendor").asFile.absolutePath,
    )
}

// Rebuilding the derived table from the vendored archives is a deliberate, reviewable act:
//   ./gradlew :gazetteer:ingest
// It rewrites `src/main/resources/org/panchang/gazetteer/gazetteer-v1.tsv` and prints the
// kept/dropped counts. Never wired into `build` — the derived table is committed, and a
// build step that can silently rewrite committed data is a build step that will.
tasks.register<JavaExec>("ingest") {
    group = "build"
    description = "Regenerates gazetteer-v1.tsv from the vendored GeoNames archives."
    mainClass.set("org.panchang.gazetteer.ingest.Ingest")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.layout.projectDirectory.asFile
    outputs.upToDateWhen { false }
}
