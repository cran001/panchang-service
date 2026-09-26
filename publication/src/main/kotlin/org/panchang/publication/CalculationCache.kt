package org.panchang.publication

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.*
import java.time.Instant
import kotlinx.serialization.Serializable
import org.panchang.calc.*
import org.panchang.sampradaya.SampradayaRules
import org.panchang.wire.*

/** This seam returns calculations only. It cannot store or grant publication permission. */
fun interface YearCalculation {
    fun calculate(site: ResolvedSite, rules: SampradayaRules, year: Int): CalcResult

    companion object {
        fun direct(engine: CalcEngine = CalcEngine()) = YearCalculation { site, rules, year ->
            engine.compute(site, rules, Scope.Year(year))
        }
    }
}

@Serializable
internal data class CalculationKey(
    val format: String = "year-calculation-cache-1-sha256",
    val place: CanonicalPlace,
    val year: Int,
    val tradition: String,
    val versions: Map<String, String>,
) {
    fun fingerprint() = digest(RecordJson.encodeToJsonElement(serializer(), this))
}

@Serializable
private data class CalculationPayload(
    val member: BundleMember,
    val computedAtUtc: String,
    val yearResolution: YearResolutionDto,
    val ekadashiYear: EkadashiYearDto,
) {
    fun fingerprint() = digest(RecordJson.encodeToJsonElement(serializer(), this))
}

@Serializable
private data class CalculationEntry(val key: CalculationKey, val payload: CalculationPayload, val sha256: String)

/**
 * Shared by cooperating processes on ONE local filesystem with reliable locks and atomic rename.
 * Trust/approval records and rendered public responses never enter this directory. A checksum
 * detects corruption, not a hostile writer capable of replacing both data and its checksum.
 */
class PersistentCalculationCache(
    directory: Path,
    private val delegate: YearCalculation = YearCalculation.direct(),
) : YearCalculation {
    private val root: Path
    init {
        Files.createDirectories(directory)
        require(!Files.isSymbolicLink(directory)) { "Calculation cache must be an operator-owned real directory" }
        root = directory.toRealPath()
    }

    override fun calculate(site: ResolvedSite, rules: SampradayaRules, year: Int): CalcResult {
        val key = CalculationKey(place = PublicationService.place(site), year = year, tradition = rules.id,
            versions = CalculationVersions.at(site.location.zone.id))
        val id = key.fingerprint()
        val entryPath = root.resolve("$id.json")
        val lockPath = root.resolve("$id.lock")
        // JVM stripes avoid overlapping-lock exceptions across cache instances without an
        // unbounded in-memory key map. The OS lock also serializes misses in different JVMs.
        synchronized(locks[(id.hashCode() and Int.MAX_VALUE) % locks.size]) {
            require(!Files.isSymbolicLink(lockPath) && !Files.isSymbolicLink(entryPath))
            FileChannel.open(lockPath, CREATE, WRITE).use { channel ->
                channel.lock().use {
                    if (Files.exists(entryPath)) {
                        require(Files.isRegularFile(entryPath) && Files.size(entryPath) <= MAX_ENTRY_BYTES) { "Invalid cache entry" }
                        val entry = RecordJson.decodeFromString(CalculationEntry.serializer(), Files.readString(entryPath))
                        require(entry.key == key && entry.sha256 == entry.payload.fingerprint()) { "Calculation cache integrity mismatch" }
                        Instant.parse(entry.payload.computedAtUtc)
                        val result = CalcResult(site, rules, Scope.Year(year), entry.payload.yearResolution, entry.payload.ekadashiYear)
                        validate(result, key)
                        require(PublicationService.member(result) == entry.payload.member) { "Cached provenance mismatch" }
                        return result
                    }
                    val result = delegate.calculate(site, rules, year)
                    validate(result, key)
                    val payload = CalculationPayload(PublicationService.member(result), Instant.now().toString(), result.yearResolution, result.ekadashiYear)
                    val entry = CalculationEntry(key, payload, payload.fingerprint())
                    val bytes = RecordJson.encodeToString(CalculationEntry.serializer(), entry).toByteArray(Charsets.UTF_8)
                    require(bytes.size <= MAX_ENTRY_BYTES)
                    val pending = Files.createTempFile(root, ".$id-", ".pending")
                    try {
                        FileChannel.open(pending, WRITE).use { output ->
                            val buffer = ByteBuffer.wrap(bytes)
                            while (buffer.hasRemaining()) output.write(buffer)
                            output.force(true)
                        }
                        // No non-atomic fallback. Unsupported filesystems fail closed.
                        Files.move(pending, entryPath, ATOMIC_MOVE)
                    } finally {
                        Files.deleteIfExists(pending)
                    }
                    return result
                }
            }
        }
    }

    private fun validate(result: CalcResult, key: CalculationKey) {
        require(result.scope == Scope.Year(key.year) && result.rules.id == key.tradition)
        require(PublicationService.place(result.site) == key.place)
        val expected = SiteDto(key.place.latitude, key.place.longitude, key.place.timeZone, key.place.elevationMeters)
        require(result.yearResolution.year == key.year && result.ekadashiYear.year == key.year)
        require(result.yearResolution.site == expected && result.ekadashiYear.site == expected)
        require(result.yearResolution.sampradaya.id == key.tradition && result.ekadashiYear.sampradaya.id == key.tradition)
    }

    companion object {
        private val locks = Array(256) { Any() }
        private const val MAX_ENTRY_BYTES = 32L * 1024 * 1024
    }
}

/** Configuration has no approval switch. Production trust/storage activation remains separate. */
object PublicationRuntime {
    fun service(environment: Map<String, String> = System.getenv()): PublicationService {
        require(environment["PANCHANG_APPROVAL_MODE"].let { it == null || it == "disabled" }) {
            "Production approval adapters are not activated; PANCHANG_APPROVAL_MODE must be disabled"
        }
        val directory = environment["PANCHANG_CALC_CACHE_DIR"]?.also { require(it.isNotBlank()) }
        val calculations = directory?.let { PersistentCalculationCache(Path.of(it)) } ?: YearCalculation.direct()
        return PublicationService(calculations = calculations)
    }
}
