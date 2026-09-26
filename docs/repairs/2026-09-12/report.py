"""Regenerate corrected historical counts and current repair summaries from retained XML."""
import json
from collections import Counter
from pathlib import Path
import xml.etree.ElementTree as ET
import sys
sys.dont_write_bytecode = True
from evidence import HERE, AUDIT, ROOT, counts, hashes, write

historical = counts(AUDIT / "baseline-xml")
write(HERE / "historical-baseline.corrected.json", historical)
original_findings = json.loads((AUDIT / "findings.json").read_text(encoding="utf-8-sig"))
original_findings["baseline"] = {**historical["total"], "tagExcludedNetworkMethods": 7}
original_findings["erratum"] = {
    "date": "2026-09-12", "scope": "Historical audit only; not post-fix status",
    "replacesReportedTotals": {"tests": 733, "passed": 724},
    "evidence": "../../audits/2026-09-12/baseline-xml",
    "originalPreserved": "../../audits/2026-09-12/findings.json",
}
write(HERE / "findings.historical.corrected.json", original_findings)
rows = ["| Module | Tests | Passed | Failed | Errors | Skipped |",
        "|---|---:|---:|---:|---:|---:|"]
for module, row in [*historical["modules"].items(), ("Total", historical["total"])]:
    rows.append("| " + module + " | " + " | ".join(str(row[k]) for k in
                ("tests", "passed", "failed", "errors", "skipped")) + " |")
(HERE / "ERRATUM-2026-09-12.md").write_text("""# Historical audit erratum — 12 September 2026

The audit's headline **733 tests / 724 passed** is incorrect. The saved JUnit XML contains
**633 tests / 624 passed / 9 failed / 0 errors / 0 skipped**. This is a total-row arithmetic
correction: the audit's nine module rows were already correct and sum to 633, not 733.
`report.py` parses each saved suite and verifies its test count against its testcase elements.

""" + "\n".join(rows) + """

The three additional audit contract checks are separate: January 1 delivery, Reykjavik's
daylight cap, and Reykjavik's sunrise comparison each failed. They are not part of 633.
Seven network-tagged methods were excluded by the pre-existing build configuration; they
are not XML skips or passes. That configuration is unchanged.

Corrections apply to AUDIT.md, REPRODUCE.md and findings.json wherever they state 733/724.
The original numerical summary.json contains no baseline total and requires no numerical
rewrite. Original baseline-counts.json module rows remain valid.

All original report text, raw references, logs, XML, source snapshots and checksum records
remain unchanged under docs/audits/2026-09-12. This clearly separate erratum supersedes the
incorrect totals without rewriting historical evidence. Machine-readable corrected history:
historical-baseline.corrected.json and findings.historical.corrected.json. Fresh results are
in separate run directories and summary.json here, never merged into the historical baseline.

Reproduce from repository root:

```powershell
python docs/repairs/2026-09-12/report.py
python docs/repairs/2026-09-12/evidence.py verify-original
```
""", encoding="utf-8")

runs = {}
for path in sorted(HERE.glob("*/xml")):
    row = counts(path)
    write(path.parent / "counts.json", row)
    exit_path = path.parent / "exit.json"
    if exit_path.exists():
        row["exitCode"] = json.loads(exit_path.read_text())["exitCode"]
    runs[path.parent.name] = row

summary = dict(date="2026-09-12", historicalBaseline=historical,
               historicalAdditionalAuditChecks=counts(HERE / "historical-audit-xml"),
               freshRuns=runs, publishingBlocked=True, releaseReadinessEstablished=False,
               unresolved=["Vrindavan fasting date and Parana", "Auckland Parana and classification",
                           "Moscow and Sydney classification", "Reykjavik sunrise rounding disagreement",
                           "Owner and qualified reviewer approval, inferred religious conditions"],
               excludedNetworkMethods=7)
summary["interruptedRuns"] = [{"run": p.parent.name, **json.loads(p.read_text())}
                              for p in HERE.glob("*/interrupted.json")]
http_checks = HERE / "http-and-files/checks.json"
if http_checks.exists():
    summary["localHttpAndWrittenFiles"] = json.loads(http_checks.read_text())
final_run = next((name for name in ("full-normal-completed", "full-normal-final", "full-normal")
                  if name in runs), "full-normal")
if final_run in runs:
    def failure_keys(row):
        return Counter((f["suite"], f["name"], f["message"]) for f in row["failures"])
    old, new = failure_keys(historical), failure_keys(runs[final_run])
    summary["fullNormalFailureComparison"] = dict(
        run=final_run, identicalToHistorical=(old == new),
        added=[dict(suite=k[0], name=k[1], message=k[2], count=v) for k,v in (new-old).items()],
        removed=[dict(suite=k[0], name=k[1], message=k[2], count=v) for k,v in (old-new).items()])
    regression_suites = {"org.panchang.core.DaylightIntervalTest", "org.panchang.calc.YearCarryoverTest",
                         "org.panchang.sampradaya.DaylightDeliveryTest", "org.panchang.api.DeliveryFrontDoorTest"}
    regression_cases = []
    for path in (HERE / final_run / "xml").rglob("*.xml"):
        suite = ET.parse(path).getroot()
        for case in suite.findall("testcase"):
            if suite.get("name") in regression_suites or (
                    suite.get("name") == "org.panchang.publish.FeedPublisherTest" and
                    case.get("name", "").startswith("yearly files retain January Parana")):
                outcome = next((tag for tag in ("failure", "error", "skipped") if case.find(tag) is not None), "passed")
                regression_cases.append(dict(suite=suite.get("name"), name=case.get("name"), outcome=outcome,
                                             xml=path.relative_to(ROOT).as_posix()))
    regression_counts = dict(tests=len(regression_cases), passed=sum(c["outcome"] == "passed" for c in regression_cases),
        failed=sum(c["outcome"] == "failure" for c in regression_cases),
        errors=sum(c["outcome"] == "error" for c in regression_cases), skipped=sum(c["outcome"] == "skipped" for c in regression_cases))
    summary["newPermanentRegressions"] = dict(run=final_run, total=regression_counts, cases=regression_cases)
    write(HERE / "new-permanent-regressions.json", summary["newPermanentRegressions"])
write(HERE / "summary.json", summary)

table = ["| Evidence | Tests | Passed | Failed | Errors | Skipped |",
         "|---|---:|---:|---:|---:|---:|"]
for label, row in [("Historical normal suite", historical),
                   ("Historical additional audit checks", summary["historicalAdditionalAuditChecks"]),
                   *runs.items()]:
    table.append("| " + label + " | " + " | ".join(str(row["total"][k]) for k in
                 ("tests", "passed", "failed", "errors", "skipped")) + " |")
comparison = summary.get("fullNormalFailureComparison", {})
(HERE / "VERIFICATION.md").write_text("""# XML-derived verification — 12 September 2026

Generated by `report.py`; rows are distinct runs and must not be added together.

""" + "\n".join(table) + "\n\n" +
    f"Full-normal failure comparison: `{comparison}`\n\n" + """The `full-normal` run is the first full post-fix run, before updating the three old
CLI/API cardinality checks. Its seven extra failures are one CalcCliTest, one
CrossFrontDoorIdentityTest, and five MultiSiteParanaApiTest cases tripped by the shared
24-record precondition. Every timing comparison still runs after that precondition correction.
The final full gate is `full-normal-completed`, when present. `full-normal-final` was interrupted
before a completion result and is not counted as a finished gate. The nine original failures remain
failed gates; a red build is not release readiness.

`post-fix-regressions` retains one newly written cross-year whole-DTO assertion failure:
different yearly index seeds rounded an unrelated tithi boundary one second differently.
The final test checks identity and complete Parana equality across years and keeps whole-DTO
day/year comparisons within each year. No numerical tolerance was changed.

`pre-fix-regressions` contains three failed methods covering two defects (two carryover checks
and one daylight check). The separate audit contract run contains three different contract
checks; after repair only the numerical sunrise check should remain red. Diagnostic collector
success records visits, not independent agreement. See each run's command, exit, log and XML.

The seven pre-existing network-tagged methods remain excluded by the normal build; they
are not included in any XML denominator. No new exclusions or skips were introduced.
Real socket HTTP and publisher CLI checks are recorded separately in `http-and-files/checks.json`
and do not inflate JUnit test totals. All original audit files and checksum records are retained.

Publishing remains blocked by unresolved approval and religious-rule issues.
""", encoding="utf-8")

starting = json.loads((HERE / "starting-state/source-hashes.json").read_text())
changed = [p for p, sha in starting.items() if not (ROOT/p).is_file() or
           hashes([ROOT/p]).get(p) != sha]
write(HERE / "changed-existing-files.json", changed)
print(json.dumps(dict(historical=historical["total"], runs={k:v["total"] for k,v in runs.items()},
                      changedExistingFiles=changed), indent=2))
