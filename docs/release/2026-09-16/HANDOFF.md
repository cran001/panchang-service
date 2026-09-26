# ISKCON first-release scope and evidence handoff

Local implementation and evidence preparation only. **No release, completed review or owner
approval has been created.** Work began 16 September, resumed on 20 September and completed on 21 September 2026.

## Public behavior

Only `iskcon` is eligible for the first public release. `/v1/meta` and unknown-tradition
discovery advertise only that scope. All nine other registered traditions receive HTTP 422
with `SAMPRADAYA_OUTSIDE_RELEASE` and explicit withheld status; the public publisher refuses
them before writing files. Their calculators, catalog code and tests remain intact for research.
The shared policy refuses both EVENTS and OBSERVANCES for excluded traditions, even if a
local signed approval exists. ISKCON still requires exact qualified review and owner approval.

The designated default reference-policy ID is now `vaisnavacalendar-info-gcal-text`, revision
`2026-09-16-proposed`. Its runtime evidence remains empty and production approval storage is
still disabled. Research proposals are not automatically installed as trusted reference policy.
No reference value replaces a calculated value. The existing schema-2 withholding/null behavior,
legacy refusal, revocation checks and diagnostic separation remain.

## What to review

1. Read [COVERAGE.md](COVERAGE.md) and [REFERENCE-POLICY.md](REFERENCE-POLICY.md).
   Exact source metadata and per-field comparisons are in `coverage-inventory.json`.
2. Start with the 2026 **OBSERVANCES-only** proposal for Bangalore, Chennai, Delhi, Guwahati,
   Hyderabad, London and Mayapur. These seven have matching measured observance comparisons
   under the proposed policy. They are candidates for review, not supported/approved places.
3. Keep Ahmedabad, Auckland, Moscow, Mumbai, New York, Sao Paulo, Sydney and Vrindavan on hold.
   Their bundle is an investigation package, not a request to approve over unresolved evidence.
   [QUESTIONS.md](QUESTIONS.md) records online findings and the remaining authority questions.
4. `bundles/priority/bundle.json` and `bundles/hold/bundle.json` are prepared by the existing
   offline CLI. Each has a fingerprint, exact versions/results/points/date coverage, evidence
   hashes and marked review-only calculated documents. Individual request files remain in
   `proposals/`, so the owner can narrow a scope before review. No signatures or journal records
   are present. A rebuilt artifact or changed scope requires a newly prepared bundle.
5. Follow the [existing local owner guide](../../publication/2026-09-13/OWNER-GUIDE.md) to obtain
   an actual qualified review, record any explicit case resolutions/withholding, and then obtain
   the owner's separate publication decision. Production activation still needs trusted identity,
   durable storage and revocation delivery. No researcher or committee member has been assigned
   publishing rights by this task.

## Scope limits

The measurement sample is 15 exact points across 2025–2027, with 45 full calendar files and
16,425 civil-day rows. Wider discovery lists 657 year/file links (218 for 2025, 221 for 2026,
218 for 2027); unmeasured links are unavailable for release. There is no geographic interpolation
or automatic support for all entries in the location database.

Only 2026 is proposed because both adjacent-year files are available. 2025 and 2027 measurements
are research evidence, not a coverage expansion. They expose additional date disagreements:
Sao Paulo 9/10 January 2025 and Delhi 11/12 September 2027. Do not infer a later year from 2026.

All 14 re-fetched 2026 baseline files match their original raw SHA-256. Every measured file
identifies GCal 11 Build 5; exact generator settings and binary identity remain unpublished.
The sample exercises calculated Trisprsa, Paksavardhini, Vyanjuli, Unmilani and Vijaya cases,
but gives no positive calculated Jaya, Jayanti or Papanasini case. Absence is not validation.
Events are only partly mapped; festival fast-ending instants and standalone sunrise/sunset
comparisons are unavailable from this export format. No EVENTS or DAILY_ASTRONOMY bundle is proposed.

Reykjavik has no calendar in the collected designated indexes. Its separate sunrise dispute
remains unresolved. Original 2026 doctrine disputes, the daylight/carryover repairs and the
existing dispute registry are preserved. New research findings remain visible in the inventory;
future scopes require their own qualified review and maintained runtime cases before approval.

## Reproduce from the repository root

```powershell
python docs/release/2026-09-16/research.py fetch
.\gradlew.bat -I docs/release/2026-09-16/evidence.init.gradle :verify:releaseEvidence --console=plain
python docs/release/2026-09-16/inventory.py
python docs/release/2026-09-16/test_inventory.py -v
.\gradlew.bat :publication:test :api:test :publish:test --continue --console=plain
.\gradlew.bat test --rerun-tasks --continue --console=plain
.\gradlew.bat :api:installDist :publish:installDist :publication:installDist --console=plain
python docs/release/2026-09-16/prepare_bundles.py
python docs/release/2026-09-16/smoke.py
python docs/release/2026-09-16/verification.py report
```

The fetch script resumes saved source identities without refreshing them. Supplementary
documentation retrievals have exact URLs and UTC times in their sidecars. Collecting a new
snapshot requires new evidence identities/directories; do not overwrite a reviewed snapshot.
Bundle preparation and smoke scripts refuse existing output directories. Run against a fresh
evidence copy when repeating. The collector is offline, separate from the normal test suite;
its success means complete collection, not that comparisons agree.

The completed checks are summarized in [VERIFICATION.md](VERIFICATION.md). XML-derived
targeted/full totals and exact baseline failure comparison are in `verification.json`.
Full Gradle command/exit and logs are retained alongside separate copied XML directories.
`launch-smoke/checks.json` records real local HTTP discovery, all 18 excluded day/year paths,
ISKCON withholding and public-publisher refusal. These checks do not inflate JUnit totals.

## Changed files

| Area | Change |
|---|---|
| `publication/.../PublicReleaseScope.kt` | Non-configurable ISKCON first-release eligibility. |
| `publication/.../PublicationPolicy.kt` | Enforce eligibility on all fields; identify designated proposed lineage and release revision. |
| `api/.../Routes.kt`, `ApiFailure.kt` | Exclude other traditions from discovery and routes with an explicit refusal code. |
| `publish/.../PublicFeedPublisher.kt`, `Main.kt` | Refuse excluded traditions before artifact generation; clarify CLI scope. |
| `PublicationPolicyTest.kt`, `PublicationControlsApiTest.kt`, `RoutesTest.kt` | Signed-record exclusion, all nine calculators preserved, 18 route refusals, publisher refusal and discovery regression checks. |
| `docs/release/2026-09-16/` | Preserved starting hashes/diff, source snapshots, collector, inventory, proposed bundles and reproducible evidence. |

No astronomical calculation, religious rule, golden reference, tolerance, Android or Content Hub
code was changed. No caching, deployment, live-artifact withdrawal, commit or push occurred.
CodeRabbit is signed out; remote review did not run. Local review checked scope enforcement,
evidence classification, source identity, preservation and bundle/approval separation.
