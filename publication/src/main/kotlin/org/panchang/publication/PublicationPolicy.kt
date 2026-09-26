package org.panchang.publication

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.zone.ZoneRulesProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.panchang.calc.*
import org.panchang.core.PanchangCalculator
import org.panchang.ephemeris.Vsop87Ephemeris
import org.panchang.gazetteer.Gazetteer
import org.panchang.wire.*

@Serializable
data class PublicationDecision(
    val state: PublicationState,
    val guidance: String,
    val field: Field,
    val coverage: Coverage,
    val bundleFingerprint: String,
    val approvalRecordId: String? = null,
    val approverKeyId: String? = null,
    val approvedAt: String? = null,
    val issues: List<Dispute> = emptyList(),
    val explanation: String,
)

/** One policy for HTTP, files and export adapters. No request parameter can set an approval. */
class PublicationPolicy(
    val registry: DisputeRegistry = DisputeRegistry.current(),
    val referencePolicy: ReferencePolicy = ReferencePolicy("vaisnavacalendar-info-gcal-text", "2026-09-16-proposed", "Designated GCal text lineage; exact evidence policy still requires qualified review and owner approval", emptyList()),
    private val source: RecordSource = RecordSource.DISABLED,
) {
    fun decide(member: BundleMember, dates: Coverage, field: Field, dependencies: Coverage = dates): PublicationDecision {
        val issues = registry.disputes.filter { it.affects(member, dependencies, field) }
        val snapshot = try { source.read() } catch (_: Exception) {
            RecordSnapshot(error = "Approval store unavailable; publication is blocked")
        }
        val base = ReviewBundle(members = listOf(member), referencePolicy = referencePolicy,
            disputeRegistrySha256 = registry.fingerprint(), evidence = emptyList())
        fun decision(state: PublicationState, why: String, record: ReviewRecord? = null) = PublicationDecision(
            state, if (state == PublicationState.APPROVED) "AVAILABLE" else "WITHHELD", field, dates,
            record?.bundle?.fingerprint() ?: base.fingerprint(), record?.id, record?.actorKeyId, record?.timestamp,
            issues, why,
        )
        if (!PublicReleaseScope.includes(member.tradition))
            return decision(PublicationState.UNSUPPORTED, PublicReleaseScope.exclusionReason)
        if (snapshot.error != null) return decision(PublicationState.INVALID_RECORDS, snapshot.error)
        if (acceptSite(member.place.latitude, member.place.longitude, member.place.timeZone, member.place.elevationMeters) !is SiteAcceptance.Accepted)
            return decision(PublicationState.UNSUPPORTED, "This location or condition is outside the supported calculation scope")
        if (field == Field.OBSERVANCES && member.tradition != "iskcon")
            return decision(PublicationState.UNSUPPORTED, "This tradition has no implemented Ekadashi/Parana rules; absence of guidance does not mean no observance exists")
        if (field == Field.DAILY_ASTRONOMY)
            return decision(PublicationState.UNSUPPORTED, "Daily astronomy is not part of this release bundle's public result schema")

        fun matches(r: ReviewRecord): Boolean = r.bundle.referencePolicy == referencePolicy &&
            r.bundle.disputeRegistrySha256 == registry.fingerprint() && r.bundle.members.any {
                it.place == member.place && it.tradition == member.tradition &&
                    it.calculationYear == member.calculationYear && it.context == member.context &&
                    it.versions == member.versions && it.resultSha256 == member.resultSha256 &&
                    it.coverage.contains(dates) && field in it.fields
            }
        val candidates = snapshot.records.filter { it.action == Action.APPROVE && matches(it) }
        val approval = candidates.lastOrNull()
        if (snapshot.records.any { it.action == Action.REJECT && matches(it) && it.sequence > (approval?.sequence ?: 0) })
            return decision(PublicationState.REJECTED, "The owner rejected publication of this exact scope", approval)
        // A later qualified dispute/pending review invalidates prior approval of the same bundle.
        val laterReview = approval?.let { a -> snapshot.records.lastOrNull {
            it.sequence > a.sequence && it.action == Action.REVIEW && it.bundle == a.bundle && it.reviewDecision != ReviewDecision.COMPLETED
        } }
        if (laterReview != null) return decision(
            if (laterReview.reviewDecision == ReviewDecision.DISPUTED) PublicationState.DISPUTED else PublicationState.PENDING_REVIEW,
            "A later qualified review reopened this exact bundle", approval,
        )
        if (approval != null) {
            val ended = snapshot.records.lastOrNull { it.targetRecordId == approval.id && it.action in setOf(Action.REVOKE, Action.SUPERSEDE) }
            if (ended != null) return decision(if (ended.action == Action.REVOKE) PublicationState.REVOKED else PublicationState.SUPERSEDED,
                "Approval ${approval.id} was ${ended.action.name.lowercase()} by ${ended.id}", approval)
        }
        val resolutions = approval?.reviewRecordIds.orEmpty().flatMap { id ->
            snapshot.records.single { it.id == id }.resolutions
        }
        val unresolved = issues.filter { issue ->
            val decisions = resolutions.filter { it.disputeId == issue.id }
            decisions.isEmpty() || decisions.any { it.decision != ResolutionDecision.RESOLVED }
        }
        if (unresolved.isNotEmpty()) return decision(
            when {
                unresolved.any { it.state == PublicationState.DISPUTED } -> PublicationState.DISPUTED
                unresolved.any { it.state == PublicationState.UNSUPPORTED } -> PublicationState.UNSUPPORTED
                else -> PublicationState.MISSING_EVIDENCE
            }, "Scoped audit cases require an explicit qualified resolution. An approved withholding decision keeps guidance withheld", approval,
        )
        if (referencePolicy.evidence.isEmpty()) return decision(PublicationState.MISSING_EVIDENCE, "No approved reference-policy evidence is configured")
        if (approval == null) return decision(PublicationState.PENDING_REVIEW, "No owner publication approval matches these exact inputs, results, dates, fields and revisions")
        val reviewedQuestions = approval.reviewRecordIds.flatMap { id -> snapshot.records.single { it.id == id }.reviewedQuestionIds }.toSet()
        val missingQuestions = registry.reviewQuestions.filter { member.tradition in it.traditions && field in it.fields && it.id !in reviewedQuestions }
        if (missingQuestions.isNotEmpty()) return decision(PublicationState.MISSING_EVIDENCE,
            "Qualified scope review has not addressed: ${missingQuestions.joinToString { it.id }}", approval)
        return decision(PublicationState.APPROVED, "Qualified review and owner decision authorize only this exact local release scope", approval)
    }
}

/** Real byte identities, not mutable project version labels. Rebuilt artifacts may need re-review. */
object CalculationVersions {
    private fun artifact(type: Class<*>): String {
        val path = Path.of(type.protectionDomain.codeSource.location.toURI())
        if (Files.isRegularFile(path)) return sha256(Files.readAllBytes(path))
        val entries = Files.walk(path).use { stream -> stream.filter { Files.isRegularFile(it) }.sorted().toList() }
        return sha256(entries.joinToString("\n") { path.relativize(it).toString().replace('\\', '/') + ":" + sha256(Files.readAllBytes(it)) }.toByteArray())
    }
    private fun resource(name: String): String = sha256(requireNotNull(CalculationVersions::class.java.getResourceAsStream(name)).use { it.readBytes() })
    private val artifacts by lazy { mapOf(
        "calculation" to sha256((artifact(CalcEngine::class.java) + artifact(PanchangCalculator::class.java)).toByteArray()),
        "ruleCatalog" to artifact(org.panchang.sampradaya.IskconRules::class.java),
        "ephemerisData" to sha256((artifact(Vsop87Ephemeris::class.java) +
            resource("/org/panchang/ephemeris/VSOP87D.ear") + resource("/org/panchang/ephemeris/deltat-iers.csv")).toByteArray()),
        "wire" to artifact(WireJson::class.java),
        "gazetteer" to sha256((artifact(Gazetteer::class.java) + resource("/org/panchang/gazetteer/gazetteer-v1.tsv")).toByteArray()),
        "publication" to artifact(PublicationPolicy::class.java),
        "serialization" to artifact(Json::class.java),
        "serializationCore" to artifact(kotlinx.serialization.KSerializer::class.java),
        "kotlinRuntime" to artifact(Unit::class.java),
        "crossYearPolicy" to "full-year-with-previous-december-and-following-january-v1",
        "cacheIntegrity" to "year-calculation-cache-1-sha256",
        "jvm" to (System.getProperty("java.vendor") + ":" + System.getProperty("java.runtime.version")),
    ) }
    fun at(zone: String) = artifacts + ("tzdb" to ZoneRulesProvider.getVersions(zone).lastKey())
}

data class PublicResult(val document: JsonObject, val member: BundleMember, val decisions: List<PublicationDecision>, val isCurrent: () -> Boolean) {
    val approved: Boolean get() = decisions.all { it.state == PublicationState.APPROVED }
}

/** Always compute a year before selecting a day so the approval fingerprint is identical. */
class PublicationService(
    engine: CalcEngine = CalcEngine(),
    private val policyFor: (BundleMember) -> PublicationPolicy = { PublicationPolicy() },
    private val calculations: YearCalculation = YearCalculation.direct(engine),
) {
    fun publish(site: ResolvedSite, rules: org.panchang.sampradaya.SampradayaRules, scope: Scope): PublicResult {
        val year = calculations.calculate(site, rules, scope.year)
        val member = member(year)
        val policy = policyFor(member)
        val dates = (scope as? Scope.Day)?.date?.let { Coverage(it.toString(), it.toString()) } ?: member.coverage
        val selected = select(year, scope)
        val calculated = CalcJson.document(selected)
        val observanceDates = selected.ekadashiYear.observances.flatMap { listOfNotNull(it.date, it.parana?.date) }
        val dependencies = Coverage(minOf(dates.from, observanceDates.minOrNull()?.toString() ?: dates.from),
            maxOf(dates.through, observanceDates.maxOrNull()?.toString() ?: dates.through))
        val decisions = listOf(Field.EVENTS, Field.OBSERVANCES).map {
            policy.decide(member, dates, it, if (it == Field.OBSERVANCES) dependencies else dates)
        }
        val output = buildJsonObject {
            put("schemaVersion", 2)
            put("request", calculated.getValue("request"))
            put("location", calculated.getValue("location"))
            put("publication", buildJsonObject {
                put("contract", "publication-v2")
                put("releaseScope", PublicReleaseScope.revision)
                put("calculation", "CALCULATED")
                put("validation", "SEPARATE_EVIDENCE_RECORDS_NOT_APPROVAL")
                put("humanApprovalRequired", true)
                put("qualifiedReviewQuestions", RecordJson.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(ReviewQuestion.serializer()),
                    policy.registry.reviewQuestions.filter { rules.id in it.traditions }))
                put("legacyPublication", "REFUSED_CONTRACT_CANNOT_EXPRESS_WITHHOLDING")
            })
            for ((key, decision) in listOf("yearResolution" to decisions[0], "ekadashiYear" to decisions[1])) {
                val original = calculated.getValue(key).jsonObject
                put(key, buildJsonObject {
                    put("schemaVersion", 2)
                    put("year", original.getValue("year"))
                    put("site", original.getValue("site"))
                    // Legacy VERIFIED / CONFIRMED values stay diagnostic and cannot masquerade as approval.
                    put("sampradaya", if (decision.state == PublicationState.APPROVED) original.getValue("sampradaya") else buildJsonObject {
                        put("id", rules.id)
                        put("displayName", original.getValue("sampradaya").jsonObject.getValue("displayName"))
                    })
                    put("publication", RecordJson.encodeToJsonElement(PublicationDecision.serializer(), decision))
                    put("diagnosticClassificationsAreNotApproval", true)
                    val arrayKey = if (key == "yearResolution") "events" else "observances"
                    if (decision.state == PublicationState.APPROVED) {
                        put(arrayKey, original.getValue(arrayKey))
                        if (key == "yearResolution") put("unresolved", original.getValue("unresolved"))
                        put("absenceMeaning", "ONLY_EMPTY_APPROVED_RESULTS_MEAN_NO_CALCULATED_EVENT_IN_SCOPE")
                    } else {
                        put(arrayKey, JsonNull)
                        if (key == "yearResolution") put("unresolved", JsonNull)
                        put("absenceMeaning", "GUIDANCE_WITHHELD_NOT_NO_EVENT")
                    }
                })
            }
        }
        return PublicResult(output, member, decisions) {
            val currentPolicy = policyFor(member)
            decisions == listOf(Field.EVENTS, Field.OBSERVANCES).map {
                currentPolicy.decide(member, dates, it, if (it == Field.OBSERVANCES) dependencies else dates)
            }
        }
    }

    companion object {
        fun place(site: ResolvedSite): CanonicalPlace {
            val p = site.location
            val point = listOf(p.latitude, p.longitude, p.elevationMeters, p.zone.id).joinToString("|")
            return CanonicalPlace("point:" + sha256(point.toByteArray()), p.latitude, p.longitude, p.elevationMeters, p.zone.id)
        }

        fun member(result: CalcResult): BundleMember {
            require(result.scope is Scope.Year)
            val y = result.scope.year
            val site = result.site.location
            val coverage = Coverage(LocalDate.of(y, 1, 1).toString(), LocalDate.of(y, 12, 31).toString())
            val dates = result.ekadashiYear.observances.flatMap { listOfNotNull(it.date, it.parana?.date) }
            // Explicit previous-December and next-January review context, even when no event exists.
            val context = Coverage(minOf(LocalDate.of(y - 1, 12, 1), dates.minOrNull() ?: LocalDate.of(y, 1, 1)).toString(),
                maxOf(LocalDate.of(y + 1, 1, 31), dates.maxOrNull() ?: LocalDate.of(y, 12, 31)).toString())
            val roots = buildJsonObject {
                put("yearResolution", WireJson.compact.encodeToJsonElement(YearResolutionDto.serializer(), result.yearResolution))
                put("ekadashiYear", WireJson.compact.encodeToJsonElement(EkadashiYearDto.serializer(), result.ekadashiYear))
            }
            return BundleMember(place(result.site),
                result.rules.id, coverage, y, context, setOf(Field.EVENTS, Field.OBSERVANCES), CalculationVersions.at(site.zone.id), digest(roots))
        }

        private fun select(year: CalcResult, scope: Scope): CalcResult {
            val day = (scope as? Scope.Day)?.date ?: return year
            return year.copy(scope = scope, yearResolution = year.yearResolution.copy(events = year.yearResolution.events.filter { it.date == day }),
                ekadashiYear = year.ekadashiYear.copy(observances = year.ekadashiYear.observances.filter { it.date == day || it.parana?.date == day }))
        }
    }
}
