"""Transcribe maintained runtime cases from saved evidence, without choosing religious answers."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
AUDIT = ROOT / 'docs/audits/2026-09-12'
rows = json.loads((AUDIT / 'fasting-results.json').read_text())
places = {r['city']: dict(id='audit:' + r['city'], latitude=r['latitude'], longitude=r['longitude'],
                        elevationMeters=0.0, timeZone=r['zone']) for r in rows}
places['reykjavik'] = dict(id='audit:reykjavik', latitude=64.1466, longitude=-21.9426,
                          elevationMeters=0.0, timeZone='Atlantic/Reykjavik')
items = []

def add(id, city, start, end, fields, details, why, files, state='DISPUTED'):
    evidence = [dict(link='docs/audits/2026-09-12/' + f,
                     sha256=hashlib.sha256((AUDIT / f).read_bytes()).hexdigest(),
                     outcome='UNRESOLVED_AUDIT_EVIDENCE') for f in files]
    items.append(dict(id=id, place=places[city], tradition='iskcon', coverage=dict(from_=start, through=end),
                      fields=fields, affectedDetails=details,
                      observedRevision='2026-09-12-dirty-audit-and-repair-handoff',
                      versionApplicability='OBSERVED_AND_UNREVIEWED_SUCCESSORS', state=state,
                      explanation=why, evidence=evidence))
    items[-1]['coverage']['from'] = items[-1]['coverage'].pop('from_')

add('OBSERVANCE-01', 'vrindavan', '2026-06-25', '2026-06-27', ['OBSERVANCES'],
    ['fastingDate', 'mahadvadashiType', 'parana'], 'Nirjala fasting date, classification and Parana disagree; sunrise ordering and reference policy remain unresolved.', ['fasting-results.json', 'independent-boundaries.json'])
add('OBSERVANCE-02', 'auckland', '2026-10-07', '2026-10-08', ['OBSERVANCES'],
    ['mahadvadashiType', 'parana.end', 'parana.endReason'], 'October Parana cap and Trisprsa classification disagree; no religious resolution is recorded.', ['parana-results.json', 'independent-boundaries.json'])
for city, date in [('moscow', '2026-05-27'), ('sydney', '2026-12-05')]:
    add('OBSERVANCE-03-' + city, city, date, date, ['OBSERVANCES'], ['mahadvadashiType'],
        'Vanjuli versus unnamed deferral; matching fasting dates do not resolve the classification.', ['fasting-results.json', 'rule-diagnostics.json'])
add('AUCKLAND-APRIL-BASIS', 'auckland', '2026-04-27', '2026-04-28', ['OBSERVANCES'], ['parana.startReason'],
    'Hari Vasara versus sunrise basis is unresolved even though the printed minute agrees.', ['parana-results.json'])
add('AHMEDABAD-NOVEMBER-BASIS', 'ahmedabad', '2026-11-05', '2026-11-06', ['OBSERVANCES'], ['parana.endReason'],
    'Tithi end and daylight third differ by less than one second; approved boundary policy is missing.', ['parana-results.json'])
for city, start, end in [('mumbai','2026-08-23','2026-08-24'), ('auckland','2026-09-22','2026-09-23'), ('new-york','2026-10-21','2026-10-22')]:
    add('OPEN-ENDED-' + city, city, start, end, ['OBSERVANCES'], ['parana'],
        'Reference prints a start only; service refuses the window. Open-ended guidance versus withholding requires qualified review.', ['parana-results.json'])
add('ASTRONOMY-01', 'reykjavik', '2026-06-27', '2026-06-27', ['DAILY_ASTRONOMY', 'OBSERVANCES', 'EVENTS'],
    ['sunrise', 'sunriseDependentGuidance'], 'Separate sunrise disagreement: -32.171 seconds versus the unchanged 30-second USNO rounding band; daylight repair did not settle it.', ['reykjavik-sunrise-sensitivity.json'])
add('REYKJAVIK-MULTIPLE-MOONSETS', 'reykjavik', '2026-06-26', '2026-06-26', ['DAILY_ASTRONOMY'], ['moonset'],
    'Reference has two moonsets; the scalar field cannot represent both.', ['astronomy-comparisons.json'], 'UNSUPPORTED')
daily = [('ahmedabad','2026-11-20','nakshatra'), ('auckland','2026-04-06','nakshatra'),
         ('auckland','2026-06-11','tithi'), ('auckland','2026-10-08','tithi'),
         ('bangalore','2026-08-13','nakshatra'), ('guwahati','2026-05-25','tithi'),
         ('london','2026-08-11','nakshatra'), ('mayapur','2026-11-22','nakshatra'),
         ('new-york','2026-03-26','nakshatra'), ('vrindavan','2026-03-13','tithi'),
         ('vrindavan','2026-06-30','tithi'), ('hyderabad','2027-09-29','nakshatra')]
for city, date, field in daily:
    add('DAILY-' + city + '-' + date, city, date, date, ['DAILY_ASTRONOMY'], [field],
        'Daily boundary label disagrees; reference instants and approved sidereal configuration are absent.', ['EXCEPTIONS.md'])
# Stale-DST rows remain scoped evidence-policy gaps, including dates excluded from old timing scores.
text = (AUDIT / 'EXCEPTIONS.md').read_text()
section = text.split('## Every stale-DST timing exclusion')[1].split('## Missing,')[0]
for line in section.splitlines():
    if line.startswith('| moscow |') or line.startswith('| sao-paulo |'):
        _, city, date, *_ = [part.strip() for part in line.split('|')]
        add('REFERENCE-DST-' + city + '-' + date, city, date, date, ['OBSERVANCES'], ['parana.referenceClock'],
            'Reference clock was excluded for stale DST; exclusion is not a validation pass or approved reference policy.', ['EXCEPTIONS.md'], 'MISSING_EVIDENCE')
target = ROOT / 'publication/src/main/resources/org/panchang/publication/disputes.json'
target.parent.mkdir(parents=True, exist_ok=True)
questions = []
audit_evidence = [dict(link='docs/audits/2026-09-12/AUDIT.md', sha256=hashlib.sha256((AUDIT/'AUDIT.md').read_bytes()).hexdigest(), outcome='UNRESOLVED_REVIEW_QUESTIONS')]
for id, fields, description in [
    ('RARE-NAKSHATRA-QUALIFIERS', ['OBSERVANCES'], 'Jaya, Jayanti and Papanasini lack positive coverage; next-sunrise survival qualifiers remain inferred.'),
    ('UNMILANI-PURITY-NAMING', ['OBSERVANCES'], 'Unmilani purity and naming qualifiers require qualified tradition review.'),
    ('FAST-ENDING-ANCHORS', ['EVENTS'], 'Dusk versus civil twilight, midnight versus Nisita and interval-edge choice need an approved policy.'),
    ('CATALOG-INTERPRETATIONS', ['EVENTS'], 'Ramanuja tithi versus regional nakshatra reckoning and Damodara closing labels remain human review questions.'),
    ('HIGH-LATITUDE-OBSERVANCE', ['EVENTS','OBSERVANCES'], 'Assess whether this exact release scope uses high-latitude conditions lacking an authoritative observance oracle; record not-applicable reasoning when appropriate.'),
    ('REFERENCE-COVERAGE', ['EVENTS','OBSERVANCES'], 'Review actual reference year/location coverage, missing 2028 calendars, uncertainty and exclusions; passing tests do not fill gaps.'),
]:
    questions.append(dict(id=id, traditions=['iskcon'], fields=fields, description=description, evidence=audit_evidence))
questions.append(dict(id='REGIONAL-CATALOG-REVIEW', traditions=['marathi','telugu','kannada','gujarati','northindian','tamil','malayalam','bengali','odia'],
    fields=['EVENTS'], description='Regional calendars lack independent conformance; kshaya fallback, evening ownership, solar month openings, omissions and year-cycle semantics require qualified review for the exact release.', evidence=audit_evidence))
target.write_text(json.dumps(dict(revision='2026-09-13.2', disputes=items, reviewQuestions=questions), indent=2) + '\n', encoding='utf-8')
print(f'{len(items)} scoped unresolved cases transcribed; no resolution or approval created')
