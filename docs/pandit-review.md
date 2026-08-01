# Questions for a pandit

**This repository does not settle any question on this page, and cannot.** Every item below is a
point where the tradition's own rule is either unstated, stated in two ways that disagree, or
stated in words that admit more than one honest reading. In each case the software currently does
*something* — it has to produce an answer — and what it does is written down here plainly so that
it can be disagreed with. None of these choices is presented to a user as authoritative, and none
of them was made by a pandit.

Where a published Vaisnava calendar and correct astronomy disagree about an observance, the
published calendar is what a temple follows. That principle is already settled project policy and
is not reopened here. What is open is a set of cases where nobody has told us what the rule *is*.

Written for a reader who knows the tradition and does not know this software. No code is quoted.
Sources and file references are in the footnotes.

## How this list is ordered

By what a wrong answer costs the person following it.

| Tier | Meaning | Items |
|---|---|---|
| **A** | A wrong answer **moves a fast to the wrong day**. | 1, 2 |
| **B** | A wrong answer **changes the hour a fast ends or begins**, or leaves a devotee with no guidance at all. | 3, 4 |
| **C** | A wrong answer gives an observance **the wrong name**, on the right day. | 5, 6 |
| **D** | A wrong answer affects **a single date or a single label** of narrow scope. | 7, 8 |

---

# Tier A — a wrong answer moves a fast

## 1. Vrindavan, Pandava Nirjala Ekadashi 2026 — which day is the fast?

**The highest-priority question on this page. It is the only one that moves an actual fast, and it
moves the most demanding fast of the year.**

**What we do now.** We place the fast on **Thursday 25 June 2026**, on the Ekadashi itself. The
published reference calendar for Vrindavan places it on **Friday 26 June 2026**, on the Dvadashi,
and names it Paksa vardhini Mahadvadasi. We do not follow the reference here; we compute and we
report the disagreement loudly. The conformance test for Vrindavan is deliberately left failing.

**Why.** Paksavardhini applies when the Purnima closing the fortnight is running at sunrise on two
successive days, lengthening the fortnight and moving the fast to the Dvadashi. The closing Purnima
of that fortnight ends at **05:26:39.8 IST on 30 June 2026**. That is a single instant — the tithi
is the Moon−Sun elongation and does not vary from place to place. The only quantity that differs
between one Indian city and another is sunrise on 30 June:

| City | Sunrise, 30 June 2026 | Purnima end minus sunrise | Our verdict | Reference |
|---|---|---|---|---|
| Delhi | 05:26:19.0 | Purnima ends **20.9 s after** sunrise | Paksavardhini, fast on 26 June | agrees |
| Mayapur | earlier | after | Paksavardhini, fast on 26 June | agrees |
| **Vrindavan** | **05:26:53.3** | Purnima ends **13.4 s before** sunrise | plain Ekadashi, fast on 25 June | **disagrees** |
| Mumbai | later | before | plain Ekadashi, fast on 25 June | agrees |

**What evidence exists.** This was investigated as a mistake in our rule first, and it is not one.
The same rule, applied to the same instants, reproduces the reference at three of the four cities
and in both directions — it fires where the reference fires and stays silent where the reference
stays silent. Only Vrindavan flips, and only because its sunrise falls 13.4 seconds on the other
side of one instant. Delhi and Vrindavan are 120 km apart and the entire disagreement is a
34-second difference in their sunrise.

**13.4 seconds is smaller than our own measured worst-case error in either input**: our tithi
instants are within 17.3 seconds of JPL Horizons, and our sunrise within 28 seconds of the US
Naval Observatory. Separately, the reference calendar's Moon runs measurably *late* against
ours — by 34 to 112 seconds of tithi time, one-signed, never once reversing across 25 measured
windows — and that lateness applied to this Purnima would on its own produce the reference's
answer. That is *consistent with* the reference's ephemeris being the cause. It is **not** a
demonstration of it and is not claimed as one. What is established is narrower: it is not a rule
error, and the margin is below the resolving power of the data available.

**What a ruling would change.** Everything about this case, and only this case. A ruling that the
Vrindavan fast is on 26 June tells us the reference is right and we should follow it there; a
ruling that it is on 25 June tells us the reference's ephemeris moved a fast by a day. Either
answer also tells us how to treat the general class — sub-minute margins between a tithi boundary
and a sunrise — which by our own estimate arises roughly one day in seven hundred.

**Urgency: immediate.** The date has passed for 2026, but the same knife-edge recurs, and a devotee
in Vrindavan keeping Nirjala on the wrong day has kept the year's hardest fast for nothing. Of the
240 fortnights this could have landed on, it landed on the one where being wrong costs most.[^1]

## 2. The four nakshatra Mahadvadashis — Jaya, Vijaya, Jayanti, Papanasini

**What we do now.** When one of four nakshatras — Punarvasu, Shravana, Rohini, Pushya — is running
at sunrise on a bright-fortnight Dvadashi **and is still running at the following sunrise**, we
declare the corresponding Mahadvadashi (Jaya, Vijaya, Jayanti, Papanasini respectively) and **move
the fast from the Ekadashi to the Dvadashi**. The result is marked as an inferred rule, not a
confirmed one.

**Why.** The pairing of nakshatra to name, the requirement of the bright fortnight, and the fact
that the fast moves to the Dvadashi are stated identically by three independent Vaishnava sources
consulted. The extra condition — that the nakshatra must survive to the next sunrise — is **ours**.
It is not quoted from any text.

**What evidence exists, and it is thin.** Stated without the extra condition the rule fires far too
often. Across seven years of the Mayapur calendar (2021–2027) there are twenty-three days on which
one of the four nakshatras runs at sunrise on a Dvadashi, ten of them in the bright fortnight, and
the published calendar marks **exactly one** of them: the Vijaya Mahadvadashi of 12 September 2027.
Some further condition must separate that one from the other twenty-two, and none of the sources
consulted states one. The condition we adopted separates them cleanly — true for the one positive,
false for all twenty-two negatives — and no competing candidate we tried does. The most natural
alternative, "the nakshatra outlasts the Dvadashi tithi", is true of thirteen of the twenty-two
negatives and so cannot be the rule.

The condition also makes sense of the published calendar's own behaviour elsewhere: on the parana
day following that 2027 observance, the reference closes the eating window at "end of naksatra", so
the fast must be broken while the nakshatra still runs. Where the nakshatra has already ended
before the parana morning that would be impossible, and the observance is not declared. That is a
coherent reading. It is still a reading.

**This rests on a single observed occurrence.** None of the ten harvested 2026 calendars contains a
second one.

**What a ruling would change.** Which days in future years carry these four names, and — because
this branch moves the fast to the Dvadashi — **which day the fast is kept on**. A citation of the
qualifying condition from Hari-bhakti-vilasa or a comparable authority would settle it outright. So
would a second observed occurrence in a published calendar, though that is confirmation by
coincidence rather than by doctrine.

**Urgency: high.** Rare, but a wrong answer moves a fast, and the rule is currently supported by
one data point.[^2]

---

# Tier B — a wrong answer changes the hour, or leaves no answer at all

## 3. "Fast till dusk" and "Fast till midnight" — what do the words mean?

**What we do now.** Two of the six times-of-day the calendar names are computed from a reading this
project chose, and both are flagged. Both readings are printed in full alongside every time we
produce, so a reviewer sees the choice rather than having to guess it.

- **"Fast till midnight" (Sri Krsna Janmastami).** Read as **Nisita-kala: the eighth of fifteen
  equal muhurtas of the night, the night taken from sunset to the following sunrise** — hence the
  muhurta containing solar midnight. Reported as an interval, not a point, because the tradition
  names a division of the night rather than an instant in it.
- **"Fast till dusk" (Nrsimha Caturdasi).** Read as **the end of civil twilight — the Sun's centre
  six degrees below the horizon after sunset.**

**Why.** For midnight: civil 00:00 is an artefact of where a government drew its time-zone meridian
and of whether it is currently observing daylight saving. At the western edge of a wide zone it
sits more than an hour away from solar midnight, so two devotees in one country keeping the same
Janmastami would break the fast at materially different points of the night while both believed
they were following "midnight". Solar midnight is a fact about the sky at the devotee's own
longitude and does not have that defect.

For dusk: mapping dusk onto sunset was not available to us, because the calendar itself
distinguishes them. Rama Navami is "Fast till sunset" and Nrsimha Caturdasi is "Fast till dusk",
and both phrases appear, once each, in every one of the ten reference calendars examined.
Collapsing the two would erase a distinction the source deliberately drew on two different festival
days — and Nrsimha Caturdasi is in practice broken after dark.

**What evidence exists: none, and that is the point.** Across all ten published calendars, 3650 day
records, the phrases "Fast till noon", "sunset", "moonrise", "dusk" and "midnight" appear 158 times
in total and **not one of them is accompanied by a clock time**. The published calendars name these
moments in words and never print them. There is therefore no published time anywhere against which
either reading can be checked. The other four anchors — sunrise, noon, sunset, moonrise — are
treated as settled not because they were verified against a printed time (they cannot be) but
because each word has exactly one astronomical meaning and the tradition uses it in that meaning.

Two points a reviewer should hold apart. First, our arithmetic is separately measured and is not
what is in question: sunrise and sunset agree with the US Naval Observatory to within 28 seconds
across ten cities in both hemispheres. What is in question is only the reading of the words.
Second, the open sub-questions are specific:

- Does "midnight" mean Nisita-kala at all? If it does, does the night run sunset→sunrise, or
  between some other pair of bounds? And does the fast end at the muhurta's beginning, its middle
  or its end? The muhurta is about 48 minutes wide on a twelve-hour night, so this is not a
  hair-splitting difference.
- Which twilight is "dusk"? Civil (6° below the horizon) was taken; nautical (12°) and
  astronomical (18°) are also called dusk in English, and *pradosha* — the period following sunset
  — is a fourth reading that matches none of the three exactly. They differ by tens of minutes at
  temperate latitudes and by much more at high ones.

**What a ruling would change.** The hour at which two of the most widely kept fasts in the year end,
everywhere on Earth, every year.

**Urgency: high, and it is the cheapest item on this page to settle** — it needs a ruling, not new
data.[^3]

## 4. When Hari Vasara outlasts the first third of daylight, which bound wins?

**What we do now.** Nothing — deliberately. On these days we emit **no parana window at all** and
say in plain words that the case needs a pandit's ruling rather than a computed answer.

**Why.** Two caps on breaking the fast are in conflict on these days:

- The fast may **not** be broken before **Hari Vasara** — the first quarter of the Dvadashi — has
  ended.
- The fast **must** be broken before the end of the **first third of the daylight period**.

On most days the first ends well before the second and the window between them is the parana. On
these days the order is reversed, so the "window" would start after it ended. We refuse to print an
inverted window, because "07:12 – 06:34" reads as perfectly ordinary to everyone except the person
trying to follow it. Which of the two bounds yields is a doctrinal ruling, not a computation, and
we do not have it.

**What evidence exists.** Three occurrences in 2026 across the ten cities examined, and the
published reference calendar meets exactly the same conflict on exactly the same three days:

| City | Date | Hari Vasara ends | First third of daylight ends | What the reference prints |
|---|---|---|---|---|
| Mumbai | 24 Aug 2026 | 10:49:47 | 10:34:39 | starts 10:50, **no end stated** |
| Auckland | 23 Sep 2026 | 10:30:39 | 10:12:24 | starts 10:31, **no end stated** |
| New York | 22 Oct 2026 | 11:15:28 | 10:51:46 | starts 11:16, **no end stated** |

The two programs detect the same conflict on the same days — the sets match exactly across all ten
cities, with no city carrying one without the other in either direction — and resolve it
differently. The reference lets Hari Vasara win and declines to state any end at all, which
implicitly permits breaking the fast after the daylight third has passed. We decline to state a
window. The difference between the two is doctrinal, not astronomical.

**What a ruling would change.** Whether a devotee on such a day gets a window at all, and if so,
where it ends. Today they get an explanation and no times.

**Urgency: high.** Three days a year at a district-accuracy service used worldwide is not rare, and
on those days a devotee currently receives no guidance on when to break their fast.[^4]

---

# Tier C — a wrong answer gives the right day the wrong name

## 5. Unmilani — the source's own prose contradicts its own output

**What we do now.** We apply Unmilani only where the Ekadashi was **pure** — it began before
arunodaya, four ghatikas before sunrise — **and** is still running at sunrise on the following day.
Where an *impure* (viddha) Ekadashi runs to a second sunrise, the fast still moves to that second
day but is given **no** Mahadvadashi name. Where an Ekadashi is lost entirely — beginning after one
sunrise and ending before the next — the fast falls on the Dvadashi, also with no name.

**Why.** Because the published rule and the published output cannot both be right, and the output
is the better witness to what the tradition actually does.

**What evidence exists.**

- The reference's own published rule list says a lost Ekadashi produces Unmilani. Its own output
  says otherwise: seven such fortnights at Mayapur between 2022 and 2027 all print the fast on the
  Dvadashi with **no** Mahadvadashi line at all.
- Three other sources describe Unmilani simply as an Ekadashi surviving to the Dvadashi day's
  sunrise. But of the nine such fortnights at Mayapur across 2021–2027, the reference names only
  **two**. What separates those two from the other seven is exactly purity: in both named cases
  the Ekadashi began before arunodaya, and in all seven unnamed ones it began after. Nine cases, no
  exceptions.

That reading is doctrinally coherent — a viddha Ekadashi moving to the second day is the ordinary
impure-Ekadashi deferral and earns no special name, whereas a pure Ekadashi that nonetheless
touches two sunrises is the genuine Unmilani — but it is our reconstruction from nine observations,
not a quotation.

**What a ruling would change.** The name shown on such days, and which of the eight
Mahadvadashis a devotee understands themselves to be observing. **The fasting date is the same
under every reading considered.**

**Urgency: moderate.** Naming only, but it is a reconstruction standing against a published rule
list, which is an uncomfortable place to be.[^5]

## 6. Two Vyanjuli labels that nothing explains — Moscow and Sydney, 2026

**What we do now.** We label **Moscow, 27 May 2026 (Padmini Ekadasi)** and **Sydney, 5 December
2026 (Utpanna Ekadasi)** as Vyanjuli Mahadvadashi. The reference puts the fast on the same day at
both and prints no Mahadvadashi line.

**Why we cannot explain it.** The reference's own printed rows satisfy the reference's own
published Vyanjuli rule — "If Dvadasi falls on the sunrise two days in a row the first Dvadasi
becomes Vyanjuli Mahadvadasi". Moscow prints Dvadasi on both 27 and 28 May; Sydney prints Dvadasi
on both 5 and 6 December. Meanwhile the identical structure at Mayapur, Vrindavan and Delhi on 24
August 2026 *is* labelled Vyanjuli. Five occurrences of one pattern; three labelled, two not.

**What evidence exists — four hypotheses tested and all four refuted.**

- *Purity of the Ekadashi*, by analogy with Unmilani above. All five have "Ekadasi (not suitable
  for fasting)" on the preceding day, so viddha does not separate them.
- *Paksha.* Moscow is bright, Sydney dark, the labelled group bright.
- *A knife-edge tithi boundary.* The Dvadashi's first sunrise falls 7.0 minutes inside it at
  Moscow and 21.8 minutes inside it at Sydney — one to two orders of magnitude beyond the
  reference's documented timing offset, so this is not a boundary flip.
- *A daylight-saving artefact.* Both unlabelled rows are stamped DST, but London, New York, São
  Paulo and Sydney all carry Mahadvadashi labels on other days inside a daylight-saving period.

Adhika-month suppression would explain Moscow (Purusottama-adhika) but not Sydney (Margasirsa), so
it is not offered as an explanation for either.

**What a ruling would change.** Either the tradition carries a further condition on Vyanjuli that
none of the sources consulted states — in which case we want it — or the reference is internally
inconsistent across these five fortnights. **Whichever way it is settled, the fasting date is
unaffected:** the fast is on the first Dvadashi under either branch. This is a naming question only.

**Urgency: moderate.**[^6]

---

# Tier D — narrow scope

## 7. Two single-entry questions

### 7a. Sri Ramanujacarya's appearance day

**What we do now.** We place it on **Caitra sukla pancami**, which is what the Mayapur 2026 export
has (23 March 2026), and mark the entry as inferred with the disagreement recorded on the entry
itself.

**Why, and what the problem is.** The Sri Vaisnava tradition reckons Ramanuja's appearance by
**Ardra nakshatra in Cittirai**, not by tithi. The nakshatra on 23 March 2026 is **Krittika**. So
the two rules are not the same rule; they happen to be close in 2026 and **will diverge in other
years**. We use the Gaudiya calendar's tithi form because that is the calendar this service
reproduces, not because we believe it is the older reckoning.

**What a ruling would change.** Which date this observance falls on in every year other than the
ones where the two rules coincide.

**Urgency: moderate for anyone who keeps it; low in volume.** It is one appearance day, but the
divergence is structural rather than a rounding difference.[^7]

### 7b. When does the Kartika (Damodara) vrata end?

**What we do now.** We end it on **Kartika Purnima** (24 November 2026).

**Why, and what is unverified.** The *date* is checked against the Mayapur export. The *name* is
not: the export names only the close of the fourth Caturmasya month on the preceding day and the
Purnima festivals on the day itself, and never says in so many words that the Damodara vrata ends
here. The entry is marked inferred for that reason.

**What a ruling would change.** Whether the month-long Damodara vrata is understood to end on the
Purnima or on the preceding day with Caturmasya. **The date shown is right either way under the
present reading; what is unconfirmed is the label attached to it.**

**Urgency: low.**[^8]

## 8. Sites inside the polar circles — noted, not yet a live question

**What we do now.** Where a day the decision depends on has no sunrise, we produce **no
observance date at all** and say why, rather than substituting some other instant. Where the
parana day has no sunrise, or no sunset to bound the daylight period, we produce no window and say
why.

**Why.** Inside the polar circles the phrase "the tithi at sunrise" has no referent. Which instant
a Gaudiya ruling substitutes there — a nominal sunrise, the previous true sunrise, a fixed civil
hour, the temple of reference — is not something any source consulted states, and producing a date
anyway would be inventing a religious ruling.

**What evidence exists.** None either way. No published Gaudiya calendar we hold covers a site
above 55.8° north, so the polar branches have never been compared against any reference.

**What a ruling would change.** Whether devotees above the Arctic Circle receive observance dates
at all.

**Urgency: low today, and it becomes urgent the first time such a devotee asks.** It is listed so
that the silence is a recorded decision rather than an oversight.[^9]

---

## What this page is not

It is not a list of bugs, and none of these items is waiting on more computation. Items 1 and 2 in
particular would not be settled by a better ephemeris: item 1 turns on a margin smaller than the
error bars of every input available, and item 2 turns on a condition no text we have consulted
states. They are waiting on a ruling.

Where a ruling arrives, the corresponding rule and its confidence marking will be updated and the
item struck from this page with the ruling recorded in its place.

---

[^1]: `docs/decisions/0002-reference-calendar-disagreement.md` (reopened 2026-08-02) and
`docs/validation-multisite.md`, section "A FASTING-DATE DISAGREEMENT". Error figures from
`docs/accuracy-baseline.md` and `docs/validation-sunrise.md`.
[^2]: `IskconRules.nakshatraMahadvadashi` KDoc, in
`sampradaya/src/main/kotlin/org/panchang/sampradaya/IskconRules.kt`; and
`docs/validation-multisite.md`, closing "Does not" paragraph.
[^3]: `docs/decisions/0003-event-anchor-definitions.md`. Anchor definitions are the `basis` strings
in `sampradaya/.../EventTime.kt` and are emitted with every event time in the JSON payload. Prose
counts measured from `verify/golden/vaisnavacalendar-*.json`. Sunrise accuracy from
`docs/validation-sunrise.md`.
[^4]: `docs/validation-multisite.md`, "Category (b)"; the refusal and its wording are in
`IskconRules.derivePotentialParana`.
[^5]: `IskconRules.classify`, branches 1 and 3.
[^6]: `docs/validation-multisite.md`, "Unexplained — 2 of 4 label disagreements".
[^7]: The `ramanujacarya_appearance` entry in
`sampradaya/src/main/kotlin/org/panchang/sampradaya/IskconEventCatalog.kt`.
[^8]: The `kartika_vrata_ends` entry in the same file.
[^9]: `IskconRules.classify` and `IskconRules.derivePotentialParana` return-null paths;
`docs/validation-multisite.md`, closing "Does not" paragraph.
