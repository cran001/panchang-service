# Moonrise/moonset validation against USNO

Measured 2026-08-01. Enforced continuously by
`verify/src/test/kotlin/org/panchang/verify/UsnoMoonTimesConformanceTest.kt`, which is a
committed test, not a one-off measurement.

This exists to put a number under the `MOONRISE` fast anchor. One catalog entry ends its fast at
moonrise, and grading that rule's confidence requires knowing how good our moonrise is rather
than assuming it inherits the Sun's result. It does not inherit it: the Moon moves about 13° a
day against the stars, its horizontal parallax is roughly 1° where the Sun's is negligible, and
its rise/set horizon therefore depends on its distance. A residual several times the Sun's would
not have been a bug.

## What was already on disk

`verify/golden/usno-grid-2026-04-14.json` has carried USNO's `moonRises` and `moonSets` for all
ten reference cities since it was harvested on 2026-08-01, and nothing read them. `UsnoParserTest`
asserts on moon times, but against a fixture — that proves we can parse USNO's JSON, not that our
astronomy agrees with it. The two polar files, `usno-polar-2026-06-21.json` and
`usno-polar-2026-12-21.json`, were likewise unused on the moon side.

No new data was harvested for this. The test reads `verify/golden` directly, as
`GaudiyaEkadashiConformanceTest` does, rather than through a copied resource: a second physical
copy of a reference file is a copy that can drift.

## Result

Signed delta is **ours − USNO**, in seconds.

| City | Date | Moonrise Δ | Moonset Δ |
|---|---|---|---|
| Mayapur | 2026-04-14 | +15 s | −19 s |
| Vrindavan | 2026-04-14 | +19 s | −3 s |
| Delhi | 2026-04-14 | +11 s | +26 s |
| Mumbai | 2026-04-14 | −27 s | +19 s |
| London | 2026-04-14 | +26 s | +16 s |
| New York | 2026-04-14 | +17 s | −11 s |
| Auckland | 2026-04-14 | −19 s | +3 s |
| Moscow | 2026-04-14 | +18 s | −2 s |
| São Paulo | 2026-04-14 | −16 s | +2 s |
| Sydney | 2026-04-14 | −23 s | −17 s |

**Worst |Δ| = 27 s (Mumbai moonrise). RMS = 17 s. Mean = +1.8 s. All twenty inside USNO's ±30 s
publication rounding.**

Nothing failed and no grid city was skipped.

### Polar probes

Not part of the ten, and reported separately because most of these rows have no time to compare.
They are here because the shallow horizon approach is the Moon's worst geometric case, and
because "no event" is an answer the `MOONRISE` anchor has to survive.

| Site | Date | USNO | Ours | Δ |
|---|---|---|---|---|
| Longyearbyen 78.2°N | 2026-06-21 | rise 12:29 | rise | +16 s |
| Longyearbyen 78.2°N | 2026-06-21 | set 01:17 | set | +2 s |
| McMurdo 77.8°S | 2026-06-21 | rise 13:10 | rise | −6 s |
| McMurdo 77.8°S | 2026-06-21 | **no moonset** | `NoEventInWindow` | not comparable |
| Longyearbyen 78.2°N | 2026-12-21 | continuously above horizon | `CircumpolarUp` | not comparable |
| McMurdo 77.8°S | 2026-12-21 | continuously below horizon | `CircumpolarDown` | not comparable |

The four "not comparable" rows are named rather than dropped. Three of them carry no time on
either side, so no residual exists to quote; what they establish is that the *kind* of answer
matches — including McMurdo on 2026-06-21, which really does have a moonrise and no moonset, the
ordinary once-a-lunar-month case that a routine returning `null` or `0.0` would render
indistinguishable from a broken one.

## Method

For each golden record: build a `GeoLocation` at the record's own latitude and longitude, call
`PanchangCalculator.moonTimes` for the record's date, convert the returned Julian Day (UT) to a
civil time, and difference it against USNO's printed value.

Three things about the reference were checked rather than assumed.

**USNO's times are local, not UT.** They are expressed in the fixed numeric offset sent as the
`tz` request parameter, echoed back in each record's `utcOffsetHours`. The comparison builds its
`GeoLocation` from that same fixed offset, not from the city's IANA zone. On 2026-04-14 the two
agree for every grid city — India +5:30, London +1 (BST), New York −4 (EDT), Auckland +12 and
Sydney +10, both already off DST as of 5 April — but relying on that coincidence is precisely how
an hour of DST error gets to hide inside an astronomical comparison. Had the offsets been read as
UT instead, the residuals would have been whole hours; had a rise and a set been transposed, a
half-day. Neither appears.

**USNO publishes to the whole minute.** The `rstt/oneday` response carries `"time": "02:59"` with
no seconds field. That caps the resolution of *any* comparison against this source at ±30 s,
whatever our code does.

**The horizon convention is the same one.** USNO reckons moonrise from the Moon's upper limb on
the apparent horizon. `Horizon.moonAltitudeDegrees` states the identical convention
geocentrically, as Meeus eq. 15.1, `h₀ = 0.7275·π − 34′`: the `0.7275` is `1 − 0.2725`, the
parallax shift less the semidiameter. This matters more than it looks. A source using the Moon's
*centre* would sit about a semidiameter away — roughly a minute of time — and the resulting bias
would be indistinguishable from a real error. The measured mean of +1.8 s is itself evidence the
conventions match; a convention mismatch could not produce a mean that small.

## Tolerance, and the order it was written in

The deltas above were measured before any band was chosen. The band was not pre-declared and
then checked for fit.

The band asserted is **±30 s**, the half-width of USNO's one-minute rounding — the tightest band a
minute-resolution reference can justify. The measurement is what shows we sit inside it at every
one of the twenty-three comparable events, with 3 s to spare at the worst city, rather than by
luck. No constant was touched to get there; `MOON_REFRACTION_DEGREES` and `MOON_PARALLAX_FACTOR`
are as they were.

If a future change pushes an event past 30 s it has moved further than rounding can explain, and
that is a real regression. The band must not be widened to accommodate one.

## Recommendation: MOONRISE anchor **CONFIRMED**

The decision rule was: if our moonrise differs from USNO by more than a minute, `MOONRISE` is
`INFERRED`.

Worst observed moonrise disagreement is **27 s**. Read pessimistically — USNO's printed 02:59 is
a rounding of an instant anywhere in a 60 s window, so our disagreement with its *unrounded*
value could be as much as 27 + 30 = **57 s** — the criterion is still met, but by 3 s. Read
conventionally, the residuals look like rounding noise about zero (mean +1.8 s over twenty
events, rms 17 s versus the 17.3 s rms of a uniform ±30 s rounding), which says the true
systematic error is far below a minute. Both readings support CONFIRMED. Only the pessimistic one
is close, and it is close because the reference is coarse, not because the code is.

For a fasting rule this is the right side of the line with room: a fast ending at moonrise is
observed to the minute at best, and a 27 s discrepancy cannot move the minute a devotee reads by
more than one.

### What would change this grade

- Any event exceeding ±30 s in the committed test. That is a genuine regression, not rounding.
- A tightening of the criterion below one minute. The current bound cannot support it, and no
  finer figure should be claimed from this reference. See below.

## What this does and does not establish

**Does.** Moonrise and moonset agree with USNO to within its publication rounding at all ten
reference cities, in both hemispheres, at latitudes from 19°N to 55.8°N and 23.6°S to 36.8°S, and
at 78.2°N and 77.8°S. The parallax term, the Moon's own motion during the solve, the sealed
no-event and circumpolar classifications, and the fixed-offset zone handling are all exercised
end to end.

**Does not.** It does not establish accuracy better than 30 s. USNO publishes to the minute; that
is the floor this reference can resolve, and the mean of +1.8 s is evidence about the systematic
error, not a bound on it.

It also does not cover the full range of lunar geometry. The twenty grid events all fall on
2026-04-14, at one Moon distance — 0.00254584 AU, about 380 850 km, a horizontal parallax of
57.6′, close to the mean — and one phase, a 10–15 % waning crescent three days before new. Ten
cities give ten independent *geographic* samples of one lunar configuration, not ten lunar
configurations. The polar rows add two further dates and phases (41–45 % in June, 86–90 % in
December), so coverage is not literally single-point, but perigee and apogee are unsampled.

The practical exposure from that is small, and boundable. `Horizon.moonAltitudeDegrees` derives
the parallax from the actual distance at the event rather than from a constant, so the untested
quantity is only the `0.7275` coefficient. An error in that coefficient produces a horizon error
proportional to the parallax, and parallax is at most about 7 % larger at perigee (61.5′) than at
the 57.6′ these events were measured at. So whatever part of the measured 27 s is due to that
coefficient, it can grow by at most 7 % — under 2 s — at the closest Moon. Extending the grid
across a lunation would close the gap properly and is cheap, since the harvester already exists.

## On ELP2000-82B, which stays unused

The full ELP2000-82B series is vendored in this repository and deliberately not wired in. This
measurement gives no reason to change that, and the reason is worth stating numerically rather
than as a preference.

The Moon crosses its own diameter, about 31′, in roughly two minutes near the horizon, so one
minute of rise-time error corresponds to about 15′ ≈ 900″ of position. `docs/decisions/0002-reference-calendar-disagreement.md`
records this project's ephemeris residual against JPL Horizons at 2.88″ rms and 8.79″ max. Nine
arcseconds is therefore about **0.6 s** of moonrise — some fifty times below the 30 s floor this
reference can even see. The truncation of the lunar series is not what limits this result and a
higher-order series could not be shown to improve it. Switching ephemerides here would be an
unfalsifiable change.

What actually limits it, in order: USNO's minute rounding first, and beneath that the
refraction assumption. `Horizon` uses a fixed 34′, and real horizon refraction departs from the
standard value by several arcminutes routinely — several arcminutes being tens of seconds of
rise time. That is a property of the atmosphere, not of the ephemeris, and no ephemeris change
addresses it.

**What would settle a finer bound:** a reference with sub-minute resolution. The obvious one is
JPL Horizons topocentric apparent altitude sampled around each event and solved for the horizon
crossing — the same source `docs/accuracy-baseline.md` already uses, though the existing
`horizons-moon-*.json` golden files hold geocentric ecliptic coordinates and cannot be used for
this without a fresh harvest of a topocentric quantity. Until someone does that, 30 s stands as
the honest floor and nothing smaller is claimed anywhere in this repository.

## Test counts

`./gradlew --rerun-tasks --no-daemon :verify:test` → **tests=90 skipped=0 failures=0 errors=0**,
of which this class contributes 14 (10 grid cities × rise and set as one dynamic test each, plus
4 polar probe rows).
