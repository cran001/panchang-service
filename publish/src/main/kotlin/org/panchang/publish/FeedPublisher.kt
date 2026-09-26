package org.panchang.publish

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import org.panchang.calc.CalcEngine
import org.panchang.calc.LocationResolver
import org.panchang.calc.ResolvedSite
import org.panchang.calc.Sampradayas
import org.panchang.calc.Scope
import org.panchang.core.GeoLocation
import org.panchang.core.PanchangCalculator
import org.panchang.ephemeris.Vsop87Ephemeris
import org.panchang.gazetteer.Gazetteer
import org.panchang.sampradaya.EventGroup
import org.panchang.wire.EkadashiYearDto
import org.panchang.wire.ObservanceDecisionDto
import org.panchang.wire.SiteAcceptance
import org.panchang.wire.WireJson
import org.panchang.wire.YearResolutionDto
import org.panchang.wire.acceptSite

/** Development-only renderer of historical calculation formats.
 * It carries no publishing authority. Writes are forced below review/ with a warning marker.
 * Public export is FeedPublisher in PublicFeedPublisher.kt and always uses PublicationService.
 * Kept so numerical, rounding and January carryover regression checks remain permanent.
 */
class ReviewFeedPublisher(
    private val calculator: PanchangCalculator = PanchangCalculator(Vsop87Ephemeris()),
    private val resolver: LocationResolver = LocationResolver(),
) {

    private val engine = CalcEngine(calculator)

    /** A site that resolved and was accepted, ready to compute. */
    private data class Accepted(val key: String, val title: String, val site: ResolvedSite)

    /**
     * Compute everything, in memory.
     *
     * Separate from [write] so a test can assert on the produced bytes without a temporary
     * directory, and so a failed count invariant aborts before anything is on disk.
     */
    fun run(specs: List<SiteSpec>, year: Int, sampradayaId: String = "iskcon"): PublishResult {
        val rules = requireNotNull(Sampradayas[sampradayaId]) {
            "unknown sampradaya '$sampradayaId'; known: ${Sampradayas.knownIds().joinToString(", ")}"
        }

        val accepted = ArrayList<Accepted>()
        val skipped = ArrayList<SkippedSite>()
        specs.forEach { spec -> resolve(spec, accepted, skipped) }

        val files = LinkedHashMap<String, String>()
        val legacyByZoneDir = LinkedHashMap<String, MutableList<LegacyLocation>>()
        val withheld = ArrayList<LegacyWithheld>()
        val omissions = ArrayList<LegacyParanaOmission>()
        val warnings = ArrayList<String>()

        accepted.sortedBy { it.key }.forEach { site ->
            val result = engine.compute(site.site, rules, Scope.Year(year))

            files["v1/${site.key}/year-resolution.json"] = v1YearResolution(result.yearResolution)
            files["v1/${site.key}/ekadashi-year.json"] = v1EkadashiYear(result.ekadashiYear)

            val location = site.site.location
            val offset = fixedOffsetOver(location.zone, year)
            if (offset == null) {
                withheld += LegacyWithheld(
                    key = site.key,
                    timeZone = location.zone.id,
                    code = LegacyWithholdingCode.ZONE_NOT_FIXED_OFFSET,
                    reason = "${location.zone.id} changes UTC offset during $year. The legacy " +
                        "feed carries one fixed offset per location and the app applies it as " +
                        "arithmetic, so a single file spanning the transition is wrong on one " +
                        "side of it whichever way it is rendered — either the parana alarm " +
                        "fires an hour out, or the displayed window is an hour off the user's " +
                        "own clock. Rather than pick which half of the year to be wrong in, no " +
                        "legacy file is published for this site. Its v1 payload is complete: it " +
                        "carries the IANA zone id and every instant as both a correctly-offset " +
                        "local string and a jdUt. See docs/legacy-contract.md section 5.",
                )
                return@forEach
            }

            val day = legacyDays(site, result.yearResolution, result.ekadashiYear, year, offset, omissions, warnings)
            val dir = LegacyFeed.zoneDir(offset)
            files["legacy/$dir/${site.key}.json"] =
                WireJson.pretty.encodeToString(kotlinx.serialization.builtins.ListSerializer(LegacyDay.serializer()), day)
            legacyByZoneDir.getOrPut(dir) { ArrayList() } += LegacyLocation(
                title = site.title,
                coordinates = LegacyFeed.coordinates(location.latitude, location.longitude),
                timezone = LegacyFeed.timezoneField(offset),
                // Never read by the app and its meaning is undetermined; see the contract doc.
                // The file name is used so the value is at least stable and traceable.
                option = site.key,
                file = site.key,
            )
        }

        // Never synthesize a different location for an unmatched offset.
        val utcFallback: UtcFallback? = null
        if (legacyByZoneDir.isNotEmpty() && "P0000" !in legacyByZoneDir) {
            warnings += "No requested site uses P0000. No UTC fallback calendar was created; unmatched locations have no guidance."
        }

        val zoneDirs = legacyByZoneDir.keys.sorted()
        files["legacy/zones.json"] = WireJson.pretty.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(LegacyZone.serializer()),
            zoneDirs.map { dir ->
                val offset = offsetOfZoneDir(dir)
                LegacyZone(title = LegacyFeed.zoneTitle(offset), pb = LegacyFeed.timezoneField(offset), dir = dir)
            },
        )
        zoneDirs.forEach { dir ->
            files["legacy/$dir/locations.json"] = WireJson.pretty.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(LegacyLocation.serializer()),
                legacyByZoneDir.getValue(dir).sortedBy { it.file },
            )
        }

        val skippedReport = SkippedReport(
            requested = specs.size,
            published = accepted.size,
            skipped = skipped.size,
            sites = skipped,
        )
        val manifest = PublishManifest(
            sampradaya = rules.id,
            year = year,
            requested = specs.size,
            published = accepted.size,
            skipped = skipped.size,
            legacyPublished = accepted.size - withheld.size,
            legacyWithheld = withheld.size,
            legacyWithheldSites = withheld.sortedBy { it.key },
            legacyParanaOmissions = omissions,
            utcFallback = utcFallback,
            warnings = warnings,
            attribution = Gazetteer.ATTRIBUTION,
        )

        files["skipped.json"] =
            WireJson.pretty.encodeToString(SkippedReport.serializer(), skippedReport) + "\n"
        files["manifest.json"] =
            WireJson.pretty.encodeToString(PublishManifest.serializer(), manifest) + "\n"

        return PublishResult(manifest = manifest, skipped = skippedReport, files = files)
    }

    /** Write a computed run to [outDir]. Creates directories; overwrites files; touches nothing else. */
    fun write(result: PublishResult, outDir: Path) {
        // Diagnostic renderer retained for inspection and permanent delivery regressions.
        // Never writes to the public artifact paths. This directory is not deployable.
        val reviewDir = outDir.resolve("review")
        Files.createDirectories(reviewDir)
        Files.writeString(reviewDir.resolve("NOT-FOR-PUBLICATION.txt"),
            "CALCULATED DIAGNOSTICS ONLY. No human approval. Do not serve these files.\n")
        result.files.forEach { (relative, content) ->
            val target = reviewDir.resolve(relative)
            Files.createDirectories(target.parent)
            Files.writeString(target, content, StandardCharsets.UTF_8)
        }
    }

    // ── Site resolution ─────────────────────────────────────────────────────────────────────

    private fun resolve(
        spec: SiteSpec,
        accepted: MutableList<Accepted>,
        skipped: MutableList<SkippedSite>,
    ) {
        when (spec) {
            is SiteSpec.Malformed -> skipped += SkippedSite(
                key = spec.key,
                query = spec.query,
                inputRejectionCode = InputRejectionCode.MALFORMED_SITE_LINE,
                reason = spec.problem,
            )

            is SiteSpec.Coordinates -> {
                // acceptSite on raw input first: GeoLocation throws, and a thrown
                // IllegalArgumentException cannot be turned into a code without parsing its text.
                when (val verdict = acceptSite(spec.latitude, spec.longitude, spec.zoneId)) {
                    is SiteAcceptance.Rejected -> skipped += SkippedSite(
                        key = spec.key,
                        query = spec.query,
                        siteRejectionCode = verdict.code,
                        reason = verdict.reason,
                    )

                    is SiteAcceptance.Accepted -> {
                        val location = GeoLocation(spec.latitude, spec.longitude, ZoneId.of(spec.zoneId))
                        accepted += Accepted(
                            key = spec.key,
                            title = spec.title,
                            site = resolver.byCoordinates(location, spec.query),
                        )
                    }
                }
            }

            is SiteSpec.PlaceId -> {
                val (site, failure) = resolver.byId(spec.geonameId)
                if (site == null) {
                    skipped += SkippedSite(
                        key = spec.key,
                        query = spec.query,
                        inputRejectionCode = InputRejectionCode.UNKNOWN_PLACE_ID,
                        reason = failure!!.message,
                    )
                    return
                }
                when (val verdict = resolver.accept(site)) {
                    is SiteAcceptance.Rejected -> skipped += SkippedSite(
                        key = spec.key,
                        query = spec.query,
                        siteRejectionCode = verdict.code,
                        reason = verdict.reason,
                    )

                    is SiteAcceptance.Accepted -> accepted += Accepted(
                        key = spec.key,
                        title = spec.title ?: site.place!!.name,
                        site = site,
                    )
                }
            }
        }
    }

    // ── v1 ──────────────────────────────────────────────────────────────────────────────────

    /**
     * `:wire`'s [YearResolutionDto], unaltered.
     *
     * The `check` is not defensive noise. `unresolved` is the sect seam's record of the catalog
     * entries it could **not** place, and a publisher that dropped it would emit a payload that
     * looks complete while being short a festival — the exact failure the seam exists to prevent,
     * and the one with no artifact for anybody to notice. It is asserted here, where the bytes
     * are, rather than trusted to stay in the DTO.
     */
    private fun v1YearResolution(dto: YearResolutionDto): String {
        val json = WireJson.pretty.encodeToString(YearResolutionDto.serializer(), dto) + "\n"
        check(json.contains("\"unresolved\"")) {
            "the published v1 year resolution carries no 'unresolved' key. A missing festival " +
                "with no record of its absence is worse than a visibly wrong one."
        }
        return json
    }

    private fun v1EkadashiYear(dto: EkadashiYearDto): String =
        WireJson.pretty.encodeToString(EkadashiYearDto.serializer(), dto) + "\n"

    // ── Legacy day file ─────────────────────────────────────────────────────────────────────

    private fun legacyDays(
        site: Accepted,
        resolution: YearResolutionDto,
        ekadashi: EkadashiYearDto,
        year: Int,
        offset: ZoneOffset,
        omissions: MutableList<LegacyParanaOmission>,
        warnings: MutableList<String>,
    ): List<LegacyDay> {
        val location = site.site.location
        val festivalsByDate = resolution.events.groupBy { it.date }
        val fastsByDate = ekadashi.observances.groupBy { it.date }
        val paranasByDate = HashMap<LocalDate, String>()

        ekadashi.observances.forEach { decision ->
            paranaTitle(site.key, decision, omissions)?.let { (date, title) ->
                paranasByDate[date] = title
            }
        }

        val yearStart = LocalDate.of(year, 1, 1)
        val yearEnd = LocalDate.of(year, 12, 31)
        // The legacy reader attaches a Parana to a fasting event on the preceding day in the
        // SAME file. Retain that December context day only for a delivered January carryover;
        // a January 1 Break fast item without it would still be silently dropped by the reader.
        val carryoverStart = ekadashi.observances.filter {
            it.date < yearStart && it.parana?.date?.let { date ->
                date.year == year && date in paranasByDate
            } == true
        }.minOfOrNull { it.date }
        val days = ArrayList<LegacyDay>(367)
        var date = carryoverStart ?: yearStart
        while (!date.isAfter(yearEnd)) {
            val events = ArrayList<LegacyEvent>()

            fastsByDate[date]?.forEach { decision ->
                // The `+` marks a major event, and the title must satisfy the app's own fasting
                // predicate or the parana emitted on the following day is dropped silently. Every
                // ISKCON observance name observed so far already contains "Ekadashi" — including
                // the Mahadvadashis, which keep the Ekadashi's name — but relying on that would
                // make a future rename delete a fasting time with nothing to show for it.
                val name = decision.name
                val display = if (LegacyFeed.appWouldTreatAsFastingTitle(name)) {
                    name
                } else {
                    "$name (Ekadashi fast)"
                }
                events += LegacyEvent("+$display")
            }

            paranasByDate[date]?.let { events += LegacyEvent(it) }

            festivalsByDate[date]?.forEach { event ->
                val prefix = if (event.group == EventGroup.MAJOR_FESTIVAL) "+" else ""
                events += LegacyEvent(prefix + event.name)
            }

            days += LegacyDay(
                date = date.toString(),
                tithi = tithiText(date, location, warnings, site.key),
                events = events,
            )
            date = date.plusDays(1)
        }
        return days
    }

    /**
     * The tithi running at sunrise, as free display text.
     *
     * The app never parses this field — it renders `"Tithi: <value>"` and, on a day with no
     * events, `"<value> Tithi"` — so the format is ours to choose. Sunrise is the convention the
     * rest of this project computes the day's tithi at.
     */
    private fun tithiText(
        date: LocalDate,
        location: GeoLocation,
        warnings: MutableList<String>,
        key: String,
    ): String {
        val sun = calculator.sunTimes(date, location)
        val reference = sun.sunrise.jdUtOrNull ?: run {
            warnings += "$key $date has no sunrise; the legacy tithi for that day was taken at " +
                "solar noon instead, which is a substitute this project has no traditional " +
                "source for. Sites beyond the midnight-sun boundary " +
                "(org.panchang.wire.MIDNIGHT_SUN_LIMIT_DEG) are refused outright, so this should " +
                "not occur — treat it as a bug report."
            sun.solarNoonJdUt
        }
        val tithi = calculator.tithiAt(reference)
        return "${tithi.paksha.displayName} ${tithi.name}"
    }

    /**
     * The break-fast item for one observance, or null with the omission recorded.
     *
     * Three things must hold or the app mis-files the window, and each is checked rather than
     * assumed (`LiveCalendarDataSource.processAllDays`, and `docs/legacy-contract.md` §3):
     *
     * 1. The parana must be on exactly `fastDate + 1`. The app never reads a date out of the
     *    parana item — it subtracts one calendar day from the day the item sits on.
     * 2. Both bounds must still land on that date after rounding to the minute.
     * 3. The rounded window must be non-empty, since `HH:MM` cannot express one that is not.
     *
     * The rounding is asymmetric on purpose. `HH:MM` drops seconds, and the safe direction
     * differs at the two ends of a fast: the start is rounded **up**, so nobody is told they may
     * eat before the window opens, and the end **down**, so nobody is told they still have time
     * after it closes. That costs under two minutes of a window that is occasionally under forty.
     */
    private fun paranaTitle(
        key: String,
        decision: ObservanceDecisionDto,
        omissions: MutableList<LegacyParanaOmission>,
    ): Pair<LocalDate, String>? {
        val parana = decision.parana ?: return null
        val omit = { reason: String ->
            omissions += LegacyParanaOmission(
                key = key,
                fastDate = decision.date.toString(),
                observance = decision.name,
                reason = reason,
            )
            null
        }

        if (parana.date != decision.date.plusDays(1)) {
            return omit(
                "parana falls on ${parana.date} but the fast is on ${decision.date}. The legacy " +
                    "feed has no field for the fast's date: the app attaches a break-fast item to " +
                    "the previous calendar day, so this window would be filed against the wrong " +
                    "observance. It is omitted from the legacy feed and is present, correct, in " +
                    "the v1 payload.",
            )
        }

        val start = OffsetDateTime.parse(parana.start.local).let {
            val floor = it.truncatedTo(ChronoUnit.MINUTES)
            if (floor == it) floor else floor.plusMinutes(1)
        }
        val end = OffsetDateTime.parse(parana.end.local).truncatedTo(ChronoUnit.MINUTES)

        if (start.toLocalDate() != parana.date || end.toLocalDate() != parana.date) {
            return omit(
                "rounded to the minute the window runs ${start.toLocalTime()} on " +
                    "${start.toLocalDate()} to ${end.toLocalTime()} on ${end.toLocalDate()}, " +
                    "which is not wholly within ${parana.date}. The legacy shape carries a time " +
                    "of day and takes the date from the day the item sits on, so it cannot " +
                    "express this.",
            )
        }
        if (!start.isBefore(end)) {
            return omit(
                "the window is under a minute wide once rounded (${parana.durationMinutes} " +
                    "minutes). HH:MM cannot express it, and a window rendered as a single minute " +
                    "would read as comfortably open. Present, to the second, in the v1 payload.",
            )
        }

        val title = LegacyFeed.breakFastTitle(hhmm(start), hhmm(end))
        check(LegacyFeed.APP_BREAK_FAST_REGEX.containsMatchIn(title)) {
            "the break-fast title '$title' does not match the app's own regex, so the app would " +
                "show it as an ordinary event and lose the parana entirely"
        }
        return parana.date to title
    }

    private fun hhmm(time: OffsetDateTime): String = "%02d:%02d".format(time.hour, time.minute)

    // ── Zones ───────────────────────────────────────────────────────────────────────────────

    private companion object {

        /**
         * The zone's single UTC offset across [year], or null if it has more than one.
         *
         * The range is padded by a day at each end because the legacy file's first and last days
         * are read in the site's own civil zone, not in UTC.
         */
        fun fixedOffsetOver(zone: ZoneId, year: Int): ZoneOffset? {
            val from: Instant = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant()
                .minus(1, ChronoUnit.DAYS)
            val to: Instant = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant()
                .plus(1, ChronoUnit.DAYS)
            val rules = zone.rules
            val next = rules.nextTransition(from)
            if (next != null && next.instant.isBefore(to)) return null
            return rules.getOffset(from)
        }

        /** Inverse of [LegacyFeed.zoneDir]; used only to label a directory we just created. */
        fun offsetOfZoneDir(dir: String): ZoneOffset {
            val sign = if (dir[0] == 'N') -1 else 1
            val hours = dir.substring(1, 3).toInt()
            val minutes = dir.substring(3, 5).toInt()
            return ZoneOffset.ofTotalSeconds(sign * (hours * 3600 + minutes * 60))
        }
    }
}
