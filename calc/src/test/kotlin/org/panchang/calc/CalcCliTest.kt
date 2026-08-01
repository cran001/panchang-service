package org.panchang.calc

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.parse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.panchang.wire.WireJson

/**
 * The acceptance tests for the command the project exists to serve.
 *
 * Every assertion that concerns output is made against `--format json`, because that is the form
 * with a defined schema. Asserting on the human table would pin column widths, and a test that
 * fails when a festival's name gets longer teaches people to delete tests.
 *
 * ## Why the year payloads are computed once and shared
 *
 * A year at one site is a few thousand ephemeris evaluations and takes seconds. Recomputing it
 * per test would make this class slow enough that people stop running it. The two places where a
 * *second, independent* computation is the point — determinism, and the Mumbai/Delhi difference —
 * do run it again, deliberately.
 */
class CalcCliTest {

    // ── Location precedence and refusals ────────────────────────────────────────────────────

    @Test
    fun `an ambiguous place is refused, non-zero, with every candidate named`() {
        val run = run("--place", "Raigarh", "--year", "2026", "--format", "json")

        assertEquals(1, run.statusCode, "an ambiguous place must not exit zero")
        assertEquals("", run.out, "nothing may be written to stdout when the site is not settled")

        // The two real Raigarh districts sit in two different states. Naming only one of them, or
        // naming neither, leaves the user unable to say which they meant.
        assertTrue(run.err.contains("7626540"), "Maharashtra's Raigarh district id must be listed")
        assertTrue(run.err.contains("1259006"), "Chhattisgarh's Raigarh district id must be listed")
        assertTrue(run.err.contains("Mahārāshtra"), "the state name must survive to the terminal")
        assertTrue(run.err.contains("--place-id"), "the refusal must say how to settle it")
    }

    @Test
    fun `a polar site is refused with wire's own stated reason`() {
        val run = run(
            "--lat", "78.2232", "--lon", "15.6469", "--tz", "Arctic/Longyearbyen",
            "--year", "2026",
        )

        assertEquals(1, run.statusCode)
        assertEquals("", run.out)
        assertTrue(run.err.contains("ABOVE_POLAR_LIMIT"), "the refusal must carry wire's code")
        // The limit is :wire's. If :calc ever grows one of its own, this sentence changes and the
        // test says so.
        assertTrue(run.err.contains("beyond 66 degrees"), "wire's own reason must be printed")
        assertTrue(run.err.contains("no sunrise"), "the reason must explain itself, not just fail")
    }

    @Test
    fun `a historical name fails with a message, and is never silently substituted`() {
        val run = run("--place", "Bombay", "--year", "2026")

        assertEquals(1, run.statusCode)
        assertEquals("", run.out, "a name we cannot resolve must produce no calendar at all")
        assertTrue(run.err.contains("renamed Mumbai"), "the hint must name the modern city")
        assertTrue(
            run.err.contains("does not fuzzy-match"),
            "the refusal must state that no substitution was made",
        )
    }

    @Test
    fun `a place that resolves elsewhere than the famous namesake says so`() {
        // 'Calcutta' is an exact, unambiguous match — for a town in South Africa. Nothing about
        // the resolution changes; the user is told, because otherwise the first sign of trouble
        // would be a sunrise four hours out.
        val (site, failure) = LocationResolver().byName("Calcutta")
        assertNull(failure)
        assertNotNull(site)
        assertEquals("ZA", site!!.place!!.country)
        assertTrue(
            site.notes.any { it.contains("Kolkata") },
            "the collision with Kolkata must be surfaced: $site",
        )
    }

    @Test
    fun `giving both a place and coordinates is refused rather than silently ranked`() {
        val run = run("--place", "Nadia", "--lat", "23.0", "--lon", "88.0", "--tz", "Asia/Kolkata", "--year", "2026")
        assertEquals(1, run.statusCode)
        assertTrue(run.err.contains("not both"))
    }

    @Test
    fun `coordinates without a zone are refused rather than guessed`() {
        val run = run("--lat", "19.076", "--lon", "72.8777", "--year", "2026")
        assertEquals(1, run.statusCode)
        assertTrue(run.err.contains("--tz is required"))
        assertTrue(run.err.contains("will not infer"), "the refusal must say why guessing is wrong")
    }

    @Test
    fun `an unknown sampradaya is refused, never answered with another tradition's dates`() {
        val run = run("--place", "Nadia", "--sampradaya", "pushtimarg", "--year", "2026")
        assertEquals(1, run.statusCode)
        assertEquals("", run.out)
        assertTrue(run.err.contains("Unknown sampradaya"))
        assertTrue(run.err.contains("iskcon"), "the refusal must name what is known")
    }

    // ── The payload ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `caller coordinates are reported as caller-supplied and used verbatim`() {
        val loc = mumbai.doc["location"]!!.jsonObject

        assertEquals("CALLER_COORDINATES", loc["coordinateSource"]!!.jsonPrimitive.content)

        val used = loc["coordinatesUsed"]!!.jsonObject
        assertEquals(19.076, used["latitude"]!!.jsonPrimitive.content.toDouble())
        assertEquals(72.8777, used["longitude"]!!.jsonPrimitive.content.toDouble())
        assertEquals("Asia/Kolkata", used["timeZone"]!!.jsonPrimitive.content)

        // The site inside the wire payload must be the same point. If these two ever disagree,
        // the payload is describing a place the header does not.
        val site = mumbai.doc["yearResolution"]!!.jsonObject["site"]!!.jsonObject
        assertEquals(used["latitude"], site["latitude"])
        assertEquals(used["longitude"], site["longitude"])

        // A nearby record may be named, but only as a label, and never as the thing computed at.
        val label = loc["nearestPlaceLabel"]!!.jsonObject
        assertEquals("Mumbai", label["place"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertTrue(label["note"]!!.jsonPrimitive.content.contains("Nothing was computed"))
    }

    @Test
    fun `a place resolves to the record's point and says that is what it did`() {
        val loc = nadia.doc["location"]!!.jsonObject
        assertEquals("GAZETTEER_PLACE_POINT", loc["coordinateSource"]!!.jsonPrimitive.content)
        assertEquals(1262293, loc["place"]!!.jsonObject["geonameId"]!!.jsonPrimitive.content.toInt())
        assertTrue(
            loc["notes"]!!.jsonArray.any { it.jsonPrimitive.content.contains("not your position") },
            "using a record's representative point must be stated, not implied",
        )
    }

    @Test
    fun `an event with no anchor emits no fast-until field at all, not a null-shaped one`() {
        val events = mumbai.events()

        val anchored = events.filter { it.containsKey("fastUntil") }
        val fastingWithoutAnchor = events.filter {
            it.containsKey("fastingNote") && !it.containsKey("fastUntil")
        }
        val noFastAtAll = events.filter { !it.containsKey("fastingNote") }

        assertTrue(anchored.isNotEmpty(), "some events do name an anchor; none were found")
        assertTrue(
            fastingWithoutAnchor.isNotEmpty(),
            "the catalog carries fasts with no time of day; the payload must be able to say so",
        )
        assertTrue(noFastAtAll.isNotEmpty(), "most events carry no fast at all")

        // The structural claim: absence is absence. Not `"fastUntil": null`, not an empty object,
        // not a zero instant. A client cannot format what is not there, which is the whole point.
        for (e in fastingWithoutAnchor + noFastAtAll) {
            assertFalse(
                e.containsKey("fastUntil"),
                "event ${e["id"]} must carry no fastUntil key",
            )
        }
        val raw = WireJson.pretty.encodeToString(
            JsonArray.serializer(),
            JsonArray(fastingWithoutAnchor + noFastAtAll),
        )
        assertFalse(raw.contains(": null"), "no null-shaped field may appear in the payload")

        // And the anchored ones must carry their own confidence and their basis, separately from
        // the date's confidence. Janmastami is the case that makes this matter: CONFIRMED date,
        // INFERRED reading of "till midnight".
        for (e in anchored) {
            val f = e["fastUntil"]!!.jsonObject
            assertTrue(f.containsKey("basis"), "an anchor must carry the definition it used")
            assertTrue(f.containsKey("confidence"), "an anchor must carry its own confidence")
            assertTrue(f["basis"]!!.jsonPrimitive.content.isNotBlank())
        }
        val janmastami = anchored.single { it["id"]!!.jsonPrimitive.content == "janmastami" }
        assertEquals("CONFIRMED", janmastami["confidence"]!!.jsonPrimitive.content)
        assertEquals(
            "INFERRED",
            janmastami["fastUntil"]!!.jsonObject["confidence"]!!.jsonPrimitive.content,
            "reading 'fast till midnight' as Nisita-kala is inferred, and must not be dressed " +
                "up as the date's own confidence",
        )
        assertTrue(
            janmastami["fastUntil"]!!.jsonObject["basis"]!!.jsonPrimitive.content
                .contains("pandit review"),
            "an inferred anchor must reach the user flagged for review",
        )
    }

    @Test
    fun `every event carries its tithi window, or honestly carries none`() {
        val events = mumbai.events()
        val withTithi = events.filter { it.containsKey("tithi") }
        assertTrue(withTithi.isNotEmpty())
        for (e in withTithi) {
            val t = e["tithi"]!!.jsonObject
            // Both instants, both halves each: the local string a human reads and the Julian Day
            // that makes a disagreement diagnosable.
            for (bound in listOf("start", "end")) {
                val i = t[bound]!!.jsonObject
                assertTrue(i["local"]!!.jsonPrimitive.content.contains("T"))
                assertTrue(i["jdUt"]!!.jsonPrimitive.content.toDouble() > 2_400_000.0)
            }
        }
        // Nakshatra-ruled and tabulated entries genuinely have no qualifying tithi. They must be
        // allowed to say nothing rather than be given an invented one.
        assertTrue(
            events.any { !it.containsKey("tithi") },
            "an event with no qualifying tithi must omit the field",
        )
    }

    @Test
    fun `unresolved is printed, not dropped`() {
        val yr = mumbai.doc["yearResolution"]!!.jsonObject
        val unresolved = yr["unresolved"]

        assertNotNull(unresolved, "the unresolved map must always be present in the payload")
        val map = unresolved!!.jsonObject

        // Mumbai 2026 genuinely has entries that could not be placed. If this ever becomes empty
        // it is a change in the rules or the year, not a flake — and it should be looked at
        // rather than relaxed away.
        assertTrue(
            map.isNotEmpty(),
            "Mumbai 2026 has catalog entries that resolve to no date; they must be reported",
        )
        for ((id, entry) in map) {
            val o = entry.jsonObject
            val type = o["type"]?.jsonPrimitive?.content
            assertNotNull(type, "$id must say which kind of failure it was")
            assertTrue(
                o["why"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true,
                "$id ($type) must say why, in words a person can act on",
            )
        }

        // And it survives a day query, because an unresolved entry has no date to be filtered by.
        assertTrue(
            mumbai.text.contains("\"unresolved\""),
            "the key must be in the emitted bytes, not merely in the object model",
        )
    }

    @Test
    fun `ekadashi observances carry both parana bounds with both reasons`() {
        val obs = mumbai.doc["ekadashiYear"]!!.jsonObject["observances"]!!.jsonArray
            .map { it.jsonObject }
        assertEquals(24, obs.size, "an ordinary year carries 24 Ekadashi observances")

        var withWindow = 0
        for (o in obs) {
            assertTrue(o.containsKey("tithi"), "${o["date"]} must carry the tithi it rests on")
            val parana = o["parana"]?.jsonObject ?: continue
            withWindow++
            // Both bounds, both reasons. A window with one reason is a window whose other edge
            // the reader has to guess at, and the two edges come from different rules.
            assertTrue(parana.containsKey("startReason"), "a window must say why it starts there")
            assertTrue(parana.containsKey("endReason"), "a window must say why it ends there")
            assertTrue(parana["durationMinutes"]!!.jsonPrimitive.content.toDouble() > 0.0)
        }
        assertTrue(withWindow >= obs.size - 2, "almost every Ekadashi yields a parana window")
        // At this site in 2026 one observance yields no window at all. It must be absent, not
        // present-and-empty: a zero-width window would be read as 'you may not break your fast'.
        assertFalse(mumbai.text.contains("\"parana\": null"))

        // Mahadvadashi typing is an if-and-only-if: a Mahadvadashi names which of the eight it is,
        // and an ordinary Ekadashi carries no stray type. Checked at both sites, because Mumbai
        // 2026 contains no Mahadvadashi at all and Nadia contains three — which is itself the
        // point. Whether a fast is an ordinary Ekadashi or a Paksavardhini depends on where you
        // are standing.
        val nadiaObs = nadia.doc["ekadashiYear"]!!.jsonObject["observances"]!!.jsonArray
            .map { it.jsonObject }
        for (o in obs + nadiaObs) {
            val isMaha = o["kind"]!!.jsonPrimitive.content == "MAHADVADASHI"
            val named = o["mahadvadashiType"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true
            assertEquals(isMaha, named, "kind and mahadvadashiType disagree on ${o["date"]}")
        }
        assertTrue(
            nadiaObs.any { it["kind"]!!.jsonPrimitive.content == "MAHADVADASHI" },
            "Nadia 2026 carries Mahadvadashis; if none are found the naming has been lost",
        )
    }

    @Test
    fun `an undecidable parana window is omitted with a reason, not invented`() {
        // Mumbai, 2026-08-23. The two rules that bound the window cross over: the fast may not be
        // broken before Hari Vasara ends, and must be broken before the first third of daylight
        // ends, which here comes first. The engine could produce an inverted window that formats
        // as a perfectly ordinary '07:12 - 06:34'. It refuses, says why, and marks the ruling
        // INFERRED so that it reaches a pandit rather than a devotee's alarm clock.
        val o = mumbai.doc["ekadashiYear"]!!.jsonObject["observances"]!!.jsonArray
            .map { it.jsonObject }
            .single { it["date"]!!.jsonPrimitive.content == "2026-08-23" }

        assertFalse(o.containsKey("parana"), "an underivable window must be absent, not empty")
        assertEquals("INFERRED", o["confidence"]!!.jsonPrimitive.content)
        val why = o["reason"]!!.jsonPrimitive.content
        assertTrue(why.contains("No parana window is given"), "the omission must be stated: $why")
        assertTrue(why.contains("pandit"), "an undecidable case must be routed to review: $why")
    }

    @Test
    fun `a day query keeps the Ekadashi whose parana falls that morning, and all unresolved`() {
        // The brief's own second example. 2026-01-15 carries no festival and no fast — but it is
        // the morning Sat-tila Ekadashi's fast is broken, between 07:14 and 10:56, and that
        // window is the most time-critical thing this program computes. A day query that
        // answered "nothing today" to someone standing in it would be useless.
        val day = payload(
            "--lat", "19.0760", "--lon", "72.8777", "--tz", "Asia/Kolkata",
            "--date", "2026-01-15", "--format", "json",
        )

        val obs = day.doc["ekadashiYear"]!!.jsonObject["observances"]!!.jsonArray
            .map { it.jsonObject }
        assertEquals(1, obs.size)
        val o = obs.single()
        assertEquals("Sat-tila Ekadashi", o["name"]!!.jsonPrimitive.content)
        // The fast was yesterday; the window is today. Both dates are present so the two cases
        // are distinguishable rather than conflated.
        assertEquals("2026-01-14", o["date"]!!.jsonPrimitive.content)
        assertEquals("2026-01-15", o["parana"]!!.jsonObject["date"]!!.jsonPrimitive.content)

        assertEquals("DAY", day.doc["request"]!!.jsonObject["scope"]!!.jsonPrimitive.content)
        assertTrue(day.doc["request"]!!.jsonObject.containsKey("scopeNote"))

        // No festival falls on this date, and the payload says so with an empty list rather than
        // by omitting the key.
        assertTrue(day.doc["yearResolution"]!!.jsonObject["events"]!!.jsonArray.isEmpty())

        // Unresolved is the whole year's and survives the filter, because an unresolved entry has
        // no date that a day filter could honestly include or exclude it by.
        assertEquals(
            mumbai.doc["yearResolution"]!!.jsonObject["unresolved"]!!.jsonObject.keys,
            day.doc["yearResolution"]!!.jsonObject["unresolved"]!!.jsonObject.keys,
            "a day query must not silently drop the year's unresolved entries",
        )
    }

    // ── Determinism, and the difference that is the whole requirement ───────────────────────

    @Test
    fun `the same arguments produce byte-identical output across two runs`() {
        val again = run(*MUMBAI_ARGS)
        assertEquals(0, again.statusCode)
        assertEquals(
            mumbai.text,
            again.out,
            "two runs of one command must be diffable; a payload that differs run to run " +
                "cannot be published, cached or compared against a reference",
        )
    }

    @Test
    fun `two cities in one time zone get different parana times`() {
        // This is the user's requirement, stated as a test. Mumbai and Delhi are both
        // Asia/Kolkata; a service that answered from the zone rather than from the place would
        // give them the same clock times, and the whole project would be pointless.
        val delhi = payload(
            "--lat", "28.6139", "--lon", "77.2090", "--tz", "Asia/Kolkata",
            "--year", "2026", "--format", "json",
        )

        val m = mumbai.paranaStartsByDate()
        val d = delhi.paranaStartsByDate()
        val shared = m.keys.intersect(d.keys)
        assertTrue(shared.size > 15, "the two cities should share most fasting dates")

        val sunriseBound = shared.filter {
            m.getValue(it).second == "SUNRISE" && d.getValue(it).second == "SUNRISE"
        }
        assertTrue(sunriseBound.isNotEmpty())

        val diffsMinutes = sunriseBound.map {
            (m.getValue(it).first - d.getValue(it).first) * 24.0 * 60.0
        }
        assertTrue(
            diffsMinutes.any { kotlin.math.abs(it) > 20.0 },
            "Mumbai and Delhi are 4.3 degrees of longitude and 9.5 degrees of latitude apart; " +
                "their sunrise-bounded parana windows must differ by tens of minutes, not " +
                "agree. Observed spread: ${diffsMinutes.minOrNull()}..${diffsMinutes.maxOrNull()}",
        )

        // A tithi-derived bound is global and must be identical at both sites. This is the
        // control: it shows the difference above comes from the astronomy of the place and not
        // from noise in the pipeline.
        val hariVasara = shared.filter {
            m.getValue(it).second == "HARI_VASARA_END" && d.getValue(it).second == "HARI_VASARA_END"
        }
        for (date in hariVasara) {
            assertEquals(
                m.getValue(date).first,
                d.getValue(date).first,
                "a Hari Vasara bound is a tithi instant and is the same everywhere on Earth",
            )
        }

        assertFalse(mumbai.text == delhi.text, "two different sites must not produce one payload")
    }

    // ── Harness ─────────────────────────────────────────────────────────────────────────────

    private data class Run(val statusCode: Int, val out: String, val err: String)

    private data class Payload(val text: String) {
        val doc: JsonObject = WireJson.pretty.parseToJsonElement(text).jsonObject

        fun events(): List<JsonObject> =
            doc["yearResolution"]!!.jsonObject["events"]!!.jsonArray.map { it.jsonObject }

        /** date -> (parana start as jdUt, startReason). Julian Day, so no re-parsing of strings. */
        fun paranaStartsByDate(): Map<String, Pair<Double, String>> =
            doc["ekadashiYear"]!!.jsonObject["observances"]!!.jsonArray
                .map { it.jsonObject }
                .mapNotNull { o ->
                    val p = o["parana"]?.jsonObject ?: return@mapNotNull null
                    o["date"]!!.jsonPrimitive.content to Pair(
                        p["start"]!!.jsonObject["jdUt"]!!.jsonPrimitive.content.toDouble(),
                        p["startReason"]!!.jsonPrimitive.content,
                    )
                }
                .toMap()
    }

    private fun run(vararg args: String): Run {
        val out = StringBuilder()
        val err = StringBuilder()
        val status = try {
            CalcCommand(out = out, err = err).parse(args.toList())
            0
        } catch (e: CliktError) {
            // Clikt's own usage errors carry a message we would otherwise swallow.
            if (e.message?.isNotBlank() == true) err.append(e.message).append('\n')
            e.statusCode
        }
        return Run(status, out.toString(), err.toString())
    }

    private fun payload(vararg args: String): Payload {
        val r = run(*args)
        assertEquals(0, r.statusCode, "command failed: ${r.err}")
        return Payload(r.out)
    }

    companion object {
        private val MUMBAI_ARGS = arrayOf(
            "--lat", "19.0760", "--lon", "72.8777", "--tz", "Asia/Kolkata",
            "--year", "2026", "--format", "json",
        )

        /** One Mumbai year, shared by every test that only reads it. */
        private val mumbai: Payload by lazy { CalcCliTest().payload(*MUMBAI_ARGS) }

        /** One district-resolved year, for the `--place` precedence assertions. */
        private val nadia: Payload by lazy {
            CalcCliTest().payload("--place", "Nadia", "--year", "2026", "--format", "json")
        }
    }
}
