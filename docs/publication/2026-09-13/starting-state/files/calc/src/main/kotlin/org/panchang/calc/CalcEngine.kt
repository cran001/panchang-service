package org.panchang.calc

import java.time.LocalDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.panchang.core.PanchangCalculator
import org.panchang.ephemeris.Vsop87Ephemeris
import org.panchang.sampradaya.BengaliRules
import org.panchang.sampradaya.GujaratiRules
import org.panchang.sampradaya.IskconRules
import org.panchang.sampradaya.KannadaRules
import org.panchang.sampradaya.MalayalamRules
import org.panchang.sampradaya.MarathiRules
import org.panchang.sampradaya.NorthIndianRules
import org.panchang.sampradaya.ObservanceContext
import org.panchang.sampradaya.OdiaRules
import org.panchang.sampradaya.SampradayaRegistry
import org.panchang.sampradaya.SampradayaRules
import org.panchang.sampradaya.TamilRules
import org.panchang.sampradaya.TeluguRules
import org.panchang.sampradaya.YearResolution
import org.panchang.wire.EkadashiYearDto
import org.panchang.wire.WireJson
import org.panchang.wire.WireRenderer
import org.panchang.wire.YearResolutionDto

/**
 * The traditions this front door can be asked for.
 *
 * `:sampradaya` ships the registry empty on purpose — registering a tradition is a statement
 * that its rules exist, and only a front door knows which ones it is prepared to make. ISKCON
 * is the one tradition whose Ekadashi, Mahadvadashi and parana rules are implemented and
 * conformance-tested; the nine regional traditions registered alongside it carry **festival
 * catalogs only** (each [VerificationStatus.UNVERIFIED]), and their `ekadashiObservances`
 * return empty lists rather than borrowing ISKCON's fasting rulings. Asking for a tradition
 * that exists but has no ekadashi timing gets exactly that — never another tradition's dates.
 */
object Sampradayas {

    private val iskcon = IskconRules()

    init {
        SampradayaRegistry.register(iskcon)
        SampradayaRegistry.register(MarathiRules())
        SampradayaRegistry.register(TeluguRules())
        SampradayaRegistry.register(KannadaRules())
        SampradayaRegistry.register(GujaratiRules())
        SampradayaRegistry.register(NorthIndianRules())
        SampradayaRegistry.register(TamilRules())
        SampradayaRegistry.register(MalayalamRules())
        SampradayaRegistry.register(BengaliRules())
        SampradayaRegistry.register(OdiaRules())
    }

    operator fun get(id: String): SampradayaRules? = SampradayaRegistry[id]

    fun knownIds(): List<String> = SampradayaRegistry.all().map { it.id }
}

/** Whether the caller asked for a year or for one day of it. */
sealed interface Scope {
    val year: Int

    data class Year(override val year: Int) : Scope

    data class Day(val date: LocalDate) : Scope {
        override val year: Int get() = date.year
    }
}

/**
 * One computed answer, held as `:wire` payloads and nothing else.
 *
 * `:calc` defines no DTO for an event, an instant, a tithi window or a parana. Those all exist
 * once, in `:wire`, and both fields below are that module's document roots verbatim. The reason
 * is the same one that put the renderer there: three front doors that each shape their own
 * payload are three chances to disagree about a fasting time in front of a user.
 */
data class CalcResult(
    val site: ResolvedSite,
    val rules: SampradayaRules,
    val scope: Scope,
    val yearResolution: YearResolutionDto,
    val ekadashiYear: EkadashiYearDto,
)

/**
 * Assembles a year (or a day of it) for one site under one tradition.
 *
 * Holds no rule and converts no time. It calls the sect seam, hands what comes back to
 * `:wire`'s renderer, and stops.
 */
class CalcEngine(
    private val calculator: PanchangCalculator = PanchangCalculator(Vsop87Ephemeris()),
) {

    fun compute(site: ResolvedSite, rules: SampradayaRules, scope: Scope): CalcResult {
        val ctx = ObservanceContext(calculator, site.location)
        val renderer = WireRenderer(site.location)

        val resolution = rules.eventResolution(scope.year, ctx)
        val observances = rules.ekadashiObservances(scope.year, ctx)

        val onlyDate = (scope as? Scope.Day)?.date

        // Sorted by date then id: the resolver walks the catalog in declaration order, which is
        // deterministic but not the order anybody reads a calendar in. `unresolved` is left to
        // the renderer, which sorts it by id.
        val events = resolution.events
            .filter { onlyDate == null || it.date == onlyDate }
            .sortedWith(compareBy({ it.date }, { it.id }))

        return CalcResult(
            site = site,
            rules = rules,
            scope = scope,
            yearResolution = renderer.yearResolution(
                year = scope.year,
                location = site.location,
                rules = rules,
                // `unresolved` is deliberately *not* filtered by --date. An unresolved entry has
                // no date — that is the whole point of it — so a date filter cannot include or
                // exclude one honestly. Dropping them on a day query would hide the failures the
                // seam exists to expose, so the whole year's map travels and the output says so.
                resolved = YearResolution(events, resolution.unresolved),
            ),
            ekadashiYear = renderer.ekadashiYear(
                year = scope.year,
                location = site.location,
                rules = rules,
                // A day query keeps an Ekadashi whose *parana* falls on that day, as well as one
                // whose fast does. The parana is the most time-critical thing this program
                // computes — a window can be under forty minutes wide — and it lands on the day
                // *after* the fast. Filtering on the fast date alone would answer "nothing today"
                // to someone asking on the very morning they must break their fast.
                observances = observances.filter {
                    onlyDate == null || it.date == onlyDate || it.parana?.date == onlyDate
                },
            ),
        )
    }
}

/**
 * The JSON document.
 *
 * The two `:wire` roots travel whole and untouched; everything `:calc` adds sits *around* them,
 * never inside. What `:calc` adds is the one thing `:wire` cannot know — which point was
 * computed at and why that point — because that is a property of how the request was phrased,
 * not of the schema. Both roots carry their own `schemaVersion`, `site` and `sampradaya`; the
 * repetition is deliberate, so either can be lifted out of the envelope and still be a valid,
 * self-describing v1 payload.
 *
 * No timestamp, no host name, no run id: two runs of the same arguments must produce the same
 * bytes, and a `generatedAt` field would make that impossible to assert.
 */
object CalcJson {

    fun document(result: CalcResult): JsonObject = buildJsonObject {
        put("tool", JsonPrimitive("panchang-calc"))
        putJsonObject("request") {
            put("sampradaya", JsonPrimitive(result.rules.id))
            put("year", JsonPrimitive(result.scope.year))
            when (val s = result.scope) {
                is Scope.Year -> put("scope", JsonPrimitive("YEAR"))
                is Scope.Day -> {
                    put("scope", JsonPrimitive("DAY"))
                    put("date", JsonPrimitive(s.date.toString()))
                    put(
                        "scopeNote",
                        JsonPrimitive(
                            "yearResolution.events is filtered to this date. " +
                                "ekadashiYear.observances keeps an Ekadashi whose fast OR whose " +
                                "parana window falls on this date, because the parana falls on " +
                                "the following day and is the thing most likely to be asked for " +
                                "on the morning itself; check observances[].date against " +
                                "observances[].parana.date to tell the two cases apart. " +
                                "yearResolution.unresolved is the whole year's, because an " +
                                "unresolved entry has no date to filter on.",
                        ),
                    )
                }
            }
        }
        put("location", location(result.site))
        put(
            "ekadashiYear",
            WireJson.pretty.encodeToJsonElement(EkadashiYearDto.serializer(), result.ekadashiYear),
        )
        put(
            "yearResolution",
            WireJson.pretty.encodeToJsonElement(
                YearResolutionDto.serializer(),
                result.yearResolution,
            ),
        )
    }

    fun render(result: CalcResult): String =
        WireJson.pretty.encodeToString(JsonObject.serializer(), document(result))

    private fun location(site: ResolvedSite): JsonElement = buildJsonObject {
        put("coordinateSource", JsonPrimitive(site.source.name))
        put("query", JsonPrimitive(site.query))
        putJsonObject("coordinatesUsed") {
            put("latitude", JsonPrimitive(site.location.latitude))
            put("longitude", JsonPrimitive(site.location.longitude))
            put("timeZone", JsonPrimitive(site.location.zone.id))
            put("elevationMeters", JsonPrimitive(site.location.elevationMeters))
        }
        site.place?.let { put("place", place(it)) }
        site.nearestLabel?.let {
            putJsonObject("nearestPlaceLabel") {
                put("place", place(it.place))
                put("distanceKm", JsonPrimitive(Math.round(it.distanceKm * 100.0) / 100.0))
                put(
                    "note",
                    JsonPrimitive(
                        "A label for the coordinates supplied. Nothing was computed at this " +
                            "record's point.",
                    ),
                )
            }
        }
        put("notes", buildJsonArray { site.notes.forEach { add(JsonPrimitive(it)) } })
    }

    private fun place(p: org.panchang.gazetteer.Place): JsonElement = buildJsonObject {
        put("geonameId", JsonPrimitive(p.geonameId))
        put("name", JsonPrimitive(p.name))
        put("asciiName", JsonPrimitive(p.asciiName))
        put("kind", JsonPrimitive(p.kind.name))
        put("country", JsonPrimitive(p.country))
        put("admin1", JsonPrimitive(p.admin1))
        put("population", JsonPrimitive(p.population))
        put("latitude", JsonPrimitive(p.latitude))
        put("longitude", JsonPrimitive(p.longitude))
        put("timeZone", JsonPrimitive(p.zone.id))
        put("attribution", JsonPrimitive(org.panchang.gazetteer.Gazetteer.ATTRIBUTION))
    }
}
