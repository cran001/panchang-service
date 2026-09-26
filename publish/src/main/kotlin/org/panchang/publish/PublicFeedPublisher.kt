package org.panchang.publish

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlinx.serialization.json.*
import org.panchang.calc.LocationResolver
import org.panchang.calc.Sampradayas
import org.panchang.calc.Scope
import org.panchang.core.GeoLocation
import org.panchang.gazetteer.Gazetteer
import org.panchang.publication.PublicationService
import org.panchang.wire.SiteAcceptance
import org.panchang.wire.WireJson
import org.panchang.wire.acceptSite

/** Public artifact producer. The diagnostic/legacy renderer is a separate review-only path. */
class FeedPublisher(
    private val publication: PublicationService = PublicationService(),
    private val resolver: LocationResolver = LocationResolver(),
) {
    fun run(specs: List<SiteSpec>, year: Int, sampradayaId: String = "iskcon"): PublishResult {
        require(org.panchang.publication.PublicReleaseScope.includes(sampradayaId)) {
            org.panchang.publication.PublicReleaseScope.exclusionReason
        }
        val rules = requireNotNull(Sampradayas[sampradayaId]) { "Unknown tradition $sampradayaId" }
        require(specs.map { it.key }.distinct().size == specs.size) { "Duplicate site keys" }
        val files = linkedMapOf<String, String>()
        val skipped = arrayListOf<SkippedSite>()
        val withheld = arrayListOf<LegacyWithheld>()
        val checks = arrayListOf<() -> Boolean>()
        var approved = 0
        for (spec in specs) {
            if (spec is SiteSpec.Malformed) {
                skipped += SkippedSite(spec.key, spec.query, inputRejectionCode = InputRejectionCode.MALFORMED_SITE_LINE, reason = spec.problem)
                continue
            }
            require(LegacyFeed.SITE_KEY.matches(spec.key))
            val site = when (spec) {
                is SiteSpec.Coordinates -> {
                    val accepted = acceptSite(spec.latitude, spec.longitude, spec.zoneId)
                    if (accepted is SiteAcceptance.Rejected) {
                        skipped += SkippedSite(spec.key, spec.query, siteRejectionCode = accepted.code, reason = accepted.reason)
                        continue
                    }
                    resolver.byCoordinates(GeoLocation(spec.latitude, spec.longitude, java.time.ZoneId.of(spec.zoneId)), spec.query)
                }
                is SiteSpec.PlaceId -> resolver.byId(spec.geonameId).first
                is SiteSpec.Malformed -> error("handled")
            }
            if (site == null) {
                skipped += SkippedSite(spec.key, spec.query, inputRejectionCode = InputRejectionCode.UNKNOWN_PLACE_ID, reason = "Unknown place ID")
                continue
            }
            val accepted = resolver.accept(site)
            if (accepted is SiteAcceptance.Rejected) {
                skipped += SkippedSite(spec.key, spec.query, siteRejectionCode = accepted.code, reason = accepted.reason)
                continue
            }
            val result = publication.publish(site, rules, Scope.Year(year))
            checks += result.isCurrent
            if (result.approved) approved++
            files["v2/${spec.key}/calendar.json"] = WireJson.pretty.encodeToString(JsonObject.serializer(), result.document)
            for ((name, key) in listOf("year-resolution" to "yearResolution", "ekadashi-year" to "ekadashiYear")) {
                files["v2/${spec.key}/$name.json"] = WireJson.pretty.encodeToString(JsonElement.serializer(), result.document.getValue(key))
                files["v1/${spec.key}/$name.json"] = buildJsonObject {
                    put("schemaVersion", 2)
                    put("code", "API_VERSION_RETIRED")
                    put("guidance", "WITHHELD")
                    put("replacement", "v2/${spec.key}/$name.json")
                    put("message", "This migration document contains no calendar guidance. Consume publication-v2 and preserve null withholding.")
                }.toString()
            }
            withheld += LegacyWithheld(spec.key, site.location.zone.id, LegacyWithholdingCode.PUBLICATION_CONTRACT_REQUIRED,
                "Legacy cannot express approval, scoped withholding or revocation. Publication is refused; use publication-v2. No fallback calendar was created.")
        }
        val published = specs.size - skipped.size
        val report = SkippedReport(requested = specs.size, published = published, skipped = skipped.size, sites = skipped)
        val manifest = PublishManifest(sampradaya = sampradayaId, year = year, requested = specs.size,
            published = published, skipped = skipped.size, legacyPublished = 0, legacyWithheld = published,
            legacyWithheldSites = withheld, legacyParanaOmissions = emptyList(),
            warnings = listOf("$approved of $published structured site documents have all guidance approved. Other documents explicitly withhold guidance. Legacy publication is refused."),
            attribution = Gazetteer.ATTRIBUTION)
        files["manifest.json"] = WireJson.pretty.encodeToString(PublishManifest.serializer(), manifest)
        files["skipped.json"] = WireJson.pretty.encodeToString(SkippedReport.serializer(), report)
        files["publication-status.json"] = buildJsonObject {
            put("schemaVersion", 2); put("approvedSites", approved); put("withheldSites", published - approved)
            put("legacy", "REFUSED_CONTRACT_CANNOT_EXPRESS_WITHHOLDING")
            put("existingLiveArtifactsWithdrawn", false)
        }.toString()
        val frozenFiles = files.toMap()
        val expected = files.toMap()
        return PublishResult(manifest, report, frozenFiles) { currentFiles -> currentFiles == expected && checks.all { it() } }
    }

    /** Fresh immutable output directories avoid stale legacy files surviving a refused new release. */
    fun write(result: PublishResult, outDir: Path) {
        require(!Files.exists(outDir)) { "Public output must be a new directory; stale approved or legacy artifacts must not survive a new run" }
        require(result.validatePublication?.invoke(result.files) == true) { "Approval state changed, or this is not a public publication result; recompute the export" }
        val targetDirectory = outDir.toAbsolutePath().normalize()
        Files.createDirectories(targetDirectory.parent)
        val staging = Files.createTempDirectory(targetDirectory.parent, ".publication-pending-")
        for ((relative, content) in result.files) {
            val root = staging
            val target = root.resolve(relative).normalize()
            require(target.startsWith(root) && !relative.startsWith("legacy/"))
            Files.createDirectories(target.parent)
            Files.writeString(target, content, StandardOpenOption.CREATE_NEW)
        }
        require(result.validatePublication.invoke(result.files)) { "Approval changed during export; incomplete staged files are not publishable" }
        Files.writeString(staging.resolve("COMPLETE"), "publication-v2\n", StandardOpenOption.CREATE_NEW)
        Files.move(staging, targetDirectory, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    }
}
