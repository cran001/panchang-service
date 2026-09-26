package org.panchang.publication

/** Launch eligibility is separate from implemented calculators and human publication approval. */
object PublicReleaseScope {
    const val revision = "first-release-iskcon-2026-09-16"
    val traditionIds: Set<String> = setOf("iskcon")
    fun includes(tradition: String): Boolean = tradition in traditionIds
    const val exclusionReason = "This tradition is outside the ISKCON-only first public release. Its calculation code remains available for local research. No alternative calendar is substituted."
}
