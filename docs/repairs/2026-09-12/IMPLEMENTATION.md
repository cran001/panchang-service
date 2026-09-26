# Scoped delivery repairs — 12 September 2026

The two implementation defects are repaired. The completed full normal suite has **643 tests:
634 passed, 9 failed, 0 errors, 0 skips**. All **10 new permanent regressions pass**. The nine
remaining failures match the historical XML messages exactly. The separate audit contract
run now has **2 passed / 1 failed**: both delivery checks pass; the sunrise disagreement remains.
Five real loopback HTTP requests and locally written publisher files also passed their agreement
checks, including legacy January attachment and Reykjavik's end-reason/rounded end.
`summary.json` and `VERIFICATION.md` separate history, intermediate attempts, and final results.
This work does not establish release readiness. Publishing remains blocked by unresolved
approval and religious-rule issues.

## Changes and evidence

**DELIVERY-01:** the rule seam now selects decisions whose fast OR Parana is in the requested
year. It uses the existing index's December margin. No fasting decision is moved and no
place/year is hard-coded. The shared calculation engine's existing day filter can now see the
December fast on January 1. HTTP and v1 yearly files use that same seam.

The legacy writer includes the specific previous December fast day when needed to attach a
delivered January Parana. The old reader requires both days in one file. See
`YEARLY-CONTRACT.md`: affected yearly arrays gain one context day, and adjacent yearly v1
lists can share a carryover decision. This is an intentional compatibility change. Existing
published artifacts have not been overwritten or deployed.

**DELIVERY-02:** a separate `SunTimes.daylightInterval` pairs sunrise with a real following
sunset, checking the immediately following civil date when necessary. Same-date rise/set
fields and signed `daylightDays` are unchanged. The Parana calculation uses the resulting
positive interval for the documented first-third cap and keeps the earliest bound's reason.
Missing endpoints still do not invent a window or imply a polar religious ruling.

Saved independent evidence gives Reykjavik sunrise **June 26, 02:58 UTC**, following sunset
**June 27, 00:02 UTC**, and a daylight third ending **09:59:20 UTC**, within the original
minute-rounding uncertainty. The old service Parana ended **16:52:50.322 UTC**. The repaired
calculation ends **09:59:36.443 UTC**, with `ONE_THIRD_DAYLIGHT` metadata. The normal
`DaylightDeliveryTest` reads those saved USNO records and keeps the 30-second band.

## Changed source files

Paths below are relative to the repository. `repair-only.diff` compares against the saved
dirty starting source, so older uncommitted changes are not attributed to this repair.

| File | Reason |
|---|---|
| core/src/main/kotlin/org/panchang/core/RiseSet.kt | Separate ordered daylight interval; preserve civil-date API. |
| sampradaya/src/main/kotlin/org/panchang/sampradaya/IskconRules.kt | Year carryover selection and actual following-sunset Parana cap. |
| sampradaya/src/main/kotlin/org/panchang/sampradaya/SampradayaRules.kt | Document fast-or-Parana yearly selection. |
| publish/src/main/kotlin/org/panchang/publish/FeedPublisher.kt | Minimal December context day for existing legacy attachment behavior. |
| core/src/test/kotlin/org/panchang/core/DaylightIntervalTest.kt | Four permanent tests covering India, midnight, DST and absent/polar events. |
| sampradaya/src/test/kotlin/org/panchang/sampradaya/DaylightDeliveryTest.kt | Permanent independent USNO daylight-cap regression. |
| calc/src/test/kotlin/org/panchang/calc/YearCarryoverTest.kt | Two permanent tests for carryover, adjacent dates/years, uniqueness and day/year agreement. |
| api/src/test/kotlin/org/panchang/api/DeliveryFrontDoorTest.kt | Two permanent HTTP day/year versus actual written v1/legacy file checks. |
| api/build.gradle.kts | Test-only publisher dependency for those cross-surface checks. |
| publish/src/test/kotlin/org/panchang/publish/FeedPublisherTest.kt | New carryover regression; retain real legacy-reader attachment checks and update context-day coverage. |
| sampradaya/src/test/kotlin/org/panchang/sampradaya/ReykjavikParanaTest.kt | Replace the demonstrated long-window expectation with the independently justified daylight cap. |
| sampradaya/src/test/kotlin/org/panchang/sampradaya/ParanaDaylightEdgeTest.kt | Keep inverted-day/polar sweeps; validate actual following-sunset caps instead of assuming negative civil subtraction means no daylight. |
| calc/src/test/kotlin/org/panchang/calc/CalcCliTest.kt | Distinguish 24 in-year fasts from 25 delivered records including carryover. |
| api/src/test/kotlin/org/panchang/api/CrossFrontDoorIdentityTest.kt | Same Mumbai count correction; retain complete byte comparison. |
| api/src/test/kotlin/org/panchang/api/MultiSiteParanaApiTest.kt | Count in-year fasts; retain every delivered record in the timing comparisons. |

The new repair directory contains preservation snapshots, reproduction scripts, logs/XML,
corrected historical summaries, the erratum, local HTTP/file evidence, and this explanation.
There are **10 new normal-suite tests**; no existing test method is deleted or disabled.

The final full command was `.\gradlew.bat test --rerun-tasks --continue --console=plain`.
It exits 1 because the original nine failures remain. The prior `full-normal-final` attempt
was interrupted before completion; its partial log is preserved and excluded from finished
run totals. `full-normal-completed` is the completed final run.

## Why expectations changed

- The old Reykjavik test explicitly accepted a 10–16 hour window and `DVADASHI_END` because
  same-date sunset preceded sunrise. The saved USNO following sunset disproves the premise.
  The corrected end is the daylight third, about seven hours after sunrise. The independent
  30-second check remains separate from the structural duration/non-sentinel checks.
- The inverted-day sweep assumed every negative civil difference must remove the daylight
  cap and leave at least one uncapped refusal. Actual following sunsets now restore those
  bounds. The sweep still visits every original site and date and verifies each cap and
  missing-window explanation. Polar/no-event cases remain covered.
- The legacy 365-row assertion omitted the December fasting context required by the existing
  reader's documented attachment contract. The corrected test checks the single extra context
  date and all 365 requested-year days, once each, without gaps. Attachment tests are retained.
- Three CLI/API cardinality checks confused fasting-year totals with delivery-year totals.
  They now explicitly account for carryover. No timing row or golden case is removed.
- A newly written cross-year whole-DTO equality assertion was too broad: the pre-existing
  yearly index seeds can round one tithi boundary a second differently. The test now checks
  stable decision identity and full Parana equality across years, and keeps whole-DTO equality
  for day/year selection within each year. The failed intermediate result is retained.

## Remaining issues and limits

The nine historical failures concern Vrindavan (fasting date, label and Parana), Auckland
(Parana start/end basis, October end and classification), and Moscow/Sydney (classification
and named-deferral checks). They remain failing release gates. Their exact XML messages are
included in the machine summaries. No religious classification, inferred condition, reference
data, approval label, or withholding policy was changed to make them pass.

Reykjavik **June 27 sunrise** remains **02:59:27.829 UTC** against USNO's printed **03:00**:
**−32.171 seconds**, outside the unchanged ±30-second rounding check. The audit retains all
6/12/24 refinement probes. No ephemeris constant, sunrise algorithm or tolerance was changed.

Existing `VERIFIED`/`CONFIRMED` labels were not relabeled as human approval. Owner and qualified
religious review of reference/version, disagreement, overrides and uncertainty policies remain
outstanding, along with the audit's other inferred-rule and high-latitude questions.

CodeRabbit 0.7.5 has a valid CodeRabbit signature but is signed out; no remote CodeRabbit review
was run. Local review inspected the repair delta against the preserved dirty source and checked
other `daylightDays` callers. No unrelated production calculation was changed. No caching,
Android or Content Hub changes, new traditions, commit, push, hosting or deployment occurred.

See `REPRODUCE.md` for exact commands and `ERRATUM-2026-09-12.md` for the historical count
correction. **Publishing remains blocked.**
