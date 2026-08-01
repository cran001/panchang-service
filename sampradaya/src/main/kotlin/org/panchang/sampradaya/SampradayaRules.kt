package org.panchang.sampradaya

import java.time.LocalDate
import org.panchang.core.GeoLocation
import org.panchang.core.PanchangCalculator

/**
 * Everything a rule is allowed to consult.
 *
 * Rules get the calculator rather than pre-computed values because the questions they ask are
 * not predictable from outside: one tradition tests the tithi at arunodaya, another at sunrise,
 * another at local noon, and a fourth wants the tithi at the *previous* day's sunrise to detect
 * trisprsha. Passing a fixed bundle of pre-computed facts would mean the seam has to be widened
 * every time a tradition is added, which defeats the purpose of having a seam.
 *
 * Rules must not fetch anything, read the clock, or depend on the default time zone. Every
 * time-of-day question goes through [location]; the app this replaces called
 * `Calendar.getInstance()` for Rahu Kaal and Vaar and so was wrong for every user outside the
 * server's own zone.
 */
class ObservanceContext(
    val calculator: PanchangCalculator,
    val location: GeoLocation,
)

/**
 * The sect seam.
 *
 * One implementation per tradition. The astronomy underneath is identical for all of them —
 * there is exactly one tithi timeline for the planet — so everything that differs between
 * traditions lives here and nowhere else. If a change to make one tradition correct requires
 * touching `core` or `ephemeris`, the change is in the wrong place.
 */
interface SampradayaRules {

    /** Stable identifier used in API requests and cache keys. Lowercase, no spaces. */
    val id: String

    val displayName: String

    /** Surfaced through the API. See [VerificationStatus]. */
    val status: VerificationStatus

    /**
     * What is known about where these rules came from, and what remains unconfirmed.
     *
     * Free text, shown to users alongside [status]. For an unverified tradition this should
     * name the source the rules were read from and say plainly what has not been checked.
     */
    val provenanceNote: String

    /**
     * All Ekadashi and Mahadvadashi observances falling in [year] at this location.
     *
     * Returns 24 in an ordinary year and 26 in an adhika-maasa year. The date on which each is
     * observed is location-dependent even though the tithi instants are not, so two locations
     * may legitimately differ by one day near the terminator.
     *
     * Implementations must return an empty list rather than throwing when [status] is
     * [VerificationStatus.NOT_IMPLEMENTED].
     */
    fun ekadashiObservances(year: Int, ctx: ObservanceContext): List<ObservanceDecision>

    /**
     * The tradition's non-Ekadashi observances — festivals, appearance and disappearance days,
     * optional fasts — for [year] at this location, **including the ones that failed to
     * resolve**.
     *
     * This is the method implementations provide, and it returns [YearResolution] rather than a
     * list because `List<ResolvedEvent>` structurally cannot carry a failure: a [ResolvedEvent]
     * requires a date, and the whole point of an unresolved entry is that it has none.
     *
     * The failures are not an edge case to be tidied away. When a catalog entry's tithi is
     * kshaya in some year the resolver deliberately refuses to move the festival to a
     * neighbouring day — which way the tradition shifts it is a ruling this project does not
     * have a source for — so that festival is *absent* from the year. A missing observance is
     * worse than a visibly wrong one: there is no artifact for anyone to notice or challenge.
     * `unresolved` is what makes it noticeable.
     */
    fun eventResolution(year: Int, ctx: ObservanceContext): YearResolution

    /**
     * Just the dates, for callers that genuinely only want to render a year's festivals.
     *
     * Deliberately *not* an overridable method. It derives from [eventResolution], so no
     * implementation can produce events without also producing the record of what it could not
     * produce — which is the only way to keep the two from drifting apart. Any caller that finds
     * an expected festival missing from this list should look at
     * `eventResolution(year, ctx).unresolved` before concluding the catalog lacks it.
     */
    fun events(year: Int, ctx: ObservanceContext): List<ResolvedEvent> =
        eventResolution(year, ctx).events
}

/**
 * Registry of known traditions.
 *
 * Every tradition named in the goal brief appears here even when it has no rules, so that the
 * API can answer "we know this tradition exists and we do not yet compute it" rather than 404
 * — and, critically, so it can never quietly answer with a different tradition's dates.
 */
object SampradayaRegistry {

    private val byId: MutableMap<String, SampradayaRules> = LinkedHashMap()

    fun register(rules: SampradayaRules) {
        require(rules.id == rules.id.lowercase() && rules.id.isNotBlank()) {
            "sampradaya id must be lowercase and non-blank, was '${rules.id}'"
        }
        val existing = byId.put(rules.id, rules)
        require(existing == null || existing === rules) {
            "two different rule sets registered under id '${rules.id}'"
        }
    }

    operator fun get(id: String): SampradayaRules? = byId[id.lowercase()]

    fun all(): List<SampradayaRules> = byId.values.toList()

    /** The traditions that will actually produce output. */
    fun implemented(): List<SampradayaRules> =
        byId.values.filter { it.status != VerificationStatus.NOT_IMPLEMENTED }
}

/**
 * A tradition registered without rules.
 *
 * Deliberately returns empty rather than delegating to ISKCON. Delegation would be the
 * friendlier-looking choice and the wrong one: a Pushtimarg user would receive Gaudiya dates
 * with a flag they have no reason to read, and would have no way to tell the difference.
 */
class UnimplementedSampradaya(
    override val id: String,
    override val displayName: String,
    override val provenanceNote: String,
) : SampradayaRules {
    override val status: VerificationStatus = VerificationStatus.NOT_IMPLEMENTED
    override fun ekadashiObservances(year: Int, ctx: ObservanceContext): List<ObservanceDecision> =
        emptyList()

    /** Nothing resolved and nothing failed to resolve: this tradition was never asked anything. */
    override fun eventResolution(year: Int, ctx: ObservanceContext): YearResolution =
        YearResolution(emptyList(), emptyMap())
}

/** An event definition resolved to an actual date at an actual location. */
data class ResolvedEvent(
    val id: String,
    val name: String,
    val group: EventGroup,
    val date: LocalDate,
    /** Present when the event carries a fast; null when it is observed without one. */
    val fastingNote: String?,
    val reason: String,
    val confidence: RuleConfidence,
)
