"""Derive research coverage and unsigned release proposals from saved measurements.

Does not update the runtime allowlist, golden data, dispute resolutions or journal.
"""
import collections
import datetime as dt
import hashlib
from html.parser import HTMLParser
import json
from pathlib import Path
import posixpath
import sys
from urllib.parse import unquote, urljoin, urlparse

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
def read(p): return json.loads(p.read_text(encoding='utf-8-sig'))
def write(p,v):
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(v,indent=2,ensure_ascii=False),encoding='utf-8')
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def evidence(p,outcome): return dict(link=p.relative_to(ROOT).as_posix(),sha256=sha(p),outcome=outcome)

MAP=read(ROOT/'docs/audits/2026-09-12/transliteration-map.json')
REGISTRY=read(ROOT/'publication/src/main/resources/org/panchang/publication/disputes.json')

class Links(HTMLParser):
    def __init__(self): super().__init__(); self.links=[]
    def handle_starttag(self,tag,attrs):
        if tag=='a':
            url=dict(attrs).get('href')
            if url: self.links.append(url)

def source_url_identity(url):
    """Match indexed dot-segment/escaped paths, without merging publishers or queries."""
    parsed = urlparse(url)
    return parsed._replace(path=posixpath.normpath(unquote(parsed.path)))

def discovery():
    rows=[]
    measured={source_url_identity(read(p)['source']['url']) for p in (HERE/'measurements').glob('*.json')}
    for year in (2025,2026,2027):
        meta=read(HERE/f'sources/index-{year}.json')
        parser=Links(); parser.feed((ROOT/meta['file']).read_text(encoding='utf-8'))
        urls=sorted(set(urljoin(meta['url'],u) for u in parser.links if urlparse(u).path.lower().endswith('.txt') and f'/{year}/' in u))
        for url in urls:
            rows.append(dict(year=year,printedFileName=unquote(urlparse(url).path.rsplit('/',1)[1]),url=url,
                discoveryEvidence=meta['file'],indexSha256=meta['sha256'],
                status='MEASURED_RESEARCH_ONLY' if source_url_identity(url) in measured else 'LISTED_ONLY_NOT_FETCHED_OR_VALIDATED',
                supported=False,approved=False))
    write(HERE/'discovered-coverage.json',rows)
    return rows

def compare(m):
    year=m['source']['year']; city=m['source']['cityId']
    refs=m['reference']; obs=m['calculated']['ekadashiYear']['observances']
    actual={o['date']:o for o in obs if o['date'].startswith(str(year))}
    expected={r['date']:r for r in refs if r.get('fastingFor')}
    details=[]
    def result(field,count,issues,unavailable=0):
        return dict(field=field,compared=count,status='DISAGREES' if issues else 'PARTIAL_OR_UNAVAILABLE' if unavailable or not count else 'SUFFICIENT_COMPARISON_EVIDENCE_PENDING_REVIEW',disagreements=len(issues),unavailable=unavailable)
    issues=[dict(field='fastingDate',date=d,reference=d if d in expected else None,calculated=d if d in actual else None) for d in sorted(set(actual)^set(expected))]
    details+=issues; fields=[result('fastingDate',len(expected),issues)]
    for field in ('ekadashiName','mahadvadashiType'):
        issues=[]
        for date in sorted(set(actual)|set(expected)):
            a=actual.get(date); r=expected.get(date)
            if field=='ekadashiName':
                av=a['name'].removesuffix('Ekadashi').strip() if a else None
                rv=r['fastingFor'].removesuffix('Ekadasi').strip() if r else None
            else:
                av=a.get('mahadvadashiType') if a else None
                labels=[e for e in r['events'] if 'mahadvadasi' in e.lower()] if r else []
                rv=next((MAP['mahadvadashi'].get(e,'UNMAPPED_REFERENCE_LABEL:'+e) for e in labels),None)
            if av!=rv: issues.append(dict(field=field,date=date,reference=rv,calculated=av))
        fields.append(result(field,len(expected),issues)); details+=issues
    paranas={o['parana']['date']:o['parana'] for o in obs if o.get('parana') and o['parana']['date'].startswith(str(year))}
    refparanas={r['date']:r['parana'] for r in refs if r.get('parana')}
    daily={d['date']:d for d in m['daily']}
    boundrows=[]
    for date in sorted(set(paranas)|set(refparanas)):
        r=refparanas.get(date); a=paranas.get(date)
        for bound in ('start','end'):
            reference=r.get(bound) if r else None
            actualtime=a.get(bound) if a else None
            basis=MAP['paranaBasis'].get(r.get(bound+'Basis')) if r else None
            actualbasis=a.get(bound+'Reason') if a else None
            stale=r is not None and daily[date]['referenceClockMatchesZone'] is False
            delta=None
            if reference and actualtime:
                value=dt.datetime.fromisoformat(actualtime['local'])
                # Keep the printed local clock untouched. DST mismatch is a separate explicit exclusion.
                referencevalue=dt.datetime.fromisoformat(date+'T'+reference).replace(tzinfo=value.tzinfo)
                delta=(value-referencevalue).total_seconds()
            lower=-15 if actualbasis in ('SUNRISE','ONE_THIRD_DAYLIGHT','SUNSET') else -180
            status=('EXTRA_CALCULATED_WINDOW' if r is None else 'REFERENCE_END_UNAVAILABLE' if reference is None else
                    'CALCULATED_WINDOW_MISSING' if actualtime is None else 'STALE_REFERENCE_DST' if stale else
                    'OUTSIDE_EXISTING_BAND' if not lower<=delta<=75 else 'WITHIN_EXISTING_BAND')
            basis_status='UNAVAILABLE' if not basis or not actualbasis else 'MATCH' if basis==actualbasis else 'DISAGREES'
            boundrows.append(dict(field='parana'+bound.title(),date=date,reference=reference,calculated=actualtime,
                referenceBasis=basis,calculatedBasis=actualbasis,basisStatus=basis_status,deltaSeconds=delta,
                bandSeconds=[lower,75],referenceClock=r.get('clock') if r else None,status=status))
    details+=boundrows
    for bound in ('Start','End'):
        rows=[r for r in boundrows if r['field']=='parana'+bound]
        bad=[r for r in rows if r['status'] in ('EXTRA_CALCULATED_WINDOW','CALCULATED_WINDOW_MISSING','OUTSIDE_EXISTING_BAND') or r['basisStatus']=='DISAGREES']
        unavailable=sum(r['status'] in ('REFERENCE_END_UNAVAILABLE','STALE_REFERENCE_DST') for r in rows)
        fields.append(result('parana'+bound,len(rows),bad,unavailable))
    daily_fields=['tithi','paksha','nakshatra','month']
    for field in daily_fields:
        issues=[]
        for r in refs:
            a=daily[r['date']][field]
            if field=='tithi': rv=MAP['tithi'][r['tithi'].split(' (')[0]]
            elif field=='paksha': rv={'G':'SHUKLA','K':'KRISHNA'}[r['paksa']]
            elif field=='nakshatra': rv=MAP['nakshatra'][r['naksatra']]
            elif r['masa']=='Purusottama-adhika': rv=a if a and a.startswith('Adhika ') else 'Adhika expected'
            else: rv=MAP['month'][r['masa'].split(' (')[0]]
            if a!=rv: issues.append(dict(field=field,date=r['date'],reference=rv,calculated=a))
        fields.append(result(field,len(refs),issues)); details+=issues
    # A literal matched event name is a narrow datum, never proof that the entire catalog matches.
    referenceevents=collections.defaultdict(list)
    for r in refs:
        for event in r['events']: referenceevents[event].append(r['date'])
    catalog=[]
    for e in m['calculated']['yearResolution']['events']:
        dates=referenceevents.get(e['name'],[])
        catalog.append(dict(id=e['id'],name=e['name'],date=e['date'],referenceDates=dates,
            status='EXACT_TEXT_AND_DATE_MATCH' if e['date'] in dates else 'EXACT_TEXT_DIFFERENT_DATE' if dates else 'UNMAPPED_TEXT_REQUIRES_CATALOG_REVIEW'))
    fields += [dict(field='festivalCatalog',status='PARTIAL_OR_UNAVAILABLE',compared=sum(x['status']=='EXACT_TEXT_AND_DATE_MATCH' for x in catalog),disagreements=sum(x['status']=='EXACT_TEXT_DIFFERENT_DATE' for x in catalog),unavailable=sum(x['status']=='UNMAPPED_TEXT_REQUIRES_CATALOG_REVIEW' for x in catalog)),
               dict(field='festivalFastEndInstants',status='UNAVAILABLE_NO_CLOCK_TIME_OR_PRECISE_ANCHOR_DEFINITION',compared=0),
               dict(field='sunriseSunsetInstants',status='UNAVAILABLE_AS_SEPARATE_FIELDS_IN_DESIGNATED_TEXT_EXPORT',compared=0)]
    member=m['member']; p=member['place']
    runtime_issues=[x['id'] for x in REGISTRY['disputes'] if all(x['place'][k]==p[k] for k in ('latitude','longitude','elevationMeters','timeZone')) and x['coverage']['from']<=member['coverage']['through'] and x['coverage']['through']>=member['coverage']['from']]
    prior=ROOT/f'verify/golden/vaisnavacalendar-{city}-{year}.json'
    unchanged=read(prior)['provenance'][0]['responseSha256']==m['source']['sha256'] if prior.exists() else None
    obs_fields=fields[:5]
    context_checks=[]
    if year==2026:
        for adjacent in (2025,2027):
            other=read(HERE/f'measurements/{city}-{adjacent}.json')
            same_point=other['member']['place']==member['place']
            context_checks.append(dict(year=adjacent,check='EXACT_POINT_IDENTITY',passed=same_point))
            for o in obs:
                if o['date'].startswith(str(adjacent)):
                    ref=next((r for r in other['reference'] if r['date']==o['date']),None)
                    context_checks.append(dict(year=adjacent,date=o['date'],check='CARRYOVER_FAST_DATE_AND_NAME',passed=bool(ref and ref.get('fastingFor') and ref['fastingFor'].replace('Ekadasi','Ekadashi')==o['name'])))
    return dict(city=city,year=year,site=m['site'],canonicalPlace=p,source=m['source'],
        elevationAssumption=m['elevationAssumption'],versions=member['versions'],resultSha256=member['resultSha256'],
        sameRawBytesAsExistingGolden=unchanged,referenceDayCount=len(refs),referenceFasts=len(expected),calculatedFasts=len(actual),
        observedMahadvadashiTypes=dict(collections.Counter(o.get('mahadvadashiType','ORDINARY') for o in actual.values())),
        fields=fields,details=details,catalog= catalog,runtimeIssueIds=runtime_issues,
        observanceGroupEvidence='SUFFICIENT_COMPARISON_EVIDENCE_PENDING_REVIEW' if all(f['status']=='SUFFICIENT_COMPARISON_EVIDENCE_PENDING_REVIEW' for f in obs_fields) else 'DISAGREES_OR_INCOMPLETE',
        crossYearContext='AVAILABLE_2025_2027' if year==2026 and all(x['passed'] for x in context_checks) else 'INCOMPLETE_OR_DISAGREES',contextChecks=context_checks,
        currentPublication='WITHHELD_NO_QUALIFIED_REVIEW_OR_OWNER_APPROVAL',supported=False,approved=False)

def build():
    measurements=sorted((HERE/'measurements').glob('*.json'))
    if len(measurements)!=45: raise ValueError(f'Expected 45 complete measurements, found {len(measurements)}')
    rows=[compare(read(p)) for p in measurements]
    discovered=discovery()
    write(HERE/'coverage-inventory.json',dict(purpose='RESEARCH_NOT_RELEASE_AUTHORIZATION',lineage='vaisnavacalendar.info/GCal-text',
        sample='15 preselected sites, 2025-2027. Other advertised locations are discovery-only. No geography interpolation.',
        sites=rows,unavailable=[dict(city='reykjavik',year=2026,fields='all observance guidance',status='NO_FILE_IN_COLLECTED_DESIGNATED_INDEXES; astronomy dispute ASTRONOMY-01 remains',supported=False),
                              dict(scope='all other years or exact points',status='NOT_IN_MEASURED_SAMPLE',supported=False)]))
    policy=dict(id='vaisnavacalendar-info-gcal-text',revision='2026-09-16-proposed',
        description='PROPOSED only: compare exact GCal text snapshots at printed coordinates, IANA mappings and assumed zero elevation. Existing solar -15..75s and lunar -180..75s bands; retain clock/basis disputes and missing ends. No replacement of calculated values. Owner and qualified review pending.',
        evidence=[evidence(HERE/'REFERENCE-POLICY.md','PROPOSED_POLICY_NOT_APPROVED'),evidence(HERE/'coverage-inventory.json','MEASURED_COMPARISONS_WITH_UNRESOLVED_CASES')])
    write(HERE/'reference-policy.proposed.json',policy)
    proposals=[]
    for row in rows:
        if row['year']!=2026: continue
        city=row['city']; m=read(HERE/f'measurements/{city}-2026.json')
        req=dict(members=[dict(place=m['member']['place'],tradition='iskcon',year=2026,coverage=m['member']['coverage'],fields=['OBSERVANCES'])],referencePolicy=policy,
            evidence=[evidence(HERE/f'measurements/{city}-2026.json','MEASURED_NOT_APPROVED'),*[
                evidence(ROOT/read(HERE/f'sources/calendar-{city}-{y}.json')['file'],'DESIGNATED_REFERENCE_CONTEXT_NOT_APPROVAL') for y in (2025,2026,2027)]])
        tier='PRIORITY_QUALIFIED_REVIEW' if row['observanceGroupEvidence'].startswith('SUFFICIENT') and row['crossYearContext']=='AVAILABLE_2025_2027' else 'HOLD_DISAGREEMENTS_OR_MISSING_FIELDS'
        write(HERE/f'proposals/{city}-2026.request.json',req)
        proposals.append(dict(city=city,year=2026,fields=['OBSERVANCES'],tier=tier,request=f'proposals/{city}-2026.request.json',approved=False,
            evidenceStatus=row['observanceGroupEvidence'],openRuntimeCases=row['runtimeIssueIds'],
            qualification='Evidence triage only. Runtime review questions, exact artifact rebuild and owner approval still apply. No EVENTS or daily-astronomy approval is proposed.'))
    write(HERE/'proposed-bundles.json',proposals)
    for name,tier in [('priority','PRIORITY_QUALIFIED_REVIEW'),('hold','HOLD_DISAGREEMENTS_OR_MISSING_FIELDS')]:
        requests=[read(HERE/p['request']) for p in proposals if p['tier']==tier]
        if requests:
            combined=dict(members=[m for r in requests for m in r['members']],referencePolicy=policy,
                evidence=list({(e['link'],e['sha256']):e for r in requests for e in r['evidence']}.values()))
            write(HERE/f'proposals/{name}.request.json',combined)
    lines=['# ISKCON coverage inventory','', 'Research sample begun 16 September and evaluated 20 September 2026. Every row remains withheld.',
           'S = sufficient comparison evidence for a scoped human review under the proposed bands; D = disagreement; P = partial/unavailable. S never means approved.',
           '', '| Location | Year | Fast date | Name | Type | Parana start | Parana end | Tithi | Paksha | Nakshatra | Month | Context |',
           '|---|---:|---|---|---|---|---|---|---|---|---|---|']
    for row in rows:
        codes=['S' if f['status'].startswith('SUFFICIENT') else 'D' if f['status']=='DISAGREES' else 'P' for f in row['fields'][:9]]
        lines.append('| '+row['city']+' | '+str(row['year'])+' | '+' | '.join(codes)+' | '+('adjacent files' if row['year']==2026 else 'incomplete')+' |')
    lines+=['','Festival catalogs are only partially mapped. Exact festival fast-end instants and standalone sunrise/sunset times lack a complete comparator in these text exports.',
            'Daily labels are measured for research; the public schema does not support DAILY_ASTRONOMY.',
            'Elevation is not printed: every comparison assumes 0 m. Coordinates are the artifact points, not modern city-center/GeoNames coordinates.',
            'Reykjavik has no file in the collected indexes. Its separate sunrise disagreement remains unresolved.',
            '', '## Proposed 2026 observance review bundles','', '| Location | Triage |', '|---|---|']
    lines += [f"| {p['city']} | {p['tier']} |" for p in proposals]
    lines+=['',f"Discovery inventory: {len(discovered)} distinct year/file links across the three saved indexes. All unmeasured links are unavailable for release.",
            'No 2025/2027 release is proposed: their outside-year boundary context is incomplete. No new point or year becomes supported because it was measured.',
            '', 'Exact source URLs, hashes, retrieval times, versions, coordinates, IANA assumptions, per-field counts and disagreements: `coverage-inventory.json`.',
            'Exact reviewed-result fingerprints are prepared under `bundles/`; they contain no reviewer decision, signature or approval.', '']
    (HERE/'COVERAGE.md').write_text('\n'.join(lines),encoding='utf-8')
    print('measured',len(rows),'indexed',len(discovered),'proposals',collections.Counter(p['tier'] for p in proposals))

if __name__=='__main__': build()
