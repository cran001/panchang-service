"""Real local public requests, file generation and review-bundle preparation; no approval/deploy."""
import json
import os
from pathlib import Path
import socket
import subprocess
import time
import urllib.request
import urllib.error
import urllib.parse

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
DEST = HERE / 'http-and-files'
DEST.mkdir(exist_ok=False)

def save(name, value):
    (DEST / name).write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')

sites = dict(mayapur=('23.416666666666668','88.38333333333334','Asia/Kolkata','2026-01-01'),
             reykjavik=('64.1466','-21.9426','Atlantic/Reykjavik','2026-06-27'))
sitefile = DEST / 'sites.tsv'
sitefile.write_text('\n'.join(f'coords {name} {lat} {lon} {zone} {name}' for name,(lat,lon,zone,_) in sites.items()), encoding='utf-8')
commands = []
def run(module, args, label):
    command = ['java','-cp',str(ROOT/f'{module}/build/install/{module}/lib/*'),f'org.panchang.{module}.MainKt',*args]
    commands.append(command)
    with (DEST / f'{label}.log').open('wb') as log:
        subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True,
                       creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)

run('publish',['--sites',str(sitefile),'--year','2026','--out',str(DEST/'feed')],'publisher')
assert not (DEST/'feed/legacy').exists()
assert (DEST/'feed/COMPLETE').is_file()
run('publication',['prepare','publication/examples/prepare.TEST-ONLY.json',str(DEST/'review-example')],'prepare')
assert (DEST/'review-example/review/NOT-FOR-PUBLICATION.txt').is_file()
assert json.loads((DEST/'review-example/bundle.json').read_text())['purpose'] == 'LOCAL_REVIEW_ONLY'

with socket.socket() as probe:
    probe.bind(('127.0.0.1', 0))
    port = probe.getsockname()[1]
command = ['java','-cp',str(ROOT/'api/build/install/api/lib/*'),'org.panchang.api.MainKt']
save('commands.json', dict(commands=commands, server=command, bind='127.0.0.1', port=port))
responses=[]
base=f'http://127.0.0.1:{port}'
with (DEST/'server.log').open('wb') as log:
    server = subprocess.Popen(command,cwd=ROOT,env={**os.environ,'HOST':'127.0.0.1','PORT':str(port)},stdout=log,stderr=subprocess.STDOUT,
                              creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    try:
        for attempt in range(100):
            try:
                with urllib.request.urlopen(base+'/v1/health',timeout=2) as response:
                    assert response.status == 200
                break
            except OSError:
                assert server.poll() is None, 'Local server exited'
                time.sleep(0.2)
        else:
            raise RuntimeError('Local server did not start')
        def get(name,path):
            with urllib.request.urlopen(base+path, timeout=180) as response:
                value=json.load(response)
                assert response.status == 200
                assert response.headers['Cache-Control'] == 'no-store'
                responses.append(dict(name=name,path=path,status=response.status))
                save(name+'.json',value)
                return value
        for name,(lat,lon,zone,date) in sites.items():
            query=urllib.parse.urlencode(dict(lat=lat,lon=lon,tz=zone))
            year=get(name+'-year',f'/v1/calendar/iskcon/2026?{query}')
            day=get(name+'-day',f'/v1/day/iskcon/{date}?{query}')
            for root,file in [('ekadashiYear','ekadashi-year'),('yearResolution','year-resolution')]:
                assert year[root] == json.loads((DEST/f'feed/v1/{name}/{file}.json').read_text(encoding='utf-8'))
            assert day['ekadashiYear']['observances'] is None
            assert 'jdUt' not in json.dumps(day)
            assert day['ekadashiYear']['absenceMeaning']=='GUIDANCE_WITHHELD_NOT_NO_EVENT'
            if name=='reykjavik':
                assert day['ekadashiYear']['publication']['state']=='DISPUTED'
                assert 'ASTRONOMY-01' in json.dumps(day)
        request=urllib.request.Request(base+'/v1/admin/approvals',data=b'{"approved":true}',headers={'Content-Type':'application/json'},method='POST')
        try:
            urllib.request.urlopen(request,timeout=5)
            raise AssertionError('Unexpected approval route')
        except urllib.error.HTTPError as e:
            assert e.code in (404,405)
            responses.append(dict(path='/v1/admin/approvals',status=e.code))
        save('checks.json',dict(passed=True,requests=responses,legacyFiles=False,
            reviewBundlePrepared=True,approvalsCreated=False,deployed=False,junitTestCounts=False))
        print('Real local HTTP, structured files, legacy refusal and review preparation passed',flush=True)
    finally:
        server.terminate()
        try:
            server.wait(timeout=20)
        except subprocess.TimeoutExpired:
            server.kill()
            server.wait(timeout=10)
