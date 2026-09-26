# XML-derived verification

Each row is a separate run. Do not add the rows together.

| Run | Tests | Passed | Failed | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| baseline | 643 | 634 | 9 | 0 | 0 |
| full-normal-final | 672 | 663 | 9 | 0 | 0 |
| targeted-final | 83 | 83 | 0 | 0 | 0 |
| targeted-initial | 79 | 79 | 0 | 0 | 0 |
| targeted-policy-and-delivery | 116 | 113 | 3 | 0 | 0 |

## Exact commands

### baseline

```powershell
.\gradlew.bat test --rerun-tasks --continue --console=plain
```

### full-normal-final

```powershell
.\gradlew.bat test --rerun-tasks --continue --console=plain
```

### targeted-final

```powershell
.\gradlew.bat :publication:test :api:test --tests *PublicationControlsApiTest --tests *DeliveryFrontDoorTest :publish:test :core:test --tests *DaylightIntervalTest :calc:test --tests *YearCarryoverTest :sampradaya:test --tests *DaylightDeliveryTest --tests *ReykjavikParanaTest --tests *ParanaDaylightEdgeTest --continue --console=plain
```

### targeted-initial

```powershell
.\gradlew.bat :publication:test :api:test :publish:test --continue --console=plain
```

### targeted-policy-and-delivery

```powershell
.\gradlew.bat :publication:test :api:test :publish:test :core:test --tests *DaylightIntervalTest :calc:test --tests *YearCarryoverTest :sampradaya:test --tests *DaylightDeliveryTest --tests *ReykjavikParanaTest --tests *ParanaDaylightEdgeTest --continue --console=plain
```

### Distribution and real local smoke checks

```powershell
.\gradlew.bat :api:installDist :publish:installDist :publication:installDist --console=plain
python docs/publication/2026-09-13/http_smoke.py
```

The distributions built successfully. Real loopback HTTP day/year responses for Mayapur and
Reykjavik matched the structured files. Unapproved timings were withheld, legacy files were
absent, the anonymous approval route returned 404, and the TEST ONLY review bundle was
prepared without creating approval. The server was stopped after verification.

Exact Java commands, local binding and requests are saved in `http-and-files/commands.json`
and `http-and-files/checks.json`. These checks are separate from JUnit counts. The script
refuses to overwrite its existing evidence directory; use a fresh evidence location to rerun.

## Full normal failure comparison

Identical failure names and messages to fresh baseline: **True**.

Remaining failures:

- `org.panchang.sampradaya.GaudiyaEkadashiConformanceTest :: vrindavan` — org.opentest4j.AssertionFailedError: parana disagreements at vrindavan: 2026-06-27: calendar prints a parana window, rules produced none 2026-06-26: rules produced a parana window the calendar does not print ==> expected: <true> but was: <false>
- `org.panchang.sampradaya.GaudiyaEkadashiConformanceTest :: auckland` — org.opentest4j.AssertionFailedError: parana disagreements at auckland: 2026-04-28 start basis: calendar '1/4 of tithi' (HARI_VASARA_END), rules SUNRISE 2026-10-08 end basis: calendar 'end of tithi' (DVADASHI_END), rules ONE_THIRD_DAYLIGHT 2026-10-08 end (ONE_THIRD_DAYLIGHT): calendar 06:47, rules 11:01:42, delta +254.71 min, outside -0.25..1.25 ==> expected: <true> but was: <false>
- `org.panchang.sampradaya.GaudiyaEkadashiConformanceTest :: vrindavan` — org.opentest4j.AssertionFailedError: Ekadashi fasting dates disagree with the vrindavan 2026 calendar. Missing: [2026-06-26]. Extra: [2026-06-25]. A fasting-date disagreement is a far stronger signal than a label disagreement and must be investigated as a rule error first; see docs/validation-multisite.md. ==> expected: <[2026-01-14, 2026-01-29, 2026-02-13, 2026-02-27, 2026-03-15, 2026-03-29, 2026-04-13, 2026-04-27, 2026-05-13, 2026-05-27, 2026-06-11, 2026-06-26, 2026-07-11, 2026-07-25, 2026-08-09, 2026-08-24, 2026-09-07, 2026-09-22, 2026-10-06, 2026-10-22, 2026-11-05, 2026-11-21, 2026-12-04, 2026-12-20]> but was: <[2026-01-14, 2026-01-29, 2026-02-13, 2026-02-27, 2026-03-15, 2026-03-29, 2026-04-13, 2026-04-27, 2026-05-13, 2026-05-27, 2026-06-11, 2026-06-25, 2026-07-11, 2026-07-25, 2026-08-09, 2026-08-24, 2026-09-07, 2026-09-22, 2026-10-06, 2026-10-22, 2026-11-05, 2026-11-21, 2026-12-04, 2026-12-20]>
- `org.panchang.sampradaya.GaudiyaEkadashiConformanceTest :: vrindavan` — org.opentest4j.AssertionFailedError: Mahadvadashi classification disagrees with the vrindavan 2026 calendar. A *label* disagreement on a fortnight whose fasting date still matches is the outcome ADR 0002 predicts from the reference's ephemeris (docs/decisions/0002-reference-calendar-disagreement.md); a disagreement that also moves the fasting date is not, and is a rule error until shown otherwise. ==> expected: <{2026-06-26=PAKSAVARDHINI, 2026-08-24=VANJULI}> but was: <{2026-08-24=VANJULI}>
- `org.panchang.sampradaya.GaudiyaEkadashiConformanceTest :: moscow` — org.opentest4j.AssertionFailedError: Mahadvadashi classification disagrees with the moscow 2026 calendar. A *label* disagreement on a fortnight whose fasting date still matches is the outcome ADR 0002 predicts from the reference's ephemeris (docs/decisions/0002-reference-calendar-disagreement.md); a disagreement that also moves the fasting date is not, and is a rule error until shown otherwise. ==> expected: <{}> but was: <{2026-05-27=VANJULI}>
- `org.panchang.sampradaya.GaudiyaEkadashiConformanceTest :: auckland` — org.opentest4j.AssertionFailedError: Mahadvadashi classification disagrees with the auckland 2026 calendar. A *label* disagreement on a fortnight whose fasting date still matches is the outcome ADR 0002 predicts from the reference's ephemeris (docs/decisions/0002-reference-calendar-disagreement.md); a disagreement that also moves the fasting date is not, and is a rule error until shown otherwise. ==> expected: <{2026-04-14=TRISPRSA}> but was: <{2026-04-14=TRISPRSA, 2026-10-07=TRISPRSA}>
- `org.panchang.sampradaya.GaudiyaEkadashiConformanceTest :: sydney` — org.opentest4j.AssertionFailedError: Mahadvadashi classification disagrees with the sydney 2026 calendar. A *label* disagreement on a fortnight whose fasting date still matches is the outcome ADR 0002 predicts from the reference's ephemeris (docs/decisions/0002-reference-calendar-disagreement.md); a disagreement that also moves the fasting date is not, and is a rule error until shown otherwise. ==> expected: <{2026-01-15=PAKSAVARDHINI, 2026-07-11=TRISPRSA}> but was: <{2026-01-15=PAKSAVARDHINI, 2026-07-11=TRISPRSA, 2026-12-05=VANJULI}>
- `org.panchang.sampradaya.GaudiyaEkadashiConformanceTest :: moscow` — org.opentest4j.AssertionFailedError: deferral classification at moscow: 2026-05-27: an unnamed deferral was promoted to VANJULI; the calendar prints no Mahadvadashi line here ==> expected: <true> but was: <false>
- `org.panchang.sampradaya.GaudiyaEkadashiConformanceTest :: sydney` — org.opentest4j.AssertionFailedError: deferral classification at sydney: 2026-12-05: an unnamed deferral was promoted to VANJULI; the calendar prints no Mahadvadashi line here ==> expected: <true> but was: <false>

## Interpretation and limits

- Fresh baseline: 643 tests, 634 passed and 9 failed. The older 733 claim remains corrected by the preserved 12 September erratum.
- The first intermediate targeted run predates the new policy tests and passed 79 API/publisher tests.
- The next targeted run exposed a cross-year scope-comparison exception, a missing review-renderer UTC warning, and an old regional synthetic-UTC expectation. The policy comparison was fixed; the warning was restored and the expectation now requires no substitute calendar. No timing tolerance or golden reference was changed.
- The nine baseline failures concern Vrindavan fasting date/classification/Parana, Auckland Parana basis and classification, and Moscow/Sydney classification and named deferral. These remain failed release gates.
- The separate Reykjavik sunrise disagreement is retained in the runtime registry and original evidence. This task does not reclassify it as passing or widen its 30-second band.
- The seven existing network-tagged methods remain excluded by the normal build. They are not XML passes or skips.
- All ten original permanent delivery regressions passed in the final full XML (delivery-regressions.json). HTTP calculation comparisons use explicitly signed TEST ONLY fixtures; legacy numeric/rounding checks use the marked review renderer. New public tests verify default withholding and legacy refusal.
- CodeRabbit 0.7.5 has a valid CodeRabbit Inc. Authenticode signature but is signed out. No remote review ran. Local review covered signatures, authority separation, stale reviews, scope matching, rollback limits, content tampering and revocation at export.
- Real local HTTP/file/prepare checks passed (http-and-files/checks.json). Distribution inspection found no test-only approval classes packaged (distribution-check.json). These checks do not inflate JUnit counts.
- Preservation verification confirmed 833 original audit, repair and calculation files unchanged (preservation.txt).
- Production identity/storage activation, qualified decisions, consumer adaptation and deployment remain separate. No real approval, commit, push, cache, Android change, Content Hub change or deployment was created.

File scope is recorded in implementation-files.json. The owner workflow and compatibility contract are in OWNER-GUIDE.md.
