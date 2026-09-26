# Source investigation — AI-assisted, unsigned

No expected date was supplied from model memory. The sole calendar comparator remains the
designated VaisnavaCalendar.info text export. Related implementation code below is supporting
evidence, never a second runtime calendar. Research began September 21 and resumed September 22.
Every saved source has a JSON sidecar containing its exact URL, retrieval time, response details,
byte count and SHA-256. `source-checks.json` verifies those bytes. Original snapshots remain intact.

## Identity, version and settings

All 15 newly fetched 2026 exports are byte-identical to their prior snapshots, including the
seven candidates. The 2025 and 2027 snapshots are retained and rehashed as historical boundary
evidence. Every measured calendar header says GCal 11, Build 5. Printed degree/minute coordinates
are preserved as doubles without city-centre substitution; the source omits elevation and IANA
names. The explicit comparison assumptions remain 0 m and the existing IANA mapping.

The [Windows lineage README](https://raw.githubusercontent.com/gopaladasa/GCAL-for-Windows/9267f9fde9aefe2e60fb30c80a7ac9fc366fa585/README.md)
has a Build 5 section dated May 31, 2019. Its tree is unchanged from the earlier research.
It documents version history, not the binary or user settings used for these exports.
Source: `sources/gcal-README.{raw,json}`, section “GCAL 11, Build 5”.

Two identifiable related implementations were archived at immutable commits:

- [C++ calendar code](https://github.com/gopa810/gcal-cpp/blob/1cedaea67278009aedbfc7c91beeb64489a3ed4c/TResultCalendar.cpp),
  commit `1cedaea67278009aedbfc7c91beeb64489a3ed4c`.
- [Python calendar code](https://github.com/gopa810/gaurabda-calendar/blob/92c36b5948e9bcbfe991f19f511371aff1cc0fcb/gaurabda/TCalendar.py),
  commit `92c36b5948e9bcbfe991f19f511371aff1cc0fcb`.

The Python `setup.py` identifies version 0.8.4 and an author contact on gopal.home.sk. This is
provenance supporting association, not independent certification. The C++ resource file has
inconsistent historical version strings (`FILEVERSION 1,2,0,1`, `ProductVersion 1,4,0,1`, lines
1539–1563). Neither repository proves identity with the exported Build 5 binary. No port was
installed, executed as a calendar, or used to replace a reference value.

## Precise implementation findings

| Question | Saved source reference and observed behavior | Comparison with this repository; proposed follow-up |
|---|---|---|
| Open-ended Parana | C++ `CalculateEParana`, lines 1708–1736: the ordinary branch retains its start and sets an absent end when start exceeds end. Python lines 919–944 agrees. | `IskconRules.derivePotentialParana` instead returns no window when end <= start. Propose a reviewed “after start” representation with explicit absent end, without inventing a cap. Do not implement it until applicable semantics are accepted. |
| Vyanjuli qualification | C++ `MahadvadasiCalc`, lines 1443–1460; Python lines 744–756: the Vyanjuli branch includes the preceding Ekadashi's sunrise and arunodaya purity. | The engine's `dvadashiVriddhi` branch does not test that purity. This is a concrete candidate explanation for Moscow and Sydney naming differences. Propose adding the prerequisite only after source applicability/rule review. |
| Trisprsa/Unmilani order | C++ `EkadasiCalc`, lines 647–696; Python lines 379–411: purity is checked before these branches and combined Unmilani–Trisprsa is represented. | The engine tests Dvadashi-kshaya before its arunodaya check and has no combined classification. Auckland October is a targeted case for review, not permission to copy its published time. |
| Rare nakshatra qualifiers | C++ `IsMhd58`, lines 93–137 and caller 1439; Python lines 99–127 and caller 742: compare successive sunrise nakshatras, bright fortnight and the caller's sunset-tithi gate. | The engine's nakshatra helper lacks the explicit sunset-tithi gate. The source helper's Vijaya exception and caller gate must be reviewed together; do not infer a universal rule from one branch. |
| Unmilani Parana | C++ lines 1579–1589; Python lines 833–840: sunrise start with the earlier cap. | Review the engine's general Hari-Vasara start handling for named Unmilani. No 2026 candidate mismatch establishes global correctness; 2025/2027 evidence stays visible. |
| Rounding | C++ `GCVaisnavaDay.cpp` lines 153–176 and Python `GCCalendarDay.py` lines 154–165 convert fractional hours to integer minutes. | These renderers truncate positive clock minutes. This supports the existing comparison design, but does not authenticate Build 5's settings or turn minute matches into exact-instant matches. Tolerances remain unchanged. |

The source references above specify code behavior, not religious authority. Exact local
implementation references are `sampradaya/.../IskconRules.kt`: `classify`,
`nakshatraMahadvadashi`, and `derivePotentialParana`; no rule was edited.

## All eight held points

The measured exception rows, original registry IDs and new local sunrise/arunodaya/tithi
diagnostics are retained in `ai-assisted-review.json`, `coverage-inventory.json` and
`measurements/<city>-2026.json`. They disclose every affected date; no aggregate score hides them.

| Held point | Dated issue and investigation outcome |
|---|---|
| Vrindavan | June 25 calculated fast versus June 26 reference fast; calculated June 26 versus reference June 27 Parana. June 30 closing-tithi/sunrise sensitivity remains the numerical lead. No authenticated export settings or authoritative boundary adjudication was obtained. Keep `OBSERVANCE-01`. |
| Auckland | April 28 start-basis conflict, September 23 reference-only open-ended window, October 7 classification and October 8 Parana cap conflict (15,283 displayed seconds). Upstream branch order and open-ended handling give specific review proposals above. Keep all cases. |
| Moscow | May 27 Vyanjuli naming conflict plus 15 stale-DST Parana dates. Purity prerequisite is a concrete code difference. Clock exclusions remain separate from naming; no one-hour correction is applied. |
| Sydney | December 5 Vyanjuli naming conflict. Investigate preceding-day purity using the saved diagnostics and cited source branch; matching fast dates/times do not settle the label. |
| Ahmedabad | November 6 end-basis conflict despite a five-second displayed-time delta. The local diagnostic Dvadashi-end and daylight-third candidates are extremely close; a matched minute cannot identify the correct cap or authenticate the other ephemeris. Keep the basis conflict. |
| Mumbai | August 24 reference says an open-ended start at 10:50; engine emits no window. The open-ended code branch explains a representational possibility, not an authorized substitution. |
| New York | October 22 reference says an open-ended start at 11:16; engine emits no window. Same representation question; separate March daily-label case remains. |
| Sao Paulo | Nine Parana dates carry DST markers incompatible with the recorded IANA zone. Fast dates can still be compared, but those clock rows cannot serve as passing timing evidence. |

The source [DST page](https://www.vaisnavacalendar.info/daylight-savings) was freshly saved as
`sources/dst.raw`; its GCal section distinguishes local/DST output. JVM tzdb version and every
date-specific check are in the measurements. The separate older VCAL instructions are not
applied to GCal exports. No timezone assumption is silently shifted to gain agreement.

## Broader review questions and evidence limits

- `RARE-NAKSHATRA-QUALIFIERS`: source conditions are now fully inspectable in related code.
  The measured sample still has no positive calculated Jaya, Jayanti or Papanasini. No validated
  positive reference fixture was obtained for these types; absence is not validation.
- `UNMILANI-PURITY-NAMING`: related code narrows purity, combined-name and Parana questions.
  Applicability to the designated export build and the intended rule remains unestablished.
- `HIGH-LATITUDE-OBSERVANCE`: the seven candidate points have ordinary sunrise/sunset in the
  measured scope, but that is a proposed non-applicability argument for a reviewer. Reykjavik
  has no file in the designated indexes; its independent sunrise dispute remains untouched.
- `REFERENCE-COVERAGE`: only exact points/year/fields can be considered. All adjacent December
  and January disagreements are listed, including January carryover checks. The whole 2025/2027
  calendars are research context, not approval extensions. Unpublished elevation/settings remain assumptions.
- `FAST-ENDING-ANCHORS`: outside this OBSERVANCES proposal. The prior authenticated
  [March 2018 SAC paper](https://www.vaisnavacalendar.info/wp2020/wp-content/uploads/2020/05/Rama-navami-fast-SAC-March-2018.pdf),
  pp. 34–36, preserves the local-authority dependency; saved PDF and original retrieval sidecar
  are in `../2026-09-16/sources/rama-navami-sac-2018.*`. Verbal anchors do not establish precise instants.
- `CATALOG-INTERPRETATIONS`: outside this proposal. The newly saved
  [2019 AGM page](https://gbc.iskcon.org/2019-agm/) and prior AGM PDF, resolution 423.3,
  document catalog changes. The 2008 list is not sufficient authority for an unchanged current
  catalog. Ramanuja reckoning and Damodara closing semantics remain unresolved.
- `REGIONAL-CATALOG-REVIEW`: other traditions remain excluded; no regional conclusion is needed
  for this release and their code/review requirements are preserved.

The [current committee page](https://gbc.iskcon.org/commmittees/) identifies the Calendar
Committee but does not supply the missing numerical specification or appoint a reviewer for us.
The publisher's saved about-page evidence explicitly separates its site from committee operation.
The new about-page fetch was incomplete; the previously authenticated snapshot is cited instead.
The complete `GCalVaisnavaCalculation.pdf` again failed hostname certificate validation. We did
not disable TLS checks, bypass restrictions, or repeatedly search the same failed endpoint.

## Narrower coverage is a proposal, not a release

An explicit optional Sydney proposal is saved in
`proposals/alternative-sydney-through-december-04.request.json`: exact source point,
**2026-01-01 through 2026-12-04, OBSERVANCES only**. It excludes the December 5 fast and
December 6 Parana and the rest of December; it preserves the full-year result fingerprint
and adjacent-year context. This is not an addition to the seven first-release candidates.
Only separately approved day reads within that range could be eligible; a whole-year API
response still exceeds the coverage and remains withheld. It still requires broader review, reference-policy
acceptance, qualified review and owner approval. We recommend finishing the seven existing
candidate reviews first. The alternative bundle is unsigned; no narrowed coverage was activated
or used to remove runtime disputes.
