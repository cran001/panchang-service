plugins {
    alias(libs.plugins.kotlin.serialization)
    `java-test-fixtures`
    application
}

dependencies {
    api(project(":calc"))
    api(project(":wire"))
    api(project(":core"))
    api(project(":gazetteer"))
    testFixturesImplementation(libs.junit.jupiter)
}

application { mainClass.set("org.panchang.publication.MainKt") }
