plugins {
    alias(libs.plugins.kotlin.serialization)
    application
}
dependencies {
    implementation(project(":sampradaya"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.clikt)
}
application {
    mainClass.set("org.panchang.publish.MainKt")
}
