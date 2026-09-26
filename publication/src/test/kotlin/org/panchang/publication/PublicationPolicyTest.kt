package org.panchang.publication

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PublicationPolicyTest {
    @TempDir lateinit var directory: Path
    private fun fixture() = TestApprovals(directory)
    private fun member() = BundleMember(
        CanonicalPlace("canonical-test-point", 23.416666666666668, 88.38333333333334, 0.0, "Asia/Kolkata"),
        "iskcon", Coverage("2026-01-01", "2026-12-31"), 2026, Coverage("2025-12-01", "2027-01-31"),
        setOf(Field.EVENTS, Field.OBSERVANCES), BundleMember.REQUIRED_VERSIONS.associateWith { "TEST-VERSION-1" }, "a".repeat(64),
    )
    private val day = Coverage("2026-01-01", "2026-01-01")

    @Test fun `launch exclusion blocks events as well as observances despite signed local approval`() {
        val f = fixture()
        val m = member().copy(tradition = "marathi")
        f.approve(m)
        for (field in m.fields) {
            assertEquals(PublicationState.UNSUPPORTED, f.policy.decide(m, day, field).state)
        }
    }

    @Test fun `calculated confirmed verified and passing tests do not imply approval`() {
        val f = fixture()
        assertEquals("TEST_ONLY_PASS", f.evidence.outcome)
        assertEquals(PublicationState.PENDING_REVIEW, f.policy.decide(member(), day, Field.OBSERVANCES).state)
        val review = f.record(f.bundle(member()), Action.REVIEW)
        assertEquals(ReviewDecision.COMPLETED, review.reviewDecision)
        assertEquals(PublicationState.PENDING_REVIEW, f.policy.decide(member(), day, Field.OBSERVANCES).state)
        assertEquals(PublicationState.MISSING_EVIDENCE, PublicationPolicy().decide(member(), day, Field.OBSERVANCES).state)
    }

    @Test fun `exact owner approved scope publishes matching results and carries provenance`() {
        val f = fixture()
        val approval = f.approve(member())
        val d = f.policy.decide(member(), day, Field.OBSERVANCES)
        assertEquals(PublicationState.APPROVED, d.state)
        assertEquals("AVAILABLE", d.guidance)
        assertEquals(approval.id, d.approvalRecordId)
        assertEquals(approval.bundle.fingerprint(), d.bundleFingerprint)
        assertEquals(approval.actorKeyId, d.approverKeyId)
        assertEquals(approval.timestamp, d.approvedAt)
    }

    @Test fun `different place coordinates elevation timezone tradition year result and every version miss approval`() {
        val f = fixture()
        val m = member()
        f.approve(m)
        val variants = listOf(
            m.copy(place = m.place.copy(id = "other-point")),
            m.copy(place = m.place.copy(latitude = m.place.latitude + 0.001)),
            m.copy(place = m.place.copy(longitude = m.place.longitude + 0.001)),
            m.copy(place = m.place.copy(elevationMeters = 1.0)),
            m.copy(place = m.place.copy(timeZone = "Asia/Colombo")),
            m.copy(tradition = "marathi"),
            m.copy(calculationYear = 2027, coverage = Coverage("2027-01-01", "2027-12-31"), context = Coverage("2026-12-01", "2028-01-31")),
            m.copy(context = Coverage("2025-11-30", "2027-01-31")),
            m.copy(resultSha256 = "b".repeat(64)),
        ) + m.versions.keys.map { key -> m.copy(versions = m.versions + (key to "TEST-VERSION-2")) }
        for (variant in variants) assertNotEquals(PublicationState.APPROVED, f.policy.decide(variant, variant.coverage, Field.OBSERVANCES).state, variant.toString())
    }

    @Test fun `date and field scopes cannot expand themselves`() {
        val f = fixture()
        f.approve(member().copy(coverage = day, fields = setOf(Field.OBSERVANCES)))
        assertEquals(PublicationState.APPROVED, f.policy.decide(member(), day, Field.OBSERVANCES).state)
        assertEquals(PublicationState.PENDING_REVIEW, f.policy.decide(member(), Coverage("2026-01-02", "2026-01-02"), Field.OBSERVANCES).state)
        assertEquals(PublicationState.PENDING_REVIEW, f.policy.decide(member(), day, Field.EVENTS).state)
    }

    @Test fun `reference policy and dispute registry revisions require new approval`() {
        val f = fixture()
        f.approve(member())
        for (policy in listOf(
            PublicationPolicy(f.registry, f.referencePolicy.copy(revision = "2"), f.journal),
            PublicationPolicy(f.registry.copy(revision = "next"), f.referencePolicy, f.journal),
            PublicationPolicy(f.registry, f.referencePolicy.copy(evidence = listOf(f.evidence.copy(sha256 = "b".repeat(64)))), f.journal),
        )) assertEquals(PublicationState.PENDING_REVIEW, policy.decide(member(), day, Field.OBSERVANCES).state)
    }

    @Test fun `known dispute cannot publish through approved flag or unresolved qualified review`() {
        val f = fixture()
        val issue = f.registry.disputes.single { it.id == "OBSERVANCE-01" }
        val m = member().copy(place = issue.place)
        f.approve(m, resolveDisputes = false)
        val d = f.policy.decide(m, issue.coverage, Field.OBSERVANCES)
        assertEquals(PublicationState.DISPUTED, d.state)
        assertEquals(listOf("OBSERVANCE-01"), d.issues.map { it.id })
    }

    @Test fun `explicit reviewed resolution permits exact disputed scope only`() {
        val f = fixture()
        val issue = f.registry.disputes.single { it.id == "OBSERVANCE-01" }
        val m = member().copy(place = issue.place)
        f.approve(m)
        assertEquals(PublicationState.APPROVED, f.policy.decide(m, issue.coverage, Field.OBSERVANCES).state)
        assertNotEquals(PublicationState.APPROVED, f.policy.decide(m.copy(resultSha256 = "b".repeat(64)), issue.coverage, Field.OBSERVANCES).state)
    }

    @Test fun `approved withholding policy retains the dispute and never publishes its timing`() {
        val f = fixture()
        val issue = f.registry.disputes.single { it.id == "OBSERVANCE-01" }
        val m = member().copy(place = issue.place)
        val bundle = f.bundle(m)
        val review = f.record(bundle, Action.REVIEW, resolutions = listOf(DisputeResolution(issue.id,
            ResolutionDecision.WITHHOLD, "TEST ONLY explicit withholding", listOf(f.evidence))))
        f.record(bundle, Action.APPROVE, reviews = listOf(review.id))
        assertEquals(PublicationState.DISPUTED, f.policy.decide(m, issue.coverage, Field.OBSERVANCES).state)
    }

    @Test fun `revocation invalidates subsequent reads and preserves approval history`() {
        val f = fixture()
        val approval = f.approve(member())
        f.record(approval.bundle, Action.REVOKE, target = approval.id)
        assertEquals(PublicationState.REVOKED, f.policy.decide(member(), day, Field.OBSERVANCES).state)
        assertEquals(3, LocalJournal(directory, f.trust).read().records.size)
        assertEquals(approval, f.journal.read().records[1])
    }

    @Test fun `superseded approval cannot authorize a release`() {
        val f = fixture()
        val approval = f.approve(member())
        f.record(approval.bundle, Action.SUPERSEDE, target = approval.id)
        assertEquals(PublicationState.SUPERSEDED, f.policy.decide(member(), day, Field.OBSERVANCES).state)
    }

    @Test fun `qualified review reopening a bundle blocks prior publication`() {
        val f = fixture()
        val approval = f.approve(member())
        f.record(approval.bundle, Action.REVIEW, reviewDecision = ReviewDecision.DISPUTED)
        assertEquals(PublicationState.DISPUTED, f.policy.decide(member(), day, Field.OBSERVANCES).state)
        assertThrows(IllegalArgumentException::class.java) {
            f.record(approval.bundle, Action.APPROVE, reviews = approval.reviewRecordIds)
        }
    }

    @Test fun `owner rejection prevents use of a prior approval`() {
        val f = fixture()
        val approval = f.approve(member())
        f.record(approval.bundle, Action.REJECT)
        assertEquals(PublicationState.REJECTED, f.policy.decide(member(), day, Field.OBSERVANCES).state)
    }

    @Test fun `unanswered general review questions and missing evidence remain blocked`() {
        val f = fixture()
        val m = member()
        val bundle = f.bundle(m)
        val completed = f.record(bundle, Action.REVIEW)
        val next = f.journal.next()
        val unanswered = completed.copy(sequence = next.first, previousSha256 = next.second, id = "unanswered", reviewedQuestionIds = emptySet())
        f.journal.append(f.signAsReviewer(unanswered))
        f.record(bundle, Action.APPROVE, reviews = listOf(unanswered.id))
        assertEquals(PublicationState.MISSING_EVIDENCE, f.policy.decide(m, day, Field.OBSERVANCES).state)
        val emptyEvidence = bundle.copy(evidence = emptyList())
        val review = f.record(emptyEvidence, Action.REVIEW)
        assertThrows(IllegalArgumentException::class.java) { f.record(emptyEvidence, Action.APPROVE, reviews = listOf(review.id)) }
    }

    @Test fun `anonymous forged and reviewer publication attempts fail`() {
        val f = fixture()
        f.approve(member())
        val original = RecordJson.decodeFromString<SignedRecord>(Files.readString(directory.resolve("00000002.json")))
        for (actor in listOf("", "anonymous", "TEST-ONLY-reviewer")) {
            assertThrows(IllegalArgumentException::class.java) {
                val next = original.record.copy(sequence = 3, previousSha256 = LocalJournal.hash(original), id = "forged", actorKeyId = actor)
                f.journal.append(original.copy(record = next))
            }
        }
        val ownerless = """{"ownerKeyId":"unknown","keys":[],"environment":"LOCAL_REVIEW_ONLY"}"""
        assertThrows(IllegalArgumentException::class.java) { RecordJson.decodeFromString<LocalTrust>(ownerless) }
        assertEquals(2, f.journal.read().records.size)
    }

    @Test fun `malformed partially written and tampered journal records fail closed`() {
        val f = fixture()
        f.approve(member())
        val path = directory.resolve("00000002.json")
        val original = Files.readString(path)
        for (bad in listOf("{", original.replace("TEST-ONLY-owner", "forged-owner"), original.replace("TEST-VERSION-1", "TEST-VERSION-2"))) {
            Files.writeString(path, bad)
            assertEquals(PublicationState.INVALID_RECORDS, f.policy.decide(member(), day, Field.OBSERVANCES).state)
        }
        Files.writeString(path, original)
        Files.writeString(directory.resolve("00000003.json.pending"), "partial")
        assertEquals(PublicationState.INVALID_RECORDS, f.policy.decide(member(), day, Field.OBSERVANCES).state)
    }

    @Test fun `valid reviewer signature does not grant owner powers and owner is not automatically qualified`() {
        val f = fixture()
        val approval = f.approve(member())
        val next = f.journal.next()
        val attempt = approval.copy(sequence = next.first, previousSha256 = next.second, id = "reviewer-publish-attempt", actorKeyId = "TEST-ONLY-reviewer")
        assertThrows(IllegalArgumentException::class.java) { f.journal.append(f.signAsReviewer(attempt)) }
        val review = attempt.copy(id = "unqualified-owner-review", actorKeyId = "TEST-ONLY-owner", action = Action.REVIEW,
            reviewDecision = ReviewDecision.COMPLETED, reviewRecordIds = emptyList())
        assertThrows(IllegalArgumentException::class.java) { f.journal.append(f.signAsOwner(review)) }
        assertEquals(2, f.journal.read().records.size)
    }

    @Test fun `truncated history and missing or altered archived evidence fail closed`() {
        val f = fixture()
        f.approve(member())
        val archive = directory.resolve("evidence").resolve(f.evidence.sha256)
        Files.writeString(archive, "changed")
        assertEquals(PublicationState.INVALID_RECORDS, f.policy.decide(member(), day, Field.OBSERVANCES).state)
        Files.write(archive, f.evidenceBytes)
        Files.delete(directory.resolve("00000002.json"))
        assertEquals(PublicationState.INVALID_RECORDS, f.policy.decide(member(), day, Field.OBSERVANCES).state)
    }

    @Test fun `registry retains separate numerical observance daily and missing reference cases`() {
        val registry = DisputeRegistry.current()
        assertEquals(47, registry.disputes.size)
        assertTrue(registry.disputes.map { it.id }.containsAll(listOf("ASTRONOMY-01", "OBSERVANCE-01", "OBSERVANCE-02",
            "OBSERVANCE-03-moscow", "OBSERVANCE-03-sydney", "OPEN-ENDED-mumbai", "AHMEDABAD-NOVEMBER-BASIS")))
        assertEquals(PublicationState.UNSUPPORTED, registry.disputes.single { it.id == "REYKJAVIK-MULTIPLE-MOONSETS" }.state)
        assertTrue(registry.disputes.any { it.state == PublicationState.MISSING_EVIDENCE })
        assertTrue(registry.disputes.all { it.evidence.isNotEmpty() && it.affectedDetails.isNotEmpty() })
    }
}
