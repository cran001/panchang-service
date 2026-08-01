rootProject.name = "panchang-service"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    ":ephemeris",
    ":core",
    ":sampradaya",
    ":api",
    ":publish",
    ":verify",
)
