# Measured accuracy baseline

Measured 2026-08-01 against `docs/measurements/horizons-2026-08-01/` — 365 daily samples of
apparent geocentric ecliptic longitude per body per epoch, from JPL Horizons. Subject:
`MeeusEphemeris`, the Meeus Ch. 25 / Ch. 47 series with full 60-term lunar tables and the
63-term IAU 1980 nutation.

Everything below is a measurement, not an estimate. Where a number is an inference from the
measurement rather than the measurement itself, it says so.

## Results

Error is `computed − Horizons`, in arcseconds. "clock" is the least-squares constant time
offset that best explains the error; "resid" is what remains after removing it, i.e. the
part attributable to the series rather than to the time argument.

| Epoch | Quantity | mean | rms | max\|·\| | clock | resid rms | resid max |
|---|---|---|---|---|---|---|---|
| 1950 | Moon | +0.37″ | 2.50″ | 9.94″ | +0.55 s | 2.48″ | 10.29″ |
| 1950 | Sun | +2.78″ | 8.87″ | 27.70″ | — | 8.37″ | 24.67″ |
| 1950 | **Elongation** | −2.41″ | **9.74″** | **28.53″** | −4.75 s | 9.44″ | 26.12″ |
| 2026 | Moon | +2.85″ | 4.01″ | 11.31″ | +5.15 s | 2.82″ | 8.23″ |
| 2026 | Sun | +12.63″ | 17.33″ | 34.11″ | — | 11.89″ | 22.32″ |
| 2026 | **Elongation** | −9.78″ | **16.34″** | **34.92″** | −19.25 s | 13.09″ | 25.15″ |
| 2100 | Moon | +74.60″ | 75.00″ | 93.52″ | +135.87 s | **2.86″** | 12.20″ |
| 2100 | Sun | +11.53″ | 14.74″ | 28.19″ | — | 9.38″ | 22.88″ |
| 2100 | **Elongation** | +63.07″ | **64.38″** | **96.77″** | +124.18 s | 12.89″ | 33.69″ |

The Sun's "clock" column is omitted: the Sun moves so slowly that fitting a time offset to
its error is ill-conditioned and the resulting number (hundreds of seconds) is an artefact,
not a finding.

**Tithi is the Moon−Sun elongation**, advancing 12.19°/day = 1829″/hour. So worst-case
boundary timing error, inferred from the elongation row:

| Epoch | max elongation error | → tithi boundary error |
|---|---|---|
| 1950 | 28.53″ | **56 s** |
| 2026 | 34.92″ | **69 s** |
| 2100 | 96.77″ | **190 s** (3.2 min) |

## Three findings that change the plan

### 1. The "±6 minute ceiling" premise was wrong

The project plan asserted that the Android engine's lunar series caps tithi accuracy at
about ±6 minutes, reasoning from the `~0.05°` figure the engine self-documents, and
concluded that `ParanaCalibrationTable`'s ±10-minute corrections against DrikPanchang were
astronomical in origin.

Measured, the worst-case tithi boundary error today is **about one minute**, not six. The
`0.05°` comment in the engine is pessimistic by more than an order of magnitude for
longitude.

This matters because it relocates the problem. If the astronomy is good to ~1 minute but the
app needed ±10-minute corrections to match DrikPanchang, **the discrepancy is not in the
ephemeris**. It is in some combination of the sunrise/sunset calculation, the ayanamsha, the
topocentric correction, and the observance rules. Those are where accuracy work should go,
and they are cheaper to fix than a new ephemeris.

Note the scope of this claim: it is about *geocentric longitudes*. It says nothing yet about
sunrise, which is measured separately against USNO.

### 2. The Sun is the binding constraint, not the Moon

At 2026 the Moon is good to 4.01″ rms while the Sun is 17.33″ rms — the Sun carries roughly
four times the error, and dominates the elongation. This is expected: Meeus Ch. 47 is the
full lunar theory truncated, whereas Ch. 25's "low accuracy" solar method is stated by Meeus
himself as ~0.01° = 36″, and the measurement sits comfortably inside that.

**This inverts the Phase 2 priority.** The plan treated VSOP87 (Sun) and ELP2000-82B (Moon)
as one work item. They are not comparable:

- **VSOP87D for the Sun** is one file already vendored, ~2500 terms, and takes solar error
  from ~17″ to well under 1″. That alone cuts elongation error from 16.34″ rms to roughly
  the Moon's 4″ — a 4× improvement for a day's work.
- **ELP2000-82B for the Moon** is 36 files and 2.5 MB of coefficients, and buys the
  remaining 4″ → ~0.1″.

Do VSOP87 first and measure again before deciding whether ELP2000 earns its cost. On these
numbers ELP2000 moves tithi boundaries by well under a minute, which no observance rule can
detect — its real justification is exact agreement with DrikPanchang (which uses Swiss
Ephemeris at ~0.001″), not observably better dates.

### 3. ΔT — not the ephemeris — is what limits far-future accuracy

This is the answer to "how do we stay accurate for all time coming in the future."

At 2100 the Moon's raw error is 75″ rms, but after removing a constant clock offset the
residual is **2.86″ — indistinguishable from 1950 and 2026**. The series does not degrade.
What degrades is the time argument: the fit attributes 135.87 s of clock error, which is
Espenak–Meeus ΔT extrapolation diverging from the ΔT model Horizons uses.

The same effect is already visible today. Espenak–Meeus returns 75.2 s for mid-2026 against
an observed ~69.3 s, a **+5.9 s bias**, contributing +3.3″ to the Moon's longitude — most of
its 2026 mean error of +2.85″.

The consequences are worth stating plainly:

- **ΔT is not computable.** It depends on the Earth's rotation, which is measured, not
  predicted. No ephemeris, however good, removes this.
- **Fixable part:** for past and present dates ΔT is *observed*. Replacing the Espenak–Meeus
  post-2005 extrapolation with IERS `finals.all` removes the current +5.9 s bias outright.
  That is a larger accuracy gain today than switching the lunar theory, and much cheaper.
- **Irreducible part:** for future dates ΔT is a forecast with genuine uncertainty — roughly
  ±10 s by 2100 even under good models, which is ±20 s of tithi boundary. The service must
  say so rather than imply that a far-future date carries the same confidence as next year's.

A calendar published for 2100 should therefore carry a wider stated tolerance than one
published for 2027, and for the same reason: not because the astronomy is worse, but because
nobody yet knows how fast the Earth will be turning.

## What this baseline does not cover

Stated so it is not mistaken for more than it is:

- Geocentric longitudes only. The topocentric correction is untested here.
- Sunrise, sunset, moonrise, moonset — untested here; USNO is the oracle for those.
- Three epochs at daily cadence. Good phase coverage (≈12.4 synodic and ≈13.2 anomalistic
  months per epoch), but it is not a continuous 1900–2200 envelope. Phase 2 should produce
  that envelope; this is a baseline, not the envelope.
- Solar distance is known-weak and separately documented on `Ephemeris.sunDistanceKm`.

---

# Measured accuracy after Phase 2A — 2026-08-01

Appended, not replacing. Everything above is the Phase 1 baseline and stands as measured.

Same reference files (`docs/measurements/horizons-2026-08-01/`), same harness
(`ephemeris/src/test/kotlin/org/panchang/ephemeris/HorizonsDifferentialTest.kt`, which is
now the committed home of the harness), two changes to the subject:

1. **ΔT is now observed.** `DeltaT.secondsAtJd` uses IERS Earth orientation
   (`finals.all.iau2000.txt`, retrieved 2026-08-01T13:23:52Z, HTTP 200) for 1973-01-02
   onward, IERS Bulletin A predictions to 2027-08-07, a parabola anchored to the end of that
   record thereafter, and the Espenak & Meeus fit before it. Provenance is in the header of
   `ephemeris/src/main/resources/org/panchang/ephemeris/deltat-iers.csv`.
2. **The Sun is now VSOP87D.** New `Vsop87Ephemeris` (id `vsop87`): all 2425 terms of the
   Earth L/B/R series, geocentric inversion + FK5 (−0.09033″) + aberration + nutation.
   `MeeusEphemeris` is unmodified, and both engines are measured below.

The ΔT change is shared by both engines, so the `meeus` rows below also differ from the
Phase 1 table. Regenerate everything here with
`./gradlew :ephemeris:test --tests '*HorizonsDifferentialTest*' -i`; the numbers are that
harness's printed output, transcribed, not recomputed by hand.

## Results

| Epoch / quantity | mean | rms | max | clock | resid rms | resid max |
|---|---|---|---|---|---|---|
| 1950 Moon (shared) | +0.37″ | 2.50″ | 9.94″ | +0.55 s | 2.48″ | 10.29″ |
| 1950 Sun — meeus | +2.78″ | 8.87″ | 27.70″ | +71.31 s | 8.37″ | 24.67″ |
| 1950 Sun — **vsop87** | −0.06″ | **0.06″** | 0.06″ | −1.44 s | 0.00″ | 0.01″ |
| 1950 Elongation — meeus | −2.41″ | 9.74″ | 28.53″ | −4.70 s | 9.44″ | 26.26″ |
| 1950 Elongation — **vsop87** | +0.43″ | **2.51″** | **9.88″** | +0.70 s | 2.48″ | 10.30″ |
| 2026 Moon (shared) | −0.56″ | 2.86″ | 8.70″ | −1.06 s | 2.80″ | 8.11″ |
| 2026 Sun — meeus | +12.38″ | 17.15″ | 33.86″ | +300.68 s | 11.90″ | 22.32″ |
| 2026 Sun — **vsop87** | +0.10″ | **0.10″** | 0.11″ | +2.48 s | 0.01″ | 0.01″ |
| 2026 Elongation — meeus | −12.94″ | 18.38″ | 38.29″ | −25.30 s | 13.06″ | 25.97″ |
| 2026 Elongation — **vsop87** | −0.66″ | **2.88″** | **8.79″** | −1.35 s | 2.80″ | 8.12″ |
| 2100 Moon (shared) | +11.08″ | 11.56″ | 23.53″ | +20.33 s | 2.79″ | 11.82″ |
| 2100 Sun — meeus | +6.79″ | 11.49″ | 23.58″ | +161.21 s | 9.39″ | 22.92″ |
| 2100 Sun — **vsop87** | +0.88″ | **0.88″** | 0.92″ | +21.51 s | 0.01″ | 0.01″ |
| 2100 Elongation — meeus | +4.29″ | 11.36″ | 34.02″ | +8.81 s | 10.43″ | 29.31″ |
| 2100 Elongation — **vsop87** | +10.20″ | **10.71″** | **22.62″** | +20.25 s | 2.79″ | 11.81″ |

`clock` is the least-squares constant time offset that best explains the error, and
`resid` is what is left after removing it. As in the Phase 1 table, the Sun's clock column
is ill-conditioned — the Sun moves 0.041″/s, so a 1″ error maps to 24 s and the fit is
mostly amplifying noise. It is printed for completeness; only its order of magnitude and
sign should be read, never its value.

### Before and after, elongation only

Elongation is the quantity tithi is computed from, so it is the one that matters.

| Epoch | rms before | rms after | max before | max after | tithi err before | tithi err after |
|---|---|---|---|---|---|---|
| 1950 | 9.74″ | **2.51″** | 28.53″ | **9.88″** | 56 s | **19 s** |
| 2026 | 16.34″ | **2.88″** | 34.92″ | **8.79″** | 69 s | **17 s** |
| 2100 | 64.38″ | **10.71″** | 96.77″ | **22.62″** | 190 s | **45 s** |

At 2100, 23 s of that 45 s is the clock-removed part; the rest is the ΔT forecast
disagreement discussed below.

## What the numbers say

### 1. VSOP87D removed the Sun from the error budget entirely

Solar rms went 17.33″ → 0.10″ at 2026 and 8.87″ → 0.06″ at 1950. The residual is the more
telling column: with a constant time offset removed, the VSOP87 solar error is **0.01″ at
every epoch**. That is not "accurate enough", it is below the resolution of this
measurement. Whatever remains is a time-argument disagreement with Horizons, not a series
error.

For confirmation independent of JPL, the implementation reproduces all ten of Bretagnon &
Francou's own published check values (`vendor/vsop87_vsop87.chk`, ten epochs at century
intervals from 1099 to 2000) to **4.0e-11 rad in longitude, 4.4e-11 rad in latitude and
4.7e-11 au in radius** — the rounding floor of their printed 10-decimal values, i.e. 8
microarcseconds and 7 metres. That test needs no network and no JPL, and is the strongest
assertion in the module.

Solar *distance* improved as a side effect, and the weakness documented on
`Ephemeris.sunDistanceKm` no longer applies to this engine: VSOP87D's R is the Earth's
true perturbed radius vector with no Earth/EMB offset, against Meeus Ch. 25's unperturbed
Kepler radius which ran ~8100 km high. (It still applies to `MeeusEphemeris`.)

### 2. The ΔT fix removed 6.26 s today and 116 s at 2100

At 2026-08-01: Espenak & Meeus give 75.43 s, observed IERS gives **69.17 s**, difference
**−6.26 s**. The predicted ≈+5.9 s bias was real and is gone.

Its effect is visible in the Moon row, which is otherwise unchanged code: at 2026 the
fitted clock offset went **+5.15 s → −1.06 s** and rms **4.01″ → 2.86″**. At 2100 the clock
went **+135.87 s → +20.33 s**.

The ~20 s that remains at 2100 is not an error that can be fixed by better astronomy. It is
the difference between two extrapolations of the Earth's rotation — this module's, anchored
to the observed record's end, and JPL's, whichever it uses. Neither is knowledge. It is
consistent between the Moon (+20.33 s) and the Sun (+21.51 s), which is exactly the
signature of a clock disagreement rather than a series defect.

### 3. Fixing ΔT alone would have made the elongation *worse*

Recorded because it is counter-intuitive and because it justifies having done both changes
in one phase. On the unchanged `meeus` engine, 2026 elongation rms went **16.34″ → 18.38″**
and max **34.92″ → 38.29″**.

Nothing regressed. The old ΔT bias moved the Moon (≈+3.4″) in the same direction as Meeus
Ch. 25's solar bias (≈+12.4″), so the two partially cancelled in the difference. Removing
one cancellation without removing the other exposes more of what was always there. The
lesson is worth keeping: **two errors that cancel are still two errors**, and an aggregate
in which they cancel is not a safe thing to optimise against. The `vsop87` engine, with
both fixed, shows what the honest number had been all along.

### 4. The Moon is now the whole error budget, and it does not decay

Lunar error with the clock offset removed:

| 1950 | 2026 | 2100 |
|---|---|---|
| 2.48″ rms, 10.29″ max | 2.80″ rms, 8.11″ max | 2.79″ rms, 11.82″ max |

Flat across 150 years. The Meeus Ch. 47 truncation is not degrading with time; the apparent
decay in the Phase 1 baseline (2.50″ → 4.01″ → 75.00″) was ΔT and nothing else. Phase 1
already suspected this from the clock fit; this is the confirmation.

`Vsop87Ephemeris.claimedAccuracyArcsec` is therefore **12.0** — the largest of those lunar
residual maxima, 11.82″, rounded up. It excludes ΔT deliberately, because the interface
methods take `jdTt`: ΔT enters at the caller's UT→TT conversion and is bounded separately
by `DeltaT.qualityAtJd(jdUt)`:

| quality | dates | ΔT uncertainty | tithi boundary |
|---|---|---|---|
| `OBSERVED` | 1973-01-02 … 2026-07-30 | 0.007 s (monthly downsampling of the IERS daily record) | negligible |
| `PREDICTED` | … 2027-08-07 | ≲0.05 s (IERS Bulletin A) | negligible |
| `EXTRAPOLATED` | after 2027-08-07 | grows to ≈±10 s by 2100 | **≈±20 s** |
| `HISTORICAL_FIT` | before 1973-01-02 | Espenak & Meeus: sub-second to ~1955, ~1 s at 1600, worse before | 1–2 s in the modern era |

The ±10 s at 2100 is measured sensitivity, not a round number chosen to look humble. The
continuation's slope is a least-squares fit over the last 10 years of observed ΔT; refit
over 5, 15 and 20 year windows it ranges from −0.037 to +0.238 s/yr, and propagating that
spread to 2100 spans about 20 s. The curvature term (tidal braking, 0.0064 s/yr², halved to
0.0032 as the coefficient of the parabola) is the well-determined part. The slope is
decadal core–mantle coupling, and that part is genuinely unknown — which is why the Phase 1
`EXTRAPOLATED` warning stands unchanged in substance even though the number improved by
115 s.

**A 2100 calendar therefore carries ≈±20 s of boundary tolerance from ΔT and ≈23 s from the
Moon.** They are comparable today; they will not stay so once ELP2000 lands, at which point
ΔT becomes the sole limit on far-future dates and no further ephemeris work will move it.

## What this measurement still does not cover

Carried forward from the Phase 1 list, with what Phase 2A added or removed:

- **The 2100 raw rows compare two ΔT forecasts, not two ephemerides.** Only the
  clock-removed column there is about astronomy. Unchanged in kind from Phase 1, much
  smaller in size.
- **1950 ΔT is still the Espenak & Meeus fit.** `finals.all` begins at MJD 41684 =
  1973-01-02 and contains no earlier UT1. Pre-1973 accuracy is therefore *unchanged* by
  this phase, which is why the 1950 Moon row is bit-identical to the Phase 1 baseline.
  Extending the observed record back to 1962 via IERS EOP C04 is a real and cheap follow-up.
- **Three sample years, not an envelope.** Unchanged from Phase 1, and still the largest
  gap in the evidence behind `claimedAccuracyArcsec`. 12″ is a measured maximum over 1095
  samples rounded up, not a proof over all time.
- **Geocentric longitudes only.** Topocentric correction, sunrise, sunset, moonrise and
  moonset remain untested here.
- **The solar aberration** uses Meeus Ch. 25's higher-accuracy form (numerical derivative of
  the VSOP87 longitude with general precession removed). It agrees with the simpler
  −20.4898″/R to 0.003″, which is asserted by test, but it has not been checked against a
  third independent source.
- **`MeeusEphemeris.claimedAccuracyArcsec` was not revised** — that file was left untouched
  on purpose. Its ΔT improved, its Sun did not, and its measured numbers are in the table
  above for anyone who wants to revisit the constant.
