package org.panchang.publication

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption.*
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.Serializable

data class RecordSnapshot(val records: List<ReviewRecord> = emptyList(), val error: String? = null)

/** Fail-closed default. No production identity or storage adapter is configured by this project. */
fun interface RecordSource {
    fun read(): RecordSnapshot
    companion object { val DISABLED = RecordSource { RecordSnapshot() } }
}

@Serializable
private data class JournalHead(val sequence: Int, val sha256: String)

/**
 * Signed, append-only LOCAL workflow. Trust is supplied out of band by the owner.
 * The mutable checkpoint detects incomplete commits / ordinary truncation, not a hostile
 * rollback of both checkpoint and directory. Production requires an external monotonic anchor.
 */
class LocalJournal(private val directory: Path, private val trust: LocalTrust) : RecordSource {
    override fun read(): RecordSnapshot = try {
        RecordSnapshot(readChecked().map { it.record })
    } catch (e: Exception) {
        RecordSnapshot(error = "Invalid or incomplete local approval journal; publication is blocked")
    }

    private fun readChecked(): List<SignedRecord> {
        if (!Files.exists(directory)) return emptyList()
        require(Files.isDirectory(directory) && !Files.isSymbolicLink(directory))
        val paths = Files.list(directory).use { it.sorted().toList() }
        require(paths.all { !Files.isSymbolicLink(it) && (Files.isRegularFile(it) || it.fileName.toString() == "evidence" && Files.isDirectory(it)) })
        require(paths.all { it.fileName.toString() in setOf("evidence", ".lock", "HEAD.json") || it.fileName.toString().matches(Regex("[0-9]{8}\\.json")) }) {
            "Unexpected or partially written journal file"
        }
        val records = paths.filter { it.fileName.toString().matches(Regex("[0-9]{8}\\.json")) }.map {
            require(Files.size(it) <= 20_000_000)
            RecordJson.decodeFromString<SignedRecord>(Files.readString(it))
        }
        var previous: String? = null
        val seen = ArrayList<ReviewRecord>()
        records.forEachIndexed { index, signed ->
            val r = signed.record
            require(r.sequence == index + 1 && r.previousSha256 == previous)
            require(Files.exists(directory.resolve("%08d.json".format(r.sequence))))
            verify(signed, trust)
            validateAction(r, seen, trust)
            validateEvidence(r)
            previous = hash(signed)
            seen += r
        }
        val headPath = directory.resolve("HEAD.json")
        if (records.isEmpty()) require(!Files.exists(headPath)) else {
            val head = RecordJson.decodeFromString<JournalHead>(Files.readString(headPath))
            require(head.sequence == records.size && head.sha256 == previous)
        }
        return records
    }

    /** Caller supplies a signed command; signing cannot grant a role absent from trusted config. */
    fun append(signed: SignedRecord) {
        Files.createDirectories(directory)
        FileChannel.open(directory.resolve(".lock"), CREATE, WRITE).use { channel ->
            channel.lock().use {
                val previous = readChecked()
                require(signed.record.sequence == previous.size + 1)
                require(signed.record.previousSha256 == previous.lastOrNull()?.let(::hash))
                verify(signed, trust)
                validateAction(signed.record, previous.map { it.record }, trust)
                validateEvidence(signed.record)
                val target = directory.resolve("%08d.json".format(signed.record.sequence))
                val temp = directory.resolve("${target.fileName}.pending")
                durableWrite(temp, RecordJson.encodeToString(SignedRecord.serializer(), signed))
                require(!Files.exists(target))
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE)
                val headTemp = directory.resolve("HEAD.pending")
                durableWrite(headTemp, RecordJson.encodeToString(JournalHead.serializer(), JournalHead(signed.record.sequence, hash(signed))))
                Files.move(headTemp, directory.resolve("HEAD.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    /** Metadata for preparing the next signed command, verified against the entire history. */
    fun next(): Pair<Int, String?> = readChecked().let { it.size + 1 to it.lastOrNull()?.let(::hash) }

    /** Content-addressed copies prevent edited source documents from silently changing history. */
    fun importEvidence(evidence: Evidence, bytes: ByteArray) {
        require(sha256(bytes) == evidence.sha256)
        val archive = directory.resolve("evidence")
        Files.createDirectories(archive)
        val target = archive.resolve(evidence.sha256)
        if (Files.exists(target)) require(sha256(Files.readAllBytes(target)) == evidence.sha256)
        else Files.write(target, bytes, CREATE_NEW)
    }

    private fun validateEvidence(record: ReviewRecord) {
        evidenceFor(record).forEach {
            val path = directory.resolve("evidence").resolve(it.sha256)
            require(!Files.isSymbolicLink(path) && sha256(Files.readAllBytes(path)) == it.sha256) { "Missing or changed evidence archive" }
        }
    }

    companion object {
        fun evidenceFor(record: ReviewRecord): List<Evidence> = record.evidence + record.bundle.evidence +
            record.bundle.referencePolicy.evidence + record.resolutions.flatMap { it.evidence }
        fun hash(signed: SignedRecord) = digest(RecordJson.encodeToJsonElement(SignedRecord.serializer(), signed))
        private fun bytes(record: ReviewRecord) = canonical(RecordJson.encodeToJsonElement(ReviewRecord.serializer(), record)).toByteArray(Charsets.UTF_8)
        fun sign(record: ReviewRecord, privateKey: PrivateKey): SignedRecord {
            val signature = Signature.getInstance("Ed25519")
            signature.initSign(privateKey)
            signature.update(bytes(record))
            return SignedRecord(record, Base64.getEncoder().encodeToString(signature.sign()))
        }
        fun verify(signed: SignedRecord, trust: LocalTrust) {
            val key = requireNotNull(trust.keys.singleOrNull { it.id == signed.record.actorKeyId }) { "Unknown signing identity" }
            val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(key.publicKeyBase64)))
            val signature = Signature.getInstance("Ed25519")
            signature.initVerify(publicKey)
            signature.update(bytes(signed.record))
            require(signature.verify(Base64.getDecoder().decode(signed.signatureBase64))) { "Invalid signature" }
        }

        private fun validateAction(r: ReviewRecord, history: List<ReviewRecord>, trust: LocalTrust) {
            require(history.none { it.id == r.id }) { "Duplicate record ID" }
            require(Instant.parse(r.timestamp) <= Instant.now().plusSeconds(60)) { "Future decision" }
            history.lastOrNull()?.let { require(Instant.parse(r.timestamp) >= Instant.parse(it.timestamp)) }
            val key = trust.keys.single { it.id == r.actorKeyId }
            if (r.action == Action.REVIEW) {
                require(key.qualifiedTraditions.containsAll(r.bundle.members.map { it.tradition })) { "Reviewer is not qualified for this tradition scope" }
                require(r.reviewDecision != null && r.evidence.isNotEmpty())
                require(r.targetRecordId == null && r.reviewRecordIds.isEmpty())
                return
            }
            require(r.actorKeyId == trust.ownerKeyId) { "Only the configured owner may decide publication" }
            require(r.reviewDecision == null && r.resolutions.isEmpty() && r.reviewedQuestionIds.isEmpty()) { "Dispute resolutions and question reviews belong to a qualified review" }
            when (r.action) {
                Action.APPROVE -> {
                    require(r.targetRecordId == null && r.reviewRecordIds.isNotEmpty())
                    require(r.bundle.evidence.isNotEmpty() && r.bundle.referencePolicy.evidence.isNotEmpty() && r.evidence.isNotEmpty())
                    val reviews = r.reviewRecordIds.map { id -> requireNotNull(history.singleOrNull { it.id == id }) }
                    require(reviews.all { it.action == Action.REVIEW && it.reviewDecision == ReviewDecision.COMPLETED && it.bundle == r.bundle })
                    val latestReview = history.last { it.action == Action.REVIEW && it.bundle == r.bundle }
                    require(latestReview.reviewDecision == ReviewDecision.COMPLETED && latestReview.id in r.reviewRecordIds) {
                        "A reopened review cannot be bypassed using an older completed review"
                    }
                }
                Action.REVOKE, Action.SUPERSEDE -> {
                    val target = requireNotNull(history.singleOrNull { it.id == r.targetRecordId })
                    require(target.action == Action.APPROVE && target.bundle == r.bundle)
                    require(history.none { it.targetRecordId == target.id && it.action in setOf(Action.REVOKE, Action.SUPERSEDE) })
                }
                Action.REJECT -> require(r.targetRecordId == null)
                Action.REVIEW -> error("handled above")
            }
        }

        private fun durableWrite(path: Path, value: String) {
            FileChannel.open(path, CREATE_NEW, WRITE).use { channel ->
                val bytes = java.nio.ByteBuffer.wrap(value.toByteArray(Charsets.UTF_8))
                while (bytes.hasRemaining()) channel.write(bytes)
                channel.force(true)
            }
        }
    }
}
