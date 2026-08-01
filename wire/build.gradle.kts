// Serialisable wire types shared by `:api` and `:publish`.
//
// Registered here so the module graph is settled before the types land; the source set is
// intentionally empty at this commit.
plugins {
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
