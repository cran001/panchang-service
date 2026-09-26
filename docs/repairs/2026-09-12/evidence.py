"""Offline repair evidence. Never writes into the original audit directory."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
AUDIT = ROOT / "docs/audits/2026-09-12"


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def hashes(paths):
    return {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(paths) if p.is_file()}


def counts(directory):
    modules, failures = {}, []
    for path in sorted(directory.rglob("*.xml")):
        suite = ET.parse(path).getroot()
        assert suite.tag == "testsuite", path
        module = path.relative_to(directory).parts[0] if path.parent != directory else "audit"
        row = modules.setdefault(module, dict(tests=0, passed=0, failed=0, errors=0, skipped=0))
        values = {k: int(suite.get(attr, 0)) for k, attr in
                  [("tests", "tests"), ("failed", "failures"), ("errors", "errors"), ("skipped", "skipped")]}
        assert values["tests"] == len(suite.findall("testcase")), path
        values["passed"] = values["tests"] - sum(values[k] for k in ("failed", "errors", "skipped"))
        for k, v in values.items():
            row[k] += v
        for case in suite.findall("testcase"):
            for tag in ("failure", "error"):
                for failure in case.findall(tag):
                    failures.append(dict(module=module, suite=suite.get("name"),
                                         name=case.get("name"), kind=tag, message=failure.get("message")))
    total = {k: sum(v[k] for v in modules.values()) for k in
             ("tests", "passed", "failed", "errors", "skipped")}
    return dict(xmlSource=str(directory.relative_to(ROOT)), modules=modules, total=total, failures=failures)


def snapshot():
    dest = HERE / "starting-state"
    dest.mkdir(exist_ok=False)
    for name, args in [("commit.txt", ["rev-parse", "HEAD"]),
                       ("status.txt", ["status", "--short", "--untracked-files=all"]),
                       ("tracked.diff", ["diff", "--binary", "HEAD"])]:
        (dest / name).write_bytes(subprocess.check_output(["git", *args], cwd=ROOT))
    paths = subprocess.check_output(["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"], cwd=ROOT).decode().split("\0")
    files = [ROOT / p for p in paths if p and not p.startswith("docs/repairs/")]
    write(dest / "source-hashes.json", hashes(files))
    write(dest / "audit-hashes.json", hashes(AUDIT.rglob("*")))
    # Preserve all existing source/build/document bytes outside the large original audit.
    for src in files:
        if src.is_file() and not src.is_relative_to(AUDIT):
            target = dest / "files" / src.relative_to(ROOT)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src, target)
    write(HERE / "historical-baseline.corrected.json", counts(AUDIT / "baseline-xml"))
    extra = HERE / "historical-audit-xml"
    extra.mkdir()
    shutil.copy2(AUDIT / "defect-tests.xml", extra / "defect-tests.xml")
    write(HERE / "historical-audit-checks.json", counts(extra))


def run(label, args):
    dest = HERE / label
    dest.mkdir(exist_ok=False)
    cmd = [str(ROOT / "gradlew.bat"), *args]
    write(dest / "command.json", cmd)
    source_paths = subprocess.check_output(["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"], cwd=ROOT).decode().split("\0")
    write(dest / "source-hashes.json", hashes(ROOT / p for p in source_paths
          if p and not p.startswith("docs/repairs/") and not p.startswith("docs/audits/")))
    with (dest / "gradle.log").open("wb") as log:
        code = subprocess.call(cmd, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    write(dest / "exit.json", dict(exitCode=code))
    task = "auditEvidence" if any("auditEvidence" in a for a in args) else "test"
    selected = {a.split(":")[1] for a in args if a.startswith(":") and a.endswith(":" + task)}
    for module in ROOT.iterdir():
        if selected and module.name not in selected:
            continue
        results = module / "build/test-results" / task
        if results.is_dir():
            target = dest / "xml" / module.name
            target.mkdir(parents=True)
            for xml in results.glob("*.xml"):
                shutil.copy2(xml, target / xml.name)
    write(dest / "counts.json", counts(dest / "xml"))
    print(json.dumps(dict(label=label, exitCode=code, total=counts(dest / "xml")["total"])))


if __name__ == "__main__":
    import sys
    if sys.argv[1] == "snapshot":
        snapshot()
        print((HERE / "historical-baseline.corrected.json").read_text())
    elif sys.argv[1] == "run":
        run(sys.argv[2], sys.argv[3:])
    elif sys.argv[1] == "verify-original":
        original = json.loads((HERE / "starting-state/audit-hashes.json").read_text())
        current = hashes(AUDIT.rglob("*"))
        assert original == current, "Original audit evidence changed"
        print(f"All {len(original)} original audit files unchanged")
