// Ground-truth harvesters and the conformance harness. Never shipped.
plugins {
    alias(libs.plugins.kotlin.serialization)
}
dependencies {
    implementation(project(":sampradaya"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
}
