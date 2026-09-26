package org.panchang.verify.audit

import java.io.File
import java.time.*
import java.time.zone.ZoneRulesProvider
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.panchang.core.*
import org.panchang.ephemeris.*
import org.panchang.sampradaya.*
import org.panchang.verify.horizons.*
import org.panchang.verify.usno.*
import org.panchang.verify.vaisnava.*
import org.panchang.calc.*

/** Measurement collector. Success means evidence was collected, not that comparisons agree. */
class AuditEvidenceTest {
    private val root = File(System.getProperty("audit.root"))
    private val out = File(System.getProperty("audit.output"))
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val calc = PanchangCalculator(Vsop87Ephemeris())
    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull
    private fun obj(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
        for ((k,v) in pairs) put(k, when(v) {
            null -> JsonNull
            is JsonElement -> v
            is Number -> JsonPrimitive(v)
            is Boolean -> JsonPrimitive(v)
            else -> JsonPrimitive(v.toString())
        })
    }
    private fun write(name: String, rows: List<JsonObject>) = File(out,name).writeText(json.encodeToString(JsonArray.serializer(),JsonArray(rows)))

    @Test fun januaryFirstDeliveryContract() {
        val loc=GeoLocation.of(23.0+25.0/60,88.0+23.0/60,"Asia/Kolkata")
        val date=LocalDate.of(2026,1,1)
        val doc=json.parseToJsonElement(File(root,"verify/golden/vaisnavacalendar-mayapur-2026.json").readText()).jsonObject
        val ref=doc["records"]!!.jsonArray.single{it.jsonObject.str("date")==date.toString()}.jsonObject["parana"]
        assertTrue(ref is JsonObject,"Reference must actually print a January 1 window")
        val result=CalcEngine().compute(ResolvedSite(loc,CoordinateSource.CALLER_COORDINATES,"audit"),IskconRules(),Scope.Day(date))
        File(out,"january-first-delivery.json").writeText(CalcJson.render(result))
        assertEquals(1,result.ekadashiYear.observances.count{it.parana?.date==date},"January 1 day output must include the previous December fast's Parana printed in the independent calendar")
    }

    @Test fun highLatitudeDiagnostics() {
        val rows=mutableListOf<JsonObject>()
        for((city,loc) in listOf("reykjavik" to GeoLocation.of(64.1466,-21.9426,"Atlantic/Reykjavik"),"akureyri" to GeoLocation.of(65.6835,-18.0878,"Atlantic/Reykjavik"),"longyearbyen" to GeoLocation.of(78.2232,15.6469,"Arctic/Longyearbyen"),"mcmurdo" to GeoLocation.of(-77.8419,166.6863,"Antarctica/McMurdo"))) {
            val date=LocalDate.of(2026,6,26);val sun=calc.sunTimes(date,loc);val tomorrow=calc.sunTimes(date.plusDays(1),loc)
            var parana: org.panchang.sampradaya.ParanaWindow?=null
            if(city=="reykjavik") parana=IskconRules().ekadashiObservances(2026,ObservanceContext(calc,loc)).mapNotNull{it.parana}.firstOrNull{it.date==date}
            val rise=sun.sunrise.jdUtOrNull;val nextSet=tomorrow.sunset.jdUtOrNull
            rows+=obj("city" to city,"latitude" to loc.latitude,"longitude" to loc.longitude,"acceptance" to org.panchang.wire.acceptSite(loc),"date" to date,"sunrise" to rise?.let{loc.zonedDateTime(it)},"sameDateSunset" to sun.sunset.jdUtOrNull?.let{loc.zonedDateTime(it)},"nextDateSunset" to nextSet?.let{loc.zonedDateTime(it)},"civilDaylightMinutes" to sun.daylightDays?.times(1440),"followingSunsetThird" to if(rise!=null&&nextSet!=null)loc.zonedDateTime(rise+(nextSet-rise)/3) else null,"actualParanaEnd" to parana?.let{loc.zonedDateTime(it.endJdUt)},"actualParanaEndBasis" to parana?.endReason,"actualParanaDurationMinutes" to parana?.durationMinutes)
        }
        write("high-latitude-diagnostics.json",rows)
    }

    @Test fun investigateDailyDisagreements() {
        val cases=json.parseToJsonElement(File(out,"daily-field-disagreements.json").readText()).jsonArray
        val locations=json.parseToJsonElement(File(out,"fasting-comparisons.json").readText()).jsonArray.map{it.jsonObject}.associateBy{it.str("city")}
        val rows=cases.map { e ->
            val r=e.jsonObject;val site=locations[r.str("city")]!!
            val loc=GeoLocation.of(site["latitude"]!!.jsonPrimitive.double,site["longitude"]!!.jsonPrimitive.double,site.str("zone")!!)
            val date=LocalDate.parse(r.str("date"));val rise=calc.sunTimes(date,loc).sunrise.jdUtOrNull!!
            val t=calc.tithiAt(rise);val n=calc.nakshatraAt(rise)
            obj("comparison" to r,"sunrise" to loc.zonedDateTime(rise),"actualTithiStart" to loc.zonedDateTime(t.startJdUt),"actualNakshatraStart" to loc.zonedDateTime(n.startJdUt),"sunriseMinusTithiStartSeconds" to (rise-t.startJdUt)*86400,"sunriseMinusNakshatraStartSeconds" to (rise-n.startJdUt)*86400,"ayanamshaDegrees" to calc.ayanamshaDegAt(rise))
        }
        write("daily-boundary-diagnostics.json",rows)
        assertEquals(cases.size,rows.size)
    }

    @Test fun reykjavikSunriseRoundingContract() {
        val file=File(out,"new-astronomy/retry-usno-reykjavik-2026-06-26.json")
        val r=json.decodeFromJsonElement<UsnoDayRecord>(json.parseToJsonElement(file.readText()).jsonObject["records"]!!.jsonArray.single())
        val date=LocalDate.parse(r.date);val loc=GeoLocation.of(r.latitudeDeg,r.longitudeDeg,"Atlantic/Reykjavik")
        val ref=TimeScale.jdUt(date.atTime(LocalTime.parse(r.sunRises.single())).toInstant(ZoneOffset.UTC))
        val rows=listOf(6,12,24).map{passes ->
            val value=calc.sunTimes(date,loc,passes).sunrise.jdUtOrNull!!
            obj("refinementPasses" to passes,"reference" to r.sunRises.single(),"date" to date,"actual" to loc.zonedDateTime(value),"deltaSeconds" to (value-ref)*86400)
        }
        write("reykjavik-sunrise-sensitivity.json",rows)
        assertTrue(kotlin.math.abs(rows.first()["deltaSeconds"]!!.jsonPrimitive.double)<=30,"Held-out Reykjavik 2026-06-27 sunrise exceeds the predeclared USNO rounding interval")
    }

    @Test fun reykjavikDocumentedDaylightCapContract() {
        fun record(name:String)=json.decodeFromJsonElement<UsnoDayRecord>(json.parseToJsonElement(File(out,"new-astronomy/$name.json").readText()).jsonObject["records"]!!.jsonArray.single())
        val day=record("usno-reykjavik-2026-06-26"); val next=record("retry-usno-reykjavik-2026-06-26")
        val rise=LocalDate.parse(day.date).atTime(LocalTime.parse(day.sunRises.single())).toInstant(ZoneOffset.UTC)
        val set=LocalDate.parse(next.date).atTime(LocalTime.parse(next.sunSets.single())).toInstant(ZoneOffset.UTC)
        val referenceThird=rise.plusSeconds(Duration.between(rise,set).seconds/3)
        val actual=json.parseToJsonElement(File(out,"high-latitude-diagnostics.json").readText()).jsonArray.first().jsonObject.str("actualParanaEnd")!!
        val actualInstant=ZonedDateTime.parse(actual).toInstant()
        File(out,"reykjavik-cap-comparison.json").writeText(obj("date" to day.date,"referenceSunrise" to rise,"referenceFollowingSunset" to set,"referenceDerivedDaylightThird" to referenceThird,"actualParanaEnd" to actual,"excessSeconds" to Duration.between(referenceThird,actualInstant).toMillis()/1000.0,"scope" to "Contract with the repository's documented daylight-third cap; human observance approval remains separate").toString())
        assertTrue(actualInstant<=referenceThird.plusSeconds(30),"An ordinary sunrise followed by sunset exists: the documented daylight-third cap must not disappear merely because sunset has the next civil date")
    }

    @Test fun parseDownloads() {
        val manifest=listOf("fetch-manifest.json","supplement-manifest.json","retry-manifest.json").map{File(out,it)}.filter{it.exists()}.flatMap{json.parseToJsonElement(it.readText().removePrefix("\uFEFF")).jsonArray}
        val reports=mutableListOf<JsonObject>()
        File(out,"new-astronomy").mkdirs(); File(out,"new-calendars").mkdirs()
        for(e in manifest) {
            val m=e.jsonObject;val q=m["parameters"]!!.jsonObject;val id=m.str("id")!!
            if(q.str("kind")=="documentation") { reports+=obj("id" to id,"status" to "documentation_only");continue }
            if(m["httpStatus"]!!.jsonPrimitive.int!=200) { reports+=obj("id" to id,"status" to "http_unavailable");continue }
            try {
                val raw=File(out,m.str("file")!!).readBytes()
                check(java.security.MessageDigest.getInstance("SHA-256").digest(raw).joinToString(""){"%02x".format(it)}==m.str("sha256"))
                var site: JsonElement?=null
                val (records,report)=when(q.str("kind")) {
                    "horizons" -> {
                        val parsed=HorizonsParser.parse(raw.toString(Charsets.UTF_8),HorizonsQuery(HorizonsBody.parse(q.str("body")!!),q.str("start")!!,q.str("stop")!!,q.str("step")!!))
                        json.encodeToJsonElement(parsed.records) to parsed.report
                    }
                    "usno" -> {
                        val parsed=UsnoParser.parse(raw.toString(Charsets.UTF_8),UsnoQuery(q.str("date")!!,q["latitude"]!!.jsonPrimitive.double,q["longitude"]!!.jsonPrimitive.double,q["offset"]!!.jsonPrimitive.double))
                        json.encodeToJsonElement(parsed.records) to parsed.report
                    }
                    else -> {
                        val (calendar,r)=VaisnavaCalendarTxtParser.parseCalendar(raw.toString(Charsets.ISO_8859_1))
                        val h=calendar.header;val cityId=q.str("city")!!.substringBefore(' ').lowercase()
                        // Hyderabad is held out from the production lookup. Parse its printed coordinates locally.
                        val c=Regex("(\\d+)([NS])(\\d+)\\s+(\\d+)([EW])(\\d+)").matchEntire(h.coordinates)!!.groupValues
                        val lat=(c[1].toDouble()+c[3].toDouble()/60)*(if(c[2]=="S") -1 else 1)
                        val lon=(c[4].toDouble()+c[6].toDouble()/60)*(if(c[5]=="W") -1 else 1)
                        check(h.utcOffset.trimStart('+')=="5:30")
                        check(calendar.days.all{it.date.startsWith(q.str("year")!!)})
                        check(calendar.days.size==365)
                        site=obj("city" to h.city,"coordinates" to h.coordinates,"utcOffset" to h.utcOffset,"generator" to h.generator,"cityId" to cityId,"latitudeDeg" to lat,"longitudeDeg" to lon,"ianaZone" to "Asia/Kolkata")
                        json.encodeToJsonElement(calendar.days) to r
                    }
                }
                check(report.unparsed.isEmpty()) { "Unparsed reference lines: ${report.unparsed}" }
                val directory=if(q.str("kind")=="calendar") "new-calendars" else "new-astronomy"
                File(out,"$directory/$id.json").writeText(obj("provenance" to m,"records" to records,"site" to site).toString())
                reports+=obj("id" to id,"status" to "parsed","count" to report.recordCount,"warnings" to report.warnings.joinToString())
            } catch(t:Exception) { reports+=obj("id" to id,"status" to "parse_unavailable","error" to t.toString()) }
        }
        write("parse-report.json",reports)
        assertTrue(reports.any{it.str("status")=="parsed"})
    }

    @Test fun planRequests() {
        val rows = mutableListOf<JsonObject>()
        for ((label,start,stop) in listOf(
            Triple("2027","2027-01-01","2027-02-01"), Triple("2028","2028-01-01","2028-02-01"),
            Triple("vrindavan-boundary","2026-06-29 23:40","2026-06-30 00:10"),
            Triple("auckland-boundary","2026-10-07 17:30","2026-10-07 18:00")
        )) for (body in HorizonsBody.entries) {
            val step = if(label.contains("boundary")) "10 m" else "1 d"
            val q = HorizonsQuery(body,start,stop,step)
            val spec = HorizonsHarvester().requestSpec(q)
            rows += obj("id" to "horizons-$label-${body.name.lowercase()}","kind" to "horizons", "url" to spec.url,
                "body" to body.name,"start" to start,"stop" to stop,"step" to step)
        }
        val locations = listOf(
            Triple("cape-town",GeoLocation.of(-33.925,18.424,"Africa/Johannesburg"),listOf("2027-01-01","2028-06-21")),
            Triple("singapore",GeoLocation.of(1.3521,103.8198,"Asia/Singapore"),listOf("2027-12-31","2028-01-01")),
            Triple("reykjavik",GeoLocation.of(64.1466,-21.9426,"Atlantic/Reykjavik"),listOf("2026-06-26","2028-06-21")),
            Triple("london",GeoLocation.of(51.5074,-0.1278,"Europe/London"),listOf("2027-03-28","2028-10-29")),
            Triple("new-york",GeoLocation.of(40.7128,-74.0060,"America/New_York"),listOf("2027-03-14","2028-11-05")),
            Triple("vrindavan",GeoLocation.of(27.0+35.0/60,77.7,"Asia/Kolkata"),listOf("2026-06-30")),
            Triple("auckland",GeoLocation.of(-36.0-52.0/60,174.0+46.0/60,"Pacific/Auckland"),listOf("2026-10-08"))
        )
        for ((id,loc,dates) in locations) for (date in dates) {
            val offset = LocalDate.parse(date).atTime(12,0).atZone(loc.zone).offset.totalSeconds/3600.0
            val q = UsnoQuery(date,loc.latitude,loc.longitude,offset,id)
            rows += obj("id" to "usno-$id-$date","kind" to "usno","url" to UsnoHarvester().requestSpec(q).url,
                "date" to date,"latitude" to loc.latitude,"longitude" to loc.longitude,"offset" to offset,"zone" to loc.zone.id)
        }
        for (year in listOf(2027,2028)) for(city in listOf("Mayapur [India]","Hyderabad [India]")) {
            val q=VaisnavaCalendarQuery(year,city)
            rows += obj("id" to "calendar-${city.substringBefore(' ').lowercase()}-$year", "kind" to "calendar",
                "url" to VaisnavaCalendarTxtHarvester().requestSpec(q).url,"year" to year,"city" to city)
        }
        write("requests.json",rows)
        File(out,"runtime.json").writeText(obj("java" to System.getProperty("java.runtime.version"),"tzdb" to ZoneRulesProvider.getVersions("Europe/London").keys.joinToString(),"defaultZone" to ZoneId.systemDefault()).toString())
        assertEquals(24,rows.size)
    }

    @Test fun calendars() {
        val files=File(root,"verify/golden").listFiles()!!.filter {it.name.startsWith("vaisnavacalendar-")}.sortedBy {it.name}
        val extra=File(out,"new-calendars").listFiles()?.filter {it.extension=="json"}.orEmpty()
        val fastRows=mutableListOf<JsonObject>(); val timeRows=mutableListOf<JsonObject>(); val dailyRows=mutableListOf<JsonObject>(); val diag=mutableListOf<JsonObject>(); val siteRows=mutableListOf<JsonObject>()
        for(file in files+extra) {
            val doc=json.parseToJsonElement(file.readText()).jsonObject
            val days=doc["records"]!!.jsonArray.map{it.jsonObject}
            val year=days.first().str("date")!!.take(4).toInt()
            val site=doc["site"]?.jsonObject
            val city=site?.str("cityId")?.takeIf{it.isNotEmpty()} ?: file.name.removePrefix("vaisnavacalendar-").removeSuffix("-$year.json")
            val loc=if(site==null) GeoLocation.of(23.0+25.0/60,88.0+23.0/60,"Asia/Kolkata") else GeoLocation.of(site["latitudeDeg"]!!.jsonPrimitive.double,site["longitudeDeg"]!!.jsonPrimitive.double,site.str("ianaZone")!!)
            val index=LunarDayIndex.build(year,ObservanceContext(calc,loc))
            val all=IskconRules().allObservancesInWindow(index)
            val actual=all.filter{it.date.year==year}
            val refs=days.filter{it.str("fastingFor")!=null}
            siteRows+=obj("city" to city,"year" to year,"referenceDayCount" to days.size,"referenceFastCount" to refs.size,"actualFastCount" to actual.size,"referenceFastDates" to json.encodeToJsonElement(refs.map{it.str("date")}),"actualFastDates" to json.encodeToJsonElement(actual.map{it.date.toString()}),"actualTypes" to json.encodeToJsonElement(actual.groupingBy{it.mahadvadashiType?.name ?: "ORDINARY"}.eachCount()),"source" to file.relativeTo(root).path,"location" to loc)
            val paranas=all.mapNotNull{d->d.parana?.let{it.date to d}}.toMap()
            for(d in refs) {
                val date=LocalDate.parse(d.str("date")); val name=d.str("fastingFor")!!
                val matched=actual.minByOrNull{kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(date,it.date))}?.takeIf{kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(date,it.date))<4}
                fastRows+=obj("city" to city,"year" to year,"referenceDate" to date,"referenceName" to name,"actualDate" to matched?.date,"actualName" to matched?.name,"dateMatch" to (matched?.date==date),"referenceLabels" to d["events"],"actualType" to matched?.mahadvadashiType,"confidence" to matched?.confidence,"reason" to matched?.reason,"source" to file.relativeTo(root).path,"latitude" to loc.latitude,"longitude" to loc.longitude,"zone" to loc.zone)
            }
            for(d in days.filter{it["parana"] is JsonObject}) {
                val date=LocalDate.parse(d.str("date")); val p=d["parana"]!!.jsonObject; val decision=paranas[date]; val window=decision?.parana
                val stale=(p.str("clock")=="DST") != loc.zone.rules.isDaylightSavings(date.atTime(12,0).atZone(loc.zone).toInstant())
                for(bound in listOf("start","end")) {
                    val ref=p.str(bound); val jd=if(bound=="start") window?.startJdUt else window?.endJdUt
                    val reason=if(bound=="start") window?.startReason else window?.endReason
                    val civil=jd?.let{loc.zonedDateTime(it)}
                    val delta=if(ref!=null&&civil!=null) Duration.between(date.atTime(LocalTime.parse(ref)).atZone(loc.zone).toInstant(),civil.toInstant()).toNanos()/1e9 else null
                    timeRows+=obj("city" to city,"year" to year,"date" to date,"bound" to bound,"reference" to ref,"referenceBasis" to p.str(bound+"Basis"),"referenceClock" to p.str("clock"),"actual" to civil,"actualBasis" to reason,"deltaSeconds" to delta,"staleReferenceDst" to stale,"status" to when { ref==null->"reference_end_unavailable";jd==null->"actual_window_missing";stale->"excluded_stale_reference_dst";else->"compared"})
                }
            }
            val refDates=days.filter{it["parana"] is JsonObject}.map{it.str("date")}.toSet()
            for((date,d) in paranas) if(date.year==year&&date.toString() !in refDates) timeRows+=obj("city" to city,"year" to year,"date" to date,"status" to "extra_actual_window","actual" to d.parana.toString())
            for(d in days) {
                val date=LocalDate.parse(d.str("date")); val span=index.spanAtSunriseOf(date); val rise=index.sunriseOf(date)
                val nak=rise?.let{calc.nakshatraAt(it)}
                dailyRows+=obj("city" to city,"year" to year,"date" to date,"referenceTithi" to d.str("tithi"),"actualTithiIndex" to span?.tithiIndex,"actualTithi" to span?.let{Tithi.nameOf(it.tithiIndex)},"referencePaksa" to d.str("paksa"),"actualPaksa" to span?.paksha,"referenceNakshatra" to d.str("naksatra"),"actualNakshatra" to nak?.name,"referenceMasa" to d.str("masa"),"actualPurnimantaMonth" to span?.monthName(MonthReckoning.PURNIMANTA),"weekdayReference" to d.str("weekdayAbbrev"),"weekdayActual" to date.dayOfWeek)
            }
            val selected=mapOf("vrindavan" to listOf("2026-06-30"),"delhi" to listOf("2026-06-30"),"mayapur" to listOf("2026-06-30"),"auckland" to listOf("2026-10-08","2026-04-28","2026-09-23"),"moscow" to listOf("2026-05-27","2026-05-28"),"sydney" to listOf("2026-12-05","2026-12-06"),"ahmedabad" to listOf("2026-11-06"),"mumbai" to listOf("2026-08-24"),"new-york" to listOf("2026-10-22"))
            if(year==2026) for(text in selected[city].orEmpty()) {
                val date=LocalDate.parse(text);val sun=index.sunTimesOf(date);val rise=sun.sunrise.jdUtOrNull!!
                val spans=index.spans().filter{kotlin.math.abs(it.endJdUt-rise)<2 || kotlin.math.abs(it.startJdUt-rise)<2}
                diag+=obj("city" to city,"date" to date,"sunrise" to loc.zonedDateTime(rise),"sunset" to sun.sunset.jdUtOrNull?.let{loc.zonedDateTime(it)},"daylightThirdEnd" to sun.daylightDays?.let{loc.zonedDateTime(rise+it/3)},"spans" to JsonArray(spans.map{obj("index" to it.tithiIndex,"start" to loc.zonedDateTime(it.startJdUt),"end" to loc.zonedDateTime(it.endJdUt),"endMinusSunriseSeconds" to (it.endJdUt-rise)*86400,"sunriseMinusStartSeconds" to (rise-it.startJdUt)*86400,"hariVasaraEnd" to loc.zonedDateTime(it.startJdUt+(it.endJdUt-it.startJdUt)/4))}))
            }
            println("AUDIT calendar $city $year references=${refs.size} actual=${actual.size}")
            write("fasting-comparisons.json",fastRows);write("parana-comparisons.json",timeRows);write("daily-field-comparisons.json",dailyRows);write("rule-diagnostics.json",diag);write("site-summary.json",siteRows)
        }
        assertEquals(14,files.size)
        assertTrue(fastRows.size>=336)
    }

    @Test fun astronomy() {
        val rows=mutableListOf<JsonObject>()
        for(year in listOf(1950,2026,2100)) for(body in listOf("sun","moon")) {
            val file=File(root,"docs/measurements/horizons-2026-08-01/${body}_$year.csv")
            for(line in file.readLines().filter{it.isNotBlank()&&!it.startsWith("#")&&!it.startsWith("jd_")}) {
                val parts=line.split(',');val jd=parts[0].toDouble();val expected=parts[1].toDouble();val tt=TimeScale.toTt(jd,calc.ephemeris)
                val actual=if(body=="moon") calc.ephemeris.moonLongitude(tt) else calc.ephemeris.sunLongitude(tt)
                rows+=obj("source" to file.relativeTo(root).path,"kind" to "longitude","body" to body,"utc" to "$year/stored-JD-UT","jdUt" to jd,"reference" to expected,"actual" to actual,"deltaArcsec" to TimeScale.angleDifference(actual,expected)*3600)
            }
        }
        val golden=File(root,"verify/golden").listFiles()!!.filter{it.name.startsWith("usno-")||it.name.startsWith("horizons-")}
        val fresh=File(out,"new-astronomy").listFiles()?.filter{it.extension=="json"}.orEmpty()
        for(file in golden+fresh) {
            val doc=json.parseToJsonElement(file.readText()).jsonObject
            if(file.name.startsWith("horizons")) {
                val body=if(file.name.contains("moon")) "moon" else "sun"
                for(e in doc["records"]!!.jsonArray) {
                    val r=json.decodeFromJsonElement<HorizonsRecord>(e);val jd=TimeScale.jdUt(Instant.parse(r.utc));val tt=TimeScale.toTt(jd,calc.ephemeris)
                    val lon=if(body=="moon") calc.ephemeris.moonLongitude(tt) else calc.ephemeris.sunLongitude(tt)
                    val lat=if(body=="moon") calc.ephemeris.moonLatitude(tt) else null
                    val distance=if(body=="moon") calc.ephemeris.moonDistanceKm(tt) else calc.ephemeris.sunDistanceKm(tt)
                    rows+=obj("source" to file.relativeTo(root).path,"kind" to "longitude","body" to body,"utc" to r.utc,"reference" to r.apparentEclipticLongitudeDeg,"actual" to lon,"deltaArcsec" to TimeScale.angleDifference(lon,r.apparentEclipticLongitudeDeg)*3600,"latitudeDeltaArcsec" to lat?.let{(it-r.apparentEclipticLatitudeDeg)*3600},"distanceDeltaKm" to r.distanceAu?.let{distance-it*149597870.7})
                }
            } else {
                for(e in doc["records"]!!.jsonArray) {
                    val r=json.decodeFromJsonElement<UsnoDayRecord>(e);val date=LocalDate.parse(r.date)
                    val loc=GeoLocation(r.latitudeDeg,r.longitudeDeg,ZoneOffset.ofTotalSeconds((r.utcOffsetHours*3600).toInt()))
                    val s=calc.sunTimes(date,loc);val m=calc.moonTimes(date,loc)
                    val pairs=listOf(Triple("sunrise",r.sunRises,s.sunrise),Triple("sunset",r.sunSets,s.sunset),Triple("moonrise",r.moonRises,m.moonrise),Triple("moonset",r.moonSets,m.moonset),Triple("solarNoon",r.sunUpperTransits,RiseSet.At(s.solarNoonJdUt)),Triple("civilDusk",listOfNotNull(r.endCivilTwilight),calc.civilTwilightEnd(date,loc)))
                    for((kind,refs,actual) in pairs) {
                        val jd=actual.jdUtOrNull;val ref=refs.singleOrNull();val instant=jd?.let{loc.zonedDateTime(it)}
                        val delta=if(jd!=null&&ref!=null) Duration.between(date.atTime(LocalTime.parse(ref)).atZone(loc.zone).toInstant(),instant!!.toInstant()).toNanos()/1e9 else null
                        rows+=obj("source" to file.relativeTo(root).path,"kind" to kind,"date" to date,"latitude" to loc.latitude,"longitude" to loc.longitude,"offsetHours" to r.utcOffsetHours,"reference" to ref,"referenceEvents" to json.encodeToJsonElement(refs),"referenceCondition" to (if(kind.startsWith("moon")) r.moonCondition else if(kind=="civilDusk") r.sunTwilightCondition else r.sunCondition),"actual" to (instant ?: actual),"deltaSeconds" to delta,"status" to if(delta==null) "categorical_or_unavailable" else "compared")
                    }
                }
            }
        }
        write("astronomy-comparisons.json",rows)
        assertTrue(rows.isNotEmpty())
    }

    @Test fun independentAngularElements() {
        val rows=mutableListOf<JsonObject>()
        val files=File(out,"new-astronomy").listFiles()!!.filter{it.name.startsWith("horizons-")&&it.name.contains("moon")}
        for(file in files) {
            val moon=json.parseToJsonElement(file.readText()).jsonObject["records"]!!.jsonArray
            val sun=json.parseToJsonElement(File(file.parentFile,file.name.replace("moon","sun")).readText()).jsonObject["records"]!!.jsonArray
            check(moon.size==sun.size)
            for(i in moon.indices) {
                val m=json.decodeFromJsonElement<HorizonsRecord>(moon[i]);val s=json.decodeFromJsonElement<HorizonsRecord>(sun[i]);check(m.utc==s.utc)
                val e=(m.apparentEclipticLongitudeDeg-s.apparentEclipticLongitudeDeg+360)%360
                val jd=TimeScale.jdUt(Instant.parse(m.utc));val t=calc.tithiAt(jd);val k=calc.karanaAt(jd)
                rows+=obj("source" to file.relativeTo(root).path,"utc" to m.utc,"referenceElongation" to e,"expectedTithiIndex" to kotlin.math.floor(e/12).toInt(),"actualTithiIndex" to t.index,"expectedKaranaIndex" to kotlin.math.floor(e/6).toInt(),"actualKaranaIndex" to k.index,"deltaElongationArcsec" to TimeScale.angleDifference(calc.elongationDeg(jd),e)*3600)
            }
        }
        write("independent-angular-elements.json",rows)
        assertEquals(72,rows.size)
        assertTrue(rows.all{it["expectedTithiIndex"]==it["actualTithiIndex"]&&it["expectedKaranaIndex"]==it["actualKaranaIndex"]})
    }
}

