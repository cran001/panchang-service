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
     * optional fasts — resolved to dates for [year] at this location.
     */
    fun events(year: Int, ctx: ObservanceContext): List<ResolvedEvent>
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

    override fun events(year: Int, ctx: ObservanceContext): List<ResolvedEvent> = emptyList()
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
