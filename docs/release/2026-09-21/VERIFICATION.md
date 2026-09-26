# Verification record

All commands ran locally from `D:\projects\panchang-service`. Each saved run contains its command, exit status, elapsed time, console log and copied XML. Reports below use individual XML test cases, not a Gradle console summary.

## Normal and targeted tests

```powershell
.\gradlew.bat test --rerun-tasks --continue --console=plain
.\gradlew.bat :publication:test :api:test :publish:test --rerun-tasks --continue --console=plain
.\gradlew.bat test --rerun-tasks --continue --console=plain
```

| Capture | Counts | Exit | Elapsed |
|---|---|---:|---:|
| [baseline](baseline/counts.json) | 673 tests, 664 passed, 9 failed, 0 errors/skips | 1 | 544.953 s |
| [targeted-1](targeted-1/counts.json) | 118 tests, 110 passed, 8 failed | 1 | See command record |
| [targeted-2](targeted-2/counts.json) | 118 tests, 118 passed, 0 failed/errors/skips | 0 | 499.015 s |
| [full](full/counts.json) | 682 tests, 673 passed, 9 failed, 0 errors/skips | 1 | 621.641 s |

The first targeted run exposed incorrectly moved diagnostic `review/v1/` assertions. Those paths were restored; their original delivery checks pass. The second targeted run and final full suite verify the fix. Additional warm-cache revocation and v1 migration-file assertions were included in the final full suite. No production source changed after that full run; later changes are research-script bytecode hygiene and documentation/reconciliation only.

All nine final normal failures are the original `GaudiyaEkadashiConformanceTest` cases: Vrindavan Parana/fasting-date/type (3), Auckland Parana/type (2), Moscow type/deferral classification (2), Sydney type/deferral classification (2). `finalize.py` compares module, suite, name, failure kind and complete message against the fresh baseline. The suite remains red; no tolerance, golden or rule was changed to suppress it.

[delivery-regressions.json](delivery-regressions.json) enumerates ten passing original regressions: YearCarryoverTest (2), DaylightIntervalTest (4), DaylightDeliveryTest (1), DeliveryFrontDoorTest (2), and the FeedPublisherTest January carryover/legacy-attachment case (1).

## Executables, reproduction and unsigned preparation

```powershell
.\gradlew.bat :api:installDist :publish:installDist :publication:installDist --console=plain
.\gradlew.bat -I docs/release/2026-09-21/evidence.init.gradle :verify:releaseEvidence :verify:releaseProbeClasspath --console=plain
python -B docs/release/2026-09-21/review.py
python -B docs/release/2026-09-21/prepare_bundles.py
python -B docs/release/2026-09-21/smoke.py
python -B docs/release/2026-09-21/finalize.py
```

Distribution build exit 0, 3.125 seconds. Research collector exit 0, 747.187 seconds, one separately captured test. [reproduction-checks.json](reproduction-checks.json) verifies every one of the 45 result hashes and full comparison structures against the old snapshot. [source-recheck.json](source-recheck.json) binds the 45 raw comparison inputs; all 15 2026 inputs were freshly retrieved and unchanged. [source-checks.json](source-checks.json) records 40 verified source artifacts plus three failed/incomplete fetch records. Failed retrievals are not evidence of source content. Authenticated older sources are clearly identified where reused; TLS checks were never disabled.

Source collection uses `research.py fetch` and its `fetch` helper for the additional pinned C++ files and Bangalore retry, followed by `prepare_reproduction.py`; exact source URLs/retrieval timestamps/response metadata/hashes are in each `sources/*.json` sidecar. The initial Bangalore timeout was preserved and a separate retry saved. Calendar comparisons use only the designated lineage. The harness and pinned-source investigation do not modify the engine or expected results.

`prepare_bundles.py` calls, for each of `priority`, `hold` and `alternative-sydney-through-december-04`:

```powershell
java -cp 'publication/build/install/publication/lib/*' org.panchang.publication.MainKt prepare docs/release/2026-09-21/proposals/priority.request.json docs/release/2026-09-21/bundles/priority
```

The other two exact argument arrays are saved in `prepare-*.command.json`. Only `prepare` ran; no `record`, signing or approval command ran. [bundle-checks.json](bundle-checks.json) verifies all 16 proposed members across the three unsigned bundles against fresh measurements and all bound evidence bytes. Sydney's alternative is a separate optional proposal, not approved coverage.

Evidence output directories deliberately refuse overwrite. For a later reproduction, copy the scripts/harness into a new dated evidence directory, update the explicit output paths in the harness/init script, keep the original input hashes and comparison policy, and explicitly record any changed input/build. Do not delete these records merely to make commands rerunnable. `verify.py` captured test runs; `finish_runs.py` sequenced the completed build, evidence, bundle and smoke stages. The final read-only checks plus regenerated verification summaries can be rerun with `finalize.py`.

## Real HTTP, files and process cache

[smoke/checks.json](smoke/checks.json) is the compact outcome. `smoke/*.command.json` contains every Java invocation and local host/port/cache path. `smoke/http-responses.json` contains real responses, status, headers and durations. The API was bound to `127.0.0.1` on a temporary available port and stopped after both runs. These were real Netty sockets, not only the Ktor test harness.

| Check | Evidence/outcome |
|---|---|
| Separate JVM cold/restart | Same result hash; exactly one `computed` line; 11145.9083 / 289.1392 ms |
| Actual artifact version change | A test-only copied publication JAR gained a marker; different versions/key and second computation, same result hash; 11142.1048 ms. Installed JAR unchanged. |
| Four simultaneous JVMs | One computation, identical results; elapsed 11716.0332 / 11504.702901 / 11643.7753 / 11843.9692 ms |
| Corrupt entry | Deliberately truncated isolated cache copy refused with nonzero exit; original bytes retained separately |
| Real HTTP | Cold year 11169.3526 ms; warm day 47.7452 ms; restarted warm year 278.0162 ms |
| Policy and migration | v1 day/year 410; v2 returns explicit null guidance without approval; all JSON no-store; nine excluded traditions times two routes return 422 |
| Restart and publisher reuse | Cache bytes unchanged after API restart and actual publisher run; exported v2 guidance roots equal HTTP roots |
| File export | New isolated directory has COMPLETE marker, v2 roots and v1 migration documents; no legacy guidance directory |

`CalculationCacheTest` additionally checks request-provenance isolation, full-year January carryover, every declared key/version dimension, changed elevation actually missing, corruption/abandoned partial files, same-JVM concurrency, approved-empty versus withheld-null, changed disputes, supersession, revocation and throwing approval-store reads. The API publication-control tests recheck a warm cache and reject a prepared export after revocation. Existing signed-journal integrity/rollback tests remain in the passing targeted suite. Test identities/storage are isolated fixtures; real-process smoke tests keep production approval disabled.

No throughput/load, network filesystem, cross-host distributed cache, power-loss durability or hostile-writer authentication claim is made. The checksum is an integrity check, and the cache needs a trusted operator-owned local directory. Retention is an operational follow-up; do not delete active lock files.

## Preservation and inspection

[starting-state](starting-state/hashes.json) saved hashes for 1,829 preexisting files plus dirty status and tracked diff. [verification.json](verification.json) verifies protected calculation/rule/golden/tolerance trees, earlier audit/repair/release/publication evidence and the exact runtime dispute registry. [implementation-files.json](implementation-files.json) reports changes relative to that starting state, including one generated `2026-09-16/__pycache__/inventory.cpython-311.pyc` refresh. No original source/report/raw reference changed. Research imports now suppress bytecode writes.

Local inspection covered cache identity, payload verification, concurrent/atomic writes, approval-store exceptions, current-state response/export checks, v1 migration and v2 consumer semantics. CodeRabbit CLI 0.7.5 was available but signed out, so no remote CodeRabbit review ran and no result is implied. `git diff --check` and the final saved-file/link/hash checks are recorded in `final-checks.json`.

Android/Content Hub, runtime rules, dispute registry, original references and approval policy were outside the edits. No production adapter was activated, no reviewer contacted, and no deploy/commit/push occurred.
