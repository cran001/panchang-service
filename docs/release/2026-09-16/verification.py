"""Preserve XML run boundaries and original work; no test-result assumptions."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
def write(p,v): p.write_text(json.dumps(v,indent=2),encoding='utf-8')
def capture(name,modules=None):
    dest=HERE/name
    dest.mkdir(exist_ok=False)
    for m in modules or [p.name for p in ROOT.iterdir() if p.is_dir()]:
        source=ROOT/m/'build/test-results/test'
        if source.exists():
            for file in source.glob('TEST-*.xml'):
                (dest/'xml'/m).mkdir(parents=True,exist_ok=True)
                shutil.copy2(file,dest/'xml'/m/file.name)
    return count(dest/'xml')
def count(folder):
    total=dict(tests=0,passed=0,failed=0,errors=0,skipped=0);failures=[]
    for f in folder.glob('*/*.xml'):
        suite=ET.parse(f).getroot()
        for test in suite.findall('testcase'):
            total['tests']+=1
            kind='passed'
            for name,key in [('failure','failed'),('error','errors'),('skipped','skipped')]:
                item=test.find(name)
                if item is not None:
                    kind=key
                    if name!='skipped': failures.append(dict(module=f.parent.name,suite=test.get('classname'),name=test.get('name'),kind=name,message=item.get('message')))
                    break
            total[kind]+=1
    return dict(total=total,failures=failures)
def preservation():
    old=json.loads((HERE/'starting-state/hashes.json').read_text(encoding='utf-8'))
    changed=[p for p,h in old.items() if not (ROOT/p).is_file() or hashlib.sha256((ROOT/p).read_bytes()).hexdigest()!=h]
    protected=[p for p in changed if p.startswith(('core/','sampradaya/','docs/audits/','docs/repairs/','docs/publication/'))]
    assert not protected,protected
    return dict(changedFromStartingState=changed,protectedFilesChanged=protected,
        originalFilesChecked=len(old),realApprovalCreated=False,committed=False,pushed=False,deployed=False)
if __name__=='__main__':
    if sys.argv[1]=='capture-targeted':
        write(HERE/'targeted-counts.json',capture('targeted-xml',['publication','api','publish']))
    elif sys.argv[1]=='full':
        args=['.\\gradlew.bat','test','--rerun-tasks','--continue','--console=plain']
        write(HERE/'full-command.json',args)
        with (HERE/'full.log').open('wb') as log:
            code=subprocess.run(args,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT).returncode
        write(HERE/'full-exit.json',dict(exitCode=code))
        write(HERE/'full-counts.json',capture('full-xml'))
    elif sys.argv[1]=='report':
        result=preservation()
        sources=[json.loads(p.read_text(encoding='utf-8')) for p in (HERE/'sources').glob('*.json')]
        assert all(hashlib.sha256((ROOT/r['file']).read_bytes()).hexdigest()==r['sha256'] for r in sources if 'file' in r)
        result['sourceArtifactsHashVerified']=sum('file' in r for r in sources)
        for name in ('targeted','full'):
            file=HERE/f'{name}-counts.json'
            if file.exists(): result[name]=json.loads(file.read_text())
        if 'full' in result:
            base=json.loads((ROOT/'docs/publication/2026-09-13/full-normal-final/counts.json').read_text())
            identity=lambda rows: sorted((r['module'],r['suite'],r['name'],r['kind'],r['message']) for r in rows)
            result['sameFailuresAsPriorBaseline']=identity(base['failures'])==identity(result['full']['failures'])
        write(HERE/'verification.json',result)
        print(json.dumps({k:v for k,v in result.items() if k not in ('targeted','full')},indent=2))
