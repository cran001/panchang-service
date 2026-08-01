package org.panchang.publish

import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/**
 * The legacy feed shape, exactly as the Android app parses it.
 *
 * This is **not** this project's schema. It is the contract of the feed the shipped app already
 * consumes (`https://vaisnava-calendar-data-collection.gaura.space/`), reproduced so that installs
 * in the field keep working after this service takes over publication. Every field below, every
 * nullability decision and every guard in this file was read out of the app's own source and is
 * cited in `docs/legacy-contract.md`; nothing here is inferred from what a feed "ought" to look
 * like.
 *
 * The v1 shape — `:wire`'s document roots, `unresolved` included — is the shape new clients should
 * read, and is emitted alongside this one by [FeedPublisher].
 */
object LegacyFeed {

    /**
     * The regex the **app itself** applies to recover a parana from an event title.
     *
     * Copied verbatim from
     * `app/src/main/java/com/app/bhagwanbharose/data/local/LiveCalendarDataSource.kt:32-33` in
     * `D:/download/vrajarealm-android-app-main (1)/vrajarealm-android-app-main` (read-only). Kept
     * here character for character rather than paraphrased, so that the publisher's own test
     * asserts against the client's real acceptance condition and not against a restatement of it
     * that could drift.
     *
     * Group 1 is the window start, group 2 the end. The app applies it with `find` to
     * `title.trimStart('+').trim()`.
     */
    val APP_BREAK_FAST_REGEX: Regex =
        Regex("""[Bb]reak fast\s+(\d{1,2}:\d{2})\s*-\s*(\d{1,2}:\d{2})""")

    /**
     * The condition an event title on the **fast day** must satisfy for the app to attach the
     * parana to it.
     *
     * `LiveCalendarDataSource.processAllDays` lines 211-215: the app finds the parana on the
     * Dvadashi, subtracts one calendar day, and looks on that day for an event matching this. If
     * nothing matches and no event on that day carries the `+` major-event prefix, the parana is
     * dropped with a log line the user never sees.
     *
     * `eventType` in the app is itself derived from the title by substring, so testing the title
     * alone is equivalent.
     */
    fun appWouldTreatAsFastingTitle(displayTitle: String): Boolean =
        displayTitle.contains("Ekādaśī", ignoreCase = true) ||
            displayTitle.contains("Ekadashi", ignoreCase = true) ||
            displayTitle.contains("fast", ignoreCase = true)

    /**
     * The only form of `timezone` that both of the app's two parsers read the same way.
     *
     * Sign, two-digit hours, colon, two-digit minutes. `±HH:MM`, nothing else.
     */
    val TIMEZONE_FIELD: Regex = Regex("""^[+-](?:0\d|1[0-4]):[0-5]\d$""")

    /** `P`/`N` followed by `HHMM`, as `CalendarSyncRepository.formatOffset` renders it. */
    val ZONE_DIR: Regex = Regex("""^[PN]\d{4}$""")

    /** `yyyy-MM-dd`. The app calls `LocalDate.parse` / `ISO_LOCAL_DATE` on this field. */
    val ISO_DATE: Regex = Regex("""^\d{4}-\d{2}-\d{2}$""")

    /** `23N50 091E17`: exactly one space, `N`/`S` and `E`/`W` separating degrees from minutes. */
    val COORDINATES: Regex = Regex("""^\d{2}[NS]\d{2} \d{3}[EW]\d{2}$""")

    /** A path segment safe to use as a directory or file name on every platform we publish from. */
    val SITE_KEY: Regex = Regex("""^[a-z0-9][a-z0-9-]{0,63}$""")

    /**
     * The `timezone` field value for [offset]: `+05:30`, `-05:00`, `+00:00`.
     *
     * **Never** `ZoneOffset.getId()`. That returns the single character `Z` at zero offset, which
     * is the same class of failure as an IANA name: see [LegacyLocation.timezone].
     */
    fun timezoneField(offset: ZoneOffset): String {
        val total = offset.totalSeconds
        val sign = if (total < 0) "-" else "+"
        val minutes = abs(total) / 60
        return "%s%02d:%02d".format(sign, minutes / 60, minutes % 60)
    }

    /**
     * The zone directory name for [offset], matching `CalendarSyncRepository.formatOffset`
     * (line 311): `P`/`N` and `%02d%02d`. The app builds this string from the device's or
     * ipapi.co's offset and looks for a `dir` equal to it, so it has to agree exactly.
     */
    fun zoneDir(offset: ZoneOffset): String {
        val total = offset.totalSeconds
        val sign = if (total < 0) "N" else "P"
        val minutes = abs(total) / 60
        return "%s%02d%02d".format(sign, minutes / 60, minutes % 60)
    }

    /** A human label for a zone directory. Never read by the app; see the contract doc. */
    fun zoneTitle(offset: ZoneOffset): String = "UTC${timezoneField(offset)}"

    /**
     * Coordinates in the app's `23N50 091E17` form: degrees and whole arc-minutes.
     *
     * Lossy by construction — an arc-minute is about 1.85 km — and that is acceptable only
     * because the app uses this string for nothing but picking the nearest entry out of
     * `locations.json`. Every published time was computed from the full-precision coordinates,
     * which travel intact in the v1 payload's `site` block.
     *
     * Arc-minutes are rounded, then a rounded 60 is carried into the degrees, so `23.99999N`
     * cannot emit the unparseable minute value `60`.
     */
    fun coordinates(latitude: Double, longitude: Double): String {
        fun part(value: Double, positive: Char, negative: Char, degreeWidth: Int): String {
            val hemisphere = if (value < 0) negative else positive
            var degrees = abs(value).toInt()
            var minutes = ((abs(value) - degrees) * 60.0).roundToInt()
            if (minutes == 60) {
                minutes = 0
                degrees += 1
            }
            return "%0${degreeWidth}d%s%02d".format(degrees, hemisphere, minutes)
        }
        return part(latitude, 'N', 'S', 2) + " " + part(longitude, 'E', 'W', 3)
    }

    /**
     * The parana event title, in the only form the app recovers a window from.
     *
     * Must be its own event item on the Dvadashi: the app *removes* any item matching
     * [APP_BREAK_FAST_REGEX] from the displayed list, so a parana folded into a festival's title
     * would delete that festival from the user's calendar.
     */
    fun breakFastTitle(startHhMm: String, endHhMm: String): String =
        "Break fast $startHhMm - $endHhMm"
}

/** One entry of `/zones.json`. */
@Serializable
data class LegacyZone(
    /** Never read by the app. A label for whatever else consumes this feed. */
    val title: String,
    /**
     * Never read by the app, and its intended meaning could not be determined from the app source
     * — see `docs/legacy-contract.md`. Emitted because Moshi requires the key to be present and
     * to be a string: omitting it throws `JsonDataException` and loses the **entire** zone list.
     */
    val pb: String,
    /** The first URL path segment, and the string the app matches the user's offset against. */
    val dir: String,
) {
    init {
        require(LegacyFeed.ZONE_DIR.matches(dir)) {
            "zone dir '$dir' is not the P/N+HHMM form CalendarSyncRepository.formatOffset builds; " +
                "a dir that never compares equal is a zone no user can ever be routed to"
        }
    }
}

/** One entry of `/{dir}/locations.json`. */
@Serializable
data class LegacyLocation(
    val title: String,
    /** `23N50 091E17`. Used only to pick the nearest entry; never to compute. */
    val coordinates: String,
    /**
     * The UTC offset every time in this location's day file is expressed in — **as a number**.
     *
     * ## The failure the `require` below prevents
     *
     * The app stores this string and hands it to two different parsers.
     * `LiveCalendarDataSource.parseZoneOffset` is numeric-only: it does
     * `parts[0].toInt()` on the text before the colon and, **on any parse failure, falls back to
     * IST (+05:30)** rather than to the device zone or to an error. That offset is what converts
     * every parana `HH:MM` in the day file into the absolute instant used for the fasting alarm.
     *
     * So an IANA name here — `Asia/Kolkata`, `America/New_York` — or the string `Z` that
     * `ZoneOffset.getId()` returns at zero offset, **makes every user of this file silently fall
     * back to IST**. For a New York location that is a 10.5-hour error on a window that can be
     * under forty minutes wide. It does not throw, it does not log anything the user sees, and
     * the second parser (`DateTimeUtils.resolveCalendarTimeZone`, which *does* accept IANA names)
     * goes on rendering the display strings in the real zone, so the screen looks self-consistent
     * while the alarm is wrong.
     *
     * `±HH:MM` is the one form both parsers read identically. The regex admits nothing else.
     */
    val timezone: String,
    /**
     * Never read by the app; meaning undetermined, like [LegacyZone.pb]. Required to be present
     * and a string, or the whole location list fails to parse.
     */
    val option: String,
    /** Second URL path segment; the app appends `.json`. */
    val file: String,
) {
    init {
        require(LegacyFeed.TIMEZONE_FIELD.matches(timezone)) {
            "legacy timezone field must be a numeric UTC offset of the form +HH:MM, was " +
                "'$timezone'. An IANA zone id or 'Z' here makes every user of this file silently " +
                "fall back to IST: LiveCalendarDataSource.parseZoneOffset is numeric-only and " +
                "catches its own parse failure into ZoneOffset.ofHoursMinutes(5, 30)."
        }
        require(LegacyFeed.COORDINATES.matches(coordinates)) {
            "coordinates '$coordinates' are not the 23N50 091E17 form CalendarSyncRepository." +
                "parseCoordinates reads; an entry it cannot parse is never chosen as nearest"
        }
        require(title.isNotBlank()) { "a location with a blank title has no label to show" }
        require(file.isNotBlank()) { "a location with a blank file name has no day file to fetch" }
    }
}

/** One event on one day of `/{dir}/{file}.json`. */
@Serializable
data class LegacyEvent(
    /**
     * A leading `+` marks a major event (`title.startsWith("+")`) and is stripped for display.
     * Non-null with no default: a null here loses the whole year's array.
     */
    val title: String,
    val link: String? = null,
) {
    init {
        require(title.isNotBlank()) { "an event with a blank title renders as an empty row" }
    }
}

/**
 * One day of `/{dir}/{file}.json`.
 *
 * [date] and [tithi] are non-nullable, and that is the app's constraint rather than a preference:
 * Moshi decodes the file as a single `List<LiveCalendarDay>`, so a missing or null value in **one**
 * element throws out of `fromJson` and loses the entire year. In `CalendarSyncRepository` that
 * leaves the previous day's cache in place and looks like "the calendar stopped updating"; in
 * `LiveCalendarDataSource` it returns an empty list and the calendar goes blank.
 */
@Serializable
data class LegacyDay(
    val date: String,
    /** Free text; the app only ever renders it. Never parsed. */
    val tithi: String,
    val link: String? = null,
    val events: List<LegacyEvent> = emptyList(),
) {
    init {
        require(LegacyFeed.ISO_DATE.matches(date)) {
            "legacy day date '$date' is not yyyy-MM-dd; the app calls LocalDate.parse on it and " +
                "one unparseable element loses the whole year"
        }
        require(tithi.isNotBlank()) {
            "legacy day $date has a blank tithi; the app shows '<tithi> Tithi' as the event name " +
                "on days with no events, which would render as a bare word 'Tithi'"
        }
        require(events.count { LegacyFeed.APP_BREAK_FAST_REGEX.containsMatchIn(it.title.trimStart('+').trim()) } <= 1) {
            "legacy day $date carries more than one break-fast item; the app keys them by date " +
                "and the later one silently overwrites the earlier"
        }
    }
}
