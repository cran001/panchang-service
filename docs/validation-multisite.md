# Multi-location conformance gate

Enforced continuously by `sampradaya/src/test/kotlin/org/panchang/sampradaya/`:
`GaudiyaEkadashiConformanceTest` (parameterised over ten sites) and
`MultiSiteParanaConformanceTest` (cross-site and DST-transition claims). Both are committed
tests, not one-off measurements.

**This page was written before the gate was first run**, deliberately. The expected failure
categories below were fixed in advance so that a red result reads as data rather than as a
crisis, and so that no category could be invented after the fact to fit whatever came out. The
"Result" section was filled in afterwards.

## The gap this closes

Through Wave 0 every observance test pinned Mayapur. Location-generality was therefore asserted
*by construction* — "no coordinate is hardcoded, everything flows from `ObservanceContext.location`"
— and never by evidence. That is a weaker claim than it looks. A service that computed one city's
timings and relabelled them would satisfy it, pass a single-site suite, and be wrong by roughly an
hour for every user elsewhere.

Ten harvested calendars now exist in `verify/golden/vaisnavacalendar-{city}-2026.json`, 365 records
each. The gate walks all ten.

## Coordinates come from the artifact, never from the reference grid

Each fixture is built from the golden file's own `site` block, not from `ReferenceCities`. The
source prints Delhi as `28N40 77E13` (28.6667/77.2167) where our grid carries 28.6139/77.2090 — a
difference of about twelve seconds of sunrise. Evaluating a reference at coordinates it was not
computed for makes a **datum** disagreement and a **rule** disagreement indistinguishable the
moment a time is truncated to the printed minute, and the entire value of this gate is that those
two stay separable.

The Mayapur file predates the site block and returns `null` from `GaudiyaGoldenCalendar.site`; the
`MAYAPUR_LATITUDE`/`LONGITUDE`/`ZONE` constants — themselves the source's own printed `23N25 88E23`
— cover that one case, and a test asserts that only Mayapur may take that path.

| City | Printed by the source | Latitude | Longitude | IANA zone | Header offset |
|---|---|---|---|---|---|
| mayapur | *(no site block)* | 23.4167 | 88.3833 | Asia/Kolkata | — |
| vrindavan | Vrindavan [India] | 27.5833 | 77.7000 | Asia/Kolkata | +5:30 |
| delhi | Delhi [India] | 28.6667 | 77.2167 | Asia/Kolkata | +5:30 |
| mumbai | Bombay [India] | 18.9833 | 72.8333 | Asia/Kolkata | +5:30 |
| london | London [United Kingdom] | 51.5167 | −0.1333 | Europe/London | +0:00 |
| new-york | New York City [USA] | 40.7167 | −74.0000 | America/New_York | −5:00 |
| sao-paulo | Sao Paulo [Brazil] | −23.5500 | −46.6333 | America/Sao_Paulo | −3:00 |
| moscow | Moskva [Russia] | 55.7500 | 37.6167 | Europe/Moscow | +3:00 |
| auckland | Auckland [New Zealand] | −36.8667 | 174.7667 | Pacific/Auckland | +12:00 |
| sydney | Sydney [Australia] | −33.8667 | 151.2000 | Australia/Sydney | +10:00 |

Four southern-hemisphere sites, eight distinct zones, six of them observing daylight saving in
2026, and four sites sharing a single civil clock.

## Tolerances were not widened

The bands are the Mayapur values from Wave 0, unchanged:

- **Solar bounds** (sunrise, 1/3 daylight): `[−0.25, +1.25]` minutes. The source truncates to the
  minute, so `[0, 1)` plus a quarter minute either side.
- **Lunar bounds** (1/4 tithi, end of tithi, end of nakshatra): `[−3.0, +1.25]` minutes. The
  asymmetry is not slack; it is the documented, one-signed offset of ADR 0002 — our tithi instants
  run 0.57–1.87 min *earlier* than the published ones, which is ~17–57″ of Moon−Sun elongation
  against our own 8.79″ max error versus JPL Horizons.

A band widened until a gate goes green measures nothing. Where a site disagrees, the disagreement
is recorded below, not absorbed.

## Expected failure categories (written before the run)

### (a) Stale daylight saving in the reference — `StaleReferenceDst`

GCal 11 Build 5 applies daylight saving that **Russia abolished in 2011** and **Brazil abolished in
2019**. Fifteen Moscow parana rows carry the `DST` stamp between 2026-03-30 and 2026-10-23 (the
pre-2011 Russian rule, which followed the EU dates); nine São Paulo rows carry it in
2026-01-15…02-28 and 2026-10-23…12-21 (the pre-2019 Brazilian rule).

The stamp costs a real hour, not just a label: Moscow's 2026-06-12 parana starts at a printed
`04:45` where sunrise at 55°45′N 37°37′E is about `03:45` MSK. London, New York, Auckland and
Sydney all stamp `DST` on exactly the days their zones really are in daylight saving in 2026, so
this is specific to two cities. **It is a defect in the reference, not in us.**

**Response: a named exclusion, not a widened tolerance and not an edit to the golden file.** Only
the *clock-time comparison* is skipped on those rows. Fasting dates, Ekadashi names, Mahadvadashi
labels and the bound *bases* are still compared at both cities, because a civil offset applied to a
printed time cannot move any of them. A tolerance wide enough to swallow an hour would swallow
every rule error this gate exists to catch.

The exclusion is pinned by `the stale-DST exclusion still describes the reference`, which asserts
that the set of cities whose stamps disagree with tzdata is exactly `{moscow, sao-paulo}`, that the
counts are exactly 15 and 9, and that each excluded row is out by 58–62 minutes — an hour of civil
offset and nothing else. If a re-harvested calendar drops the stale stamps, that test fails and the
exclusion can be deleted. An exclusion nobody is told has stopped being necessary becomes permanent
by accident.

### (b) Open-ended parana windows

Three calendars print one "Break fast after…" window each where the source declines to state a cap:

| City | Date |
|---|---|
| auckland | 2026-09-23 |
| mumbai | 2026-08-24 |
| new-york | 2026-10-22 |

**Response: assert the start bound only.** No cap is invented — a fabricated end would be a claim
about a tradition that the only reference available declines to make — and the assertion is not
deleted, because deleting it would quietly stop checking the start as well. The suite additionally
fails if a file ever states an end *basis* with no end *time*, since the allowance assumes both are
absent together.

### (c) Tithi-boundary flips (ADR 0002)

Our tithi instants run 0.57–1.87 min earlier than the published ones, consistently signed. Tithi
advances 1828″/hour, so one minute is 30.5″. ADR 0002 predicts roughly **0–2 Mahadvadashi *label*
disagreements across 240 fortnights** (ten sites × 24), from fortnights where a tithi ends within
about two minutes of sunrise or arunodaya and lands on the other side of it for us than for them.

**Response: classify as (c) only when the *fasting date still matches*.** A label moving while the
date holds is exactly the outcome ADR 0002 predicts and describes as costing nothing a devotee
experiences. It is recorded here, not suppressed.

### A fasting-date disagreement is not category (c)

If a *fasting date* disagrees anywhere, that is a different and much stronger signal. It must be
investigated as a **rule error first**, and attributed to the reference's ephemeris only if that
can actually be shown. ADR 0002's own "reopen if" list names this case explicitly. Such a finding
is reported prominently with city, date, our answer, the reference's answer and the evidence for
the cause — not excluded, not tolerated, not adjusted away. A wrong fasting date is not a rounding
error to the person who kept the fast on it.

## What `MultiSiteParanaConformanceTest` adds that no per-site comparison can

Every assertion in the parameterised suite is of the form "this site agrees with its own oracle".
Two properties are invisible to any number of such comparisons:

1. **That location actually reaches the answer.** Caught only by comparing two sites *to each
   other*, and most sharply by two sites in one time zone, where a copied timing is not even
   visibly wrong on the clock face. Delhi and Mumbai are both `Asia/Kolkata`. The test asserts
   their sunrise-based parana starts differ by more than 10 minutes, that the Delhi-minus-Mumbai
   offset we compute matches the offset the two published calendars print to within 1.5 minutes
   (truncation at both ends can move a difference by just under a minute either way), and that
   Mayapur versus Mumbai — 15.5° of longitude in one zone — exceeds 50 minutes.
2. **That the civil offset is right on the two days a year it changes.** No harvested day-by-day
   comparison is guaranteed to land near a transition; the fasts fall where the Moon puts them, not
   where the clocks do. There was no DST-transition test in this repository before Wave 1. London
   (spring forward, March, northern) and Auckland (fall back, April, southern) cover both
   directions in both hemispheres. The test states its premises from `java.time`'s own zone rules
   first, so a tzdata update that moves a transition changes the premise loudly instead of quietly
   retargeting the test at an ordinary day.

## Result

First run 2026-08-02, `./gradlew --rerun-tasks --no-daemon :sampradaya:test :verify:test`, counts
read from `build/test-results/test/*.xml` rather than from the console.

| Module | tests | skipped | failures | errors |
|---|---|---|---|---|
| `:sampradaya` | 141 | 0 | **9** | 0 |
| `:verify` | 97 | 0 | 0 | 0 |

`GaudiyaEkadashiConformanceTest` 91 tests / 9 failures; `MultiSiteParanaConformanceTest` 5 / 0;
`EventResolverTest` 20 / 0; `EventTimeTest` 16 / 0; `IskconEventCatalogConformanceTest` 7 / 0;
`ParanaDaylightEdgeTest` 2 / 0.

**The gate is red, deliberately and visibly.** Nine failures across four sites. Nothing below was
made to pass by widening a band or by editing a golden file.

### Per site

Nine parameterised assertions run at each site: fasting dates, one-decision-per-date, Ekadashi
names, Mahadvadashi labels, deferral classification, parana windows, every-fast-has-a-window,
every-decision-explains-itself, and coordinates-from-the-site-block.

| Site | Passed | Failed | Parana rows | Excluded (a) | Uncapped (b) | Disagreements |
|---|---|---|---|---|---|---|
| mayapur | 9/9 | — | 25 | 0 | 0 | none |
| vrindavan | 6/9 | fasting dates, Mahadvadashi labels, parana | 25 | 0 | 0 | **2026-06-25 vs 06-26 fasting date** (+2 knock-ons) |
| delhi | 9/9 | — | 25 | 0 | 0 | none |
| mumbai | 9/9 | — | 25 | 0 | 1 | none |
| london | 9/9 | — | 24 | 0 | 0 | none |
| new-york | 9/9 | — | 24 | 0 | 1 | none |
| sao-paulo | 9/9 | — | 24 | 9 | 0 | none |
| moscow | 7/9 | Mahadvadashi labels, deferral classification | 24 | 15 | 0 | 2026-05-27 Vyanjuli label |
| auckland | 7/9 | Mahadvadashi labels, parana | 25 | 0 | 1 | 2026-10-07 Trisprsa label; 2026-04-28 and 2026-10-08 parana bases |
| sydney | 7/9 | Mahadvadashi labels, deferral classification | 25 | 0 | 0 | 2026-12-05 Vyanjuli label |

**246 printed parana windows, 240 fasting days, 240 fortnights.** Of those: **1 fasting-date
disagreement**, 4 Mahadvadashi label disagreements, 3 parana bound disagreements at one site,
24 rows excluded under (a), 3 rows handled under (b).

### Category (a) — closed, as predicted

Exactly as written above the run. `the stale-DST exclusion still describes the reference` passes:
the set of cities whose `DST` stamps disagree with tzdata is exactly `{moscow, sao-paulo}`, the
counts are exactly 15 and 9, both zones have no 2026 offset transitions at all, and every excluded
row's printed start is 58–62 minutes from our computed sunrise — one hour of civil offset and
nothing else. Six other sites' stamps agree with tzdata on every printed row.

### Category (b) — not what it looked like, and now an assertion rather than an allowance

The three uncapped rows are **not** rows where the publisher merely declined to state an end. On
all three the source's own two caps are in conflict: **Hari Vasara — the first quarter of the
Dvadashi, before whose end the fast may not be broken — ends after the first third of daylight,
before whose end it must be broken.**

| Site | Window | Hari Vasara ends | First third of daylight ends | Printed |
|---|---|---|---|---|
| mumbai | 2026-08-24 | 10:49:47 | 10:34:39 | `10:50`, `1/4 of tithi`, no end |
| auckland | 2026-09-23 | 10:30:39 | 10:12:24 | `10:31`, `1/4 of tithi`, no end |
| new-york | 2026-10-22 | 11:15:28 | 10:51:46 | `11:16`, `1/4 of tithi`, no end |

`IskconRules` meets the same conflict on the same day at the same site and resolves it the other
way: it refuses to emit an inverted window and says in the decision's own reason that the case
needs a pandit's ruling rather than a computed answer.

The start bound is asserted, re-derived from the index's Dvadashi spans by
`GaudiyaSiteFixture.hariVasaraEndOn` rather than read off a window that by construction does not
exist; all three agree with the printed start to under 0.6 min. And
`the source's uncapped windows are exactly the days the rules refuse to bound one` asserts the
set equality across all ten sites — three sites, three dates, **no site carrying one without the
other in either direction**. That equality is the evidence that both programs are detecting the
same geometric conflict; the difference between them is doctrinal, not astronomical.

> **Open question for a pandit.** When Hari Vasara outlasts the first third of daylight, may the
> fast be broken after the daylight third (the source's implicit answer), or is there a ruling this
> repository does not have? Three occurrences in 2026 across ten sites. Nothing here decides it.

### Category (c) — tithi-boundary flips, 2 of 4 label disagreements

Both are sub-30-second margins between a tithi boundary and a sunrise, in the direction ADR 0002
predicts and by less than the offset it documents.

**Auckland 2026-10-07 / 2026-10-08.** One root cause, three symptoms. Our Dvadashi ends
2026-10-08 06:46:57.06 NZDT; Auckland sunrise that day is 06:47:17.59 — the Dvadashi ends
**20.5 s before sunrise**. We therefore see a Dvadashi kshaya and call the fast Trisprsa; the
reference sees the Dvadashi at sunrise on 10-08 (its calendar prints Dvadasi on 10-08 and
Caturdasi on 10-09, with no Trayodasi day at all — the *Trayodashi* is its kshaya tithi) and prints
no Mahadvadashi line. The same 20.5 s moves the parana cap: the reference caps at `end of tithi`
06:47 (a printed window of at most 60 seconds, `06:47`–`06:47`), we cap at the first third of
daylight, 11:01:42. ADR 0002's documented offset is 34–112 s, larger than 20.5 s and in the right
direction. **The fasting date is 2026-10-07 either way.**

**Auckland 2026-04-28 parana start basis.** Our Hari Vasara end is 06:55:23.5, sunrise is
06:57:19.4, so Hari Vasara ends 1.93 min *before* sunrise and our start basis is `sunrise`. The
reference prints `06:57` with basis `1/4 of tithi`, so its Hari Vasara end lies in
`[06:57:00, 06:58:00)` — 1.61 to 2.61 min later than ours. The sign matches ADR 0002; the magnitude
is at or just above the 0.57–1.87 min range measured at Mayapur. **The printed time agrees; only
the basis label differs.**

### Unexplained — 2 of 4 label disagreements

**Moscow 2026-05-27 (Padmini Ekadasi) and Sydney 2026-12-05 (Utpanna Ekadasi).** We classify both
as Vyanjuli Mahadvadashi; the reference puts the fast on the same day and prints no Mahadvadashi
line. The fasting dates match.

What makes this unexplained rather than category (c) is that the reference's own printed rows
satisfy the reference's own published Vyanjuli rule — "If Dvadasi falls on the sunrise two days in
a row the first Dvadasi becomes Vyanjuli Mahadvadasi". Moscow prints `Dvadasi` on both 05-27 and
05-28; Sydney prints `Dvadasi` on both 12-05 and 12-06. Meanwhile the identical structure at
Mayapur, Vrindavan and Delhi on 2026-08-24 *is* labelled Vyanjuli. Five occurrences, three
labelled, two not.

Hypotheses tested against the ten calendars and **refuted**:

- *Ekadashi purity*, by analogy with the Unmilani branch, where a viddha Ekadashi earns no name.
  All five have `Ekadasi (not suitable for fasting)` on the preceding day, so viddha does not
  separate them.
- *Paksha.* Moscow is gaura, Sydney krishna, the labelled group gaura.
- *A knife-edge boundary.* The Dvadashi's first sunrise falls 7.0 min inside it at Moscow and
  21.8 min inside it at Sydney — one to two orders of magnitude beyond the reference's documented
  offset, so this is not a category (c) flip.
- *The `DST` stamp.* Both unlabelled rows are stamped `DST` and all three labelled ones `LT`, but
  London 2026-08-09, New York 2026-09-07, São Paulo 2026-02-13 and Sydney 2026-01-15 all carry
  Mahadvadashi labels inside a DST period.

Adhika-month suppression would explain Moscow (Purusottama-adhika) but not Sydney (Margasirsa), so
it is not offered as an explanation for either.

> **Open question for a pandit.** Either the tradition carries a further condition on Vyanjuli that
> none of the sources consulted states, or the reference is internally inconsistent between these
> five fortnights. Not guessed at, not excluded, not tolerated. Note that whichever way it is
> settled, the *fasting date* is unaffected: `IskconRules` branches 5 and 7 both put the fast on
> the first Dvadashi, so this is a naming question only.

---

## ⚠ A FASTING-DATE DISAGREEMENT: Vrindavan, Pandava Nirjala Ekadashi

**This is the strongest signal in the run and the reason the gate exists.**

| | |
|---|---|
| Site | vrindavan (27.5833 N, 77.7000 E, Asia/Kolkata) |
| Fast | Pandava Nirjala Ekadashi — a full-day waterless fast |
| We say | **2026-06-25**, on the Ekadashi |
| The reference says | **2026-06-26**, on the Dvadashi, labelled `Paksa vardhini Mahadvadasi` |

### Investigated as a rule error first. It is not one.

The rule involved is Paksavardhini: the Purnima or Amavasya closing the fortnight is running at
sunrise on two successive days, the fortnight is lengthened, and the fast moves to the Dvadashi.

The closing Purnima ends at **2026-06-30 05:26:39.8 IST**. That is an instant — it is the same
number at all four `Asia/Kolkata` sites. The only thing that differs between them is sunrise on
2026-06-30:

| Site | Sunrise 2026-06-30 | Purnima end minus sunrise | Sunrises in the Purnima | Our verdict | Reference |
|---|---|---|---|---|---|
| delhi | 05:26:19.0 | **+20.9 s** | 06-29, 06-30 | Paksavardhini, fast 06-26 | agrees |
| mayapur | (earlier) | + | 06-29, 06-30 | Paksavardhini, fast 06-26 | agrees |
| **vrindavan** | **05:26:53.3** | **−13.4 s** | 06-29 only | plain Ekadashi, fast 06-25 | **disagrees** |
| mumbai | (later) | − | 06-29 only | plain Ekadashi, fast 06-25 | agrees |

The same rule, evaluated from the same tithi instants, reproduces the reference at the three sites
where the margin is large, in **both** directions — it fires at Delhi and Mayapur and correctly
does not fire at Mumbai, where the reference also puts the fast on the Ekadashi. Only Vrindavan
flips, and only because its sunrise lands 13.4 seconds on the other side of one instant. A rule
error would not be selective like that.

### What the 13.4 seconds is worth against our own error bars

| Quantity | Our documented accuracy | In seconds of tithi time |
|---|---|---|
| Tithi instant vs JPL Horizons (ADR 0002, `docs/accuracy-baseline.md`) | 2.88″ rms, 8.79″ max | ≤ **17.3 s** |
| Sunrise vs USNO (`docs/validation-sunrise.md`) | +0.3 s mean, 28 s worst | ~30 s, USNO-resolution-limited |
| The reference's documented one-signed lateness (ADR 0002) | +0.57 to +1.87 min | **+34 to +112 s** |

**The margin that decides this fasting date, 13.4 s, is smaller than our own worst-case error in
either input.** We cannot resolve this case with the accuracy we currently have. Separately, the
reference's documented offset applied to the Purnima end — it runs *later* than ours, always —
would on its own put the 06-30 sunrise inside the Purnima and produce the reference's answer.

That is consistent with the reference's ephemeris being the cause. **It is not a demonstration of
it**, and it is not claimed as one here. What is established is narrower and, for this purpose,
enough: it is not a rule error, and it is below the resolving power of the reference data we have.

### Required response

1. **The test stays red.** No tolerance was widened, no exclusion was added, no golden file was
   touched. A gate that went green here would be worse than useless.
2. **ADR 0002's reopen trigger has fired and the ADR needs revisiting.** Its own trigger list says
   so verbatim: *"A divergence moves an actual fasting date rather than a label"*, and *"Phase 5's
   per-city gate shows the offset behaving differently away from Mayapur"*. Both have now happened.
   The ADR itself is not edited here — that is a separate change and a separate review.
3. **This is a ruling for a pandit and the GBC, not for this repository.** ADR 0002 already states
   the principle: where the official Vaisnava calendar and correct astronomy disagree on an
   observance, the official calendar is what a temple follows. A devotee in Vrindavan will keep
   Nirjala Ekadashi on the day the published calendar names. Nothing in this codebase should
   quietly decide otherwise, in either direction.

Two further things a reviewer should know. Delhi and Vrindavan are 120 km apart and the entire
disagreement is a 34-second difference in their sunrise — this is exactly the kind of case a
single-site suite could never surface, and it appeared in the first run of the first multi-site
gate. And Nirjala is the year's most demanding fast, so of the 240 fortnights this could have
landed on, it landed on one where being wrong costs the most.

## Summary of what this run establishes

**Does.** Location reaches the computation, with evidence rather than by construction: two cities
sharing `Asia/Kolkata` differ by up to 38.1 minutes (Delhi−Mumbai, 2026-06-12, over 22 shared
parana days), and 71.2 minutes for Mayapur−Mumbai, with the computed inter-city offset matching the
offset the two published calendars print to within 1.5 minutes on every shared day. Six of ten
sites reproduce their own calendar completely. Civil offsets are correct across four DST
transitions at two sites in both hemispheres, including 23- and 25-hour civil days measured through
the same `jdUtAtStartOfDay` the index buckets sunrises with.

**Does not.** One year. Ten sites. No site above 55.8° latitude, so the polar branches of
`IskconRules` remain untested against any published calendar. And the four nakshatra-based
Mahadvadashis still rest on the single observed occurrence ADR-noted in
`IskconRules.nakshatraMahadvadashi` — none of the ten 2026 calendars contains a second.
