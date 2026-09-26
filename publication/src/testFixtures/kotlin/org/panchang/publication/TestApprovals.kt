package org.panchang.publication

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.time.Instant
import java.util.Base64
import java.util.UUID

/** TEST ONLY. Ephemeral identities/decisions are never bundled in production artifacts. */
class TestApprovals(val directory: Path = Files.createTempDirectory("panchang-TEST-ONLY-")) {
    private val owner = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val reviewer = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    val trust = LocalTrust("TEST-ONLY-owner", listOf(
        TrustKey("TEST-ONLY-owner", Base64.getEncoder().encodeToString(owner.public.encoded)),
        TrustKey("TEST-ONLY-reviewer", Base64.getEncoder().encodeToString(reviewer.public.encoded),
            setOf("iskcon", "marathi", "telugu", "kannada", "gujarati", "northindian", "tamil", "malayalam", "bengali", "odia")),
    ))
    val journal = LocalJournal(directory, trust)
    val evidenceBytes = "TEST ONLY synthetic evidence; not a religious finding or real approval".toByteArray()
    val evidence = Evidence("test-only:synthetic", sha256(evidenceBytes), "TEST_ONLY_PASS")
    val referencePolicy = ReferencePolicy("TEST-ONLY-policy", "1", "Synthetic test fixture only", listOf(evidence))
    val registry = DisputeRegistry.current()
    val policy get() = PublicationPolicy(registry, referencePolicy, journal)
    fun signAsReviewer(record: ReviewRecord) = LocalJournal.sign(record, reviewer.private)
    fun signAsOwner(record: ReviewRecord) = LocalJournal.sign(record, owner.private)

    fun bundle(member: BundleMember) = ReviewBundle(members = listOf(member), referencePolicy = referencePolicy,
        disputeRegistrySha256 = registry.fingerprint(), evidence = listOf(evidence))

    fun record(bundle: ReviewBundle, action: Action, reviews: List<String> = emptyList(),
        resolutions: List<DisputeResolution> = emptyList(), target: String? = null,
        reviewDecision: ReviewDecision = ReviewDecision.COMPLETED): ReviewRecord {
        journal.importEvidence(evidence, evidenceBytes)
        val next = journal.next()
        val record = ReviewRecord(sequence = next.first, previousSha256 = next.second,
            id = UUID.randomUUID().toString(), action = action,
            actorKeyId = if (action == Action.REVIEW) "TEST-ONLY-reviewer" else "TEST-ONLY-owner",
            timestamp = Instant.now().toString(), bundle = bundle, reason = "TEST ONLY, not an actual qualified or publishing decision",
            evidence = listOf(evidence), reviewDecision = if (action == Action.REVIEW) reviewDecision else null,
            reviewRecordIds = reviews, resolutions = resolutions,
            reviewedQuestionIds = if (action == Action.REVIEW) registry.reviewQuestions.map { it.id }.toSet() else emptySet(), targetRecordId = target)
        journal.append(LocalJournal.sign(record, if (action == Action.REVIEW) reviewer.private else owner.private))
        return record
    }

    fun approve(member: BundleMember, resolveDisputes: Boolean = true): ReviewRecord {
        val bundle = bundle(member)
        val resolutions = if (resolveDisputes) registry.disputes.filter { d -> member.fields.any { d.affects(member, member.coverage, it) } }.map {
            DisputeResolution(it.id, ResolutionDecision.RESOLVED, "TEST ONLY invented outcome exercises the policy; never real approval", listOf(evidence))
        } else emptyList()
        val review = record(bundle, Action.REVIEW, resolutions = resolutions)
        return record(bundle, Action.APPROVE, reviews = listOf(review.id))
    }

    companion object {
        /** Explicit opt-in at test call sites; production has no analogous configuration. */
        fun service(): PublicationService {
            val fixtures = mutableMapOf<BundleMember, TestApprovals>()
            return PublicationService(policyFor = { member ->
                fixtures.getOrPut(member) { TestApprovals().also { it.approve(member) } }.policy
            })
        }
    }
}
