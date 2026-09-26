# Sampradaya coverage

What this service computes for each tradition, where each rule came from, and what remains
unverified. Written to be read alongside `docs/pandit-review.md`, which holds the open ISKCON
questions; this page holds the regional-tradition ones.

The single most important fact on this page: **Ekadashi and parana timing is computed for
ISKCON only.** Every other tradition below ships a festival catalog and *no* fasting timing.
A request for a regional tradition's `ekadashiYear` returns a well-formed payload with an
empty `observances` list — never ISKCON's dates wearing another tradition's name.

## Status at a glance

| Tradition | id | Reckoning | New year entry | Status | Catalog entries |
|---|---|---|---|---|---|
| ISKCON / Gaudiya | `iskcon` | Purnimanta | — (Gaura Purnima era) | **VERIFIED** | full + Ekadashi/parana |
| Marathi | `marathi` | Amanta | `gudi_padwa` | UNVERIFIED | 14 |
| Telugu | `telugu` | Amanta | `ugadi` | UNVERIFIED | 8 |
| Kannada | `kannada` | Amanta | `ugadi` | UNVERIFIED | 7 |
| Gujarati | `gujarati` | Amanta | `bestu_varas` (Kartika Shukla 1) | UNVERIFIED | 8 |
| North Indian Hindi | `northindian` | **Purnimanta** | `chaitra_navratri_begins` | UNVERIFIED | 18 |
| Tamil | `tamil` | Solar (Sankranti-opened) | `puthandu` | UNVERIFIED | 4 |
| Malayalam | `malayalam` | Solar (Kollam era noted) | `chingam_1`; `vishu` also dated | UNVERIFIED | 3 |
| Bengali | `bengali` | Solar year + amanta festivals | `pohela_boishakh` | UNVERIFIED | 7 |
| Odia | `odia` | Solar year + amanta festivals | `pana_sankranti` | UNVERIFIED | 6 |

All ten are registered at every front door (`:calc`, `:api`, `:publish`) through the
`Sampradayas` object in `calc/CalcEngine.kt`.

## What the regional catalogs contain, and why so few entries

Every entry in a regional catalog is **definitional**: the tithi or Sankranti named in the
entry is the festival's own rule — Ganesh Chaturthi *is* Bhadrapada Shukla Chaturthi,
Thai Pongal *is* the Makara Sankranti day. Definitional rules were chosen deliberately over
inference from a single year's printed calendar: they can be wrong about a local convention,
but they cannot be silently wrong about nothing, and they extrapolate to any year.

Each `{Tradition}EventCatalog` carries a `KNOWN_GAPS` map naming what was *deliberately
omitted* and why — the list a reviewing pandit most needs. The recurring omissions:

- **Nakshatra within a solar month** (Tamil: Thai Poosam, Panguni Uthiram, Karthigai Deepam;
  Malayalam: Onam, Thiruvathira). `EventRule.OnNakshatraInMonth` matches a nakshatra within a
  *lunar* month and would misdate these, so they are absent rather than approximated.
- **Tithi–weekday conjunctions** (Hartalika, Varalakshmi Vratam, Ahoi Ashtami). No rule form
  expresses the weekday; a tithi-only rule would sometimes place them on the wrong weekday.
- **Time-of-day conventions** (Karva Chauth's moonrise, Kali Puja's midnight, Vishu Kani's
  pre-dawn). The date is carried; the convention is not, and the sourceNote says so.
- **All Ekadashi vrata.** See the top of this page.

## Rule forms added for the solar calendars

`EventRule.OnSolarMonth(solarMonthIndex, dayOfMonth, transitionPoint)` — a day of a sidereal
solar month, 1 = Mesha. Resolved by `SolarIngressIndex`, which walks the window's solar noons,
detects each sign change and bisects the ingress instant to sub-second precision. Computed
2026 ingresses at Chennai: Mesha 14 Apr 09:24 IST, Simha 17 Aug, Makara 14 Jan — the published
Sankranti dates for 2026 (Drik Panchang prints the Mesha Sankranti on 14 April 2026 at
09:39 IST; the ~15-minute difference is the known ayanamsha/ephemeris gap, immaterial at
day resolution).

`SolarTransition` holds the two month-opening conventions in live use:

- `SANKRANTI_START` — the civil day containing the ingress is day 1 (Tamil, Malayalam, Odia).
- `SANKRANTI_AT_SUNRISE` — the first day whose sunrise follows the ingress is day 1 (Bengali).

The 2026 new years show the difference exactly: the Mesha ingress falls on 14 April at ~09:24
IST, after that morning's sunrise, so the Tamil and Odia new years (`SANKRANTI_START`) land on
**14 April** and the Bengali new year (`SANKRANTI_AT_SUNRISE`) lands on **15 April** — which is
what the published calendars print for Puthandu, Pana Sankranti and Pohela Boishakh 2026
respectively (Pohela Boishakh 2026: 15 April in West Bengal, per the Indian Express and Drik
Panchang; 14 April in Bangladesh under the revised fixed calendar, which this service does not
model).

`EventRule.In60YearCycle(cycleYear, baseRule)` — a rule reserved for one position in the
sixty-year Samvatsara cycle. The mapping (`SixtyYearCycle`) is anchored to the published
southern-list cycle years — 2024 = Krodhi (43rd), 2025 = Vishvavasu (44th), 2026 = Parabhava
(45th) — and holds arithmetic only, **not the sixty names**: reproducing a name list from
memory risked a misremembered name travelling into rendered output. Two cautions documented
on the object: the cyclic year turns over at Ugadi/Puthandu (March–April), not 1 January, so
whole-year gating smears the first eleven weeks; and the northern (Vikram) list diverges from
the southern in the later part of the cycle — the northern year opening March 2026 is Sharvari
where the southern is Parabhava. No shipped catalog entry uses `In60YearCycle` yet (no sourced
event needs it); it is exercised by `SolarRuleTest` with synthetic rules.

## The 2026 kshaya new year — a gap that needs a pandit

The Chaitra Shukla Pratipada of March 2026 is **kshaya across India**: this engine places it
from 06:53 IST on 19 March to 04:52 IST on 20 March — after the sunrise of the 19th and before
the sunrise of the 20th, so no day carries it. Drik Panchang prints the same window (06:52 to
04:52) and **still dates Ugadi/Gudi Padwa on 19 March 2026**, under a fallback ruling
(some traditions take the day the tithi begins; some require the tithi to last a stated
fraction of the day; the rulings differ and this project has no source for any of them).

This service therefore reports the new year as **loudly skipped** (`TithiSkipped` in
`yearResolution.unresolved`) for the four Chaitra-opening traditions — Marathi, Telugu,
Kannada, North Indian — in 2026, rather than guessing the fallback day. The catalogs resolve
normally in ordinary years (the tests assert 2027). **This is the first question to put to a
pandit when one is next consulted**, and it will recur roughly one year in fifteen: the same
kshaya affected nothing in 2025 and returns whenever the Pratipada compresses past a sunrise.

## Ekadashi and parana: ISKCON only, verified worldwide

The Gaudiya Ekadashi rules are unchanged by this expansion and remain the only implemented
fasting rules: viddha tests at arunodaya, the eight Mahadvadashis, Hari Vasara, and parana
windows capped by the Dvadashi's end, the first third of daylight, or a qualifying
nakshatra. The conformance suite runs them against published calendars at ten sites —
Mayapur, Vrindavan, Delhi, Mumbai, plus Auckland, Moscow, Sydney and others across the world.

Known, documented disagreements remain at exactly nine assertions (unchanged by this work),
all listed in `docs/pandit-review.md` with their margins: the Vrindavan Nirjala Ekadashi
knife-edge (a 13.4-second sunrise difference), four Mahadvadashi labels, two Auckland parana
bases and two deferral labels. The full gate before and after this expansion: **179 ISKCON
tests, the same 9 failing; 221 sampradaya-module tests with the 42 new ones all passing.**

### The high-latitude parana behaviour (the "Reykjavik bug")

At Reykjavik (64.15°N — accepted, since its Sun sets every day of the year) the late-June
civil days have **no usable daylight interval**: around the solstice the sunset falls just
past civil midnight into the next civil day, so `SunTimes.daylightDays` is null or negative
there. The parana window never claims a daylight third it could not compute:

- When the Dvadashi (or a qualifying nakshatra) still bounds the window, it is emitted with
  that bound *as its own label*. On 2026-06-26 at Reykjavik — the exact case the expansion
  guide filed — the window runs 834 minutes from sunrise to the Dvadashi's end and is labelled
  `DVADASHI_END`, not silently capped.
- When no bound survives at all, the window is refused with a sentence and
  `confidence: INFERRED` — never emitted uncapped, never invented.

`ReykjavikParanaTest` pins both the premise (the unusable days exist) and the behaviour;
`ParanaDaylightEdgeTest` sweeps the polar longitudes with the same invariants.

## Site acceptance: the dynamic midnight-sun boundary

The fixed 66° polar gate is replaced by a computed one in `wire/SiteAcceptance.kt`:
`hasMidnightSunOrPolarNight(latitude, elevationMeters)` rejects a site when the Sun would stay
above the rise/set horizon at local midnight at midsummer — `latitude > 90° − 23.44° − 0.833°`
≈ **65.73°** at sea level, lower for an elevated site (horizon dip). The 0.8333° is
`core.Horizon.SUN_ALTITUDE_DEGREES`, the same refraction-and-semidiameter allowance the
solver itself uses to decide what a sunrise is, so the gate and the solver cannot disagree
about whether the Sun is up. Reykjavik (64.15°) stays accepted; Longyearbyen and McMurdo stay
refused; Akureyri (65.68°) stays accepted with 0.05° to spare.

The rejection code remains `ABOVE_POLAR_LIMIT` (a serialised enum used by the API), with the
reason text now naming the computed boundary. `POLAR_LIMIT_DEG` is gone; the boundary is
`MIDNIGHT_SUN_LIMIT_DEG`.

## Content-hub compatibility

The expansion changes no wire schema. Verified end-to-end by
`publish/RegionalFeedPublishTest`, which runs the Marathi tradition through the whole feed
pipeline:

- **v1 payloads** (`YearResolutionDto`, `EkadashiYearDto`) remain schema-version-1 document
  roots; a regional tradition's `ekadashiYear` carries `"observances": []` and the
  tradition's own `status: "UNVERIFIED"` and provenance note, so a hub client can say why
  timing is absent.
- **Legacy day files** (the shape the shipped Android app parses, per
  `docs/legacy-contract.md`) carry the festivals with the same `+`-prefix conventions, every
  date of the year in stride order, and **no** Ekadashi fasting or "Break fast" parana items —
  the app would treat those as fasting days and mis-anchor alarms.
- **`zones.json` / `locations.json`** are unchanged in shape; every run still publishes the
  P0000 fallback directory, per the owner's standing decision.
- The `FeedPublisher` takes `sampradayaId` per run, so a hub deployment can publish one
  directory tree per tradition without schema forks.

## What remains unverified, plainly

- **No regional catalog has been compared against a published calendar for any year** beyond
  the spot-checks named above (2026 new-year dates and Sankranti instants). The spot-checks
  agreed; they are spot-checks, not conformance.
- Each tradition's `provenanceNote` says the same thing in one sentence, and every payload
  carries it.
- **Night-and-evening conventions can print a day earlier than published calendars**: this
  engine dates a tithi at sunrise, so for 2026 it places Maha Shivaratri on 16 February and
  the Diwali Amavasya on 9 November, where panchangs applying the nishita (midnight) or
  pradosha (evening) conventions on the same tithi print 15 February and 8–9 November
  respectively. Same tithi, different reading of which civil day owns the night; every such
  entry says in its sourceNote that the night convention is not modelled.
- Regional Ekadashi rules (viddha tests, parana bounds, any Mahadvadashi equivalents) are
  unimplemented and unsourced; implementing them needs a reference calendar and a stated rule
  source per tradition.
- Local conventions not modelled include: the Bengali revised calendar's fixed dates, the
  Gujarati and Marathi regional vrata calendars, Kollam Varsham year numbering (the era is
  noted; the number is not computed), and every time-of-day convention listed in the gaps.

## Deviations from the expansion guide, and why

The guide (`docs/sampradaya-expansion-guide.md`) sketched the work; three of its sketches
were improved rather than followed literally:

1. **The Reykjavik fix** as sketched (refuse whenever `daylight == null`) was already
   superseded in the codebase by a more precise ruling: a window with no daylight cap is
   emitted only when a real bound (Dvadashi or nakshatra end) exists and is carried *as its
   own label*, and refused otherwise. At Reykjavik the daylight is negative rather than null
   (civil-day accounting, not midnight sun), so the sketch's literal check would not even
   have fired. This work verified the behaviour and pinned it in `ReykjavikParanaTest`
   rather than re-implementing it.
2. **The polar gate formula** in the guide is arithmetically off (`66.5 − 23.44 = 43.06`,
   commented as "~63.06"); following it would have rejected London. The implemented check
   uses the boundary its own prose describes — the solver's horizon turned into a latitude
   — which is 65.73° at sea level.
3. **`RuleConfidence.UNVERIFIED`** does not exist in the enum (CONFIRMED / INFERRED /
   TABULATED). Unverified definitional entries ship as `INFERRED` with the tradition-level
   `VerificationStatus.UNVERIFIED` and a plain statement in every sourceNote; the mapping is
   stated in each catalog's KDoc.
