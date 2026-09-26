rootProject.name = "panchang-service"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    ":ephemeris",
    ":core",
    ":gazetteer",
    ":sampradaya",
    ":wire",
    ":calc",
    ":api",
    ":publish",
    ":verify",
)
