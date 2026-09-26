# Repair reproduction — 12 September 2026

Run PowerShell from `D:\projects\panchang-service`. Use Java 21 and the repository Gradle wrapper.
The starting commit was `4e7fc3a54d8e3dcc8664a05adbb5689ec5db3b09`, with 9 modified tracked files
and 25 pre-existing untracked files outside the audit. `starting-state/status.txt`,
`tracked.diff`, `source-hashes.json`, and `files/` preserve the exact working source; the commit
alone does not describe it. There was no applicable AGENTS.md. Nothing was reset or committed.

Every run directory contains `command.json`, `gradle.log`, `exit.json`, copied JUnit XML and
`counts.json`. The wrapper creates a new run directory and refuses to overwrite an existing
one. Choose a fresh run name when repeating. `report.py` derives totals directly from saved XML.

## Permanent regression tests

```powershell
.\gradlew.bat :core:test --tests '*DaylightIntervalTest' :calc:test --tests '*YearCarryoverTest' :sampradaya:test --tests '*DaylightDeliveryTest' --tests '*ReykjavikParanaTest' --tests '*ParanaDaylightEdgeTest' :api:test --tests '*DeliveryFrontDoorTest' :publish:test --tests '*FeedPublisherTest' --continue --console=plain
```

These sources run in the normal module suites without an audit init script. `DaylightDeliveryTest`
reads the independently saved USNO records; retain the original audit directory alongside the
tests. No expected numerical value is replaced with a service-generated golden value.

Pre-fix permanent-test evidence: `pre-fix-regressions/`, command:

```powershell
.\gradlew.bat :calc:test --tests '*YearCarryoverTest' :sampradaya:test --tests '*DaylightDeliveryTest' --continue --console=plain
```

It failed before production edits: two carryover assertions and one independent daylight-cap
assertion. The initial cross-year assertion subsequently exposed an existing one-second tithi
rounding difference between different index seeds. The final test checks identity and complete
Parana equality between years, while continuing to compare complete DTOs for day/year queries
within the same year. It does not change a numerical tolerance. Historical failing XML remains.
To reproduce the old production behavior again, use the saved starting source in a **separate
scratch copy**, adding the permanent regression sources. Do not reset this working tree.

## Full normal suite

```powershell
.\gradlew.bat test --rerun-tasks --continue --console=plain
```

The original nine religious/reference failures remain failures. Existing network-tag exclusions
remain unchanged; they are neither XML skips nor passes.

## Original audit checks against repaired source

The original harness has a hard-coded output path that would overwrite historical evidence.
The copy here changes only that output-path binding to `audit.output`. The copied init script
selects it and supplies the separate path. Run diagnostics before the cap check because that
check reads the diagnostic output. Populate a fresh directory's `new-astronomy/` with the
unchanged files from `docs/audits/2026-09-12/new-astronomy/` first.

```powershell
$repairInit='docs/repairs/2026-09-12/audit.init.gradle'
.\gradlew.bat -I $repairInit -PauditOutput=docs/repairs/2026-09-12/post-fix-audit-output :verify:auditEvidence --tests '*highLatitudeDiagnostics' --console=plain
.\gradlew.bat -I $repairInit -PauditOutput=docs/repairs/2026-09-12/post-fix-audit-output :verify:auditEvidence --tests '*januaryFirstDeliveryContract' --tests '*reykjavikSunriseRoundingContract' --tests '*reykjavikDocumentedDaylightCapContract' --console=plain
```

The sunrise check keeps the original **30-second** band and the 6/12/24 refinement probes.
It is expected to remain red; delivery fixes do not resolve that numerical disagreement.

## Real local HTTP and written-file checks

```powershell
.\gradlew.bat :api:installDist :publish:installDist --console=plain
python docs/repairs/2026-09-12/http_smoke.py
```

This starts a real server on a free loopback port, issues HTTP requests, runs the publisher
locally, compares saved responses with written v1 files, checks legacy attachment and rounding,
then terminates only its own server process. `http-and-files/` is a new output directory;
the script refuses to overwrite a previous run. Exact Java commands and request URLs are saved.
This is local verification, not deployment or publishing approval.

Exact cases:

- Mayapur: latitude `23.416666666666668`, longitude `88.38333333333334`, zone `Asia/Kolkata`,
  `/v1/day/iskcon/2026-01-01` and `/v1/calendar/iskcon/2026`.
- Reykjavik: latitude `64.1466`, longitude `-21.9426`, zone `Atlantic/Reykjavik`,
  `/v1/day/iskcon/2026-06-26` and `/v1/calendar/iskcon/2026`.

## Counts and preservation

```powershell
python docs/repairs/2026-09-12/report.py
python docs/repairs/2026-09-12/evidence.py verify-original
```

See `ERRATUM-2026-09-12.md` for the 733-to-633 historical correction, `summary.json` for separate
historical/additional/fresh results, and `YEARLY-CONTRACT.md` for carryover compatibility.
**Publishing remains blocked by unresolved approval and religious-rule issues.**
