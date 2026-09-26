# Reproduce this audit

Run PowerShell in `D:\projects\panchang-service`, on the same source state identified by `commit.txt`, `baseline-status.txt`, `baseline-tracked.diff` and `audited-source-hashes.json`. The commit alone is insufficient: this audit includes pre-existing uncommitted regional work. Original production files and existing expected values were not changed.

## Existing baseline

```powershell
java -version
.\gradlew.bat --version
.\gradlew.bat test --rerun-tasks --continue --console=plain
```

Expected on the audited tree: 733 tests, 9 failures, 0 errors/skips. Read JUnit XML in each module's `build/test-results/test/`, not a previous document. This run's immutable copied results are under `baseline-xml/`, with summaries in `baseline-counts.json` and `baseline-failures.json`. Full original output: `baseline-tests.log`. Exit 1 is expected. The seven `LiveSourceTest` methods are tag-excluded, not JUnit skips; the audit uses deliberate, recorded fetches instead.

## Offline comparison, with the saved references

The init script adds audit sources and test-only dependencies for this invocation. It does not modify the ordinary build or production code. Run the steps in this order; the collector methods deliberately have file dependencies.

```powershell
$auditInit='docs/audits/2026-09-12/audit.init.gradle'
.\gradlew.bat -I $auditInit :verify:auditEvidence --tests '*parseDownloads' --console=plain
.\gradlew.bat -I $auditInit :verify:auditEvidence --tests '*calendars' --tests '*astronomy' --console=plain
python docs/audits/2026-09-12/analyse.py
.\gradlew.bat -I $auditInit :verify:auditEvidence --tests '*investigateDailyDisagreements' --tests '*highLatitudeDiagnostics' --tests '*independentAngularElements' --console=plain
.\gradlew.bat -I $auditInit :verify:auditEvidence --tests '*januaryFirstDeliveryContract' --tests '*reykjavikSunriseRoundingContract' --tests '*reykjavikDocumentedDaylightCapContract' --console=plain
python docs/audits/2026-09-12/exceptions.py
python docs/audits/2026-09-12/evidence_inventory.py
```

The last Gradle command intentionally fails **three tests**. Two expose delivery/daylight-contract defects; one preserves a new sunrise reference disagreement. Its output is copied to `defect-tests.log` and `defect-tests.xml`. Collector success means rows were visited, not that all rows agree. Early audit-only compile mistakes and their corrected runs remain in the plan/delivery logs; they are not production failures.

`analyse.py` uses only the Python standard library. It records raw residuals, strict transliteration mappings, excluded cases, and nearest-rank p95. Independent tithi crossing interpolation uses JPL Moon and Sun values only; the service result enters afterwards as the comparison subject. No LLM or service-generated expected value is used.

## Optional re-fetch, separate from offline reproducibility

Current downloads and hashes already live in `raw/` and the three fetch manifests. Re-fetching can yield revised evidence and should be done in a new dated audit directory rather than overwrite these bytes. The exact commands originally used were:

```powershell
.\gradlew.bat -I docs/audits/2026-09-12/audit.init.gradle :verify:auditEvidence --tests '*planRequests' --console=plain
& docs/audits/2026-09-12/fetch.ps1
& docs/audits/2026-09-12/fetch.ps1 -RequestFile supplement-requests.json -ManifestFile supplement-manifest.json
& docs/audits/2026-09-12/fetch.ps1 -RequestFile retry-requests.json -ManifestFile retry-manifest.json
```

The Kotlin repository harvesters generate the request URLs and parse the responses. Windows `curl.exe` is the transport; it checks TLS, uses a 25-second timeout and a three-second gap, captures failures, and reuses matching checksum-verified repository cache entries before networking. No existing cache is deleted. Parsed downloads are separate from curated `verify/golden`. Each manifest records the actual parameters, UTC retrieval time, status, origin and SHA-256. Some supplementary/retry ids retain the original probe's date; the `parameters.date` and response record contain the actual date and control every comparison.

The supplementary probes were selected after transient 502 responses, for reference availability, before calculating their residuals. Two held-out 2026 locations and four targeted retries succeeded. No publisher was substituted. The GCal rules PDF was unavailable because TLS hostname verification failed; certificate checks were not disabled.
