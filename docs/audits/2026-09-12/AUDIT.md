# Numerical accuracy and tradition audit — 12 September 2026

**Publishing is blocked.** The audit reproduced the known Vrindavan fasting-date and Auckland Parana disagreements, found a January 1 delivery omission, and demonstrated that an accepted Reykjavik site loses the daylight-third cap. The astronomy has substantial, bounded reference support; that does not establish approved ISKCON observance or universal accuracy.

The owner remains the publishing approver. No production calculations, religious rules, existing expectations or verification labels were changed. Nothing was deployed, pushed or purchased; Android and the Content Hub were untouched. All additions are under this audit directory.

## 1. Subject, baseline and reproducibility

Repository: `D:\projects\panchang-service`. Commit: `4e7fc3a54d8e3dcc8664a05adbb5689ec5db3b09`. The subject includes **9 modified tracked files and 25 existing untracked files**, predominantly regional-tradition work. See [initial status](baseline-status.txt), [initial tracked diff](baseline-tracked.diff), and [audited source checksums](audited-source-hashes.json). The tracked diff after the audit is byte-identical to the captured initial diff. Existing untracked files were read, not edited. No applicable `AGENTS.md` was found in the repository or its ancestor paths.

Runtime: Windows 11 amd64; JetBrains OpenJDK `21.0.6+-13391695-b895.109`; Gradle **8.11.1**; project Kotlin **2.0.21** (Gradle's embedded Kotlin is 2.0.20); JUnit **5.11.3**; JVM timezone fixed to **UTC** by the project; installed Java tzdb **2024b**. The actual calculation default is `Vsop87Ephemeris`, sharing the Meeus lunar series, with stored IERS Delta-T observations/predictions and the repository's extrapolation after 2027-08-07.

Exact baseline command: `.\gradlew.bat test --rerun-tasks --continue --console=plain`. It completed in **8m 52s**, exit **1**, all 43 scheduled tasks executed. Counts below were read from this run's JUnit XML, copied into [baseline-xml](baseline-xml/).

| Module | Tests | Passed | Failed | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| ephemeris | 54 | 54 | 0 | 0 | 0 |
| core | 82 | 82 | 0 | 0 | 0 |
| sampradaya | 221 | 212 | 9 | 0 | 0 |
| verify | 101 | 101 | 0 | 0 | 0 |
| wire | 43 | 43 | 0 | 0 | 0 |
| gazetteer | 36 | 36 | 0 | 0 | 0 |
| calc | 20 | 20 | 0 | 0 | 0 |
| api | 37 | 37 | 0 | 0 | 0 |
| publish | 39 | 39 | 0 | 0 | 0 |
| **Total** | **733** | **724** | **9** | **0** | **0** |

Seven `LiveSourceTest` methods are excluded by the existing `network` tag, so they do not appear as JUnit skips. Their exact names and every failure are listed in [EXCEPTIONS.md](EXCEPTIONS.md). Deliberate reference fetches below exercised the existing request builders and parsers instead. Code-review guidance informed inspection; no AI review output was used as numerical or religious evidence.

The isolated evidence collectors completed. Three additional contract checks intentionally fail: January 1 delivery, Reykjavik's documented daylight cap, and its 27 June sunrise rounding comparison. They are separate from the 733-test baseline. [Reproduction commands](REPRODUCE.md), [baseline log](baseline-tests.log), [new failing checks](defect-tests.xml), [machine summary](summary.json).

## 2. Reference policy and provenance

**Astronomy:** JPL Horizons geocentric apparent ecliptic longitude of date, quantity 31, Earth centre `500@399`, Sun `10` and Moon `301`; quantity 20 provides distances. New raw responses identify **DE441**, AIRLESS geocentric coordinates, and EOP file `eop.260911.p261208`. JPL uses IAU76/80 ecliptic-of-date for this quantity. Modern rows are UTC; pre-1962 stored UT rows must not be interpreted as modern UTC. Comparisons use the same angular convention as the service; they include time-argument differences, not just orbital-series error. [JPL manual](https://ssd.jpl.nasa.gov/horizons/manual.html)

**Rise/set:** USNO `rstt/oneday`, response API **4.0.1**, requested coordinates and fixed numerical offset reproduced exactly, sea-level unobstructed horizon. Solar horizon is -0.8333 degrees; lunar events use the upper-limb/refraction convention; civil twilight uses solar centre -6 degrees. USNO's printed minutes limit resolution, and real refraction can move observed events further. We do not interpret small residuals as a physical second-level accuracy guarantee. [USNO definitions](https://aa.usno.navy.mil/faq/RST_defs)

**Observance:** retain the repository's designated `vaisnavacalendar.info` TXT lineage, **GCal 11 Build 5**, at its own printed coordinates, standard offset and per-row DST indicator. Mayapur's old fixture lacks a site object; its cached raw header confirms **23N25 88E23, +5:30**. All comparison elevations are zero; the calendar does not state an elevation or detailed refraction/ephemeris configuration, so those are convention gaps, not silently matched facts.

The publisher says it uses ISKCON-approved GCal 11 calculations, but expressly states that the website is **not run or sponsored by the GBC Calendar Committee**, and its event descriptions/content are not endorsed by that committee. This supports the generator lineage, **not an approval of this service or every exported date**. The upstream author's version history also records algorithm/configuration changes; the exact source/build/configuration used for these Build 5 exports was not recovered. A rules PDF fetch failed TLS hostname verification and was not bypassed. No recorded owner/qualified-reviewer approval of an exact reference/version/override policy was found. That remains a required human decision. [Publisher policy](https://www.vaisnavacalendar.info/calendar-file-downloads-2), [upstream version history](https://github.com/gopaladasa/GCAL-for-Windows)

All **34 provenance entries** in curated golden files have their raw cached bodies present and matching the recorded SHA-256. The six historical longitude CSVs have source instructions but no captured raw-response checksum chain; their provenance is weaker than the newly retained raw responses. The audit hashes both kinds and distinguishes them. See [stored provenance checks](stored-provenance-checks.json), [download manifests](fetch-manifest.json), [supplement](supplement-manifest.json), [targeted retries](retry-manifest.json), and [checksums](SHA256SUMS.json). All comparison data is independently sourced; no expected astronomical/observance value came from the service or an LLM.

## 3. Tolerances and coverage

[method.md](method.md) was written before new comparisons. New USNO screening uses **±30 seconds from the printed minute**; JPL longitude screening uses **1 arcsecond solar / 12 arcseconds lunar**, empirical thresholds rather than guaranteed envelopes. Existing Parana bands were retained: solar **-15 to +75 seconds**, lunar **-180 to +75 seconds**. These asymmetric Parana bands describe compatibility with the older calendar, **not astronomical accuracy**. Calendar minute truncation is the repository's supported hypothesis, not a proved rounding policy for every export.

Statistics are absolute residuals from printed values: conventional median, nearest-rank p95, maximum. Start and end are separate. Large residuals are retained. Obsolete-DST rows have raw residuals and a separately identified exclusion; missing windows never enter a timing denominator as zero error.

Coverage completed:

- All **14 city × 365 day 2026** fixtures: Ahmedabad, Auckland, Bangalore, Chennai, Delhi, Guwahati, London, Mayapur, Moscow, Mumbai, New York, São Paulo, Sydney, Vrindavan. Eight share Asia/Kolkata; the grid includes both hemispheres and four DST-observing zones. Prior prose claiming four southern sites/six DST zones is incorrect: these fixtures contain **three southern sites and four DST zones**.
- Two live 2027 calendars: Mayapur and **held-out Hyderabad** (printed **17N23 78E28**, Asia/Kolkata). Mayapur 2027 was already used to develop the rules and is **not** a held-out validation year. Hyderabad is a new geographic holdout; its 2027 Vijaya is the same global event previously used at Mayapur, not a second independent rare occurrence.
- JPL: 2,190 stored CSV body-position rows across 1950/2026/2100; 16 stored April 2026 rows; **144 new body-position rows** (32 daily rows per body in each of January 2027 and January 2028, plus four rows per body around each disputed boundary). **72 new paired instants** also match independently derived tithi and karana sector indices.
- USNO: **18 stored site-days** (14 April grid cities and four polar probes) and **9 newly fetched site-days**: Cape Town 2026-01-01 and 2028-06-21; Singapore 2026-12-31 and 2028-01-01; Reykjavik 2026-06-26/27; London 2027-03-28; Vrindavan 2026-06-30; Auckland 2026-10-08. This adds held-out locations, opposite solstices, a year boundary, and an independent DST-transition-day solar check.
- Existing software tests cover London/Auckland transition directions and local-day durations, negative offsets, legacy DST withholding, year margins and high-latitude rejection. They are delivery/structural checks, not independent festival approval.

Both attempted 2028 observance calendars returned **404**. Several USNO requests initially returned **502**; targeted retries recovered some, while the remaining requested dates remain unavailable. Every attempt and every excluded comparison is enumerated in [EXCEPTIONS.md](EXCEPTIONS.md); no alternate publisher was chosen to improve agreement.

## 4. Astronomy results

The original 365-sample-per-epoch VSOP87 longitude measurements reproduce: 2026 Sun max **0.11 arcsecond**, Moon max **8.70 arcseconds**, elongation max **8.79 arcseconds**. The conversion of an angular maximum using average lunar speed is an estimate of boundary timing, **not a verified worst-case time bound**. At 2100 the raw Moon/elongation residuals include different Delta-T forecasts and do not validate future clock accuracy.

New JPL results, arcseconds:

| Body/year | n | Median absolute | p95 absolute | Maximum absolute |
|---|---:|---:|---:|---:|
| Sun 2027 | 32 | 0.104 | 0.106 | 0.106 |
| Moon 2027 | 32 | 1.503 | 3.970 | 3.989 |
| Sun 2028 | 32 | 0.104 | 0.105 | 0.106 |
| Moon 2028 | 32 | 1.204 | 3.802 | 4.171 |

All are inside the predeclared longitude screening thresholds. These January samples do not establish a full-year 2027/2028 envelope. Available position records also measure lunar latitude (maximum absolute **2.843 arcseconds**) and distances (Moon **42.34 km**, Sun **5.49 km**); these are descriptive results with no claimed full-range tolerance. Solar latitude has no public `Ephemeris` method and was not fabricated as zero.

New USNO timing results, seconds from printed minutes:

| Quantity | n | Median absolute | p95 absolute | Maximum absolute |
|---|---:|---:|---:|---:|
| Sunrise | 9 | 18.180 | 32.171 | **32.171** |
| Sunset | 9 | 14.597 | 27.107 | 27.107 |
| Moonrise | 8 | 20.421 | 27.247 | 27.247 |
| Moonset | 7 | 17.506 | 27.157 | 27.157 |
| Solar noon | 9 | 12.945 | 26.995 | 26.995 |
| Civil twilight end | 7 | 14.851 | 27.744 | 27.744 |

The 89 comparable stored USNO times are inside ±30 seconds, including previously unused transit/twilight records. The new outlier is **Reykjavik 2026-06-27**: USNO **03:00**, service **02:59:27.829 UTC**, delta **-32.171 s**, at least **2.171 s outside** the nearest-minute rounding interval. Increasing refinement passes from 6 to 12 and 24 changes nothing at the recorded precision. **Unresolved numerical/model disagreement**, not evidence that the religious rule is wrong; no tolerance was widened. [Sensitivity results](reykjavik-sunrise-sensitivity.json)

Reykjavik 26 June has **two** USNO moonsets (00:17 and 23:31); the service's singular `moonset` field cannot expose both. That day is excluded from scalar moonset timing statistics and retained as an exposure/cardinality limitation. Polar and no-event records have categorical outcomes, not timing errors. [All astronomy rows](astronomy-comparisons.json)

## 5. Observance results and timing distributions

| Scope | Reference fasts | Date matches | Name matches | Mahadvadashi-label matches |
|---|---:|---:|---:|---:|
| 14 cities, stored 2026 | 336 | **335** | 336 | **332** |
| Mayapur/Hyderabad, live 2027 | 50 | **50** | 50 | **50** |

Names use the explicit Ekadasi/Ekadashi spelling mapping. The four 2026 label disagreements include the shifted Vrindavan observance. No one-number “overall accuracy” is appropriate.

Parana residuals **exclude only the named stale-DST rows** from this table, not Auckland's large error:

| Scope/bound | Comparable n | Median absolute seconds | p95 absolute seconds | Maximum absolute seconds |
|---|---:|---:|---:|---:|
| 2026 starts | 318 | 33.303 | 60.175 | 115.688 |
| 2026 ends | 318 | 33.808 | 97.479 | **15,282.688** |
| 2027 starts | 50 | 34.711 | 57.870 | 58.204 |
| 2027 ends | 50 | 31.038 | 57.573 | 59.317 |

There are **346 printed 2026 windows**, including prior-December carryover. Four lack a service window: one bounded Vrindavan window and three reference start-only cases. Thus 342 starts and 342 ends have actual values before the 24 stale-DST row exclusions. Their raw statistics remain in `summary.json`: 2026 start median/p95/max **34.960/3562.537/3612.591 s**; end **35.762/3565.709/15282.688 s**. A window produced on an extra Vrindavan date is explicitly recorded.

This audit's main start statistics exclude the three refused start-only windows because the service emits no start. The old conformance tests separately re-derive Hari Vasara for those rows; that internal calculation is **not a delivered Parana window**. All 100 new 2027 bounds fit the unchanged compatibility bands; the two 13 September nakshatra ends are about 50 seconds earlier than the printed reference minute. [Parana rows](parana-results.json), [fasting rows](fasting-results.json)

Daily-field comparisons broaden the old fasting-only gate. On the 5,110 stored days, tithi names match **5,105**, nakshatras **5,104**, paksha **5,109**, month category **5,109**, weekdays **5,110**. On 730 new 2027 days, all those fields match except **one Hyderabad nakshatra**. Month comparison maps the source's Purusottama label to the service's adhika category; it does not validate every month-boundary instant or era number. These descriptive matches do not establish an approved ayanamsha configuration. The **12 distinct disagreeing site-days** and actual boundary margins are listed in [EXCEPTIONS.md](EXCEPTIONS.md) and [diagnostics](daily-boundary-diagnostics.json).

## 6. Investigations and defects

### P1 — January 1 Parana disappears at the delivery seam: confirmed software defect

Input: Mayapur's printed coordinates, Asia/Kolkata, ISKCON, **day 2026-01-01**. The independent calendar prints a Parana window; `CalcEngine.compute(... Scope.Day(...))` emits **zero observances**. The underlying window exists in the 2026 index's December margin and is included by the conformance fixture.

Cause: `IskconRules.kt:59–62` filters on **fasting year** before `CalcEngine.kt:103–143` can retain a decision whose Parana occurs on the queried day. The API delegates to this same engine, and the publisher's yearly computation also uses this filtered list. This is a year-selection/delivery bug, independent of the religious disputes. The isolated test exercised the shared engine, not a deployed HTTP server. [Captured payload](january-first-delivery.json), [failing test](defect-tests.xml)

### P1 — Reykjavik drops an available daylight-third cap: confirmed contract defect

Input: **64.1466 N, -21.9426 E**, elevation 0, Atlantic/Reykjavik, ISKCON, fast 25 June / Parana **26 June 2026**. Site acceptance says accepted.

USNO gives sunrise **26 June 02:58** and the following sunset **27 June 00:02**. This is a real daylight interval crossing civil midnight; its first third ends around **09:59:20 UTC**, with minute-rounding uncertainty. The service independently computes the corresponding first third as **09:59:36.443**, but its actual Parana ends **16:52:50.322**, **24,810.322 seconds (6h 53m 30s)** later than the USNO-derived third.

Cause: `SunTimes.daylightDays` (`core/RiseSet.kt:61`) subtracts the **same-date** sunset, which precedes sunrise, yielding -175.37 minutes. `IskconRules.kt:441–447` therefore removes the daylight cap and uses Dvadashi end. Existing `ReykjavikParanaTest` explicitly accepts this long window and proves its label, not the documented daylight-third rule. A civil midnight boundary is not evidence that daylight has no end. The unresolved high-latitude religious policy must not be inferred from this arithmetic defect. Fix the interval handling or withhold affected output pending approval; this audit does neither. [Independent cap comparison](reykjavik-cap-comparison.json), [all high-latitude probes](high-latitude-diagnostics.json)

### P1 — Vrindavan Nirjala: unresolved fasting-date disagreement

Input: **27°35′ N, 77°42′ E**, elevation 0, Asia/Kolkata. Reference fast **26 June 2026**, Paksavardhini; service **25 June**, ordinary Ekadashi: **-1 calendar day**. Reference Parana 27 June is missing; an extra actual window occurs 26 June.

Rule path: closing Purnima at two sunrises → Paksavardhini (`IskconRules.classify`, branch 4). Service closing boundary **30 June 05:26:39.819 IST**, sunrise **05:26:53.259**; the boundary precedes sunrise by **13.440 s**. Independent JPL interpolation gives **05:26:41.191**, only **1.372 s later** than the service, still **12.068 s before its sunrise**. USNO prints sunrise **05:27**, whose nearest-minute interval contains the JPL boundary.

This substantially checks the lunar boundary but **does not resolve sunrise ordering or approve a fasting date**. Local ephemeris/sunrise convention differences, reference policy, and the branch's religious authority remain relevant. The prior documents' categorical “it is not a rule error” conclusion is stronger than their evidence: agreeing at neighbouring sites cannot prove doctrinal correctness. No claim that GCal's ephemeris caused the date change is established. Qualified human decision required.

### P1 — Auckland 8 October Parana: unresolved cap disagreement

Input: **36°52′ S, 174°46′ E**, elevation 0, Pacific/Auckland (**UTC+13** on this date). Both fast **7 October 2026**. Reference Parana **8 October 06:47–06:47**, end basis **end of tithi**; the same printed minute does not prove a zero-duration real window. Service ends **11:01:42.688**, daylight-third basis, **+15,282.688 s (4h 14m 42.688s)**.

Service Dvadashi end **06:46:57.059**, sunrise **06:47:17.585**, gap **-20.526 s**. JPL gives Dvadashi end **06:47:04.426**, service difference **-7.367 s**, still **13.159 s before the service sunrise**. USNO prints **06:47** and cannot resolve that ordering. The code takes Dvadashi-kshaya → Trisprsa, then drops a tithi cap ending before sunrise and uses daylight-third (`classify` branch 2, `derivePotentialParana`). This is a decision-boundary/reference-rule dispute with high user impact, not a DST mistake. The calendar's unprinted boundary/configuration remains unknown. Do not publish either interpretation as human-approved.

The independent roots use three-point interpolation of ten-minute JPL samples. Linear versus quadratic estimates differ by **0.002 s / 0.006 s**; that is an interpolation consistency check, not total measurement uncertainty. [Independent boundary results](independent-boundaries.json)

### Moscow and Sydney: unresolved classification rule

Moscow **27 May 2026**, printed **55N45 37E37**, Europe/Moscow: service **VANJULI**, reference no Mahadvadashi name, fasting date unchanged. Dvadashi begins **422.36 s before** the first sunrise. Sydney **5 December 2026**, **33S52 151E12**, Australia/Sydney: same classification disagreement, first sunrise **1309.87 s inside** Dvadashi. Both reference calendars print Dvadashi at the next sunrise too. Code branch 5 classifies two Dvadashi sunrises as Vanjuli before reaching ordinary viddha deferral.

These margins are much larger than the two disputed lunar-boundary residuals; a sub-30-second boundary flip is not supported here. The reference's omission could mean a further qualifying condition, an export/display convention or a reference defect. The repository's four rejected hypotheses do not establish which. The two deferral-classification failures are additional assertions of the same two cases. [Rule diagnostics](rule-diagnostics.json)

### Other current disagreements and unresolved review items

- **Auckland 28 April 2026:** reference start basis Hari Vasara at 06:57; actual sunrise basis at 06:57:19.423. The actual Hari Vasara ends about 06:55:23.5, roughly 116 s before sunrise. Same printed start minute, different branch; consistent with reference timing differences, not proved to be their cause.
- **Ahmedabad 6 November 2026:** actual Dvadashi end **10:31:04.467**, daylight-third **10:31:05.395**, gap **0.928 s**. Reference names daylight-third; actual names tithi-end. Both print 10:31. The gap is below measured input uncertainty; basis unresolved. The old named exception remains unchanged and visible.
- **Mumbai 24 August; Auckland 23 September; New York 22 October 2026:** reference gives a start with no end; service gives no window. The computed Hari Vasara/daylight-third conflicts reproduce. Choice of an open-ended window versus refusal requires a qualified ruling. Do not count re-derived starts as delivered guidance.
- **Nakshatra Mahadvadashis:** the next-sunrise survival condition remains inferred from one global positive event, now reproduced at two 2027 sites. Jaya, Jayanti and Papanasini do not acquire new positive-event coverage.
- **Unmilani purity/naming**, **dusk versus civil twilight**, **midnight versus Nisita and which edge ends a fast**, **Ramanuja's tithi versus regional nakshatra reckoning**, **Damodara-vrata closing label**, **polar substitutes** remain human-review questions. More accurate positions cannot approve those interpretations.
- Regional catalogs are **UNVERIFIED**, with no regional Ekadashi/Parana implementation. Their passing structural tests do not validate festival calendars. Kshaya new-year fallbacks, evening/night festival ownership, solar month-opening conventions, omitted festivals and unused 60-year-cycle semantics remain in `sampradaya-coverage.md`/catalog source notes. No regional year calendar was independently validated in this audit.

The service still emits tradition `VERIFIED` and can emit individual `CONFIRMED` on disputed decisions. Those labels mean repository confidence categories, **not qualified human approval**. There is no live reference-disagreement detection at the request seam; a failing offline test is not a user-visible warning. The old “verified worldwide” and “report divergence” prose must not be used as release evidence.

## 7. Field-by-field limits

| Implemented field or output | Demonstrated evidence | Still unavailable/unverified |
|---|---|---|
| Sun/Moon longitude, lunar latitude, distances | JPL comparisons above, historical VSOP published check values | Continuous all-year/all-epoch bounds; solar latitude unexposed |
| Tithi and karana | 72 new independent sector-index comparisons; two independently solved tithi boundaries; daily tithi labels | Every boundary/end-to-end minute claim; angular maxima are not time maxima |
| Nakshatra/pada | 5,840 descriptive daily name comparisons with 7 disagreements | Independently approved ayanamsha, numeric boundary/pada oracle |
| Yoga | Existing structural tests | No compatible independent numeric/name fixture evaluated |
| Lunar month/adhika/paksha | Daily source labels with explicit spelling/category map | Rare kshaya months, era numbering, all boundary instants |
| Vaar/local civil date | All 5,840 weekday labels agree; zone conversion tests | Arbitrary historic calendar conventions; latest global tzdb validation |
| Sunrise/sunset/noon/moonrise/moonset | USNO, separated above | All event multiplicities, real atmosphere/terrain, elevations, perigee/apogee envelope |
| Arunodaya | 96-minute subtraction structurally tested; sunrise measured | Independent published arunodaya clocks/religious definition approval |
| Rahu kaal, yamaganda, gulika, abhijit | Day-division structural tests | Independent timed field oracle; cross-midnight daylight exposure |
| Civil dusk | USNO astronomical twilight values | Whether that definition is the tradition's fast-breaking dusk |
| Nisita/midnight interval | Arithmetic/serialization tests | Independent printed observance clock and human choice of interval edge |
| ISKCON festival catalog | Existing Mayapur event-date conformance tests | Broad independent multisite/multiyear festival coverage; inferred entries |
| JSON/API/static output | 139 calc/API/wire/publish baseline tests pass; exact year-boundary defect added | Deployment/storage readback and cache correctness; general Panchang fields are not all exposed by the current event-focused v1 API |

The legacy feed carries a reduced day representation; tests of that representation do not validate fields it drops. Moon illumination/phase percentages and lunar transit in USNO data have no corresponding exposed Panchang fields here and are not scored.

## 8. Shared-cache assessment and acceptance plan

**No persistent shared calculation cache is implemented.** `CalcEngine` rebuilds resolutions; API documentation explicitly says no cross-call cache. The test fixture cache, reference-download artifact store, and synchronized weak per-index solar memo are different mechanisms. `FeedPublisher` produces static files, but it is not a lookup/reuse cache with version invalidation. Existing publisher tests validate local output/determinism/omission records; no deployed stored result was checked.

For the intended compute-once model, identity must include a **canonical place identifier plus versioned coordinate/elevation/zone record**, Gregorian year and range/margin policy, tradition **and rule/catalog revision**, ephemeris implementation/build and coefficient hashes, Delta-T/IERS data and extrapolation version, ayanamsha identity/version, month reckoning, horizon/refraction/dip model, solver tolerance, tzdb version, and reference/approved-override policy revision. Serialized artifacts additionally need wire/rendering/rounding/legacy format versions and inclusion policy (day versus year/carryover). A mutable `0.1.0-SNAPSHOT`, `iskcon` or `lahiri` string alone cannot invalidate all relevant changes. Store provenance with the value, not just a hashed key.

City-reference results are safe to reuse **only as results for that stated canonical point**, under an explicit owner-approved place policy. They are not verified for every user in that city/district. The Delhi/Vrindavan example shows that tens of seconds can alter a date; a universal distance radius or silent GPS rounding is not justified. Explicit GPS inputs presently remain the requested point and must retain exact identity; a nearest-place label is not permission to substitute that place's calculation.

Acceptance tests for future implementation, not performed or implemented here:

1. **Cold/warm:** same canonical inputs produce identical domain results and deterministic serialized bytes, with provenance; a cache hit is measurable without altering content.
2. **Simultaneous requests:** many identical requests yield one committed artifact; readers see a complete answer or an explicit pending/failure state. No partial file, duplicate publication or cross-request location leak.
3. **Restart:** committed values survive process restart; incomplete writes are discarded; readback checksum/schema/provenance validation fails closed.
4. **Invalidation:** independently vary every version/input above. Any output-affecting change misses the old entry. A year-boundary inclusion change must invalidate previously incomplete January 1 data.
5. **Isolation:** distinguish same-timezone Ahmedabad/Guwahati, nearby Delhi/Vrindavan, elevation variants, 2026/2027/2028, ISKCON/regional catalogs, exact GPS/canonical-place modes and DST offsets. Never serve ISKCON timing under another tradition id.
6. **Publication state:** owner approval, qualified overrides and unresolved cases remain attached to the exact version. Updating a source/authority policy must not silently re-use a prior approved-looking artifact.
7. **Coverage edges:** retain prior-December Parana, leap-day/year ends, both DST directions, cross-midnight daylight, explicit polar refusals and no-event/multi-event cases. Compare readback to the source computation and independent fixtures separately.

## 9. Required decisions, fixes and release blockers

**Demonstrably validated:** bounded geocentric positions; most sampled standard-horizon rise/set/transit/twilight results; 335/336 stored and 50/50 new reference fasting dates; separately measured Parana timings; explicit daily-label comparisons; local serialization and structural regional separation. This is the validated scope, not universal accuracy.

**Confirmed defects:** January 1 carryover omission; removal of the documented daylight cap at an accepted cross-midnight site. The verification wording also overstates what the actual evidence/approval record supports. **Numerical disagreement still open:** Reykjavik 27 June sunrise, unchanged at higher iteration counts.

**Unresolved observance/reference disputes:** Vrindavan fasting date, Auckland October cap and April start basis, Moscow/Sydney Vanjuli classification, Ahmedabad near-tie, three start-only/no-window cases, inferred rare Mahadvadashi and festival anchor conditions.

**Missing coverage:** 2028 observance calendars; independent regional calendar conformance; more rare-event positives; broad seasonal lunar rise/set geometry and elevations; high-latitude authoritative observances; compatible yoga/pada/ayanamsha/time-division oracles; current global tzdb validation; live deployment, persistent cache and stored production readback.

**Priority order:**

1. Repair January 1 selection and sunrise-to-following-sunset handling in a separately approved implementation task; retain the new failing examples as regression tests. Resolve the 32.171-second sunrise comparison without widening tolerances to hide it.
2. Before publication, have the owner and qualified ISKCON reviewer approve the exact reference lineage/version and policy for disagreement, uncertainty, overrides and withholding. Obtain rulings for Vrindavan/Auckland and the unanswered cases above. Do not make devotees choose between two confident unapproved dates.
3. Make runtime confidence/disagreement output reflect the actual review state; a generic `VERIFIED` label and offline red gate are insufficient. Reassess the published geographic scope, especially accepted high-latitude sites.
4. Extend independent coverage, then implement/test shared caching against the specified identity and acceptance plan. Re-run scientific, rule and delivery gates separately against the changed source state.

**No release-readiness or human-approval claim is supported by this audit.**
