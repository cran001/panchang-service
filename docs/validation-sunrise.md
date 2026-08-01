# Sunrise/sunset validation against USNO

Measured 2026-08-01. Enforced continuously by `core/src/test/.../UsnoSunTimesConformanceTest.kt`,
which is a committed test, not a one-off measurement.

## Result

Twenty events — sunrise and sunset at each of the ten reference cities — compared against the
US Naval Observatory `rstt/oneday` API, using `MeeusEphemeris` and the `:core` two-pass
rise/set solver.

| City | Date | Sunrise Δ | Sunset Δ |
|---|---|---|---|
| Delhi | 2026-04-14 | +5 s | +19 s |
| Mayapur | 2026-04-14 | −11 s | +5 s |
| Vrindavan | 2026-08-24 | +11 s | −20 s |
| Mumbai | 2026-02-01 | −14 s | +28 s |
| London | 2026-06-21 | +5 s | −25 s |
| New York | 2026-03-15 | −28 s | −8 s |
| Auckland | 2026-01-15 | −14 s | +12 s |
| Moscow | 2026-12-21 | +26 s | −22 s |
| Sydney | 2026-09-10 | 0 s | +4 s |
| São Paulo | 2026-11-05 | +7 s | +26 s |

**Worst |Δ| = 28 s. Mean = +0.3 s. All twenty inside USNO's ±30 s publication rounding.**

## What this does and does not establish

**Does.** The `sunrise within 30 s of USNO across the city grid` item in the definition of done
is met. Both hemispheres are covered (a latitude sign error cannot hide behind northern-only
data), as is 55.7°N at the winter solstice — the shallowest horizon approach short of the polar
cutoff, and the case where an un-iterated solver errs most. It exercises `MeeusEphemeris`, the
UT→TT conversion, the rise/set solver and zone handling end to end; before this, every test in
`:core` ran against a linear fake and proved only internal consistency.

**Does not.** It does not establish accuracy *better* than 30 s. USNO publishes to the minute,
so 30 s is the floor this reference can resolve.

That said, the mean signed error of **+0.3 s across 20 events** is worth reading correctly. If
the code carried a real systematic bias, the mean would sit near that bias; instead the spread
looks like pure rounding noise about zero. That is evidence the true systematic error is far
below the tolerance — but it is evidence, not a bound, and no smaller figure is claimed
anywhere in this codebase. Bounding it properly needs a reference with sub-minute resolution.

## Why the zone column is a fixed offset

Each row uses the fixed UTC offset that was passed to USNO as `tz`, not the city's IANA zone.
Under DST these differ — Auckland in January is NZDT (+13) while the reference was requested at
+12 — and comparing against the IANA zone would inject a one-hour error with no astronomical
content. IANA zone behaviour, including DST transitions, is covered separately by
`TimeZoneTest`.

## Bearing on the accuracy question

`docs/accuracy-baseline.md` showed that geocentric longitudes are good to about one minute of
tithi boundary error, not the six minutes the plan assumed, and concluded the app's ±10-minute
corrections against DrikPanchang could not have been astronomical in origin. This result closes
the other half of that argument: sunrise is not the culprit either.

Two of the three factors in

```
observance = global tithi instants × local sunrise/arunodaya × sect rule
```

are now measured and sound. By elimination the discrepancy lies in **the sect rules** — the
arunodaya viddha test, Mahadvadashi handling, and parana derivation — which is where the
`ParanaCalibrationTable` was papering over it, and which is exactly the part no ephemeris can
fix. That is the correct target for the next phase.
