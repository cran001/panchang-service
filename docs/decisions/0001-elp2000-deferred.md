# ELP2000-82B is deferred, on measured grounds

**Status:** deferred, not rejected. Revisit if the conformance gate (Phase 5) shows printed
times disagreeing with published calendars.
**Date:** 2026-08-01
**Supersedes:** the project plan's treatment of VSOP87 and ELP2000 as one Phase 2 work item.

## Decision

Ship with Meeus Ch. 47 for the Moon. Do not implement ELP2000-82B now, despite its
coefficient tables already being vendored and hash-pinned in `vendor/elp2000/`.

## Why

Phase 2A removed the Sun from the error budget and replaced extrapolated ΔT with observed
IERS values. After it, the Moon is the entire remaining error and it is smaller than the
plan assumed:

| | elongation rms | elongation max | → tithi boundary |
|---|---|---|---|
| Plan's assumption | — | — | ±6 min |
| Phase 1 baseline (2026) | 16.34″ | 34.92″ | 69 s |
| **After Phase 2A (2026)** | **2.88″** | **8.79″** | **17 s** |

ELP2000 would take the lunar residual from ~2.8″ to ~0.1″, so tithi boundaries would go from
about 17 s to under 1 s. The question is whether 17 s is detectable in the output, and for
observance dates it is almost never:

**Dates.** The ISKCON viddha test is binary — is Dashami running at arunodaya? A boundary
error flips that answer only when the true Dashami-end instant falls within the error of
arunodaya. That offset is effectively uniform across the day over many Ekadashis, so a ±17 s
error flips roughly `2 × 17 / 86400 ≈ 4 × 10⁻⁴` of them: with ~25 Ekadashis a year, **about
one flipped date per century**. The same argument covers every other tithi-at-sunrise rule in
the calendar.

**Times.** This is the real case for ELP2000, and it is weaker than it looks. Parana bounds
are printed to the minute, and one of them — the Dvadashi end — is a tithi boundary carrying
the full 17 s. So a printed minute can differ from a published calendar's. That is an
agreement problem, not a correctness problem: a devotee breaking their fast inside a window
whose end we print as 09:52 rather than 09:53 has kept the fast correctly.

**Far-future dates.** ELP2000 would not help there at all. Beyond 2027 the binding term is
the ΔT forecast, ≈±20 s of tithi boundary by 2100, which no ephemeris can reduce because it
depends on how fast the Earth will be turning. Sharpening the Moon from 17 s to 1 s under a
±20 s clock uncertainty buys nothing.

## What would reverse this

Phase 5 compares against published calendars per city. If printed parana times disagree in
the last digit often enough to matter, ELP2000 is the fix and the tables are already here.
That is a measurement we have not made yet, which is why this is "deferred" and not "no".

## Cost avoided

36 files, 2.5 MB of coefficients, a substantially more intricate series than VSOP87D's, and
a permanent maintenance surface — against a change no observance rule can detect. The
accuracy work that Phase 2A's measurements actually pointed at is the sampradaya rules, which
is where Phase 3 went instead.

## Honest caveat

The 17 s figure is a measured maximum over 1095 daily samples at three epochs (1950, 2026,
2100), not a proof over all time. `docs/accuracy-baseline.md` lists the absence of a
continuous 1900–2200 envelope as the largest remaining gap in the evidence, and that gap
applies to this decision too.
