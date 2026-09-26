# Proposed first-release reference policy

Status: **proposal for qualified review and owner approval; no approval granted**.
First public tradition: **ISKCON only**. The other nine calculators remain available locally.
The runtime gate is independent of the internal calculator registry and cannot be bypassed
by a signed approval for another tradition. Public discovery lists only ISKCON.

## One designated calendar lineage

Observance comparisons use the existing VaisnavaCalendar.info GCal text-export lineage only:
[2025 index](https://www.vaisnavacalendar.info/calendar-file-downloads/txt-calendar-files-2025),
[2026 index](https://www.vaisnavacalendar.info/calendar-file-downloads/txt-calendar-files-2026),
[2027 index](https://www.vaisnavacalendar.info/calendar-file-downloads/txt-calendar-files-2027).
Indexes are discovery evidence, not authority to publish. No dates from another publisher,
modern GCal port, astronomical service, or alternate tradition replace an inconvenient result.

Each source has its own JSON sidecar in `sources/`: exact requested and final URL, UTC retrieval
time, HTTP status/headers, raw-byte SHA-256, size and local artifact. Failures are retained.
The inventory records generator headers per file; it does not infer a software version from
the website footer. An export saying GCal 11 Build 5 does not identify its binary checksum,
ayanamsha settings, event database edits, or timezone database version. Those remain limitations.

The publisher describes GCal-derived calculations but expressly distinguishes its own site and
descriptive content from the GBC Calendar Committee. An old about page names GCal 6; current
pages name GCal 11. These claims do not constitute human review of our engine or this release.
[Publisher about page](https://www.vaisnavacalendar.info/about-the-vaisnava-calendar).

## Points, clocks and boundaries

- Coordinates come from the file's printed degree/minute header, converted without rounding.
  A nearby GeoNames city, district or user's GPS point is a different approval scope.
- Elevation is not printed. Comparisons explicitly assume **0 metres**; this is not evidence
  for an elevated observer, mountain location or user-specific horizon.
- The printed UTC offset is a standard offset, not an IANA zone. Existing `VaisnavaSiteZones`
  mappings are preserved and checked against January/July standard offsets. Hyderabad's
  research-only mapping is `Asia/Kolkata`; its header must identify Hyderabad and +05:30.
- Each printed LT/DST marker is compared with the JVM's recorded tzdb version for that date.
  Stale Moscow/Sao Paulo stamps are explicit unusable clock comparisons. No reference time is
  silently shifted to pass. Fasting dates and bound bases remain independently compared.
- Annual proposals are limited to 2026, with 2025 December and 2027 January files available as
  boundary context. Full files are retained. A neighbouring year's discrepancy does not
  silently transfer approval or prove the boundary correct: qualified review must examine the
  actual carryover records. 2025 and 2027 remain research inventory, lacking an outer context year.
- Computed tithi/Parana values remain our engine's values. Reference evidence never becomes
  a production override or fallback calendar.

## Meaning of sufficient evidence

`SUFFICIENT_COMPARISON_EVIDENCE_PENDING_REVIEW` means a complete parsed calendar and measured
agreement for the named field at that exact point/year under the **existing** compatibility
bands. It is enough to submit a scoped review proposal, not to claim accuracy or human approval.
Solar bounds use -15..75 seconds; lunar bounds use -180..75 seconds. Basis disagreements remain
disagreements even when a displayed minute matches. Missing bounds and source clock errors
are partial evidence. No tolerance, golden file or calculation is changed.

The collector checks every civil date exactly once and rejects unparsed lines. It compares
fasting dates in both directions, names, named Mahadvadashi classifications, Parana bounds and
bases, and daily tithi/paksha/nakshatra/month labels. Daily labels remain research-only because
the current public schema does not support DAILY_ASTRONOMY. Literal festival-name matches are
partial catalog evidence; unmapped labels are not asserted to be religious disagreements.
Festival fast-end instants and standalone sunrise/sunset values lack complete evidence in
this export format. Numeric astronomy evidence from the prior audit remains separate.

The fine-grained inventory does not change the runtime field groups. Proposals request only
OBSERVANCES; EVENTS stay withheld. Even a priority proposal still requires all applicable
qualified-review questions and explicit resolutions of any relevant runtime cases, followed
by an exact owner approval. Whole-year OBSERVANCES are withheld when one affected case remains.
The separate Reykjavik sunrise disagreement is preserved; no designated calendar in these
indexes authorizes high-latitude observance guidance.

## Lifecycle and reproducibility

Research scripts never append review records or turn inventory rows into a runtime allowlist.
Proposed bundles have `LOCAL_REVIEW_ONLY` purpose, exact result/version fingerprints and
hashed evidence. They contain no reviewer, signature or approval. A rebuild can change artifact
fingerprints and requires re-preparation. Changes to input, rules, reference policy, evidence,
registry or result cannot inherit an old approval.

Production identity/storage activation, qualified review and owner approval remain separate
requirements. No public admin API, AI approval, cache, deployment, live-artifact withdrawal,
Android change, Content Hub change, commit or push is part of this work.
