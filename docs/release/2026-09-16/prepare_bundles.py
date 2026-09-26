"""Prepare two unsigned review bundles with the existing offline CLI. No journal writes."""
import json
import os
from pathlib import Path
import subprocess

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
commands=[]
for name in ('priority','hold'):
    request=HERE/f'proposals/{name}.request.json'
    if not request.exists(): continue
    dest=HERE/f'bundles/{name}'
    if dest.exists(): raise RuntimeError(f'Refusing to overwrite prior bundle: {dest}')
    command=['java','-cp',str(ROOT/'publication/build/install/publication/lib/*'),'org.panchang.publication.MainKt','prepare',str(request),str(dest)]
    commands.append(command)
    (HERE/'bundle-commands.json').write_text(json.dumps(commands,indent=2),encoding='utf-8')
    with (HERE/f'prepare-{name}.log').open('wb') as log:
        subprocess.run(command,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,check=True,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
    bundle=json.loads((dest/'bundle.json').read_text(encoding='utf-8'))
    assert bundle['purpose']=='LOCAL_REVIEW_ONLY'
    assert all(m['tradition']=='iskcon' and m['calculationYear']==2026 and m['fields']==['OBSERVANCES'] for m in bundle['members'])
    print(name,len(bundle['members']),'unsigned review members prepared',flush=True)
