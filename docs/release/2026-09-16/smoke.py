"""Real local launch restriction check; no hosting or approval changes."""
import json, os, socket, subprocess, time, urllib.request, urllib.error
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
DEST=HERE/'launch-smoke'
DEST.mkdir(exist_ok=False)
with socket.socket() as s:
    s.bind(('127.0.0.1',0));port=s.getsockname()[1]
command=['java','-cp',str(ROOT/'api/build/install/api/lib/*'),'org.panchang.api.MainKt']
flags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0
checks=[]
with (DEST/'server.log').open('wb') as log:
    server=subprocess.Popen(command,cwd=ROOT,env={**os.environ,'HOST':'127.0.0.1','PORT':str(port)},stdout=log,stderr=subprocess.STDOUT,creationflags=flags)
    try:
        base=f'http://127.0.0.1:{port}'
        for _ in range(100):
            try:
                urllib.request.urlopen(base+'/v1/health',timeout=2).close();break
            except OSError:
                assert server.poll() is None
                time.sleep(.2)
        else: raise RuntimeError('Server did not start')
        with urllib.request.urlopen(base+'/v1/meta') as response:
            meta=json.load(response)
        assert [r['id'] for r in meta['sampradayas']['sampradayas']]==['iskcon']
        checks.append(dict(path='/v1/meta',publicTraditions=['iskcon']))
        query='lat=23.416666666666668&lon=88.38333333333334&tz=Asia/Kolkata'
        for id in ('marathi','telugu','kannada','gujarati','northindian','tamil','malayalam','bengali','odia'):
            for path in (f'/v1/day/{id}/2026-01-01',f'/v1/calendar/{id}/2026'):
                try:
                    urllib.request.urlopen(base+path+'?'+query,timeout=10)
                    raise AssertionError('Excluded tradition was exposed')
                except urllib.error.HTTPError as e:
                    body=json.load(e)
                    assert e.code==422 and body['error']['code']=='SAMPRADAYA_OUTSIDE_RELEASE'
                    assert body['publication']['guidance']=='WITHHELD'
                    checks.append(dict(path=path,status=e.code))
        with urllib.request.urlopen(base+'/v1/day/iskcon/2026-01-01?'+query,timeout=180) as response:
            body=json.load(response)
            assert response.headers['Cache-Control']=='no-store'
        assert body['ekadashiYear']['observances'] is None
        assert 'jdUt' not in json.dumps(body)
        checks.append(dict(path='/v1/day/iskcon/2026-01-01',status=200,guidance='WITHHELD'))
        sitefile=DEST/'sites.txt'
        sitefile.write_text('coords mayapur 23.416666666666668 88.38333333333334 Asia/Kolkata Mayapur\n')
        pub=['java','-cp',str(ROOT/'publish/build/install/publish/lib/*'),'org.panchang.publish.MainKt','--sites',str(sitefile),'--year','2026','--sampradaya','marathi','--out',str(DEST/'refused-feed')]
        result=subprocess.run(pub,cwd=ROOT,capture_output=True,creationflags=flags)
        assert result.returncode!=0 and not (DEST/'refused-feed').exists()
        (DEST/'publisher.log').write_bytes(result.stdout+result.stderr)
        (DEST/'checks.json').write_text(json.dumps(dict(passed=True,checks=checks,serverCommand=command,publisherCommand=pub,
            excludedPublisherExit=result.returncode,approvalsCreated=False,deployed=False),indent=2),encoding='utf-8')
        print('Local API discovery, all 18 excluded routes, ISKCON withholding and publisher refusal passed')
    finally:
        server.terminate()
        try: server.wait(timeout=15)
        except subprocess.TimeoutExpired: server.kill();server.wait(timeout=10)
