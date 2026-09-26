"""Unsigned local preparation only. Never calls record, supplies a key, or activates trust."""
import hashlib
import json
import os
from pathlib import Path
import subprocess

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
def read(p):return json.loads(p.read_text(encoding='utf-8'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    checks=[]
    measurements=[read(p)['member'] for p in (HERE/'measurements').glob('*-2026.json')]
    for name,count in [('priority',7),('hold',8),('alternative-sydney-through-december-04',1)]:
        request=HERE/f'proposals/{name}.request.json'
        dest=HERE/f'bundles/{name}'
        assert not dest.exists(),f'Refusing to replace {dest}'
        args=['java','-cp',str(ROOT/'publication/build/install/publication/lib/*'),'org.panchang.publication.MainKt','prepare',str(request),str(dest)]
        with (HERE/f'prepare-{name}.log').open('wb') as log:
            code=subprocess.run(args,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,
                creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0).returncode
        (HERE/f'prepare-{name}.command.json').write_text(json.dumps(dict(command=args,exitCode=code),indent=2))
        assert code==0
        bundle=read(dest/'bundle.json')
        assert bundle['purpose']=='LOCAL_REVIEW_ONLY' and len(bundle['members'])==count
        for m in bundle['members']:
            assert m['tradition']=='iskcon' and m['calculationYear']==2026 and m['fields']==['OBSERVANCES']
            measured=next(x for x in measurements if x['place']==m['place'])
            for key in ('versions','resultSha256','context'): assert measured[key]==m[key],(name,key)
            if name.startswith('alternative-'):
                assert m['coverage']=={'from':'2026-01-01','through':'2026-12-04'}
            else: assert measured['coverage']==m['coverage']
        for e in bundle['evidence']+bundle['referencePolicy']['evidence']:
            assert sha(ROOT/e['link'])==e['sha256'], e['link']
        checks.append(dict(name=name,members=count,fingerprint=(dest/'fingerprint.txt').read_text(),
                           bundleBytesSha256=sha(dest/'bundle.json'),resultsAndVersionsMatchMeasurements=True,
                           classification='AI_ASSISTED_EVIDENCE_REVIEW',unsigned=True,realApproval=False))
        print(name,checks[-1]['fingerprint'],flush=True)
    (HERE/'bundle-checks.json').write_text(json.dumps(checks,indent=2))

if __name__=='__main__':main()
