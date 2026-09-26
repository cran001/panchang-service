"""Offline audit statistics. Standard library only; never supplies expected values to production."""
import json, math, statistics, pathlib, collections, datetime as dt, xml.etree.ElementTree as ET, hashlib
P=pathlib.Path(__file__).resolve().parent
ROOT=P.parents[2]
def read(name): return json.loads((P/name).read_text(encoding='utf-8-sig'))
def write(name, value): (P/name).write_text(json.dumps(value,indent=2,ensure_ascii=False),encoding='utf-8')
def stats(values):
    a=sorted(abs(x) for x in values)
    return dict(n=len(a),medianAbs=statistics.median(a),p95Abs=a[math.ceil(.95*len(a))-1],maxAbs=a[-1],meanSigned=statistics.mean(values),rms=math.sqrt(statistics.mean(x*x for x in values))) if a else dict(n=0)
f=read('fasting-comparisons.json'); p=read('parana-comparisons.json'); a=read('astronomy-comparisons.json'); daily=read('daily-field-comparisons.json')
label_map={'Paksa vardhini Mahadvadasi':'PAKSAVARDHINI','Trisprsa Mahadvadasi':'TRISPRSA','Vyanjuli Mahadvadasi':'VANJULI','Unmilani Mahadvadasi':'UNMILANI','Jaya Mahadvadasi':'JAYA','Vijaya Mahadvadasi':'VIJAYA','Jayanti Mahadvadasi':'JAYANTI','Papanasini Mahadvadasi':'PAPANASINI'}
basis_map={'sunrise':'SUNRISE','1/4 of tithi':'HARI_VASARA_END','end of tithi':'DVADASHI_END','1/3 of daylight':'ONE_THIRD_DAYLIGHT','end of naksatra':'NAKSHATRA_END'}
for r in f:
    r['referenceType']=next((label_map[s] for s in r['referenceLabels'] if s in label_map),None)
    r['labelMatch']=r['referenceType']==r['actualType']
    r['nameMatch']=r['referenceName'].replace('Ekadasi','Ekadashi')==r['actualName']
for r in p:
    if r.get('actualBasis') is not None:
        r['basisMatch']=basis_map.get(r.get('referenceBasis'))==r['actualBasis']
        lower=-15 if r['actualBasis'] in ('SUNRISE','ONE_THIRD_DAYLIGHT','SUNSET') else -180
        r['insideExistingCompatibilityBand']=lower<=r['deltaSeconds']<=75 if r['deltaSeconds'] is not None else None
write('fasting-results.json',f);write('parana-results.json',p)
summary={'method':'absolute residual to published value; median conventional; p95 nearest rank; seconds unless arcseconds named', 'fasting':{},'parana':{},'astronomy':{},'dailyFields':{}}
for year in sorted({r['year'] for r in f}):
    rows=[r for r in f if r['year']==year]
    summary['fasting'][str(year)]={'n':len(rows),'dateMatches':sum(r['dateMatch'] for r in rows),'labelMatches':sum(r['labelMatch'] for r in rows),'nameMatches':sum(r['nameMatch'] for r in rows),'sites':sorted({r['city'] for r in rows}),'mismatches':[r for r in rows if not r['dateMatch'] or not r['labelMatch'] or not r['nameMatch']]}
    for bound in ['start','end']:
        subset=[r for r in p if r['year']==year and r.get('bound')==bound and r.get('deltaSeconds') is not None]
        summary['parana'][f'{year}/{bound}/raw-including-stale-dst']=stats([r['deltaSeconds'] for r in subset])
        summary['parana'][f'{year}/{bound}/excluding-stale-dst']=stats([r['deltaSeconds'] for r in subset if not r['staleReferenceDst']])
summary['parana']['excludedOrUnavailable']=[r for r in p if r['status']!='compared']
summary['parana']['basisDisagreements']=[r for r in p if r.get('basisMatch') is False]
summary['parana']['outsideExistingBands']=[r for r in p if r.get('insideExistingCompatibilityBand') is False and not r.get('staleReferenceDst')]
for scope, rows in [('stored',[r for r in a if r['source'].startswith('verify')]),('historical-csv',[r for r in a if 'measurements' in r['source']]),('new',[r for r in a if 'new-astronomy' in r['source']])]:
    for kind in sorted({r['kind'] for r in rows}):
        group=[r for r in rows if r['kind']==kind]
        if kind=='longitude':
            for body in ['sun','moon']:
                for year in sorted({r['utc'][:4] for r in group}):
                    subset=[r for r in group if r['body']==body and r['utc'].startswith(year)]
                    summary['astronomy'][f'{scope}/{body}/{year}/longitudeArcsec']=stats([r['deltaArcsec'] for r in subset])
        else: summary['astronomy'][f'{scope}/{kind}/seconds']=stats([r['deltaSeconds'] for r in group if r['deltaSeconds'] is not None])
summary['astronomy']['beyond30Seconds']=[r for r in a if abs(r.get('deltaSeconds') or 0)>30]
summary['astronomy']['unavailableOrCategorical']=[r for r in a if r.get('status')=='categorical_or_unavailable']
tithi=dict(zip(['Pratipat','Dvitiya','Tritiya','Caturthi','Pancami','Sasti','Saptami','Astami','Navami','Dasami','Ekadasi','Dvadasi','Trayodasi','Caturdasi','Purnima','Amavasya'],['Pratipada','Dvitiya','Tritiya','Chaturthi','Panchami','Shashthi','Saptami','Ashtami','Navami','Dashami','Ekadashi','Dvadashi','Trayodashi','Chaturdashi','Purnima','Amavasya']))
nak=dict(zip(['Asvini','Bharani','Krittika','Rohini','Mrigasira','Ardra','Punarvasu','Pusyami','Aslesa','Magha','Purva-phalguni','Uttara-phalguni','Hasta','Citra','Swati','Visakha','Anuradha','Jyestha','Mula','Purva-asadha','Uttara-asadha','Sravana','Dhanista','Satabhisa','Purva-bhadra','Uttara-bhadra','Revati'],['Ashvini','Bharani','Krittika','Rohini','Mrigashira','Ardra','Punarvasu','Pushya','Ashlesha','Magha','Purva Phalguni','Uttara Phalguni','Hasta','Chitra','Svati','Vishakha','Anuradha','Jyeshtha','Mula','Purva Ashadha','Uttara Ashadha','Shravana','Dhanishta','Shatabhisha','Purva Bhadrapada','Uttara Bhadrapada','Revati']))
months=dict(zip(['Caitra','Vaisakha','Jyestha','Asadha','Sravana','Bhadra','Asvina','Kartika','Margasirsa','Pausa','Magha','Phalguna'],['Chaitra','Vaishakha','Jyeshtha','Ashadha','Shravana','Bhadrapada','Ashvina','Kartika','Margashirsha','Pausha','Magha','Phalguna']))
weekday=dict(zip(['Mo','Tu','We','Th','Fr','Sa','Su'],['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY']))
for r in daily:
    r['tithiMatch']=tithi[r['referenceTithi'].split(' (')[0]]==r['actualTithi']
    r['nakshatraMatch']=nak[r['referenceNakshatra']]==r['actualNakshatra']
    r['pakshaMatch']={'G':'SHUKLA','K':'KRISHNA'}[r['referencePaksa']]==r['actualPaksa']
    r['weekdayMatch']=weekday[r['weekdayReference']]==r['weekdayActual']
    r['monthMatch']=(r['actualPurnimantaMonth'].startswith('Adhika ') if r['referenceMasa']=='Purusottama-adhika' else months[r['referenceMasa'].split(' (')[0]]==r['actualPurnimantaMonth'])
for field in ['tithi','nakshatra','paksha','weekday','month']:
    for year in [2026,2027]:
        group=[r for r in daily if r['year']==year]
        summary['dailyFields'][f'{year}/{field}']={'n':len(group),'matches':sum(r[field+'Match'] for r in group),'mismatches':[r for r in group if not r[field+'Match']]}
write('daily-field-disagreements.json',[r for r in daily if not all(r[k+'Match'] for k in ['tithi','nakshatra','paksha','weekday','month'])])
write('transliteration-map.json',{'tithi':tithi,'nakshatra':nak,'month':months,'weekday':weekday,'mahadvadashi':label_map,'paranaBasis':basis_map})
failures=[]
for path in (P/'baseline-xml').glob('*/*.xml'):
    suite=ET.parse(path).getroot()
    for case in suite.findall('testcase'):
        for failure in case.findall('failure')+case.findall('error')+case.findall('skipped'):
            failures.append(dict(module=path.parent.name,classname=case.attrib['classname'],test=case.attrib['name'],kind=failure.tag,message=failure.attrib.get('message'),details=failure.text))
write('baseline-failures.json',failures)
# Independent crossing interpolation uses reference longitudes only. No service value participates.
crossings=[]
for label,target in [('vrindavan',180),('auckland',324)]:
    moon=read(f'new-astronomy/horizons-{label}-boundary-moon.json')['records'];sun=read(f'new-astronomy/horizons-{label}-boundary-sun.json')['records']
    times=[dt.datetime.fromisoformat(m['utc'].replace('Z','+00:00')).timestamp() for m in moon]
    values=[(m['apparentEclipticLongitudeDeg']-s['apparentEclipticLongitudeDeg'])%360-target for m,s in zip(moon,sun)]
    i=next(i for i in range(len(values)-1) if values[i]<=0<values[i+1])
    linear=times[i]-values[i]*(times[i+1]-times[i])/(values[i+1]-values[i])
    # quadratic Lagrange interpolation at three adjacent samples; bracketed bisection
    ids=list(range(max(0,min(i-1,len(times)-3)),max(0,min(i-1,len(times)-3))+3))
    def poly(t):
        return sum(values[j]*math.prod((t-times[k])/(times[j]-times[k]) for k in ids if k!=j) for j in ids)
    lo,hi=times[i],times[i+1]
    for _ in range(45):
        mid=(lo+hi)/2
        if poly(mid)>0: hi=mid
        else: lo=mid
    root=(lo+hi)/2
    diagnostic=next(r for r in read('rule-diagnostics.json') if r['city']==label and r['date']=={'vrindavan':'2026-06-30','auckland':'2026-10-08'}[label])
    span=next(s for s in diagnostic['spans'] if s['index']=={'vrindavan':14,'auckland':26}[label])
    actual=dt.datetime.fromisoformat(span['end'].split('[')[0]).timestamp(); sunrise=dt.datetime.fromisoformat(diagnostic['sunrise'].split('[')[0]).timestamp()
    crossings.append(dict(site=label,referenceSource=f'new-astronomy/horizons-{label}-boundary-*.json',targetElongationDegrees=target,referenceUtc=dt.datetime.fromtimestamp(root,dt.timezone.utc).isoformat(),linearVsQuadraticSeconds=linear-root,serviceBoundary=span['end'],serviceMinusReferenceSeconds=actual-root,serviceSunrise=diagnostic['sunrise'],referenceBoundaryMinusServiceSunriseSeconds=root-sunrise,uncertaintyNote='Interpolation consistency is not total uncertainty. USNO minute rounding cannot resolve the associated sunrise ordering.'))
write('independent-boundaries.json',crossings)
write('summary.json',summary)
print('Fasting:',[(k,v['n'],v['dateMatches'],v['labelMatches']) for k,v in summary['fasting'].items()])
print('Parana:',{k:v for k,v in summary['parana'].items() if isinstance(v,dict)})
print('Astronomy:',{k:v for k,v in summary['astronomy'].items() if isinstance(v,dict)})
print('Daily:',{k:(v['n'],v['matches']) for k,v in summary['dailyFields'].items()})
print('Boundaries:',crossings)
