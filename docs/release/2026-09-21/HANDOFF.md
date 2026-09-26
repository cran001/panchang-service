# ISKCON-only release handoff

Completed locally on September 22, 2026; research began September 21. **AI_ASSISTED_EVIDENCE_REVIEW. No qualified-human review, owner approval or production activation was created. Public guidance remains withheld.**

The independent engineering and evidence work is complete for this task. The normal suite still has nine existing conformance failures. This is not a release-ready or globally accurate calendar claim.

## What changed

The starting implementation already had schema-2 publication controls, disputes, a local signed review journal, yearly-first calculation and delivery repairs. Schema 2 was exposed through v1, yearly results were recalculated, and the executable applications had no persistent calculation-cache adapter. Production identity/storage adapters were absent.

The service now provides explicit `/v2/day` and `/v2/calendar` routes, a 410 migration response for v1 guidance routes, and `no-store` on JSON responses. Public files use `v2/`; old v1 file paths contain migration refusals. Diagnostic `review/v1/` behavior is preserved. Null/withheld and approved-empty guidance remain distinct.

API and publisher executables can share an integrity-checked yearly disk cache through `PANCHANG_CALC_CACHE_DIR`. Exact points, elevation, zone, year, tradition and output-affecting artifacts/data/runtime versions identify entries. JVM and OS locks prevent duplicate same-key work; complete temporary files are atomically renamed. Cache hits validate the result and provenance, then publication evaluates current approval, revocation, supersession and disputes. Approval-store exceptions withhold guidance. The cache holds no publication permission.

See [CACHE.md](CACHE.md), [API-ANDROID-HANDOFF.md](API-ANDROID-HANDOFF.md) and the inactive [production template](production.env.example). Production approval identity, durable journal/rollback protection, approval-aware static serving and client integration remain unconfigured or unimplemented. Selecting and activating those requires the decisions below.

Changes relative to the preserved dirty starting state—not to Git HEAD—are listed in [implementation-files.json](implementation-files.json):

- Added `CalculationCache.kt`, `CalculationCacheTest.kt` and `VersionMigrationTest.kt`.
- Updated `PublicationPolicy.kt`, API `Main.kt`/`Routes.kt`, publisher `Main.kt`/`PublicFeedPublisher.kt`.
- Updated the existing API fixtures, route/rendering, publication-control and delivery tests for v2; their original calculation assertions remain.
- Added this evidence/reproduction/verification directory. The starting dirty implementation and prior reports were not discarded.

## Evidence and exact proposed scope

[AI-ASSISTED-REVIEW.md](AI-ASSISTED-REVIEW.md) is the readable comparison summary. [ai-assisted-review.json](ai-assisted-review.json) records each location and field group's evidence-supported, conflicting, missing-evidence and technical status, plus every applicable dispute/question. [coverage-inventory.json](coverage-inventory.json) contains the complete comparison details. [SOURCE-FINDINGS.md](SOURCE-FINDINGS.md) ties rule questions to pinned upstream code/document references, saved bytes, dates and hashes.

All 45 location/year calculations and comparison results reproduced the previous snapshot. All 15 freshly retrieved 2026 GCal text exports have unchanged hashes. The 2025/2027 files were rehashed and reused for December/January context. The seven candidates have matching discrete fasting dates/names/types and Parana bounds within the existing bands with matching basis. Same-minute and within-band agreement are counted separately; neither proves identical astronomical instants. Solar bands remain -15..75 seconds and lunar bands -180..75 seconds.

The proposal is **2026 OBSERVANCES only** at these exact points. Elevation is explicitly assumed to be 0 m; the publisher does not provide it. Coordinates, timezone assumptions and artifact versions are bound in each bundle member.

| Point | Latitude | Longitude | IANA zone |
|---|---:|---:|---|
| Bangalore | 12.983333333333333 | 77.6 | Asia/Kolkata |
| Chennai | 13.083333333333334 | 80.28333333333333 | Asia/Kolkata |
| Delhi | 28.666666666666668 | 77.21666666666667 | Asia/Kolkata |
| Guwahati | 26.183333333333334 | 91.73333333333333 | Asia/Kolkata |
| Hyderabad | 17.383333333333333 | 78.46666666666667 | Asia/Kolkata |
| London | 51.516666666666666 | -0.13333333333333333 | Europe/London |
| Mayapur | 23.416666666666668 | 88.38333333333334 | Asia/Kolkata |

EVENTS, DAILY_ASTRONOMY, other years, nearby GPS coordinates, other elevations and other traditions are excluded. Exact export settings/binary identity and positive rare-type validation remain missing. Agreement is evidence for review, not approval of these assumptions.

### Findings from the reproduced diagnostics

The new `measurements/<city>-2026.json` files include `ruleDiagnostics`, already hash-bound in the unsigned bundles. These local astronomical values help distinguish plausible rule differences from boundary sensitivity; they are not an independent astronomical oracle. Tithi indices in the files are zero-based.

| Held case | Concrete diagnostic and implication |
|---|---|
| Moscow, May 27 naming | May 26 has Ekadashi at sunrise but Dashami at arunodaya (02:24:39 local); Dashami ends 02:41:27. This fails the related upstream Vyanjuli prerequisite. The engine lacks that prerequisite. This is a supported candidate explanation, conditional on that source's applicability. |
| Sydney, December 5 naming | December 4 has Ekadashi at sunrise but Dashami at arunodaya (04:01:04); Dashami ends 04:34:01. The same purity difference is present. Matching fasting/Parana dates does not settle the label. |
| Auckland, October 7–8 | October 7 is locally Ekadashi at both sunrise and arunodaya. **The purity-order difference alone does not explain this case.** Dvadashi ends October 8 at 06:46:57.059, about 20.526 seconds before sunrise at 06:47:17.585. The designated export's exact astronomy/settings are unavailable; classification and the large Parana cap difference remain unresolved. |
| Vrindavan, June 25–30 | June 25 is locally pure Ekadashi. The closing Purnima boundary on June 30 is 05:26:39.819, about 13.439 seconds before sunrise at 05:26:53.259. This supports investigating closing-tithi sensitivity; it does not adjudicate the June 25 versus June 26 fast. |
| Ahmedabad, November 6 | Dvadashi ends 10:31:04.467; the daylight-third cap is 10:31:05.395. Their 0.928-second separation explains why a near-identical printed time cannot resolve the end-basis disagreement. |
| Mumbai, New York and Auckland open-ended Parana | Related upstream code retains a start with no end when the bounds cross; the engine currently drops the window. The saved August 24, October 22 and September 23 rows justify a proposed explicit open-ended representation. No rule or contract substitution was made. |
| Moscow and Sao Paulo DST | Fifteen and nine Parana dates respectively retain source DST markers inconsistent with the recorded IANA zone. These remain unusable timing comparisons; no one-hour correction was applied. |

All eight whole-year holds remain. Source applicability, the high-impact fasting/Parana cases, named-type prerequisites, rare nakshatra conditions and Unmilani interpretation still need qualified determination. No dispute was removed. The separate optional Sydney January 1–December 4 proposal preserves full-year/adjacent-year context and excludes the disputed fast and Parana; it is not an automatic addition to the seven candidates.

## Unsigned bundles

Prepared with the existing offline `prepare` command only. No signing keys, real identities, completed review or approval records were supplied. These exact fingerprints bind this local build and evidence; a changed build/evidence needs a new bundle and review.

| Bundle | Members | Fingerprint |
|---|---:|---|
| [Seven candidates](bundles/priority/bundle.json) | 7 | `bac29c1ecbc3ffc945bc04a7bb14bdccca0e65d2ac508213a2d41426c14993e2` |
| [Eight held points](bundles/hold/bundle.json) | 8 | `c126d0044a9c72b98428235d7e3619a1dde8319db22dff2902bee0f7443bc9f5` |
| [Optional narrower Sydney](bundles/alternative-sydney-through-december-04/bundle.json) | 1 | `0a0539a38fc77edd3c909b2d7f7567daa2e7e957c0f30b4bdae0ebd26eeb6523` |

[bundle-checks.json](bundle-checks.json) also contains the exact file SHA-256s. Every member's result fingerprint, versions and context matches the fresh calculation measurements; all referenced evidence bytes were rechecked.

## Verification and observed performance

| Run | Total | Passed | Failed | Errors / skipped |
|---|---:|---:|---:|---:|
| Fresh starting normal suite | 673 | 664 | 9 | 0 / 0 |
| Final targeted publication/API/publisher | 118 | 118 | 0 | 0 / 0 |
| Final full normal suite | 682 | 673 | 9 | 0 / 0 |

All nine remaining failure identities **and messages** match the starting XML. All ten prior delivery regressions pass. The separate one-test research collector reproduced 45 location/year results; it is not included in the normal test counts or an accuracy score. Three application distributions built. Local code inspection was completed; CodeRabbit could not run because its CLI was signed out.

Real local HTTP/file/process checks passed: restart reuse, actual artifact-byte invalidation, corruption refusal, four JVMs performing one computation, API/publisher cache reuse, v1 migration, no-store and all 18 excluded-tradition guidance routes refused. Current-policy and approval-store outage tests use isolated test-only identities/storage.

At the tested Mayapur point, real HTTP cold yearly calculation took **11.17 s**, warm day retrieval **47.75 ms**, and yearly retrieval after API restart **278.02 ms**. A separate JVM probe measured **11.15 s cold / 289.14 ms restart-warm**. These are single local observations, not load-test guarantees. Same-key cold concurrent callers waited roughly 11.5–11.8 seconds while only one calculated. The cache is validated for cooperating processes on a local filesystem, not distributed storage.

See [VERIFICATION.md](VERIFICATION.md) for exact commands, XML links, failure categories, process evidence and preservation checks. [verification.json](verification.json) reconciles 1,829 starting files. No protected calculation/rule/golden/tolerance source, original evidence/report or runtime dispute changed. One generated prior Python `__pycache__` file was refreshed by importing its parser; this is explicitly recorded and further bytecode writes are disabled in the research scripts.

## What still needs a decision

[DECISIONS.md](DECISIONS.md) contains four exact questions, supported options, recommendations, reasons evidence cannot establish them and decision owners:

1. A qualified reviewer evaluates the seven exact scopes and declared reference assumptions; the owner separately decides publication.
2. A qualified calendar authority determines disputed rule/source applicability before any later rule changes.
3. The owner/operator selects real identities, key custody, durable approval storage and independent rollback protection.
4. The owner/client/operator chooses and implements current-approval handling before activating consumers or file hosting.

Android and Content Hub were not modified. No religious rules, goldens, tolerances or existing human-review/publication requirements were changed. No reviewer was contacted; nothing was purchased, deployed, committed or pushed. Server revocation cannot retract already downloaded files.
