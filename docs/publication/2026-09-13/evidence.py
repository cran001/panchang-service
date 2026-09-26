"""Append-only local execution evidence for publication controls."""
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
spec = importlib.util.spec_from_file_location('repair_evidence', ROOT / 'docs/repairs/2026-09-12/evidence.py')
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)
helper.HERE = HERE

def snapshot():
    dest = HERE / 'starting-state'
    dest.mkdir(exist_ok=False)
    for name, args in [('status.txt', ['status', '--short', '--untracked-files=all']),
                       ('tracked.diff', ['diff', '--binary', 'HEAD'])]:
        (dest / name).write_bytes(subprocess.check_output(['git', *args], cwd=ROOT))
    paths = subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=ROOT).decode().split('\0')
    files = [ROOT / p for p in paths if p and not p.startswith('docs/publication/')]
    helper.write(dest / 'hashes.json', helper.hashes(files))
    for src in files:
        if src.is_file() and '/src/' in src.as_posix():
            if src.is_relative_to(ROOT / 'docs'):
                continue
            target = dest / 'files' / src.relative_to(ROOT)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src, target)

if sys.argv[1] == 'snapshot':
    snapshot()
elif sys.argv[1] == 'run':
    helper.run(sys.argv[2], sys.argv[3:])
elif sys.argv[1] == 'preservation':
    original = json.loads((HERE / 'starting-state/hashes.json').read_text())
    protected = {p: h for p, h in original.items() if p.startswith(('docs/audits/', 'docs/repairs/')) or
                 p.startswith(('core/', 'sampradaya/')) or p.endswith('YearCarryoverTest.kt')}
    for p, h in protected.items():
        assert hashlib.sha256((ROOT / p).read_bytes()).hexdigest() == h, p
    print(f'{len(protected)} protected audit, repair and calculation files unchanged')
