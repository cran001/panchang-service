# Exceptions and exclusions — 2026-09-12

Generated from audit JSON. Missing data and intentional exclusions are not passes. See AUDIT.md for rule interpretation.

## Existing test failures

| Module | Test | Failure |
|---|---|---|
| sampradaya | vrindavan | org.opentest4j.AssertionFailedError: parana disagreements at vrindavan: 2026-06-27: calendar prints a parana window, rules produced none 2026-06-26: rules produced a parana window the calendar does not print ==> expected: <true> but was: <false> |
| sampradaya | auckland | org.opentest4j.AssertionFailedError: parana disagreements at auckland: 2026-04-28 start basis: calendar '1/4 of tithi' (HARI_VASARA_END), rules SUNRISE 2026-10-08 end basis: calendar 'end of tithi' (DVADASHI_END), rules ONE_THIRD_DAYLIGHT 2026-10-08 end (ONE_THIRD_DAYLIGHT): calendar 06:47, rules 11:01:42, delta +254.71 min, outside -0.25..1.25 ==> expected: <true> but was: <false> |
| sampradaya | vrindavan | org.opentest4j.AssertionFailedError: Ekadashi fasting dates disagree with the vrindavan 2026 calendar. Missing: [2026-06-26]. Extra: [2026-06-25]. A fasting-date disagreement is a far stronger signal than a label disagreement and must be investigated as a rule error first; see docs/validation-multisite.md. ==> expected: <[2026-01-14, 2026-01-29, 2026-02-13, 2026-02-27, 2026-03-15, 2026-03-29, 2026-04-13, 2026-04-27, 2026-05-13, 2026-05-27, 2026-06-11, 2026-06-26, 2026-07-11, 2026-07-25, 2026-08-09, 2026-08-24, 2026-09-07, 2026-09-22, 2026-10-06, 2026-10-22, 2026-11-05, 2026-11-21, 2026-12-04, 2026-12-20]> but was: <[2026-01-14, 2026-01-29, 2026-02-13, 2026-02-27, 2026-03-15, 2026-03-29, 2026-04-13, 2026-04-27, 2026-05-13, 2026-05-27, 2026-06-11, 2026-06-25, 2026-07-11, 2026-07-25, 2026-08-09, 2026-08-24, 2026-09-07, 2026-09-22, 2026-10-06, 2026-10-22, 2026-11-05, 2026-11-21, 2026-12-04, 2026-12-20]> |
| sampradaya | vrindavan | org.opentest4j.AssertionFailedError: Mahadvadashi classification disagrees with the vrindavan 2026 calendar. A *label* disagreement on a fortnight whose fasting date still matches is the outcome ADR 0002 predicts from the reference's ephemeris (docs/decisions/0002-reference-calendar-disagreement.md); a disagreement that also moves the fasting date is not, and is a rule error until shown otherwise. ==> expected: <{2026-06-26=PAKSAVARDHINI, 2026-08-24=VANJULI}> but was: <{2026-08-24=VANJULI}> |
| sampradaya | moscow | org.opentest4j.AssertionFailedError: Mahadvadashi classification disagrees with the moscow 2026 calendar. A *label* disagreement on a fortnight whose fasting date still matches is the outcome ADR 0002 predicts from the reference's ephemeris (docs/decisions/0002-reference-calendar-disagreement.md); a disagreement that also moves the fasting date is not, and is a rule error until shown otherwise. ==> expected: <{}> but was: <{2026-05-27=VANJULI}> |
| sampradaya | auckland | org.opentest4j.AssertionFailedError: Mahadvadashi classification disagrees with the auckland 2026 calendar. A *label* disagreement on a fortnight whose fasting date still matches is the outcome ADR 0002 predicts from the reference's ephemeris (docs/decisions/0002-reference-calendar-disagreement.md); a disagreement that also moves the fasting date is not, and is a rule error until shown otherwise. ==> expected: <{2026-04-14=TRISPRSA}> but was: <{2026-04-14=TRISPRSA, 2026-10-07=TRISPRSA}> |
| sampradaya | sydney | org.opentest4j.AssertionFailedError: Mahadvadashi classification disagrees with the sydney 2026 calendar. A *label* disagreement on a fortnight whose fasting date still matches is the outcome ADR 0002 predicts from the reference's ephemeris (docs/decisions/0002-reference-calendar-disagreement.md); a disagreement that also moves the fasting date is not, and is a rule error until shown otherwise. ==> expected: <{2026-01-15=PAKSAVARDHINI, 2026-07-11=TRISPRSA}> but was: <{2026-01-15=PAKSAVARDHINI, 2026-07-11=TRISPRSA, 2026-12-05=VANJULI}> |
| sampradaya | moscow | org.opentest4j.AssertionFailedError: deferral classification at moscow: 2026-05-27: an unnamed deferral was promoted to VANJULI; the calendar prints no Mahadvadashi line here ==> expected: <true> but was: <false> |
| sampradaya | sydney | org.opentest4j.AssertionFailedError: deferral classification at sydney: 2026-12-05: an unnamed deferral was promoted to VANJULI; the calendar prints no Mahadvadashi line here ==> expected: <true> but was: <false> |

## Tag-excluded existing network methods

These seven methods were not run. They are excluded by Gradle, not reported as JUnit skips. Deliberate source fetches and stored conformance tests were run instead.

- `LiveSourceTest`: JPL Horizons still returns the committed regression anchor
- `LiveSourceTest`: USNO still answers for a grid city
- `LiveSourceTest`: USNO still reports polar day in the shape we parse
- `LiveSourceTest`: vaisnavacalendar text export still parses cleanly
- `LiveSourceTest`: every grid city still has a published calendar for the current year
- `LiveSourceTest`: ISKCON Mumbai still publishes a parseable Ekadasi notice
- `LiveSourceTest`: drikpanchang day panchang still exposes the rows we read

## Every stale-DST timing exclusion

Only the clock comparisons are excluded. Dates, names, and labels remain scored. Raw timing residuals remain in parana-results.json.

| City | Date | Start raw delta (s) | End raw delta (s) |
|---|---|---|---|
| moscow | 2026-03-30 | -3594.379 | -3593.298 |
| moscow | 2026-04-14 | -3565.769 | -3569.715 |
| moscow | 2026-04-28 | -3553.066 | -3576.309 |
| moscow | 2026-05-14 | -3589.455 | -3707.669 |
| moscow | 2026-05-28 | -3598.194 | -3688.789 |
| moscow | 2026-06-12 | -3576.025 | -3548.592 |
| moscow | 2026-06-26 | -3579.617 | -3541.914 |
| moscow | 2026-07-12 | -3562.537 | -3557.401 |
| moscow | 2026-07-26 | -3562.579 | -3567.595 |
| moscow | 2026-08-10 | -3575.968 | -3641.677 |
| moscow | 2026-08-24 | -3612.591 | -3598.593 |
| moscow | 2026-09-08 | -3571.392 | -3565.709 |
| moscow | 2026-09-23 | -3575.146 | -3557.123 |
| moscow | 2026-10-07 | -3547.571 | -3548.083 |
| moscow | 2026-10-23 | -3571.661 | -3573.574 |
| sao-paulo | 2026-01-15 | -3588.127 | -3561.43 |
| sao-paulo | 2026-01-30 | -3594.366 | -3551.644 |
| sao-paulo | 2026-02-14 | -3548.003 | -3709.974 |
| sao-paulo | 2026-02-28 | -3570.678 | -3597.676 |
| sao-paulo | 2026-10-23 | -3546.448 | -3638.939 |
| sao-paulo | 2026-11-05 | -3610.109 | -3566.324 |
| sao-paulo | 2026-11-21 | -3597.456 | -3597.196 |
| sao-paulo | 2026-12-05 | -3558.22 | -3596.154 |
| sao-paulo | 2026-12-21 | -3545.104 | -3608.333 |

## Missing, uncapped and extra Parana rows

A missing emitted start is not scored using a re-derived internal Hari Vasara value.

| City | Date | Bound | Reference | State |
|---|---|---|---|---|
| auckland | 2026-09-23 | start | 10:31 | actual_window_missing |
| auckland | 2026-09-23 | end | None | reference_end_unavailable |
| mumbai | 2026-08-24 | start | 10:50 | actual_window_missing |
| mumbai | 2026-08-24 | end | None | reference_end_unavailable |
| new-york | 2026-10-22 | start | 11:16 | actual_window_missing |
| new-york | 2026-10-22 | end | None | reference_end_unavailable |
| vrindavan | 2026-06-27 | start | 05:25 | actual_window_missing |
| vrindavan | 2026-06-27 | end | 10:03 | actual_window_missing |
| vrindavan | 2026-06-26 | window | None | extra_actual_window |

## Fasting date and Mahadvadashi disagreements

| City | Reference date | Actual date | Reference type | Actual type | Confidence |
|---|---|---|---|---|---|
| auckland | 2026-10-07 | 2026-10-07 | None | TRISPRSA | CONFIRMED |
| moscow | 2026-05-27 | 2026-05-27 | None | VANJULI | CONFIRMED |
| sydney | 2026-12-05 | 2026-12-05 | None | VANJULI | CONFIRMED |
| vrindavan | 2026-06-26 | 2026-06-25 | PAKSAVARDHINI | None | CONFIRMED |

## Parana basis disagreements and non-DST timing failures

| City | Date | Bound | Reference basis | Actual basis | Delta (s) |
|---|---|---|---|---|---|
| ahmedabad | 2026-11-06 | end | 1/3 of daylight | DVADASHI_END | 4.467 |
| auckland | 2026-04-28 | start | 1/4 of tithi | SUNRISE | 19.423 |
| auckland | 2026-10-08 | end | end of tithi | ONE_THIRD_DAYLIGHT | 15282.688 |

## Every daily field disagreement

Seconds below describe the actual boundary before the actual sunrise, not a measured reference boundary error. Reference boundary instants and approved sidereal configuration are absent.

| City/date | Reference / actual tithi | Reference / actual nakshatra | Tithi start before sunrise (s) | Nakshatra start before sunrise (s) |
|---|---|---|---|---|
| ahmedabad 2026-11-20 | Dasami / Dashami | Purva-bhadra / Uttara Bhadrapada | 85769.495 | 2.966 |
| auckland 2026-04-06 | Caturthi / Chaturthi | Visakha / Anuradha | 43674.28 | 50.427 |
| auckland 2026-06-11 | Dasami / Ekadashi | Revati / Revati | 103.162 | 56336.462 |
| auckland 2026-10-08 | Dvadasi / Trayodashi | Purva-phalguni / Purva Phalguni | 20.105 | 5845.276 |
| bangalore 2026-08-13 | Pratipat / Pratipada | Aslesa / Magha | 25233.728 | 65.588 |
| guwahati 2026-05-25 | Navami / Dashami | Uttara-phalguni / Uttara Phalguni | 77.454 | 6122.791 |
| london 2026-08-11 | Caturdasi / Chaturdashi | Punarvasu / Pushya | 18895.715 | 38.562 |
| mayapur 2026-11-22 | Trayodasi / Trayodashi | Revati / Ashvini | 3482.117 | 33.289 |
| new-york 2026-03-26 | Navami / Navami | Ardra / Punarvasu | 16195.715 | 38.562 |
| vrindavan 2026-03-13 | Navami / Dashami | Purva-asadha / Purva Ashadha | 101.843 | 20884.46 |
| vrindavan 2026-06-30 | Purnima / Pratipada | Purva-asadha / Purva Ashadha | 13.513 | 5024.597 |
| hyderabad 2027-09-29 | Caturdasi / Chaturdashi | Purva-phalguni / Uttara Phalguni | 58255.334 | 4.944 |

## Astronomy without a scalar timing comparison

No event and polar conditions are categorical evidence. Multiple reference events cannot be represented by a single event field. These rows are retained without an invented error.

| Source file | Date | Field | Reference events / condition | Actual |
|---|---|---|---|---|
| usno-polar-2026-06-21.json | 2026-06-21 | sunrise | [] / CONTINUOUSLY_ABOVE_HORIZON | CircumpolarUp |
| usno-polar-2026-06-21.json | 2026-06-21 | sunset | [] / CONTINUOUSLY_ABOVE_HORIZON | CircumpolarUp |
| usno-polar-2026-06-21.json | 2026-06-21 | civilDusk | [] / CONTINUOUSLY_ABOVE_TWILIGHT_LIMIT | CircumpolarUp |
| usno-polar-2026-06-21.json | 2026-06-21 | sunrise | [] / CONTINUOUSLY_BELOW_HORIZON | CircumpolarDown |
| usno-polar-2026-06-21.json | 2026-06-21 | sunset | [] / CONTINUOUSLY_BELOW_HORIZON | CircumpolarDown |
| usno-polar-2026-06-21.json | 2026-06-21 | moonset | [] / None | NoEventInWindow |
| usno-polar-2026-06-21.json | 2026-06-21 | solarNoon | [] / CONTINUOUSLY_BELOW_HORIZON | 2026-06-21T12:54:58.269+12:00 |
| usno-polar-2026-06-21.json | 2026-06-21 | civilDusk | [] / CONTINUOUSLY_BELOW_TWILIGHT_LIMIT | CircumpolarDown |
| usno-polar-2026-12-21.json | 2026-12-21 | sunrise | [] / CONTINUOUSLY_BELOW_HORIZON | CircumpolarDown |
| usno-polar-2026-12-21.json | 2026-12-21 | sunset | [] / CONTINUOUSLY_BELOW_HORIZON | CircumpolarDown |
| usno-polar-2026-12-21.json | 2026-12-21 | moonrise | [] / CONTINUOUSLY_ABOVE_HORIZON | CircumpolarUp |
| usno-polar-2026-12-21.json | 2026-12-21 | moonset | [] / CONTINUOUSLY_ABOVE_HORIZON | CircumpolarUp |
| usno-polar-2026-12-21.json | 2026-12-21 | solarNoon | [] / CONTINUOUSLY_BELOW_HORIZON | 2026-12-21T11:55:27.288+01:00 |
| usno-polar-2026-12-21.json | 2026-12-21 | civilDusk | [] / CONTINUOUSLY_BELOW_TWILIGHT_LIMIT | CircumpolarDown |
| usno-polar-2026-12-21.json | 2026-12-21 | sunrise | [] / CONTINUOUSLY_ABOVE_HORIZON | CircumpolarUp |
| usno-polar-2026-12-21.json | 2026-12-21 | sunset | [] / CONTINUOUSLY_ABOVE_HORIZON | CircumpolarUp |
| usno-polar-2026-12-21.json | 2026-12-21 | moonrise | [] / CONTINUOUSLY_BELOW_HORIZON | CircumpolarDown |
| usno-polar-2026-12-21.json | 2026-12-21 | moonset | [] / CONTINUOUSLY_BELOW_HORIZON | CircumpolarDown |
| usno-polar-2026-12-21.json | 2026-12-21 | civilDusk | [] / CONTINUOUSLY_ABOVE_TWILIGHT_LIMIT | CircumpolarUp |
| retry-usno-reykjavik-2026-06-26.json | 2026-06-27 | moonrise | [] / CONTINUOUSLY_BELOW_HORIZON | CircumpolarDown |
| retry-usno-reykjavik-2026-06-26.json | 2026-06-27 | moonset | [] / CONTINUOUSLY_BELOW_HORIZON | CircumpolarDown |
| retry-usno-reykjavik-2026-06-26.json | 2026-06-27 | civilDusk | [] / CONTINUOUSLY_ABOVE_TWILIGHT_LIMIT | CircumpolarUp |
| usno-reykjavik-2026-06-26.json | 2026-06-26 | moonset | ['00:17', '23:31'] / None | 2026-06-26T23:31:03.593Z |
| usno-reykjavik-2026-06-26.json | 2026-06-26 | civilDusk | [] / CONTINUOUSLY_ABOVE_TWILIGHT_LIMIT | CircumpolarUp |

## New numerical screening failure

| Date/site | Reference | Actual | Delta (s) | Outcome |
|---|---|---|---|---|
| 2026-06-27 64.1466,-21.9426 | 03:00 | 2026-06-27T02:59:27.829Z | -32.171 | Unresolved; unchanged at 6/12/24 refinement passes |

## Every unavailable fetch attempt

Later successful retries remain separate rows in the manifests. A recovered request does not erase its failed attempt. Supplementary ids sometimes retain the original date; actual request parameters are shown here.

| Id | Actual requested date/year | HTTP | Error |
|---|---|---|---|
| usno-cape-town-2027-01-01 | 2027-01-01 | 502 | HTTP error response retained |
| usno-cape-town-2028-06-21 | 2028-06-21 | 502 | HTTP error response retained |
| usno-singapore-2027-12-31 | 2027-12-31 | 502 | HTTP error response retained |
| usno-singapore-2028-01-01 | 2028-01-01 | 502 | HTTP error response retained |
| usno-reykjavik-2028-06-21 | 2028-06-21 | 502 | HTTP error response retained |
| usno-london-2027-03-28 | 2027-03-28 | 502 | HTTP error response retained |
| usno-london-2028-10-29 | 2028-10-29 | 502 | HTTP error response retained |
| usno-new-york-2027-03-14 | 2027-03-14 | 502 | HTTP error response retained |
| usno-new-york-2028-11-05 | 2028-11-05 | 502 | HTTP error response retained |
| usno-vrindavan-2026-06-30 | 2026-06-30 | 502 | HTTP error response retained |
| usno-auckland-2026-10-08 | 2026-10-08 | 502 | HTTP error response retained |
| calendar-mayapur-2028 | 2028 | 404 | HTTP error response retained |
| calendar-hyderabad-2028 | 2028 | 404 | HTTP error response retained |
| gcal-rules |  | 0 | curl: (60) schannel: SNI or certificate check failed: SEC_E_WRONG_PRINCIPAL (0x80090322) - The target principal name is incorrect. More details here: https://curl.se/docs/sslcerts.html  curl failed to verify the legitimacy of the server and therefore could not establish a secure connection to it. To learn more about this situation and how to fix it, please visit the webpage mentioned above.  |

## Parse omissions

No successful numerical/calendar response had unparsed lines or parser warnings. The rules PDF was not retrieved and the documentation pages were not treated as numerical fixtures.

## Coverage not available

- 2028 Mayapur and Hyderabad observance calendars (404); no replacement publisher used.
- Unrecovered originally requested USNO dates: Cape Town 2027-01-01; Singapore 2027-12-31; Reykjavik 2028-06-21; London 2028-10-29; New York 2027-03-14 and 2028-11-05. Some other dates at the same locations were fetched and scored separately.
- Regional festival-calendar references; rare Jaya/Jayanti/Papanasini positives; approved Unmilani and nakshatra qualifiers; high-latitude observance oracles.
- Independent yoga/pada/ayanamsha/numeric day-division references; complete lunar geometry/elevation/atmospheric coverage; full 2027/2028 position envelopes.
- Human approvals, deployed service validation, production stored-result readback and a persistent shared calculation cache.

All named pandit questions, decision-boundary uncertainty, and publication blockers are carried in AUDIT.md.
