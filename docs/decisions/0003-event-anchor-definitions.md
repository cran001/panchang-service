# Two of the six observance anchors are inferred readings, and say so

**Status:** accepted, with **DUSK** and **NISITA_KALA** flagged for pandit review. Their
definitions ship inside the payload rather than only in this file, so a reviewer can challenge
the reading without reading any code. See `docs/pandit-review.md`, items 7a and 7b.
**Date:** 2026-08-02
**Arose from:** Wave 1's addition of `fastUntil` to the ISKCON event catalog — the first time this
project had to turn "Fast till dusk" into an instant.
**Related:** ADR 0002 (reopened 2026-08-02), which governs disagreements about a *date*. This ADR
governs disagreements about a *time of day*, and the two have opposite evidence situations: ADR
0002 has an oracle and disputes it, this one has no oracle at all.

## What is being decided

The catalog carried its time-of-day semantics only as English prose — "Fast till noon", "Fast till
moonrise", "Fast till midnight". A client could render the sentence and could not compute the time,
so the single piece of information a fasting devotee actually needs at 11:40 in the morning was the
one piece the payload did not contain. Six anchors now exist. Four of the six have exactly one
astronomical meaning; two do not, and this ADR records which reading was taken for those two and
why.

## The constraint that shapes everything below: there is no oracle for event times

Measured in this session, over the ten harvested reference calendars in `verify/golden/`, 365
records each:

| Prose | Occurrences per calendar | Observance it lands on |
|---|---|---|
| `Fast till noon` | **12** (11 at new-york and sao-paulo) | various acharya and appearance days |
| `Fast till sunset` | **1** | Rama Navami |
| `Fast till moonrise` | **1** | Gaura Purnima |
| `Fast till dusk` | **1** | Nrsimha Caturdasi |
| `Fast till midnight` | **1** | Sri Krsna Janmastami |

**158 such strings across all ten calendars. Zero of them carry a clock time.** Not one, at any
site, on any of 3650 day records.[^1]

So there is nothing to check an anchor's *instant* against. What can be checked is the astronomy:
our solar noon either is or is not the Sun's upper meridian transit at that longitude, and
`:core`'s solar and lunar work is measured against JPL Horizons and USNO (`docs/accuracy-baseline.md`,
`docs/validation-sunrise.md`). What cannot be checked, by any evidence this project has, is whether
the tradition means *solar* noon by "noon" — the reading of the prose.

> The counts above are what this session measured. The task brief that commissioned this ADR
> quoted `Fast till noon` ×10; the actual figure in the shipped goldens is 12 at eight sites and
> 11 at two. The measured number is the one recorded here. The other four counts match the brief
> exactly.

## The confidence grades the reading, not the arithmetic

This is the part most easily misread, and it is worth being blunt about.

`RuleConfidence.CONFIRMED` on an anchor means: **"this is correctly-computed solar noon at your
district."** It does **not** mean "this is the moment ISKCON says you may eat." Those are different
claims, and only the first is one this repository is in a position to make. A devotee reading a
CONFIRMED time is being told the sky was computed right, not that the doctrine was read right —
though for the four confirmed anchors the reading is not seriously in dispute, which is exactly
what the grade is recording.

The grade lives on the anchor (`ObservanceAnchor.mappingConfidence`) and is derived, not stored, by
`EventTime.confidence`, so no construction site can grade an anchor differently from the anchor's
own grading.

## Confirmed: four anchors whose words have one meaning

Quoted verbatim from the shipped `basis` constants. These strings travel into the JSON payload
(`wire`'s `EventTimeDto.basis`); they are not comments.

| Anchor | Grade | Used by |
|---|---|---|
| `SUNRISE` | CONFIRMED | no catalog entry anchors a fast to it today; defined and computed |
| `SOLAR_NOON` | CONFIRMED | 7 catalog entries |
| `SUNSET` | CONFIRMED | 1 (Rama Navami) |
| `MOONRISE` | CONFIRMED | 1 (Gaura Purnima) |

> sunrise: the Sun's upper limb on the horizon (centre at −0°50′), the same instant the day's
> panchanga is read at

> solar noon: the Sun's upper meridian transit at this longitude. Not 12:00 civil time, which can
> sit more than an hour away from it at a wide zone's edge

> sunset: the Sun's upper limb on the horizon (centre at −0°50′) while descending

> moonrise: the Moon's centre on the horizon while rising, including its parallax at the Moon's
> distance on the day

Noon is the only one of the four where the reading was ever really open, and it is closed by the
same argument that closes Nisita below: 12:00 on the clock is a fact about a government's choice of
meridian, not about the Sun, and no tradition anchors an observance to a government's choice of
meridian.

## Inferred 1 — NISITA_KALA, for "Fast till midnight"

**Decision.** The 8th of the 15 equal muhurtas of the night, the night taken from sunset on the day
to sunrise on the following day. Reported as an **interval**, not a point.

Shipped `basis`, verbatim:

> Nisita-kala: the 8th of the 15 equal muhurtas of the night, the night taken from sunset to the
> following sunrise — hence the muhurta containing solar midnight. Chosen over civil 00:00 because
> civil midnight is an artefact of the zone meridian and of DST — at a wide zone's western edge it
> sits over an hour from solar midnight, which would put the same observance at a different point
> of the night for two devotees in one country. Flagged INFERRED for pandit review.

**Why not civil 00:00.** Civil midnight is an artefact of where a government drew its zone
meridian and of whether it is currently on daylight saving. At the western edge of a wide zone it
sits more than an hour from solar midnight. Two devotees in one country, keeping Janmastami on the
same night, would then be breaking a fast at two materially different points of the night while
both believed they were following "midnight". Solar midnight is a fact about the sky at their own
longitude and does not have that property. The same reasoning already governs `SOLAR_NOON`, and
applying it to noon but not to midnight would be incoherent.

**Why an interval and not a point.** Nisita-kala is a muhurta — the tradition names a division of
the night, not an instant in it. Reporting the midpoint would be a precision the tradition does not
state. `EventTime.Window` exists for this, and it is one of the three reasons `core.RiseSet` was
the wrong type to reuse.

**Why the 8th of 15 contains solar midnight.** The night is divided into fifteen equal parts, as
the day is. The 8th part spans 7/15 to 8/15 of the night, i.e. 46.67% to 53.33% — it straddles the
midpoint by construction. For a 12-hour night that is a window of roughly 48 minutes centred on the
midpoint of sunset→sunrise. The midpoint of that interval and the Sun's true lower meridian transit
are not identical, because the Sun's declination and the equation of time both move during the
night; that offset was **not measured in this session**, and the claim made here is only that it is
far smaller than the ±24 minutes of margin the muhurta's half-width provides. If anyone ever finds
a site and date where it is not, this paragraph is the thing to check.

**What remains uncertain, and is a pandit's to settle.** Whether "midnight" in this calendar's
prose means Nisita-kala at all; and if it does, whether the night runs sunset→sunrise (taken here)
or some other pair of bounds, and whether the fast ends at the muhurta's start, its midpoint or its
end. All four readings are defensible and the source distinguishes none of them.

**Blast radius.** One observance in the shipped ISKCON catalog: Janmastami. That is one day a year
and one of the most widely kept fasts in the tradition, so a small count is not a small consequence.

**Absence.** Where the night cannot be bounded — a missing sunset or sunrise, or a "night" that
comes out non-positive because a site's civil zone is far from its longitude — the answer is
`Absent(NIGHT_NOT_WELL_DEFINED)`, carrying the same basis and no time field. An eighth of an
interval that does not exist is not computed and not guessed.

## Inferred 2 — DUSK, for "Fast till dusk"

**Decision.** End of civil twilight: the Sun's centre 6° below the horizon after sunset
(`PanchangCalculator.civilTwilightEnd`, `CIVIL_TWILIGHT_ALTITUDE_DEGREES = -6.0`).

Shipped `basis`, verbatim:

> dusk: end of civil twilight, the Sun's centre 6° below the horizon after sunset. Read as end of
> civil twilight rather than as sunset because the source distinguishes 'till dusk' from 'till
> sunset' and the two must not collapse. No horizon dip is applied: twilight is defined against the
> geometric horizon, which also keeps this consistent with the project's sea-level convention.
> Flagged INFERRED for pandit review.

**Why dusk could not simply be mapped to sunset.** That mapping was unavailable, not merely
unattractive. The catalog itself distinguishes the two: Rama Navami is `Fast till sunset` and
Nrsimha Caturdasi is `Fast till dusk`, and both strings appear, once each, in every one of the ten
reference calendars. Collapsing dusk onto sunset would erase a distinction the source itself drew
on two different festival days. Nrsimha Caturdasi is in practice broken *after* dark, which is
consistent with the source drawing that distinction deliberately.

**Why no atmospheric dip.** Twilight is defined against the geometric horizon, so the depression
angle is applied as −6° flat rather than −6° plus the observer's horizon dip. Rise and set do carry
the dip (via `Horizon.sunAltitudeDegrees`, which reads `GeoLocation.elevationMeters`); twilight
deliberately does not. This is also consistent with the project's sea-level convention, under which
`elevationMeters` defaults to 0 for gazetteer-resolved districts.

**What remains uncertain, and is a pandit's to settle.** Which twilight. Civil (−6°) was taken;
nautical (−12°) and astronomical (−18°) are also called "dusk" in English, and the Dharmasastra
notion of *pradosha* — the period following sunset — is a fourth reading and is not obviously any
of the three. The three differ by tens of minutes at temperate latitudes, so this is not a
hair-splitting distinction on the day it is used.

**Blast radius.** One observance: Nrsimha Caturdasi.

**Absence.** At high latitudes the Sun can set perfectly ordinarily and never reach −6° — a white
night. That produces `Absent(TWILIGHT_NOT_REACHED)`, deliberately *not* `CIRCUMPOLAR_UP`, which
would be a claim about the rise/set horizon that the twilight solve never tested.

## Alternatives rejected

**Omit the anchors and ship the prose only.** This is what the source data did and it is the
failure being fixed: the client can print "Fast till dusk" and cannot tell the user when that is.

**Ship the inferred anchors ungraded, alongside the confirmed ones.** Rejected under the brief's
second objective. An inferred reading presented with the same weight as a measured one is a
silent guess, and the whole point of `RuleConfidence` is that it cannot be.

**Refuse to compute the two inferred anchors at all.** Rejected: it would leave Janmastami and
Nrsimha Caturdasi — two of the most-kept fasts in the year — with no time at all, in service of a
purity that helps nobody, when a stated, challengeable reading is available.

## What would change this

- A pandit or the GBC ruling on either reading. That closes the item; it does not need new data.
- A published Gaudiya calendar that prints an actual clock time against any of these five prose
  forms. That would be the first oracle for event times this project has seen, and would move
  the confirmed four from "unchallenged" to "checked" as well.
- A citation from Hari-bhakti-vilasa or a comparable authority defining Nisita-kala's bounds.

## What this ADR does *not* settle

Anything a devotee should actually do. It records which of several defensible readings this
service implements, so that a reviewer can disagree with it specifically rather than in general.
Both inferred anchors are on the pandit queue and neither is resolved here.

[^1]: Counts produced in this session by walking every string in
`verify/golden/vaisnavacalendar-*.json` and matching `Fast till (\w+)` and `\d{1,2}:\d{2}`.
Anchor definitions quoted from `sampradaya/src/main/kotlin/org/panchang/sampradaya/EventTime.kt`
(`EventTimes.*_BASIS`) and `.../EventCatalog.kt` (`ObservanceAnchor`); catalog usage counts from
`.../IskconEventCatalog.kt` — 69 entries, 15 carrying a `fastingNote`, 11 carrying a `fastUntil`.
