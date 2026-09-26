"""Reparse each run's XML, preserve run boundaries, and compare failure identities."""
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
spec = importlib.util.spec_from_file_location('xml_counts', ROOT/'docs/repairs/2026-09-12/evidence.py')
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)

runs = {}
for path in HERE.iterdir():
    if path.is_dir() and (path/'exit.json').exists() and (path/'xml').exists():
        runs[path.name] = helper.counts(path/'xml')
        helper.write(path/'counts.json', runs[path.name])

def failures(run):
    return {(r['module'],r['suite'],r['name'],r['kind'],r['message']) for r in runs[run]['failures']}

comparison = None
if 'full-normal-final' in runs:
    comparison = dict(identicalToBaseline=failures('baseline')==failures('full-normal-final'),
                      added=sorted(failures('full-normal-final')-failures('baseline')),
                      removed=sorted(failures('baseline')-failures('full-normal-final')))
original = json.loads((HERE/'starting-state/hashes.json').read_text())
changed = [p for p,h in original.items() if not (ROOT/p).is_file() or hashlib.sha256((ROOT/p).read_bytes()).hexdigest()!=h]
paths = subprocess.check_output(['git','ls-files','-z','--cached','--others','--exclude-standard'],cwd=ROOT).decode().split('\0')
new = sorted(p for p in paths if p and p not in original and not p.startswith('docs/publication/'))
helper.write(HERE/'implementation-files.json',dict(changedExisting=sorted(changed),newImplementationFiles=new))
summary = dict(runs=runs,failureComparison=comparison,realApprovalsCreated=False,productionApprovalWrites=False,
               deploymentPerformed=False,changedExisting=sorted(changed),newImplementationFiles=new)
for name, filename in [('deliveryRegressions', 'delivery-regressions.json'),
                       ('distributionCheck', 'distribution-check.json'),
                       ('localHttpAndFiles', 'http-and-files/checks.json')]:
    path = HERE/filename
    if path.exists():
        summary[name] = json.loads(path.read_text())
helper.write(HERE/'summary.json',summary)
lines=['# XML-derived verification','', 'Each row is a separate run. Do not add the rows together.','',
       '| Run | Tests | Passed | Failed | Errors | Skipped |', '|---|---:|---:|---:|---:|---:|']
for name in sorted(runs):
    c=runs[name]['total']
    lines.append('| '+name+' | '+' | '.join(str(c[k]) for k in ['tests','passed','failed','errors','skipped'])+' |')
lines+=['','## Exact commands','']
for name in sorted(runs):
    cmd=json.loads((HERE/name/'command.json').read_text())
    lines+=['### '+name,'','```powershell', '.\\gradlew.bat '+' '.join(cmd[1:]),'```','']
if summary.get('localHttpAndFiles', {}).get('passed'):
    lines += ['### Distribution and real local smoke checks', '', '```powershell',
              '.\\gradlew.bat :api:installDist :publish:installDist :publication:installDist --console=plain',
              'python docs/publication/2026-09-13/http_smoke.py', '```', '',
              'The distributions built successfully. Real loopback HTTP day/year responses for Mayapur and',
              'Reykjavik matched the structured files. Unapproved timings were withheld, legacy files were',
              'absent, the anonymous approval route returned 404, and the TEST ONLY review bundle was',
              'prepared without creating approval. The server was stopped after verification.', '',
              'Exact Java commands, local binding and requests are saved in `http-and-files/commands.json`',
              'and `http-and-files/checks.json`. These checks are separate from JUnit counts. The script',
              'refuses to overwrite its existing evidence directory; use a fresh evidence location to rerun.', '']
if comparison is not None:
    lines += ['## Full normal failure comparison','',f"Identical failure names and messages to fresh baseline: **{comparison['identicalToBaseline']}**.",'',
              'Remaining failures:', '']
    lines += ['- `'+r['suite']+' :: '+r['name']+'` — '+r['message'].replace('\n',' ') for r in runs['full-normal-final']['failures']]
lines += ['', '## Interpretation and limits','',
    '- Fresh baseline: 643 tests, 634 passed and 9 failed. The older 733 claim remains corrected by the preserved 12 September erratum.',
    '- The first intermediate targeted run predates the new policy tests and passed 79 API/publisher tests.',
    '- The next targeted run exposed a cross-year scope-comparison exception, a missing review-renderer UTC warning, and an old regional synthetic-UTC expectation. The policy comparison was fixed; the warning was restored and the expectation now requires no substitute calendar. No timing tolerance or golden reference was changed.',
    '- The nine baseline failures concern Vrindavan fasting date/classification/Parana, Auckland Parana basis and classification, and Moscow/Sydney classification and named deferral. These remain failed release gates.',
    '- The separate Reykjavik sunrise disagreement is retained in the runtime registry and original evidence. This task does not reclassify it as passing or widen its 30-second band.',
    '- The seven existing network-tagged methods remain excluded by the normal build. They are not XML passes or skips.',
    '- All ten original permanent delivery regressions passed in the final full XML (delivery-regressions.json). HTTP calculation comparisons use explicitly signed TEST ONLY fixtures; legacy numeric/rounding checks use the marked review renderer. New public tests verify default withholding and legacy refusal.',
    '- CodeRabbit 0.7.5 has a valid CodeRabbit Inc. Authenticode signature but is signed out. No remote review ran. Local review covered signatures, authority separation, stale reviews, scope matching, rollback limits, content tampering and revocation at export.',
    '- Real local HTTP/file/prepare checks passed (http-and-files/checks.json). Distribution inspection found no test-only approval classes packaged (distribution-check.json). These checks do not inflate JUnit counts.',
    '- Preservation verification confirmed 833 original audit, repair and calculation files unchanged (preservation.txt).',
    '- Production identity/storage activation, qualified decisions, consumer adaptation and deployment remain separate. No real approval, commit, push, cache, Android change, Content Hub change or deployment was created.', '',
    'File scope is recorded in implementation-files.json. The owner workflow and compatibility contract are in OWNER-GUIDE.md.', '']
(HERE/'VERIFICATION.md').write_text('\n'.join(lines),encoding='utf-8')
print(json.dumps(dict(totals={k:v['total'] for k,v in runs.items()},failureComparison=comparison),indent=2))
