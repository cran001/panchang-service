import json, pathlib, hashlib, subprocess, datetime
P=pathlib.Path(__file__).resolve().parent; ROOT=P.parents[2]
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def write(n,v): (P/n).write_text(json.dumps(v,indent=2),encoding='utf-8')
rows=[]
for file in (ROOT/'verify/golden').glob('*.json'):
    doc=json.loads(file.read_text(encoding='utf-8-sig'))
    for prov in doc.get('provenance',[]):
        raw=ROOT/'verify/cache'/prov['sourceId']/prov['rawArtifactFile']
        rows.append(dict(fixture=str(file.relative_to(ROOT)),fixtureSha256=sha(file),sourceUrl=prov['url'],retrievedAtUtc=prov['retrievedAtUtc'],rawFile=str(raw.relative_to(ROOT)),rawPresent=raw.exists(),rawChecksumMatches=sha(raw)==prov['responseSha256'] if raw.exists() else None,rawResponseSha256=prov['responseSha256'],parameters=prov['requestParameters']))
write('stored-provenance-checks.json',rows)
listed=subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard'],cwd=ROOT,text=True).splitlines()
sources=[dict(path=n,sha256=sha(ROOT/n)) for n in listed if not n.startswith('docs/audits/') and (ROOT/n).is_file()]
write('audited-source-hashes.json',sources)
# This tree-listing response was saved before the generic fetch script was created.
tree=P/'gcal-tree.json'
write('gcal-tree-provenance.json',dict(url='https://api.github.com/repos/gopaladasa/GCAL-for-Windows/git/trees/master?recursive=1',httpStatus=200,sha256=sha(tree),retrievalTimeFromSavedFileMtimeUtc=datetime.datetime.fromtimestamp(tree.stat().st_mtime,datetime.timezone.utc).isoformat(),note='JSON response text saved with PowerShell Set-Content; checksum covers saved text, not a captured raw HTTP body. Discovery only.'))
inventory=[]
for path in sorted(P.rglob('*')):
    if path.is_file() and path.name!='SHA256SUMS.json': inventory.append(dict(path=str(path.relative_to(P)),sha256=sha(path),bytes=path.stat().st_size))
write('SHA256SUMS.json',inventory)
print('Stored provenance:',len(rows),'raw present',sum(r['rawPresent'] for r in rows),'raw hash verified',sum(r['rawChecksumMatches'] is True for r in rows))
print('Source hashes:',len(sources),'audit files:',len(inventory))
