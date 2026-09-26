"""Continue this local verification sequence once the already-running normal suite finishes."""
import json
from pathlib import Path
import subprocess
import sys
import time

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
def read(p):return json.loads(p.read_text())
def stage(name):
    (HERE/'pipeline-state.json').write_text(json.dumps(dict(stage=name),indent=2))
    print(name,flush=True)
def command(args):
    code=subprocess.run(args,cwd=ROOT).returncode
    if code:raise RuntimeError(f'Stage failed with {code}: {args}')

if __name__=='__main__':
    stage('waiting-for-running-full-normal-suite')
    while not (HERE/'full/counts.json').exists():time.sleep(2)
    base=read(HERE/'baseline/counts.json'); full=read(HERE/'full/counts.json')
    assert sorted(base['failures'],key=str)==sorted(full['failures'],key=str),'New full-suite failures require investigation'
    for name in ('distributions','evidence'):
        stage(name)
        command([sys.executable,str(HERE/'verify.py'),name])
        assert read(HERE/name/'command.json')['exitCode']==0,name
    stage('classifying-reproduced-evidence')
    command([sys.executable,str(HERE/'review.py')])
    stage('preparing-unsigned-bundles')
    command([sys.executable,str(HERE/'prepare_bundles.py')])
    stage('real-http-file-cache-process-checks')
    command([sys.executable,str(HERE/'smoke.py')])
    stage('verification-sequence-complete')
