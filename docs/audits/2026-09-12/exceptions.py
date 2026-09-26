import json,pathlib,collections
P=pathlib.Path(__file__).resolve().parent
def read(n):return json.loads((P/n).read_text(encoding='utf-8-sig'))
def cell(v):return str(v).replace('|','/').replace('\n',' ')
lines=['# Exceptions and exclusions — 2026-09-12','', 'Generated from audit JSON. Missing data and intentional exclusions are not passes. See AUDIT.md for rule interpretation.','']
def table(headers,rows):
    lines.extend(['| '+' | '.join(headers)+' |','|'+'|'.join(['---']*len(headers))+'|'])
    lines.extend('| '+' | '.join(cell(x) for x in row)+' |' for row in rows)
    lines.append('')
lines+=['## Existing test failures','']
table(['Module','Test','Failure'],[(r['module'],r['test'],r['message']) for r in read('baseline-failures.json')])
lines+=['## Tag-excluded existing network methods','', 'These seven methods were not run. They are excluded by Gradle, not reported as JUnit skips. Deliberate source fetches and stored conformance tests were run instead.','']
for name in ['JPL Horizons still returns the committed regression anchor','USNO still answers for a grid city','USNO still reports polar day in the shape we parse','vaisnavacalendar text export still parses cleanly','every grid city still has a published calendar for the current year','ISKCON Mumbai still publishes a parseable Ekadasi notice','drikpanchang day panchang still exposes the rows we read']:
    lines.append('- `LiveSourceTest`: '+name)
lines+=['','## Every stale-DST timing exclusion','', 'Only the clock comparisons are excluded. Dates, names, and labels remain scored. Raw timing residuals remain in parana-results.json.','']
p=read('parana-results.json')
table(['City','Date','Start raw delta (s)','End raw delta (s)'],[(c,d,next(r['deltaSeconds'] for r in p if r['city']==c and r['date']==d and r.get('bound')=='start'),next(r['deltaSeconds'] for r in p if r['city']==c and r['date']==d and r.get('bound')=='end')) for c,d in sorted({(r['city'],r['date']) for r in p if r.get('staleReferenceDst')})])
lines+=['## Missing, uncapped and extra Parana rows','', 'A missing emitted start is not scored using a re-derived internal Hari Vasara value.','']
table(['City','Date','Bound','Reference','State'],[(r['city'],r['date'],r.get('bound','window'),r.get('reference'),r['status']) for r in p if r['status'] not in ('compared','excluded_stale_reference_dst')])
lines+=['## Fasting date and Mahadvadashi disagreements','']
table(['City','Reference date','Actual date','Reference type','Actual type','Confidence'],[(r['city'],r['referenceDate'],r['actualDate'],r['referenceType'],r['actualType'],r['confidence']) for r in read('fasting-results.json') if not r['dateMatch'] or not r['labelMatch']])
lines+=['## Parana basis disagreements and non-DST timing failures','']
table(['City','Date','Bound','Reference basis','Actual basis','Delta (s)'],[(r['city'],r['date'],r.get('bound'),r.get('referenceBasis'),r.get('actualBasis'),r.get('deltaSeconds')) for r in p if r.get('basisMatch') is False or (r.get('insideExistingCompatibilityBand') is False and not r.get('staleReferenceDst'))])
lines+=['## Every daily field disagreement','', 'Seconds below describe the actual boundary before the actual sunrise, not a measured reference boundary error. Reference boundary instants and approved sidereal configuration are absent.','']
table(['City/date','Reference / actual tithi','Reference / actual nakshatra','Tithi start before sunrise (s)','Nakshatra start before sunrise (s)'],[(r['comparison']['city']+' '+r['comparison']['date'],r['comparison']['referenceTithi']+' / '+r['comparison']['actualTithi'],r['comparison']['referenceNakshatra']+' / '+r['comparison']['actualNakshatra'],round(r['sunriseMinusTithiStartSeconds'],3),round(r['sunriseMinusNakshatraStartSeconds'],3)) for r in read('daily-boundary-diagnostics.json')])
lines+=['## Astronomy without a scalar timing comparison','', 'No event and polar conditions are categorical evidence. Multiple reference events cannot be represented by a single event field. These rows are retained without an invented error.','']
a=read('astronomy-comparisons.json')
table(['Source file','Date','Field','Reference events / condition','Actual'],[(pathlib.PureWindowsPath(r['source']).name,r['date'],r['kind'],str(r.get('referenceEvents'))+' / '+str(r.get('referenceCondition')),r['actual']) for r in a if r.get('status')=='categorical_or_unavailable'])
lines+=['## New numerical screening failure','']
table(['Date/site','Reference','Actual','Delta (s)','Outcome'],[(r['date']+f" {r['latitude']},{r['longitude']}",r['reference'],r['actual'],r['deltaSeconds'],'Unresolved; unchanged at 6/12/24 refinement passes') for r in a if abs(r.get('deltaSeconds') or 0)>30])
lines+=['## Every unavailable fetch attempt','', 'Later successful retries remain separate rows in the manifests. A recovered request does not erase its failed attempt. Supplementary ids sometimes retain the original date; actual request parameters are shown here.','']
f=[r for n in ['fetch-manifest.json','supplement-manifest.json','retry-manifest.json'] for r in read(n)]
table(['Id','Actual requested date/year','HTTP','Error'],[(r['id'],r['parameters'].get('date',r['parameters'].get('year','')),r['httpStatus'],r.get('error') or 'HTTP error response retained') for r in f if r['httpStatus']!=200])
lines+=['## Parse omissions','', 'No successful numerical/calendar response had unparsed lines or parser warnings. The rules PDF was not retrieved and the documentation pages were not treated as numerical fixtures.','', '## Coverage not available','', '- 2028 Mayapur and Hyderabad observance calendars (404); no replacement publisher used.', '- Unrecovered originally requested USNO dates: Cape Town 2027-01-01; Singapore 2027-12-31; Reykjavik 2028-06-21; London 2028-10-29; New York 2027-03-14 and 2028-11-05. Some other dates at the same locations were fetched and scored separately.', '- Regional festival-calendar references; rare Jaya/Jayanti/Papanasini positives; approved Unmilani and nakshatra qualifiers; high-latitude observance oracles.', '- Independent yoga/pada/ayanamsha/numeric day-division references; complete lunar geometry/elevation/atmospheric coverage; full 2027/2028 position envelopes.', '- Human approvals, deployed service validation, production stored-result readback and a persistent shared calculation cache.', '', 'All named pandit questions, decision-boundary uncertainty, and publication blockers are carried in AUDIT.md.']
(P/'EXCEPTIONS.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
