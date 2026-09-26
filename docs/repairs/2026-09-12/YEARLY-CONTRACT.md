# Yearly delivery contract — 12 September 2026

`SampradayaRules.ekadashiObservances(year, context)`, the shared engine, HTTP year responses,
and v1 yearly files retain a decision when its **fasting date OR delivered Parana date** is
in the requested year. A preceding December fast is included only when its Parana belongs
to the requested year. Other index-margin records are excluded. The fast keeps its actual
December date; `parana.date` remains January 1. Non-Ekadashi event selection is unchanged.

Day responses select from that list using the fasting date OR Parana date. Thus a January 1
request can return a December 31 decision. Ordinary-date selection is unchanged.

A yearly list is not a count of that year's fasts. Count by fasting date when that is the
question. Adjacent years intentionally share a carryover decision; combine by site, tradition,
and fasting date, rather than concatenating or requiring complete DTO byte equality. Separate
yearly index seeds can already round a tithi boundary a second differently. This repair changes
neither the solver nor its tolerance. Parana for the tested carryover agrees exactly.

## Legacy files

The existing legacy consumer reads `Break fast HH:MM - HH:MM` on the Parana day, then looks for
a fasting event on the previous day **in the same file** (see `docs/legacy-contract.md`, section
3). Emitting January 1 alone still loses the Parana at that attachment step.

The publisher therefore prepends the preceding December fast day only when its Parana is
actually representable and delivered inside the requested year. This context day contains its
fasting title and tithi; it does not claim a complete previous-year festival catalog. All days
in the requested year remain present, sorted, once each. No unrelated December or following
January day is included. For Mayapur 2026 the file now has **366 rows: December 31, 2025 through
December 31, 2026**, including 365 requested-year days. Other files without carryover retain
their previous length. Consumers must not require an array length of exactly 365/366; when
combining yearly legacy files, deduplicate the shared context date and use the owning year's
file for that date's complete festival list.

The wire schema version and fields are unchanged. Consumers assuming every fasting date or
every legacy row is inside the requested year must adapt to the explicit carryover contract.
Existing generated files need regeneration before any approved publication; no hosting,
production overwrite, Android integration, or deployment was performed here.

## Daylight cap

`SunTimes.sunrise`, `sunset`, and `daylightDays` still describe the same civil date. The new
`daylightInterval` helper separately pairs that sunrise with a later same-date sunset or the
actual immediately following civil date's sunset before the next sunrise. It returns no
interval for missing endpoints or a polar season that cannot be closed within those civil
days. It does not add 24 hours, take an absolute value, or guess daylight duration.

Only the Parana cap uses this helper in production. Other civil rise/set and day-division
callers keep their existing behavior. The earliest applicable cap still supplies both the
end instant and its reason. No classification, religious fallback, approval label, ephemeris
constant, sunrise algorithm, or comparison tolerance was changed.

**Publishing remains blocked by unresolved approval and religious-rule issues.**
