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
