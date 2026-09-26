# The legacy feed contract, as the Android app actually parses it

Read-only investigation of the Bhagwan Bharose Android app (package `com.app.bhagwanbharose`)
at `D:/download/vrajarealm-android-app-main (1)/vrajarealm-android-app-main`. Nothing in that
repository was modified. Every statement below cites the file and line it was read from; where a
fact could not be established by reading, it is listed under
["What could not be determined"](#what-could-not-be-determined) rather than guessed.

The point of the investigation was one design question: **the legacy feed's `timezone` field
holds one fixed UTC offset, and a DST zone has two. Does that harm users?** That depends entirely
on whether the app *applies* the offset to arithmetic or merely *displays* pre-rendered strings.
It applies it to arithmetic. The finding and what follows from it are in
["The `timezone` finding"](#the-timezone-finding).

---

## 1. Where the feed is fetched and in what order

`CalendarSyncRepository.syncCalendarData` (`data/repository/CalendarSyncRepository.kt:134-279`),
base URL `https://vaisnava-calendar-data-collection.gaura.space/` (line 34):

1. Resolve the user's position and a **UTC offset in seconds** — from an explicitly supplied
   city (device zone `TimeZone.getDefault().rawOffset`), else GPS + ipapi.co's `utc_offset`,
   else ipapi.co alone, else the device zone (lines 146-205).
2. `formatOffset(offsetSeconds)` (line 311) renders that as `P`/`N` + `%02d%02d`:
   `+05:30 → "P0530"`, `-05:00 → "N0500"`, UTC → `"P0000"`.
3. `GET /zones.json`, then `zones.find { it.dir == formattedOffset }`, falling back to
   `zones.firstOrNull { it.dir == "P0000" }` (line 212). **If neither matches, `syncCalendarData`
   returns `false` and no calendar is downloaded at all** (lines 214-217).
4. `GET /{dir}/locations.json`. Empty list ⇒ return `false` (lines 223-227).
5. Pick the closest location by squared-degree distance on the parsed `coordinates` string
   (lines 232-244). Note the fallback: `closestLocation` is initialised to `locations.first()`,
   so if *every* `coordinates` string fails to parse the app silently uses the first entry.
6. Persist `closestLocation.timezone` under `CalendarSyncPrefs / calendar_user_timezone` and
   `closestLocation.title` under `calendar_user_location_title` (lines 248-253).
7. `GET /{dir}/{file}.json`, and if the parsed list is non-empty write it verbatim to
   `filesDir/live_calendar_cache.json` (lines 256-268).

The sync is gated to once per day (`hasSyncedToday`, lines 61-66), but the gate is on the *date*,
not on the zone, so the zone directory is re-resolved on the first sync of every day.

## 2. Exact shapes the app expects

Parsed by Moshi through `KotlinJsonAdapterFactory` (`di/AppModule.kt:23-25`); the classes also
carry `@JsonClass(generateAdapter = true)`. Unknown JSON keys are ignored (Moshi's default —
`failOnUnknown` is not set anywhere), so extra fields are safe. **Missing or explicitly-null keys
for a non-null Kotlin property without a default are not**; see
[section 4](#4-which-fields-kill-the-whole-array-parse).

`data/model/LiveCalendarModels.kt:34-62`:

### `/zones.json` → `List<CalendarZone>`

| field   | Kotlin type | nullable? | what the app does with it |
|---------|-------------|-----------|---------------------------|
| `title` | `String`    | no        | never read |
| `pb`    | `String`    | no        | never read |
| `dir`   | `String`    | no        | matched against `P`/`N`+`HHMM`; used as the first URL path segment |

### `/{dir}/locations.json` → `List<CalendarLocation>`

| field         | Kotlin type | nullable? | what the app does with it |
|---------------|-------------|-----------|---------------------------|
| `title`       | `String`    | no        | stored as the displayed location label |
| `coordinates` | `String`    | no        | parsed as `"23N50 091E17"` to pick the closest entry |
| `timezone`    | `String`    | no        | **stored and applied to arithmetic — see section 5** |
| `option`      | `String`    | no        | never read |
| `file`        | `String`    | no        | second URL path segment, `.json` appended |

`coordinates` parsing (`CalendarSyncRepository.parseCoordinates`, lines 282-302): split on a
single `" "`, **exactly two parts** or `null`; latitude sign is `+` iff the first part contains
`"N"`, longitude `+` iff the second contains `"E"`; each part is `split("N","S")` /
`split("E","W")` and both halves go through `toDouble()`, combined as `deg + min/60`. Failure
returns `null` and that entry is simply never considered as the closest.

### `/{dir}/{file}.json` → `List<LiveCalendarDay>`

| field    | Kotlin type                  | nullable? | what the app does with it |
|----------|------------------------------|-----------|---------------------------|
| `date`   | `String`                     | **no**    | `LocalDate.parse(day.date)` / `ISO_LOCAL_DATE` — must be `yyyy-MM-dd` |
| `tithi`  | `String`                     | **no**    | display text only |
| `link`   | `String?` (default `null`)   | yes       | per-day fallback details URL |
| `events` | `List<LiveCalendarEventItem>` (default `[]`) | yes | see below |

`LiveCalendarEventItem`: `title: String` (**non-null, no default**), `link: String?`.

`tithi` is genuinely free text. `LiveCalendarDataSource.convertToVaishnavaEvents` uses it only as
`description = "Tithi: ${day.tithi}"` (line 284) and, on a day with no events, as the whole event
name `"${day.tithi} Tithi"` (line 304). Nothing parses it.

### Event-title conventions the app reads

`LiveCalendarDataSource.convertToVaishnavaEvents` (lines 252-314):

- A **leading `+`** on `title` means "major event": `isMajorEvent = apiEvent.title.startsWith("+")`
  (line 274). The `+` is stripped for display (`title.trimStart('+').trim()`, line 265).
- `eventType` is derived from the display title by substring, case-insensitively (lines 277-282):
  `"Ekādaśī"` or `"Ekadashi"` → `ekadasi`; `"Appearance"` → `appearance`; `"Disappearance"` →
  `disappearance`; otherwise `festival`.
- Any title matching the break-fast regex is **skipped** as a standalone event (lines 268-271).

## 3. What the app does with `parana`

There is no `parana` field. **Parana travels as an ordinary event title on the Dvadashi day** and
is recovered by regex. `LiveCalendarDataSource.kt:32-33`, verbatim:

```kotlin
private val BREAK_FAST_REGEX =
    Regex("""[Bb]reak fast\s+(\d{1,2}:\d{2})\s*-\s*(\d{1,2}:\d{2})""")
```

It is applied with `.find(...)` to `item.title.trimStart('+').trim()` — so a leading `+` is
tolerated, group 1 is the start `H:MM`/`HH:MM`, group 2 the end. It is the **only** regex the
live-calendar path applies to feed content (`grep -rn "Regex(" data/ ui/calendar/` returns this
one and an unrelated wallpaper slug).

Three passes, `processAllDays` (lines 165-248):

1. Collect `paranaByDvadashiDate[day.date] = (startMillis, endMillis)`, both produced by
   `parseTimeToMillis(day.date, "HH:MM", zoneOffset)` — `LocalDate.parse(dateStr).atTime(h, m)
   .toInstant(zoneOffset)` (lines 71-82). **One parana per day**: the map is keyed by date, so a
   second break-fast item on the same day silently overwrites the first.
2. Convert every day's remaining events to `VaishnavaEvent`s.
3. **Attach to the previous civil day.** `prevDate = LocalDate.parse(dvadashiDate).minusDays(1)`
   (line 200), then search that day's events for the first whose `eventType` contains `"ekadash"`
   or `"fast"`, or whose `eventName` contains `"Ekādaśī"`, `"Ekadashi"` or `"fast"` (all
   case-insensitive, lines 211-215); failing that, the first event with `isMajorEvent` (the `+`
   prefix). If neither exists the parana is **dropped** with a log warning (lines 236-241).

Consequences that constrain the publisher, all load-bearing:

- The break-fast item must sit on **exactly `fastDate + 1`**. The app derives the fast day by
  subtracting one calendar day; it never reads a date out of the parana.
- The fast day must carry an event title the predicate above matches, or the parana vanishes.
- The parana item must be its own event, not appended to another title, because the regex-matching
  item is dropped from the displayed event list.

Downstream, `paranaStartTimeMillis` / `paranaEndTimeMillis` are absolute instants used for
display (`VrajaLockScreenActivity.kt:718`, `CalendarEventDetailDialog.kt:209`), for `AlarmManager`
scheduling (`ReminderWorker.kt:289-295` → `ParanaAlarmReceiver`), and for expiry filtering
(`LockScreenViewModel.kt:362`).

## 4. Which fields kill the whole array parse

Moshi decodes each file as a single `List<T>`; a failure anywhere throws `JsonDataException` out
of `fromJson`, so **one bad element loses the entire file**, not just that element:

- `parseZones` / `parseLocations` / `parseLiveDays` (`CalendarSyncRepository.kt:337-347`) call
  `.fromJson(json).orEmpty()`. The `.orEmpty()` only covers a literal JSON `null` document; a
  thrown `JsonDataException` propagates to the `catch (e: Exception)` at line 274 and
  `syncCalendarData` returns `false` — **no cache is written and the previous day's cache is left
  in place**, so the failure looks like "the calendar just stopped updating".
- `LiveCalendarDataSource.loadEventsFromJson` (lines 86-99) catches and returns `emptyList()` —
  **the whole calendar goes blank**.

Fatal if missing or null:

| file | field |
|------|-------|
| `zones.json` | `title`, `pb`, `dir` |
| `locations.json` | `title`, `coordinates`, `timezone`, `option`, `file` |
| `{file}.json` | `date`, `tithi`, and `title` on every event item |

Safe to omit: `link` on a day, `link` on an event, and `events` on a day (all have defaults).

`pb` and `option` are never read but are **required to be present and to be strings** — they are
load-bearing purely as schema, which is why the publisher emits them.

## 5. The `timezone` finding

**The app applies `timezone` to arithmetic. It is not a display string.**

The stored `calendar_user_timezone` is read back by **two different parsers that do not agree**:

**(a) The arithmetic parser** — `LiveCalendarDataSource.parseZoneOffset` (lines 53-65):

```kotlin
val sign = if (cleaned.startsWith("-")) -1 else 1
val parts = cleaned.removePrefix("+").removePrefix("-").split(":")
val hours = parts[0].toInt()
val minutes = if (parts.size > 1) parts[1].toInt() else 0
ZoneOffset.ofHoursMinutes(sign * hours, sign * minutes)
// catch → ZoneOffset.ofHoursMinutes(5, 30)
```

Numeric only. **On any parse failure it falls back to IST (+05:30)** — not to the device zone.
The default when the key is absent is the string `"+5:30"` (line 46). The resulting `ZoneOffset`
is what converts every parana `HH:MM` into the absolute epoch millis used for alarms.

**This is the failure the publisher's `require` guard exists to prevent.** An IANA name such as
`"Asia/Kolkata"` — or the string `"Z"`, which is what `ZoneOffset.UTC.getId()` returns —
throws in `parts[0].toInt()` and every user of that file is silently computed at **+05:30**. For
a New York location that is a 10.5-hour error in a fasting window that can be under 40 minutes
wide, and nothing in the feed or the UI says so.

**(b) The display parser** — `DateTimeUtils.resolveCalendarTimeZone` (lines 247-264): tries
`ZoneId.of(cleaned)` **first**, so it *does* accept `"Asia/Kolkata"`; then a normalised numeric
offset; then the device default. Used by `getCalendarUserTimeZone` for every rendered parana time
and date (`DateTimeUtils.kt:78-154`).

So an IANA name does not fail loudly and symmetrically — it makes the two halves disagree: the
instants are computed at IST while the strings are rendered in the real zone.

Both parsers accept `+HH:MM` and `+H:MM` identically. `+HH:MM` is the only form that is
unambiguously safe in both.

### Does the single fixed offset harm users in a DST zone?

Yes, and there is no way to publish one legacy file that is right all year. Take
`America/New_York`, whose `timezone` field can only hold one of `-05:00` / `-04:00`:

| what we emit as `HH:MM` | absolute instant the app derives | calendar screen (fixed offset) | notification text (`ZoneId.systemDefault()`, DST-aware — `NotificationStrategyWorker.kt:318-325`) | `AlarmManager` |
|---|---|---|---|---|
| wall clock in the **fixed** offset | **correct** | correct-as-instant, but 1 h off the user's phone clock during DST | correct | correct |
| **true local** wall clock (DST-aware) | **1 h wrong** during DST | matches the phone clock | 1 h wrong | fires 1 h late |

The round trip through the *same* fixed offset is why the calendar screen looks self-consistent
either way; the harm surfaces in the alarm and in the notification, which do not use that offset.

### The design that follows from the finding

Stated plainly: **the choice below follows from the finding above, not from preference.**

1. **`timezone` is emitted as a numeric `±HH:MM` offset and never an IANA id or `"Z"`,** guarded
   by a regex `require` in `LegacyFeed.kt`. Because parser (a) is numeric-only and falls back to
   IST rather than failing, a name here is silent and total.
2. **A legacy file is published only for a location whose zone holds one offset across the whole
   published range.** Every time in the file is then rendered in that offset, which is also the
   real local wall clock, and all four columns of the table above are correct simultaneously. The
   overwhelming majority of Gaudiya sites — all of India — qualify.
3. **A location whose zone has a DST transition inside the range gets no legacy file.** It is
   listed in `manifest.json` under `legacyWithheld` with code `ZONE_NOT_FIXED_OFFSET`, and the
   count invariant `legacyPublished + legacyWithheld == published` is asserted in a test. It
   still gets the full v1 output, which carries the IANA zone id and renders every instant as
   both a correctly-offset local string and a `jdUt`, so nothing is lost — only the legacy
   compatibility shim is withheld.

Option 3 is where a project-owner decision is needed; see
["Needs a decision"](#needs-a-decision-project-owner-not-a-pandit).

## 6. What could not be determined

Stated rather than guessed:

- **The real feed's contents.** No sample of `zones.json`, `locations.json` or any `{file}.json`
  exists anywhere in the Android repository (`find . -name zones.json -o -name locations.json`
  → nothing; `app/src/main/assets/` holds only the bundled offline calendar), and this task
  permits no network access. Everything above is what the app *expects*; the shapes are read
  from the Moshi classes and the code that consumes them, which is authoritative for
  compatibility but says nothing about what upstream actually sends.
- **What `pb` and `option` mean.** Neither is read anywhere in the app. Their presence and string
  type are load-bearing; their content is not, as far as the app is concerned. The publisher
  emits documented placeholders and this is flagged rather than invented.
- **Whether upstream duplicates DST cities across two zone directories.** The app re-resolves the
  zone directory on the first sync of each day using the *current* offset, so an upstream that
  listed New York under both `N0500` and `N0400` would hand the app a correct file within each
  season. Whether it does that is unknowable without seeing the feed. It would not fully solve
  the problem either — the app caches and displays a whole year, so months on the other side of
  the transition would still read an hour out.
- **What the `title` of a zone is displayed as.** Never read by the app; it may be shown by some
  other consumer of the same feed.
- **Whether any consumer other than this app reads the feed.** Out of scope for a read of one
  repository.
- **Whether the feed is a calendar year, a rolling window, or open-ended.** The app requests one
  file and caches whatever it gets; nothing in the app constrains the range.

## 7. Decided by the project owner — 2026-08-02

Neither of these was a question about tradition; no pandit review is implied by this document.
Both were put to the project owner and both are now settled.

### DST locations get no legacy file — **confirmed, unchanged**

The alternatives, both of which ship a knowingly-wrong number to somebody, are laid out in the
table in section 5. The ruling is that **no legacy file is published for a zone that changes
offset during the year**. Those sites get complete v1 output — IANA zone id, every instant as
both a correctly-offset local string and a `jdUt` — and appear under `legacyWithheld` /
`ZONE_NOT_FIXED_OFFSET` in `manifest.json`.

The accepted cost is stated plainly: **London, New York, Auckland, Sydney and São Paulo see no
data at all in the currently-shipped app** until it is updated to read v1. That is preferred to a
parana alarm firing an hour out, or a displayed window an hour off the user's own clock, with
nothing on screen to say which. The alternative was rejected because its error is *seasonal and
silent* — correct for roughly seven months a year, wrong for five — which is the hardest kind for
a user to notice or report.

This remains one predicate, `FeedPublisher.fixedOffsetOver`, if the call is ever revisited.

### A UTC site is now always published — **changed**

The app falls back to the `P0000` (UTC) zone directory when the user's offset matches no `dir`,
and returns failure if that directory does not exist either (`CalendarSyncRepository.kt:212-217`),
which reaches the user as a stale cache with no visible error.

The publisher therefore **synthesises one site into `legacy/P0000/` whenever no requested site
landed there**. It is computed on the prime meridian at `Etc/UTC`, and it is honest about what it
is in the one place a user can see it — `locations.json` titles it
`UTC — generic fallback, not your location`, because a row reading plain "UTC" invites someone to
select it and believe the result. It is recorded in full under `manifest.utcFallback`, announced
in `manifest.warnings`, and **counted in nothing**: it was not requested, so counting it would
break `published + skipped == requested`, the invariant that makes a silently short run
detectable. When a real requested site already holds `P0000` (Accra, Reykjavík), no fallback is
synthesised.

The reasoning is that a present-but-generic calendar is a lesser harm than a stale cache showing
last year's dates with no error — the user can at least see that the times do not match their
sunrise. It does not make an unmatched offset *correct*; it makes it *visible*.

## 8. What this session actually measured

Every number here came out of a run in this session; nothing is estimated.

- **The IST fallback is real, not inferred from reading.** `LegacyFeedTest.the emitted offset
  survives the app's own parser unchanged` transcribes `parseZoneOffset` (lines 53-65) and applies
  it to what the publisher emits. Seven offsets round-trip exactly; `"America/New_York"` and
  `"Z"` both come back as `+05:30`. The `require` in `LegacyLocation` is therefore guarding a
  measured failure.
- **`ZoneOffset.UTC.getId()` is `"Z"`** — asserted directly, so the reason `timezoneField` formats
  by hand rather than calling `getId()` is recorded as a fact.
- **`zoneDir` agrees with `CalendarSyncRepository.formatOffset`** for five offsets, transcribed the
  same way. `P0000` for UTC, `N0500` for −05:00.
- **One real publish run, Mayapur, 2026** (`--sites` one line, `--out` a local directory): 7 files;
  365 legacy days with contiguous dates and no gaps; **24 Ekadashi observances, 24 paranas, 24
  break-fast items**, every one on the fast date + 1 and every one matched by the app's own regex
  with both bounds extracted; `legacyParanaOmissions` empty.
- **The five-site fixture in `FeedPublisherTest`** exercises each outcome once: `requested 5 =
  published 2 + skipped 3`, with `ABOVE_POLAR_LIMIT` (from `:wire`), `UNKNOWN_PLACE_ID` and
  `MALFORMED_SITE_LINE` (this module's own), and `legacyPublished 1 + legacyWithheld 1 = published
  2` for the `America/New_York` site withheld under section 5.
- **`:publish` test totals from the JUnit XML:** `tests=31 skipped=0 failures=0 errors=0`.

**Not measured, and not claimed:** nothing here has been compared against the live feed at
`vaisnava-calendar-data-collection.gaura.space`, because this task permits no network access and
the repository contains no sample. The shapes are what the app's parsers *require*; whether the
current upstream satisfies them is unverified. Nor has any generated file been loaded by the
actual Android app — the app's parsing logic is reproduced in tests, which is not the same thing.
