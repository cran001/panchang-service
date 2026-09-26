plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    group = "org.panchang"
    version = "0.1.0-SNAPSHOT"

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(21)
        compilerOptions {
            // The engine's astronomy is float-heavy; keep warnings loud but non-fatal.
            allWarningsAsErrors.set(false)
        }
    }

    dependencies {
        add("testImplementation", rootProject.libs.junit.jupiter)
        add("testRuntimeOnly", rootProject.libs.junit.platform.launcher)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // Any test that depends on the host's default timezone is a bug. Pin it to
        // something that is NOT the developer's zone so such bugs surface immediately.
        systemProperty("user.timezone", "UTC")
        testLogging {
            events("failed", "skipped")
            showStandardStreams = false
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
