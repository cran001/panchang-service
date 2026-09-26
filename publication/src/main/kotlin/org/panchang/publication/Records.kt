package org.panchang.publication

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId

/** Publication state is independent of calculation confidence and test outcomes. */
@Serializable
enum class PublicationState {
    PENDING_REVIEW, DISPUTED, MISSING_EVIDENCE, UNSUPPORTED, APPROVED, REVOKED, SUPERSEDED,
    INVALID_RECORDS, REJECTED,
}

@Serializable
enum class Field { EVENTS, OBSERVANCES, DAILY_ASTRONOMY }

@Serializable
data class Coverage(val from: String, val through: String) {
    init { require(LocalDate.parse(from) <= LocalDate.parse(through)) }
    fun contains(date: String) = date >= from && date <= through
    fun contains(other: Coverage) = contains(other.from) && contains(other.through)
    fun overlaps(other: Coverage) = from <= other.through && other.from <= through
}

@Serializable
data class CanonicalPlace(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double,
    val timeZone: String,
) {
    init {
        require(id.isNotBlank())
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        require(elevationMeters.isFinite())
        require(timeZone in ZoneId.getAvailableZoneIds()) { "An IANA timezone is required" }
    }
}

@Serializable
data class Evidence(val link: String, val sha256: String, val outcome: String) {
    init { require(link.isNotBlank() && outcome.isNotBlank()); requireDigest(sha256) }
}

@Serializable
data class ReferencePolicy(
    val id: String,
    val revision: String,
    val description: String,
    val evidence: List<Evidence>,
) {
    init { require(id.isNotBlank() && revision.isNotBlank() && description.isNotBlank()) }
}

/** A whole reproducible calculation, not an individual astronomical instant. */
@Serializable
data class BundleMember(
    val place: CanonicalPlace,
    val tradition: String,
    val coverage: Coverage,
    val calculationYear: Int,
    val context: Coverage,
    val fields: Set<Field>,
    val versions: Map<String, String>,
    val resultSha256: String,
) {
    init {
        require(tradition.isNotBlank() && fields.isNotEmpty())
        require(LocalDate.parse(coverage.from).year == calculationYear && LocalDate.parse(coverage.through).year == calculationYear)
        require(context.contains(coverage))
        require(versions.keys.containsAll(REQUIRED_VERSIONS) && versions.values.all { it.isNotBlank() })
        requireDigest(resultSha256)
    }
    companion object {
        val REQUIRED_VERSIONS = setOf("calculation", "ruleCatalog", "ephemerisData", "wire", "gazetteer", "publication", "tzdb", "jvm")
    }
}

@Serializable
data class ReviewBundle(
    val schemaVersion: Int = 1,
    val purpose: String = "LOCAL_REVIEW_ONLY",
    val members: List<BundleMember>,
    val referencePolicy: ReferencePolicy,
    val disputeRegistrySha256: String,
    val evidence: List<Evidence>,
) {
    init {
        require(schemaVersion == 1 && purpose == "LOCAL_REVIEW_ONLY")
        require(members.isNotEmpty() && members.distinct().size == members.size)
        requireDigest(disputeRegistrySha256)
    }
    fun fingerprint(): String = digest(RecordJson.encodeToJsonElement(serializer(), this))
}

@Serializable
enum class Action { REVIEW, APPROVE, REJECT, REVOKE, SUPERSEDE }

@Serializable
enum class ReviewDecision { COMPLETED, DISPUTED, PENDING }

@Serializable
enum class ResolutionDecision { RESOLVED, WITHHOLD }

/** Neither an approved boolean nor a free text reviewer can resolve a known dispute. */
@Serializable
data class DisputeResolution(
    val disputeId: String,
    val decision: ResolutionDecision,
    val explanation: String,
    val evidence: List<Evidence>,
) {
    init { require(disputeId.isNotBlank() && explanation.isNotBlank() && evidence.isNotEmpty()) }
}

@Serializable
data class ReviewRecord(
    val schemaVersion: Int = 1,
    val environment: String = "LOCAL_REVIEW_ONLY",
    val sequence: Int,
    val previousSha256: String?,
    val id: String,
    val action: Action,
    val actorKeyId: String,
    val timestamp: String,
    val bundle: ReviewBundle,
    val reason: String,
    val evidence: List<Evidence>,
    val reviewDecision: ReviewDecision? = null,
    val reviewRecordIds: List<String> = emptyList(),
    val resolutions: List<DisputeResolution> = emptyList(),
    val reviewedQuestionIds: Set<String> = emptySet(),
    val targetRecordId: String? = null,
) {
    init {
        require(schemaVersion == 1 && environment == "LOCAL_REVIEW_ONLY" && sequence > 0)
        require(id.matches(Regex("[a-zA-Z0-9-]{1,100}")))
        require(actorKeyId.isNotBlank() && reason.isNotBlank())
        java.time.Instant.parse(timestamp)
        previousSha256?.let(::requireDigest)
        require(resolutions.map { it.disputeId }.distinct().size == resolutions.size)
    }
}

@Serializable
data class SignedRecord(val record: ReviewRecord, val signatureBase64: String)

@Serializable
data class TrustKey(val id: String, val publicKeyBase64: String, val qualifiedTraditions: Set<String> = emptySet())

/** Operator-supplied trust configuration; never read from an HTTP request or a journal record. */
@Serializable
data class LocalTrust(val ownerKeyId: String, val keys: List<TrustKey>, val environment: String = "LOCAL_REVIEW_ONLY") {
    init {
        require(environment == "LOCAL_REVIEW_ONLY" && ownerKeyId.isNotBlank())
        require(keys.map { it.id }.distinct().size == keys.size && keys.count { it.id == ownerKeyId } == 1)
    }
}

val RecordJson = Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = false; isLenient = false }

/** Sorted object keys make whitespace/order irrelevant; array order remains significant. */
fun canonical(value: JsonElement): String = when (value) {
    is JsonObject -> value.entries.sortedBy { it.key }.joinToString(",", "{", "}") {
        JsonPrimitive(it.key).toString() + ":" + canonical(it.value)
    }
    is JsonArray -> value.joinToString(",", "[", "]") { canonical(it) }
    else -> value.toString()
}

fun digest(value: JsonElement) = sha256(canonical(value).toByteArray(Charsets.UTF_8))
fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
fun requireDigest(value: String) { require(value.matches(Regex("[a-f0-9]{64}"))) { "Expected SHA-256" } }
