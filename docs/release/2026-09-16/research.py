"""Read-only source collection. No golden files, approvals or runtime coverage are changed."""
import datetime as dt
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import time
import urllib.request
import urllib.error

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False), encoding='utf-8')

def snapshot():
    dest = HERE/'starting-state'
    dest.mkdir(exist_ok=False)
    paths = subprocess.check_output(['git','ls-files','-z','--cached','--others','--exclude-standard'], cwd=ROOT).decode().split('\0')
    hashes = {p: hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in paths if p and not p.startswith('docs/release/')}
    write(dest/'hashes.json', hashes)
    (dest/'tracked.diff').write_bytes(subprocess.check_output(['git','diff','--binary','HEAD'],cwd=ROOT))
    (dest/'status.txt').write_bytes(subprocess.check_output(['git','status','--short'],cwd=ROOT))

def fetch(id, url, kind, **extra):
    metadata = HERE/'sources'/f'{id}.json'
    if metadata.exists():
        return json.loads(metadata.read_text(encoding='utf-8'))
    record = dict(id=id, url=url, kind=kind, retrievedAtUtc=dt.datetime.now(dt.timezone.utc).isoformat(), **extra)
    headers = {'User-Agent':'panchang-service-evidence/0.1 (local release research)'}
    record['requestHeaders'] = headers
    for attempt in range(2):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=25) as response:
                body=response.read()
                record.update(status=response.status, finalUrl=response.url, responseHeaders=dict(response.headers))
            suffix = '.pdf' if body.startswith(b'%PDF') else '.raw'
            file=HERE/'sources'/f'{id}{suffix}'
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_bytes(body)
            record.update(file=str(file.relative_to(ROOT)).replace('\\','/'), sha256=hashlib.sha256(body).hexdigest(), bytes=len(body))
            break
        except Exception as exc:
            record.update(status=getattr(exc,'code',None), error=str(exc))
            if attempt == 0 and getattr(exc,'code',None) in (429,500,502,503,504):
                time.sleep(2)
                continue
            break
    write(metadata,record)
    print(id, record.get('status'), record.get('bytes', record.get('error')), flush=True)
    time.sleep(0.3)
    return record

def collect():
    for y in (2025,2026,2027):
        fetch(f'index-{y}', f'https://www.vaisnavacalendar.info/calendar-file-downloads/txt-calendar-files-{y}', 'index', year=y)
    for id,path in [('about','about-the-vaisnava-calendar'),('dst','daylight-savings'),('fasting','fasting-indications'),('updates','2020-gbc-changes-updates')]:
        fetch(id,'https://www.vaisnavacalendar.info/'+path,'documentation')
    tree=fetch('gcal-tree','https://api.github.com/repos/gopaladasa/GCAL-for-Windows/git/trees/master?recursive=1','upstream-discovery')
    if tree.get('file'):
        revision=json.loads((ROOT/tree['file']).read_text())['sha']
        for id,path in [('gcal-readme','README.md'),('gcal-astronomy','documentation/GCalAstronomyDocumentation.pdf'),('gcal-events','documentation/StandardEventsList.pdf'),('gcal-doc-readme','documentation/readme.txt')]:
            fetch(id,f'https://raw.githubusercontent.com/gopaladasa/GCAL-for-Windows/{revision}/{path}','upstream-documentation', upstreamRevision=revision)
    fetch('gcal-rules','https://gopal.home.sk/gcal/docs/GCalVaisnavaCalculation.pdf','upstream-documentation')
    sources=[]
    for p in sorted((ROOT/'verify/golden').glob('vaisnavacalendar-*-2026.json')):
        doc=json.loads(p.read_text())
        city=p.stem.removeprefix('vaisnavacalendar-').removesuffix('-2026')
        for year in (2025,2026,2027):
            sources.append((city,year,doc['provenance'][0]['url'].replace('/2026/','/'+str(year)+'/')))
    sources.extend(('hyderabad',y,f'https://www.vaisnavacalendar.info/calendars/{y}/Hyderabad%20%5BIndia%5D.txt') for y in (2025,2026,2027))
    for city,year,url in sources:
        fetch(f'calendar-{city}-{year}',url,'calendar',cityId=city,year=year, lineage='vaisnavacalendar.info/GCal-text')

if __name__ == '__main__':
    if sys.argv[1]=='snapshot': snapshot()
    elif sys.argv[1]=='fetch': collect()
