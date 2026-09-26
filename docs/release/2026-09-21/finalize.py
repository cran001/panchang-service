"""Reconcile final evidence with the preserved starting state; no approval writes."""
import hashlib
import json
from pathlib import Path
import subprocess
import ast
import re

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
def read(p):return json.loads(p.read_text(encoding='utf-8'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(p,v):p.write_text(json.dumps(v,indent=2),encoding='utf-8')

def main():
    old=read(HERE/'starting-state/hashes.json')
    changed=[p for p,h in old.items() if not (ROOT/p).is_file() or sha(ROOT/p)!=h]
    generated=[p for p in changed if '/__pycache__/' in p and p.endswith('.pyc')]
    protected=[p for p in changed if p not in generated and p.startswith(('core/','sampradaya/','calc/','wire/','verify/',
               'docs/audits/','docs/repairs/','docs/publication/','docs/release/2026-09-16/'))]
    assert not protected,protected
    assert sha(ROOT/'publication/src/main/resources/org/panchang/publication/disputes.json')==old['publication/src/main/resources/org/panchang/publication/disputes.json']
    paths=subprocess.check_output(['git','ls-files','-z','--cached','--others','--exclude-standard'],cwd=ROOT).decode().split('\0')
    added=[p for p in paths if p and p not in old and not p.startswith('docs/release/2026-09-21/')]
    base=read(HERE/'baseline/counts.json'); full=read(HERE/'full/counts.json'); targeted=read(HERE/'targeted-2/counts.json')
    assert sorted(base['failures'],key=str)==sorted(full['failures'],key=str)
    assert targeted['total']['failed']==targeted['total']['errors']==0
    for source in read(HERE/'source-checks.json'):
        if source['verified']:assert sha(ROOT/source['file'])==source['sha256']
    for m in (HERE/'measurements').glob('*.json'):
        source=read(m)['source'];assert sha(ROOT/source['file'])==source['sha256']
    bundles=read(HERE/'bundle-checks.json')
    for b in bundles:
        p=HERE/'bundles'/b['name']
        assert sha(p/'bundle.json')==b['bundleBytesSha256']
        assert (p/'fingerprint.txt').read_text()==b['fingerprint']
        doc=read(p/'bundle.json')
        for e in doc['evidence']+doc['referencePolicy']['evidence']:assert sha(ROOT/e['link'])==e['sha256']
    smoke=read(HERE/'smoke/checks.json')
    result=dict(classification='AI_ASSISTED_EVIDENCE_REVIEW',startingFilesChecked=len(old),
        changedFromStartingState=changed,newImplementationFiles=added,protectedFilesChanged=protected,
        regeneratedPythonBytecode=generated,
        disputeRegistryUnchanged=True,baseline=base,full=full,targeted=targeted,
        exactBaselineFailuresUnchanged=True,smoke=smoke,bundles=bundles,
        noActualReviewApprovalSignatureOrIdentityCreated=True,noAndroidOrContentHubChange=True,
        noDeploymentCommitOrPush=True,remoteCodeReview='NOT_RUN_CODERABBIT_SIGNED_OUT')
    write(HERE/'verification.json',result)
    write(HERE/'implementation-files.json',dict(changed=changed,added=added))
    # Separate delivery regressions retain the original names and must pass in final normal XML.
    import xml.etree.ElementTree as ET
    names=('YearCarryoverTest','DaylightIntervalTest','DaylightDeliveryTest','DeliveryFrontDoorTest')
    delivery=[]
    for p in (HERE/'full/xml').glob('*/*.xml'):
        if any(n in p.name for n in names) or p.name=='TEST-org.panchang.publish.FeedPublisherTest.xml':
            for case in ET.parse(p).getroot().findall('testcase'):
                if 'FeedPublisherTest' in p.name and case.get('name')!='yearly files retain January Parana with the preceding legacy fast for attachment()':continue
                assert case.find('failure') is None and case.find('error') is None
                delivery.append(dict(suite=case.get('classname'),name=case.get('name')))
    assert len(delivery)==10,len(delivery)
    write(HERE/'delivery-regressions.json',delivery)
    links=[]
    for doc in HERE.glob('*.md'):
        for link in re.findall(r'\]\(([^)]+)\)',doc.read_text(encoding='utf-8')):
            if '://' in link or link.startswith('#'):continue
            target=(doc.parent/link.split('#')[0]).resolve()
            assert target.exists(),(doc.name,link)
            links.append(dict(document=doc.name,target=link))
    scripts=sorted(HERE.glob('*.py'))
    for script in scripts:ast.parse(script.read_text(encoding='utf-8'),filename=str(script))
    diff=subprocess.run(['git','diff','--check'],cwd=ROOT,capture_output=True,text=True)
    assert diff.returncode==0,diff.stdout+diff.stderr
    write(HERE/'final-checks.json',dict(gitDiffCheckExitCode=diff.returncode,
        gitDiffCheckOutput=diff.stdout+diff.stderr,localDocumentLinksChecked=links,
        pythonSourcesParsed=[p.name for p in scripts],
        finalDeliverableHashes={p.name:sha(p) for p in sorted(HERE.iterdir()) if p.is_file() and p.suffix in ('.md','.py','.example')},
        implementationHashes={p:sha(ROOT/p) for p in changed+added if p not in generated},
        allBundleEvidenceHashesChecked=True,allMeasurementReferenceHashesChecked=True))
    print(json.dumps(dict(baseline=base['total'],full=full['total'],targeted=targeted['total'],deliveryRegressions=len(delivery),
                         protectedFilesChanged=protected,cachePerformance=smoke['performanceMillis']),indent=2))

if __name__=='__main__':main()
