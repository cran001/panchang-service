package org.panchang.publish

import kotlinx.serialization.Serializable
import org.panchang.wire.SiteRejectionCode

/** The publisher's own refusal codes: things wrong with the *request*, not with the site. */
@Serializable
enum class InputRejectionCode {
    /** A site-list line that could not be read at all. */
    MALFORMED_SITE_LINE,

    /** A `place-id` that is not in the shipped gazetteer. */
    UNKNOWN_PLACE_ID,
}

/**
 * One requested site that produced no output, and which code says why.
 *
 * The two code fields are separate and typed rather than one string, because they come from
 * different authorities and mean different things to whoever reads this file.
 * [siteRejectionCode] is `:wire`'s verdict on a real place — a stated boundary of the service,
 * carried verbatim so `:publish` cannot invent a reason `:wire` did not give. [inputRejectionCode]
 * is this module's verdict on the operator's input, and is deliberately **not** a
 * [SiteRejectionCode]: there is no wire code for "you typed an id that does not exist", and
 * borrowing the nearest one would misreport a typo as a property of the Earth.
 */
@Serializable
data class SkippedSite(
    val key: String,
    val query: String,
    val siteRejectionCode: SiteRejectionCode? = null,
    val inputRejectionCode: InputRejectionCode? = null,
    /** A sentence that can be shown or logged unmodified. */
    val reason: String,
) {
    init {
        require((siteRejectionCode == null) != (inputRejectionCode == null)) {
            "a skipped site carries exactly one code; '$key' has " +
                "siteRejectionCode=$siteRejectionCode inputRejectionCode=$inputRejectionCode"
        }
    }
}

/**
 * `skipped.json`: every requested place that produced no output, with its code.
 *
 * The counts are in the file and not only in the log. A shorter-than-expected output directory is
 * invisible; a file that says "requested 412, published 409, skipped 3" and lists the three is not.
 */
@Serializable
data class SkippedReport(
    val schemaVersion: Int = PUBLISH_REPORT_VERSION,
    val requested: Int,
    val published: Int,
    val skipped: Int,
    val sites: List<SkippedSite>,
) {
    init {
        require(published + skipped == requested) {
            "published ($published) + skipped ($skipped) != requested ($requested)"
        }
        require(sites.size == skipped) {
            "skipped count ($skipped) disagrees with the number of listed sites (${sites.size})"
        }
    }
}

/** Why a published site got no *legacy* file, though it got a full v1 payload. */
@Serializable
enum class LegacyWithholdingCode {
    PUBLICATION_CONTRACT_REQUIRED,
    /**
     * The site's zone changes offset inside the published range.
     *
     * The legacy `timezone` field holds one fixed offset and the app applies it as arithmetic, so
     * a single file spanning a DST transition is wrong on one side of it whichever way it is
     * rendered. See `docs/legacy-contract.md` §5. Needs a project-owner decision, not a workaround.
     */
    ZONE_NOT_FIXED_OFFSET,
}

/** One published site that was withheld from the legacy feed, and why. */
@Serializable
data class LegacyWithheld(
    val key: String,
    val timeZone: String,
    val code: LegacyWithholdingCode,
    val reason: String,
)

/** One parana that was computed but could not be expressed in the legacy shape. */
@Serializable
data class LegacyParanaOmission(
    val key: String,
    val fastDate: String,
    val observance: String,
    val reason: String,
)

/**
 * The synthesised UTC site published into `legacy/P0000/` when no requested site landed there.
 *
 * `CalendarSyncRepository` falls back to the `P0000` zone directory whenever the user's offset
 * matches no published zone, and returns failure when that directory is missing too — which reaches
 * the user as a stale cache with nothing on screen to say so. A present-but-wrong calendar is the
 * lesser harm, so one is always published.
 *
 * **This site is not a place anyone lives and its times are wrong for anyone who reads them.** It
 * is computed on the prime meridian at a fixed UTC offset, which is what `P0000` means and nothing
 * more. It is recorded here, and titled in `locations.json`, so that neither an operator reading
 * the manifest nor a user reading the app can mistake it for their own district.
 *
 * It is deliberately excluded from every count in [PublishManifest]: it was not requested, so
 * counting it would break `published + skipped == requested`, which is the invariant that makes a
 * silently short run detectable.
 */
@Serializable
data class UtcFallback(
    val key: String,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val timeZone: String,
    val reason: String,
)

/**
 * `manifest.json`: what this run produced, and every gap in it, in one place.
 *
 * No timestamp and no host name: two runs of the same arguments must produce the same bytes, for
 * the same reason `:calc`'s document carries no `generatedAt`.
 */
@Serializable
data class PublishManifest(
    val schemaVersion: Int = PUBLISH_REPORT_VERSION,
    val tool: String = "panchang-publish",
    val sampradaya: String,
    val year: Int,
    val requested: Int,
    val published: Int,
    val skipped: Int,
    val legacyPublished: Int,
    val legacyWithheld: Int,
    val legacyWithheldSites: List<LegacyWithheld>,
    /** Parana windows dropped from the legacy feed. Empty is the expected state. */
    val legacyParanaOmissions: List<LegacyParanaOmission>,
    /**
     * The synthesised `P0000` site, or null when a requested site already occupied that zone
     * directory. Counted in none of the fields above — see [UtcFallback].
     */
    val utcFallback: UtcFallback? = null,
    /** Things an operator must read before treating this run as complete. */
    val warnings: List<String>,
    val attribution: String,
) {
    init {
        require(published + skipped == requested) {
            "published ($published) + skipped ($skipped) != requested ($requested)"
        }
        require(legacyPublished + legacyWithheld == published) {
            "legacyPublished ($legacyPublished) + legacyWithheld ($legacyWithheld) != published " +
                "($published)"
        }
        require(legacyWithheldSites.size == legacyWithheld) {
            "legacyWithheld ($legacyWithheld) disagrees with the listed sites " +
                "(${legacyWithheldSites.size})"
        }
    }
}

/** Version of `manifest.json` / `skipped.json`. Distinct from `:wire`'s: these are not wire types. */
const val PUBLISH_REPORT_VERSION: Int = 1

/** Everything one run produced, in memory, so a test can assert on it without reading files. */
data class PublishResult(
    val manifest: PublishManifest,
    val skipped: SkippedReport,
    /** Relative output path → file content, exactly as written. */
    val files: Map<String, String>,
    /** Public producer binds contents and rechecks approval before completing a new export. */
    internal val validatePublication: ((Map<String, String>) -> Boolean)? = null,
)
