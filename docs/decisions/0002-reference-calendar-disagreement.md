# We are more accurate than the calendar we validate against

**Status:** open question for the project owner. A default is implemented; it is reversible.
**Date:** 2026-08-01
**Arose from:** Phase 3G conformance work against `verify/golden/vaisnavacalendar-mayapur-2026.json`.

## The observation

Phase 3G matched every parana bound in the official Mayapur 2026 Gaudiya calendar, but only
by allowing an asymmetric tolerance on the lunar bounds. The offsets were not noise. Across
all 25 windows our tithi instants land **earlier** than the published ones, by 0.57 to 1.87
minutes, with the sign never once reversing.

## Why the difference is theirs

Tithi is the Moon−Sun elongation, advancing 1828″/hour, so a minute of time is 30.5″:

| | |
|---|---|
| Published calendar minus ours | 0.57–1.87 min = **17–57″** of elongation |
| Our measured error against JPL Horizons (2026) | 2.88″ rms, **8.79″ max** |
| Implied error of the published calendar against JPL | roughly **9–66″** |

The offset is up to seven times larger than the worst error we have measured in ourselves,
and our figure is not self-assessed — it comes from 365 daily samples against JPL Horizons,
with the ephemeris independently confirmed against Bretagnon & Francou's own published check
values to 8 microarcseconds (`docs/accuracy-baseline.md`).

A systematic one-signed offset of this size, against a bound we can verify to under 9″, is
the reference calendar's ephemeris, not ours. That is unsurprising: the community calendar
software predates cheap high-precision series and has no reason to carry VSOP87-class
accuracy for a result printed to the minute.

## What it costs today

Almost nothing, which is why this is a question and not an emergency.

- **Fasting dates: 0 disagreements in 169** across Mayapur 2021–2027.
- **Ekadashi names: 0 disagreements in 169.**
- **Mahadvadashi labels: 1 disagreement in 169.** At Mayapur 2025-06-07 the published
  calendar says Unmilani and we say Vyanjuli, because our Ekadashi ends 57 seconds before
  sunrise where theirs ends after it. The fasting date is the same either way.

The exposure is confined to tithi boundaries falling within about two minutes of sunrise or
arunodaya, which is roughly one day in seven hundred.

## The fork

**Match the published calendar.** Reproduce its ephemeris error so output is identical. This
is what the app's `ParanaCalibrationTable` was doing at a cruder scale — snapping computed
times to DrikPanchang within ±10 minutes — and its own header said to delete it. Reproducing
another program's error requires knowing which program and which version; it breaks when they
upgrade; and it is unmaintainable in the direction the brief actually cares about, which is
being right for any location on Earth rather than agreeing with one publisher.

**Compute correctly and report divergence.** Emit the astronomically correct instant and
detect where it disagrees with a published calendar, surfacing that rather than hiding it.

## Implemented default, and why

**Compute correctly and report divergence.** Three reasons.

1. The brief's first objective is that accuracy is the product. Deliberately introducing a
   57″ error to match a third party inverts that.
2. The plan already provisions a drift monitor, for a reason that applies here unchanged:
   **the GBC can override astronomy.** Official calendars sometimes reflect human decisions
   no ephemeris predicts. A service that silently matched the publisher could never
   distinguish "they made a ruling" from "their Moon is 50″ off" — and those need opposite
   responses. Computing correctly and diffing keeps them separable.
3. It is reversible. Matching later is a deliberate offset applied at one seam. Un-matching
   later means finding every place an error was baked in.

## What this does *not* settle

**Which date a devotee should actually keep.** That is not an astronomy question. Where the
official Vaisnava calendar and correct astronomy disagree on an observance, the official
calendar is what a temple follows, and this service saying otherwise would be wrong in the
way that matters even while being right about the sky.

The one known case is a Mahadvadashi *label*, not a date, so nothing turns on it yet. If a
future case moves a fasting date, that is a ruling for a pandit and the GBC, not for this
repository — and it should be surfaced loudly rather than resolved by whichever number the
code happened to produce.

## Reopen if

- A divergence moves an actual fasting date rather than a label.
- Phase 5's per-city gate shows the offset behaving differently away from Mayapur.
- The publisher's ephemeris is identified precisely enough that the ~50″ can be attributed
  rather than inferred. Right now we know the size and sign of the disagreement, and that it
  is not ours; we do not know its source.
