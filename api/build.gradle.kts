plugins {
    alias(libs.plugins.kotlin.serialization)
    application
}
dependencies {
    implementation(project(":sampradaya"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.caching.headers)
    implementation(libs.ktor.server.conditional.headers)
    implementation(libs.ktor.serialization.json)
    implementation(libs.logback.classic)
    testImplementation(libs.ktor.server.test.host)
}
application {
    mainClass.set("org.panchang.api.MainKt")
}
