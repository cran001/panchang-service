"""Append-only run evidence for this task. Run from the repository root."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]

def write(path, value):
    path.write_text(json.dumps(value, indent=2), encoding='utf-8')

def snapshot():
    dest = HERE / 'starting-state'
    dest.mkdir(exist_ok=False)
    paths = subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=ROOT).decode().split('\0')
    write(dest / 'hashes.json', {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in paths if p and not p.startswith('docs/release/2026-09-21/')})
    for filename, args in [('status.txt', ['git', 'status', '--short']), ('tracked.diff', ['git', 'diff', '--binary', 'HEAD'])]:
        (dest / filename).write_bytes(subprocess.check_output(args, cwd=ROOT))

def run(name, args, modules=None, test_task='test'):
    dest = HERE / name
    dest.mkdir(exist_ok=False)
    start = time.monotonic()
    with (dest / 'console.log').open('wb') as log:
        code = subprocess.run(args, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT).returncode
    write(dest / 'command.json', dict(command=args, exitCode=code, elapsedSeconds=time.monotonic()-start))
    counts = dict(tests=0, passed=0, failed=0, errors=0, skipped=0)
    failures = []
    for module in modules if modules is not None else [p.name for p in ROOT.iterdir() if p.is_dir()]:
        for xml in (ROOT / module / 'build/test-results' / test_task).glob('TEST-*.xml'):
            target = dest / 'xml' / module / xml.name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(xml, target)
            for test in ET.parse(xml).getroot().findall('testcase'):
                counts['tests'] += 1
                kind = 'passed'
                for tag, key in [('failure', 'failed'), ('error', 'errors'), ('skipped', 'skipped')]:
                    item = test.find(tag)
                    if item is not None:
                        kind = key
                        if tag != 'skipped':
                            failures.append(dict(module=module, suite=test.get('classname'), name=test.get('name'), kind=tag, message=item.get('message')))
                        break
                counts[kind] += 1
    write(dest / 'counts.json', dict(total=counts, failures=failures))
    print(name, code, counts, flush=True)

if __name__ == '__main__':
    name = sys.argv[1]
    if name == 'snapshot': snapshot()
    elif name in ('baseline', 'full'):
        run(name, ['.\\gradlew.bat', 'test', '--rerun-tasks', '--continue', '--console=plain'])
    elif name.startswith('targeted'):
        run(name, ['.\\gradlew.bat', ':publication:test', ':api:test', ':publish:test', '--rerun-tasks', '--continue', '--console=plain'], ['publication', 'api', 'publish'])
    elif name == 'distributions':
        run(name, ['.\\gradlew.bat', ':api:installDist', ':publish:installDist', ':publication:installDist', '--console=plain'], [])
    elif name.startswith('evidence'):
        run(name, ['.\\gradlew.bat', '-I', 'docs/release/2026-09-21/evidence.init.gradle', ':verify:releaseEvidence', ':verify:releaseProbeClasspath', '--console=plain'], ['verify'], 'releaseEvidence')
