package org.panchang.publication

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.serialization.Serializable
import org.panchang.calc.*
import org.panchang.core.GeoLocation
import org.panchang.wire.SiteAcceptance
import org.panchang.wire.acceptSite

@Serializable
data class PrepareMember(val place: CanonicalPlace, val tradition: String, val year: Int,
    val coverage: Coverage, val fields: Set<Field>)

@Serializable
data class PrepareRequest(val members: List<PrepareMember>, val referencePolicy: ReferencePolicy, val evidence: List<Evidence>)

@Serializable
data class LocalCommand(
    val action: Action,
    val actorKeyId: String,
    val bundleFile: String,
    val reason: String,
    val evidence: List<Evidence>,
    val reviewDecision: ReviewDecision? = null,
    val reviewRecordIds: List<String> = emptyList(),
    val resolutions: List<DisputeResolution> = emptyList(),
    val reviewedQuestionIds: Set<String> = emptySet(),
    val targetRecordId: String? = null,
)

/** Offline only. No key generation, invented identities, HTTP writes, environment credentials or production mode. */
fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "Usage: prepare REQUEST.json NEW-DIRECTORY | record TRUST.json JOURNAL COMMAND.json PRIVATE-KEY.pk8 | history TRUST.json JOURNAL | evaluate TRUST.json JOURNAL BUNDLE.json" }
    when (args[0]) {
        "prepare" -> {
            require(args.size == 3)
            val request = RecordJson.decodeFromString<PrepareRequest>(Files.readString(Path.of(args[1])))
            val directory = Path.of(args[2])
            require(!Files.exists(directory)) { "Review output must be a new directory" }
            val registry = DisputeRegistry.current()
            val calculated = request.members.map { m ->
                require(acceptSite(m.place.latitude, m.place.longitude, m.place.timeZone, m.place.elevationMeters) is SiteAcceptance.Accepted)
                val site = LocationResolver().byCoordinates(GeoLocation(m.place.latitude, m.place.longitude, ZoneId.of(m.place.timeZone), m.place.elevationMeters), "local review")
                val raw = CalcEngine().compute(site, requireNotNull(Sampradayas[m.tradition]), Scope.Year(m.year))
                val member = PublicationService.member(raw)
                require(member.coverage.contains(m.coverage) && member.fields.containsAll(m.fields))
                // Canonical ID is deterministically derived from the exact point; submitted labels cannot change identity.
                member.copy(coverage = m.coverage, fields = m.fields) to raw
            }
            val bundle = ReviewBundle(members = calculated.map { it.first }, referencePolicy = request.referencePolicy,
                disputeRegistrySha256 = registry.fingerprint(), evidence = request.evidence)
            Files.createDirectories(directory.resolve("review"))
            Files.writeString(directory.resolve("bundle.json"), RecordJson.encodeToString(ReviewBundle.serializer(), bundle), CREATE_NEW)
            Files.writeString(directory.resolve("fingerprint.txt"), bundle.fingerprint(), CREATE_NEW)
            calculated.forEachIndexed { index, pair -> Files.writeString(directory.resolve("review/calculated-$index.json"), CalcJson.render(pair.second), CREATE_NEW) }
            Files.writeString(directory.resolve("review/disputes.json"), RecordJson.encodeToString(DisputeRegistry.serializer(), registry), CREATE_NEW)
            Files.writeString(directory.resolve("review/NOT-FOR-PUBLICATION.txt"), "CALCULATED DIAGNOSTICS ONLY. No qualified review or owner approval has been created.\n", CREATE_NEW)
            println("Prepared local review bundle ${bundle.fingerprint()}; no approval created")
        }
        "record" -> {
            require(args.size == 5)
            val trust = RecordJson.decodeFromString<LocalTrust>(Files.readString(Path.of(args[1])))
            val journal = LocalJournal(Path.of(args[2]), trust)
            val command = RecordJson.decodeFromString<LocalCommand>(Files.readString(Path.of(args[3])))
            val bundle = RecordJson.decodeFromString<ReviewBundle>(Files.readString(Path.of(command.bundleFile)))
            val next = journal.next()
            val record = ReviewRecord(sequence = next.first, previousSha256 = next.second, id = UUID.randomUUID().toString(),
                action = command.action, actorKeyId = command.actorKeyId, timestamp = Instant.now().toString(), bundle = bundle,
                reason = command.reason, evidence = command.evidence, reviewDecision = command.reviewDecision,
                reviewRecordIds = command.reviewRecordIds, resolutions = command.resolutions,
                reviewedQuestionIds = command.reviewedQuestionIds, targetRecordId = command.targetRecordId)
            val key = KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(Files.readAllBytes(Path.of(args[4]))))
            val signed = LocalJournal.sign(record, key)
            LocalJournal.verify(signed, trust)
            // The CLI accepts local evidence files only. Remote URLs must first be independently archived.
            LocalJournal.evidenceFor(record).distinct().forEach { journal.importEvidence(it, Files.readAllBytes(Path.of(it.link))) }
            journal.append(signed)
            println("LOCAL_REVIEW_ONLY ${record.action}: ${record.id}. Production approval writes remain disabled.")
        }
        "history" -> {
            require(args.size == 3)
            val trust = RecordJson.decodeFromString<LocalTrust>(Files.readString(Path.of(args[1])))
            val snapshot = LocalJournal(Path.of(args[2]), trust).read()
            require(snapshot.error == null) { snapshot.error!! }
            snapshot.records.forEach { println("${it.sequence} ${it.timestamp} ${it.id} ${it.action} actor=${it.actorKeyId} bundle=${it.bundle.fingerprint()} target=${it.targetRecordId ?: "-"}") }
        }
        "evaluate" -> {
            require(args.size == 4)
            val trust = RecordJson.decodeFromString<LocalTrust>(Files.readString(Path.of(args[1])))
            val journal = LocalJournal(Path.of(args[2]), trust)
            val bundle = RecordJson.decodeFromString<ReviewBundle>(Files.readString(Path.of(args[3])))
            val policy = PublicationPolicy(referencePolicy = bundle.referencePolicy, source = journal)
            for (m in bundle.members) {
                val site = LocationResolver().byCoordinates(GeoLocation(m.place.latitude, m.place.longitude, ZoneId.of(m.place.timeZone), m.place.elevationMeters), "local review")
                val current = PublicationService.member(CalcEngine().compute(site, requireNotNull(Sampradayas[m.tradition]), Scope.Year(m.calculationYear)))
                for (field in m.fields) println(RecordJson.encodeToString(PublicationDecision.serializer(), policy.decide(current, m.coverage, field)))
            }
        }
        else -> error("Unknown local review command; production writes are unavailable")
    }
}
