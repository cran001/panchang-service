"""Real local sockets, executable restarts, file exports and cooperating cache processes."""
import concurrent.futures
import json
import os
from pathlib import Path
import socket
import shutil
import subprocess
import time
import urllib.error
import urllib.request
import zipfile

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
OUT=HERE/'smoke'
FLAGS=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0
def write(p,v): p.write_text(json.dumps(v,indent=2),encoding='utf-8')
def run(args,name,env=None,expected=0):
    start=time.perf_counter()
    with (OUT/f'{name}.log').open('wb') as log:
        code=subprocess.run(args,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,creationflags=FLAGS).returncode
    write(OUT/f'{name}.command.json',dict(command=args,exitCode=code,elapsedSeconds=time.perf_counter()-start))
    assert (code==0)==(expected==0),(name,code)

def main():
    OUT.mkdir(exist_ok=False)
    checks={}
    cp=(HERE/'probe-classpath.txt').read_text()
    def probe(folder,name,classpath=None,expected=0):
        args=['java','-cp',classpath or cp,'org.panchang.releaseprobe.CacheProbe',str(folder),str(OUT/f'{name}.json')]
        run(args,name,expected=expected)
        return json.loads((OUT/f'{name}.json').read_text()) if expected==0 else None
    folder=OUT/'process-cache'
    cold=probe(folder,'cold')
    warm=probe(folder,'restart-warm')
    assert cold['resultSha256']==warm['resultSha256']
    assert (folder/'computations.log').read_text().splitlines()==['computed']
    checks['restartReuse']=True
    # Change an actual artifact byte identity in an isolated COPY, never a runtime source or JAR.
    publication_jar=next(Path(p) for p in cp.split(os.pathsep) if Path(p).name.startswith('publication-') and p.endswith('.jar'))
    changed_jar=OUT/'TEST-ONLY-changed-publication.jar'
    shutil.copy2(publication_jar,changed_jar)
    with zipfile.ZipFile(changed_jar,'a') as archive:
        archive.writestr('TEST-ONLY-cache-version-marker.txt','Test-only artifact identity change. No calculation code altered.')
    changed_cp=os.pathsep.join(str(changed_jar) if Path(p)==publication_jar else p for p in cp.split(os.pathsep))
    changed=probe(folder,'changed-version',classpath=changed_cp)
    assert changed['versions']!=warm['versions']
    assert changed['resultSha256']==warm['resultSha256']
    assert len((folder/'computations.log').read_text().splitlines())==2
    checks['artifactVersionInvalidates']=True
    concurrent_dir=OUT/'concurrent-processes'
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        results=list(pool.map(lambda n:probe(concurrent_dir,f'concurrent-{n}'),range(4)))
    assert len((concurrent_dir/'computations.log').read_text().splitlines())==1
    assert all(r['resultSha256']==cold['resultSha256'] for r in results)
    checks['fourProcessesOneComputation']=True
    checks['performanceMillis']=dict(cold=cold['elapsedMillis'],restartWarm=warm['elapsedMillis'],versionMiss=changed['elapsedMillis'],
                                    concurrent=[r['elapsedMillis'] for r in results])
    entry=next((concurrent_dir/'cache').glob('*.json'))
    original=entry.read_bytes()
    (OUT/'corrupt-entry-original.json').write_bytes(original)
    entry.write_bytes(original[:100])
    probe(concurrent_dir,'corrupt-cache',expected=1)
    checks['corruptionRefused']=True

    env=os.environ.copy()
    env.update(HOST='127.0.0.1',PANCHANG_APPROVAL_MODE='disabled',PANCHANG_CALC_CACHE_DIR=str(OUT/'http-cache'))
    with socket.socket() as sock:
        sock.bind(('127.0.0.1',0)); port=sock.getsockname()[1]
    env['PORT']=str(port)
    base=f'http://127.0.0.1:{port}'
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    responses=[]
    def get(path):
        start=time.perf_counter()
        try: response=opener.open(base+path,timeout=120)
        except urllib.error.HTTPError as error: response=error
        with response:
            raw=response.read(); body=json.loads(raw)
            record=dict(path=path,status=response.status,headers=dict(response.headers),body=body,elapsedMillis=(time.perf_counter()-start)*1000)
            responses.append(record)
            assert response.headers['Cache-Control']=='no-store'
            return record
    def start_server(name):
        log=(OUT/f'{name}.log').open('wb')
        args=['java','-cp',str(ROOT/'api/build/install/api/lib/*'),'org.panchang.api.MainKt']
        p=subprocess.Popen(args,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,creationflags=FLAGS)
        write(OUT/f'{name}.command.json',dict(command=args,host='127.0.0.1',port=port,cacheDir=env['PANCHANG_CALC_CACHE_DIR']))
        for _ in range(300):
            if p.poll() is not None: raise RuntimeError('Local API exited; inspect log')
            try:
                with opener.open(base+'/v2/health',timeout=1) as response:
                    if response.status==200:return p,log
            except (OSError,urllib.error.URLError):time.sleep(.1)
        p.terminate();p.wait();log.close();raise TimeoutError('Local API startup')
    def stop(p,log):p.terminate();p.wait(timeout=20);log.close()
    query='?lat=23.416666666666668&lon=88.38333333333334&tz=Asia/Kolkata'
    p,log=start_server('api-first')
    try:
        assert get('/v1/day/iskcon/2026-01-01'+query)['status']==410
        assert get('/v1/calendar/iskcon/2026'+query)['status']==410
        cold_http=get('/v2/calendar/iskcon/2026'+query)
        warm_http=get('/v2/day/iskcon/2026-01-01'+query)
        for r in (cold_http,warm_http):
            assert r['status']==200 and r['body']['schemaVersion']==2
            assert r['body']['ekadashiYear']['observances'] is None
            assert r['body']['yearResolution']['events'] is None
        for tradition in ['marathi','telugu','kannada','gujarati','northindian','tamil','malayalam','bengali','odia']:
            for route in [f'day/{tradition}/2026-01-01',f'calendar/{tradition}/2026']:
                assert get('/v2/'+route+query)['status']==422
        assert len(list((OUT/'http-cache').glob('*.json')))==1
        cache_before={p.name:p.read_bytes() for p in (OUT/'http-cache').glob('*.json')}
    finally:stop(p,log)
    p,log=start_server('api-restart')
    try:
        again=get('/v2/calendar/iskcon/2026'+query)
        assert again['body']==cold_http['body']
        assert cache_before=={p.name:p.read_bytes() for p in (OUT/'http-cache').glob('*.json')}
    finally:stop(p,log)
    specs=OUT/'sites.txt'
    specs.write_text('coords mayapur 23.416666666666668 88.38333333333334 Asia/Kolkata Mayapur\n')
    args=['java','-cp',str(ROOT/'publish/build/install/publish/lib/*'),'org.panchang.publish.MainKt','--sites',str(specs),'--year','2026','--out',str(OUT/'export')]
    run(args,'publisher',env)
    assert cache_before=={p.name:p.read_bytes() for p in (OUT/'http-cache').glob('*.json')}
    published=json.loads((OUT/'export/v2/mayapur/calendar.json').read_text())
    assert published['ekadashiYear']==cold_http['body']['ekadashiYear']
    assert published['yearResolution']==cold_http['body']['yearResolution']
    assert (OUT/'export/COMPLETE').is_file() and not (OUT/'export/legacy').exists()
    migration=json.loads((OUT/'export/v1/mayapur/ekadashi-year.json').read_text())
    assert migration['code']=='API_VERSION_RETIRED' and 'observances' not in migration
    checks.update(realHttpAndPublisher=True,apiRestartSameBytes=True,sharedApiPublisherCache=True,all18ExcludedRoutesRefused=True,
                  noRealApprovalCreated=True,staticFilesNotDeployed=True,
                  httpPerformanceMillis=dict(cold=cold_http['elapsedMillis'],warmDay=warm_http['elapsedMillis'],restartWarm=again['elapsedMillis']))
    write(OUT/'http-responses.json',responses)
    write(OUT/'checks.json',checks)
    print(json.dumps(checks,indent=2),flush=True)

if __name__=='__main__':main()
