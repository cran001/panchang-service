package org.panchang.publication

import kotlinx.serialization.Serializable

@Serializable
data class Dispute(
    val id: String,
    val place: CanonicalPlace,
    val tradition: String,
    val coverage: Coverage,
    val fields: Set<Field>,
    val affectedDetails: List<String>,
    val observedRevision: String,
    val versionApplicability: String = "OBSERVED_AND_UNREVIEWED_SUCCESSORS",
    val state: PublicationState = PublicationState.DISPUTED,
    val explanation: String,
    val evidence: List<Evidence>,
) {
    init {
        require(id.isNotBlank() && fields.isNotEmpty() && affectedDetails.isNotEmpty())
        require(observedRevision.isNotBlank() && versionApplicability == "OBSERVED_AND_UNREVIEWED_SUCCESSORS")
        require(state in setOf(PublicationState.DISPUTED, PublicationState.MISSING_EVIDENCE, PublicationState.UNSUPPORTED))
        require(explanation.isNotBlank() && evidence.isNotEmpty())
    }
    fun affects(member: BundleMember, dates: Coverage, field: Field): Boolean =
        tradition == member.tradition && field in fields && coverage.overlaps(dates) &&
            place.latitude == member.place.latitude && place.longitude == member.place.longitude &&
            place.elevationMeters == member.place.elevationMeters && place.timeZone == member.place.timeZone
}

@Serializable
data class ReviewQuestion(val id: String, val traditions: Set<String>, val fields: Set<Field>, val description: String, val evidence: List<Evidence>) {
    init { require(id.isNotBlank() && traditions.isNotEmpty() && fields.isNotEmpty() && description.isNotBlank() && evidence.isNotEmpty()) }
}

@Serializable
data class DisputeRegistry(val revision: String, val disputes: List<Dispute>, val reviewQuestions: List<ReviewQuestion> = emptyList()) {
    init {
        require(revision.isNotBlank() && disputes.map { it.id }.distinct().size == disputes.size)
        require(reviewQuestions.map { it.id }.distinct().size == reviewQuestions.size)
    }
    fun fingerprint() = digest(RecordJson.encodeToJsonElement(serializer(), this))
    companion object {
        fun current(): DisputeRegistry = RecordJson.decodeFromString(
            requireNotNull(DisputeRegistry::class.java.getResourceAsStream("/org/panchang/publication/disputes.json")) {
                "Required dispute registry is missing; publication must stop"
            }.use { it.readBytes().toString(Charsets.UTF_8) },
        )
    }
}
