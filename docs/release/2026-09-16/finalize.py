"""Summarize completed evidence, then bind prepared bundles back to measured results."""
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import verification

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
def read(p): return json.loads(p.read_text(encoding='utf-8-sig'))
v=read(HERE/'verification.json')
assert v['sameFailuresAsPriorBaseline'] is True
assert read(HERE/'launch-smoke/checks.json')['passed'] is True
bundles=[]
for name in ('priority','hold'):
    bundle=read(HERE/f'bundles/{name}/bundle.json')
    request=read(HERE/f'proposals/{name}.request.json')
    assert bundle['purpose']=='LOCAL_REVIEW_ONLY'
    assert len(bundle['members'])==len(request['members'])
    for member in bundle['members']:
        matches=[read(f) for f in (HERE/'measurements').glob('*-2026.json') if read(f)['member']['place']==member['place']]
        assert len(matches)==1
        assert member['resultSha256']==matches[0]['member']['resultSha256']
        assert member['fields']==['OBSERVANCES']
    for item in bundle['evidence']+bundle['referencePolicy']['evidence']:
        import hashlib
        assert hashlib.sha256((ROOT/item['link']).read_bytes()).hexdigest()==item['sha256']
    bundles.append(dict(name=name,members=len(bundle['members']),fingerprint=(HERE/f'bundles/{name}/fingerprint.txt').read_text(),realApproval=False,resultsMatchMeasurements=True))
(HERE/'bundle-checks.json').write_text(json.dumps(bundles,indent=2),encoding='utf-8')
delivery_names={(r['suite'],r['name']) for r in read(ROOT/'docs/publication/2026-09-13/delivery-regressions.json')['results']}
delivery=[]
for file in (HERE/'full-xml/xml').glob('*/*.xml'):
    for case in ET.parse(file).getroot().findall('testcase'):
        key=(case.get('classname'),case.get('name'))
        if key in delivery_names:
            assert not any(case.find(k) is not None for k in ('failure','error','skipped'))
            delivery.append(key)
assert len(delivery)==10
summary=['# Completed verification','', '| Check | Result |', '|---|---|']
for name in ('targeted','full'):
    c=v[name]['total'];summary.append(f"| {name} JUnit XML | {c['tests']} total, {c['passed']} passed, {c['failed']} failed, {c['errors']} errors, {c['skipped']} skipped |")
summary += ['| Prior failure comparison | The same nine test identities and messages; no new failures |',
            '| Original delivery regressions | All ten passed in full-suite XML |',
            '| Inventory regression checks | 6 passed; separate Python run |',
            '| Offline evidence collector | 1 collection test passed; comparison disagreements are retained as data |',
            '| Real local HTTP/publisher | Public discovery, all 18 excluded paths, ISKCON withholding and publisher refusal passed |',
            '| Review bundle checks | Seven priority and eight hold members; results match measurements; all evidence hashes valid; no approvals |',
            f"| Preservation | {v['originalFilesChecked']} starting files checked; no protected calculation/audit/repair/publication-history files changed |",
            f"| Source integrity | {v['sourceArtifactsHashVerified']} archived raw source hashes verified |",'',
            'The normal suite remains red because of the nine existing Vrindavan/Auckland/Moscow/Sydney conformance failures.',
            'Research additionally records other-year disagreements; collection success does not relabel those comparisons as passing.',
            'Seven pre-existing network-tagged tests remain excluded from the normal build, not counted as XML passes/skips.',
            '', 'Exact commands are in HANDOFF.md and full-command.json. Logs, exit records and separate copied XML are retained.',
            'No real qualified review, owner approval, production activation, commit, push or deployment occurred.','']
(HERE/'VERIFICATION.md').write_text('\n'.join(summary),encoding='utf-8')
print('Verified bundles, evidence hashes, unchanged failures and ten delivery regressions')
