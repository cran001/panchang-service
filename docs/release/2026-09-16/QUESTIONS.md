# Evidence investigation and remaining decisions

Research began 16 September and continued 20 September 2026. This is an investigation record,
not a completed qualified review. Sources below explain the designated lineage; no alternate
publisher was used to select matching calendar values. Exact URL/hash/retrieval metadata is
beside every downloaded source. Evidence review and owner publication approval remain mandatory.

## Findings that can be documented without a new religious ruling

| Question | Online evidence and finding | Consequence |
|---|---|---|
| Which source/generator? | The designated publisher's text headers identify GCal 11 Build 5. The developer repository is pinned to tree `9267f9fde9aefe2e60fb30c80a7ac9fc366fa585`; its README and 2008 PDFs are historical implementation documentation, not proof that every export used identical settings. | Keep exact per-file versions and hashes. Do not substitute current ports or other calendars. |
| Is a city name sufficient? | Text headers state degree/minute coordinates and standard UTC offsets, but omit elevation and IANA identifiers. | Compare those points, disclose 0 m and zone mappings, and exclude arbitrary nearby coordinates from evidence claims. |
| Should DST be added to every export? | The publisher's [DST page](https://www.vaisnavacalendar.info/daylight-savings) distinguishes LT/DST for GCal exports and separately reproduces older VCAL instructions. Some saved Moscow/Sao Paulo exports contradict current zone rules. | No blanket one-hour adjustment. Mark those rows unusable as clock evidence. This is an observable source-data problem, not a question requiring a pandit to explain timezones. |
| Does the 2008 catalog define today's list? | The old developer PDF includes Lalita Sasti and the older Radha Kunda placement. The publisher's [2020 update](https://www.vaisnavacalendar.info/2020-gbc-changes-updates) reports their removal/movement and Bhadra Purnima's addition following committee changes. | The older table cannot be treated as an unchanged current catalog. Preserve version differences and review the current rule catalog against the pinned export. No code ruling is made here. |
| Are all anchors precise clock definitions? | The publisher's [fasting page](https://www.vaisnavacalendar.info/fasting) supplies verbal anchors and directs Rama-navami readers to the SAC paper. Its text exports do not provide a complete clock-time oracle for these festival anchors. | Keep numeric festival fast-end fields unavailable; words alone do not validate civil twilight, Nisita or interval-edge choices. |
| Why not assume one universal Rama-navami fast-end instant? | The [March 2018 SAC paper](https://www.vaisnavacalendar.info/wp2020/wp-content/uploads/2020/05/Rama-navami-fast-SAC-March-2018.pdf), pp. 34–36, discusses differing practices and recommends calendar wording tied to worship and local ISKCON authority. | Evidence supports retaining a local-authority dependency. It does not authorize our numerical noon/evening choice. |

The historical [astronomy document](https://raw.githubusercontent.com/gopaladasa/GCAL-for-Windows/9267f9fde9aefe2e60fb30c80a7ac9fc366fa585/documentation/GCalAstronomyDocumentation.pdf)
(revision history ends 20 June 2008) explains GCal's tithi/paksha indexing and its month algorithm.
Its indexing differs in origin from this engine's, so labels must be translated before comparison.
It cannot by itself establish matching ephemeris instants or resolve a sunrise-boundary dispute.
The [standard events PDF](https://raw.githubusercontent.com/gopaladasa/GCAL-for-Windows/9267f9fde9aefe2e60fb30c80a7ac9fc366fa585/documentation/StandardEventsList.pdf)
is dated 11 June 2008 on its pages; the directory README is dated 20 June. Both dates are retained.

The catalog change is also directly documented in the GBC's own
[2019 AGM minutes](https://gbc.iskcon.org/2019-agm/), section 423.3, PDF pp. 11–12.
This resolves the question of whether those three changes were merely publisher edits: the
committee recommendation was endorsed by the GBC. Applying it correctly to our current catalog
still requires implementation evidence and qualified scope review. The
[2026 resolutions](https://gbc.iskcon.org/wp-content/uploads/2026/05/GBC-Res-26.pdf), PDF p. 19,
identify the Calendar Committee and SAC; they do not supply a new numerical Parana specification.
No named committee member has been appointed as our reviewer or approver by this research.

Search-index excerpts of the upstream [Vaisnava Events Calculation PDF](https://gopal.home.sk/gcal/docs/GCalVaisnavaCalculation.pdf)
provide narrower leads: its normal Parana branch explicitly describes open-ended output when
the computed beginning exceeds the cap; its nakshatra cases require the named nakshatra at both
successive sunrises; its Unmilani table distinguishes the preceding day's arunodaya/sunrise
conditions and combined Unmilani–Trisprsa naming. Its Unmilani Parana branch begins at sunrise
and caps at the earlier of Dvadashi's end or one-third daylight. These excerpts explain specific
implementation questions worth checking; they do not establish the complete document revision,
its applicability to Build 5 exports, or a completed review. The remaining escalation is that
authority/version/application gap, not a request for a reviewer to invent missing definitions.
Exact search queries and the limited nature of this evidence are saved in `indexed-rule-leads.json`.

## Escalate these remaining ambiguities or authority gaps

| Runtime question/case | What this research establishes | Specific remaining decision |
|---|---|---|
| Vrindavan Nirjala, Auckland Parana/classification, Moscow/Sydney labels | Exact publisher files and current results remain independently preserved. The previous near-boundary diagnostics remain evidence, not a verdict. | Qualified reviewer must decide the applicable rule/reference policy or approve withholding. Do not move our date/time to match the reference. |
| Open-ended Parana and Ahmedabad basis tie | Reference-only/missing windows and basis differences are measured separately from numeric tolerances. The indexed upstream rules text suggests an explicit open-ended export case, but a complete authenticated PDF could not be archived. | Obtain the authoritative complete rule and approve how the public contract represents the case; until then withhold. No invented cap. |
| RARE-NAKSHATRA-QUALIFIERS and UNMILANI-PURITY-NAMING | Upstream indexed excerpts narrow the survival, preceding-day purity and combined-name conditions; the historical README records algorithm changes. The complete applicable revision remains unarchived. Presence/absence of named cases is counted in the inventory. | Verify the complete applicable specification and positive-case coverage, then review our implementation against it; do not infer a ruling from a lack of examples. |
| FAST-ENDING-ANCHORS | Verbal anchors and the SAC recommendation explain why numeric interpretations are not automatic. | Authorize precise meanings for the intended public field, including relevant local authority; retain withholding if that meaning is intentionally flexible. |
| CATALOG-INTERPRETATIONS | Version drift is documented; literal mapped event comparisons are evidence only for those entries. | Resolve Ramanuja reckoning and Damodara closing-label semantics only where authoritative material remains ambiguous; review other unmapped entries against current lineage. |
| HIGH-LATITUDE-OBSERVANCE | No Reykjavik calendar was found in the collected designated indexes. Prior sunrise discrepancy remains separate from the repaired daylight interval. | Qualified authority is still needed for that observance scope. Lower-latitude proposals can supply reasoned non-applicability without declaring the global question solved. |
| REFERENCE-COVERAGE | Inventory gives exact 15-point, three-year measurements plus wider discovery-only links. 2025/2027 outer boundary context is missing. | Qualified reviewer assesses the exact proposed 2026 scope, evidence exclusions and limitations; availability does not approve additional points/years. |
| REGIONAL-CATALOG-REVIEW | Other traditions are outside the first release. | No regional religious decision is needed for this release; their code and unresolved review requirements remain. |

## Retrieval limits

Both HTTPS host variants for `GCalVaisnavaCalculation.pdf` failed certificate hostname checks,
including the 20 September retry. HTTP returned 403. TLS checks were not disabled. Search-index
snippets are discovery leads, not an authenticated full document or sufficient rule authority.
The upstream GitHub documentation folder supplied the astronomy/event PDFs but not that rules PDF.
The first fasting-page path returned 404; the actual `/fasting` page was then archived successfully.
No publisher was swapped after a disagreement or failed download. Failed attempts remain in
`sources/*.json` so absence cannot be mistaken for validation.
