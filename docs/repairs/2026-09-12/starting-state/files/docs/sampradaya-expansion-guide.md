# Sampradaya Expansion Guide

## Goal
Add support for 8 regional Hindu calendars to the panchang service, fixing existing issues and making the system production-ready.

## The 8 New Sampradayas

### Lunisolar (Amanta - Month ends on New Moon)
1. **Marathi** (Maharashtra, Goa) — New Year: Chaitra 1 (Gudi Padwa)
2. **Telugu** (Andhra Pradesh, Telangana) — New Year: Chaitra 1 (Ugadi)
3. **Kannada** (Karnataka) — New Year: Chaitra 1 (Ugadi, same as Telugu)
4. **Gujarati** (Gujarat) — New Year: Kartika 1, day after Diwali (Bestu Varas)

### Lunisolar (Purnimanta - Month ends on Full Moon)
5. **North Indian Hindi** (UP, MP, Bihar, Rajasthan, Haryana, HP) — New Year: Chaitra Navratri

### Solar (Month begins on Sankranti)
6. **Tamil** (Tamil Nadu) — 60-year cycle, New Year: mid-April when Sun enters Aries (Puthandu)
7. **Malayalam** (Kerala) — Kollam Era (epoch 825 CE), year opens Aug-Sep (Chingam), astrological cycle at Vishu (April)
8. **Bengali** (West Bengal, Tripura) — New Year: ~April 14 (Pohela Boishakh)
9. **Odia** (Odisha) — New Year: mid-April (Pana Sankranti)

## Key Files to Modify

### Core Architecture (READ ONLY - understand, don't modify)
- `core/src/main/kotlin/org/panchang/core/PanchangCalculator.kt` — astronomy engine
- `ephemeris/src/main/kotlin/org/panchang/ephemeris/Vsop87Ephemeris.kt` — planetary positions
- `sampradaya/src/main/kotlin/org/panchang/sampradaya/SampradayaRules.kt` — the interface all traditions implement

### Files You WILL Modify

#### 1. Event Catalog Infrastructure
**File**: `sampradaya/src/main/kotlin/org/panchang/sampradaya/EventCatalog.kt`
**Current limitation**: Only 4 rule types exist:
- `EventRule.OnTithi` (tithi in lunar month)
- `EventRule.OnNakshatraInMonth` (nakshatra in lunar month)
- `EventRule.RelativeTo` (offset from another event)
- `EventRule.FixedGregorian` (hardcoded per-year dates)

**What needs adding**:
```kotlin
// For solar calendars
data class OnSolarMonth(
    val solarMonthIndex: Int,  // 1-12, Mesha=1, Vrishabha=2...
    val dayOfMonth: Int,
    val transitionPoint: SolarTransition = SolarTransition.SANKRANTI_START
) : EventRule

enum class SolarTransition {
    SANKRANTI_START,  // Day sun enters the sign
    SANKRANTI_AT_SUNRISE,  // Next sunrise after entry
}

// For 60-year cycle systems
data class In60YearCycle(
    val cycleYear: Int,  // 1-60
    val baseRule: EventRule  // Nests another rule
) : EventRule
```

#### 2. Event Resolver (converts rules to dates)
**File**: `sampradaya/src/main/kotlin/org/panchang/sampradaya/EventResolver.kt`
**Lines to modify**: ~150-200 (the `when` block that matches on `EventRule` sealed types)

**What needs adding**: Handler for the new `OnSolarMonth` and `In60YearCycle` cases.

**Critical**: `PanchangCalculator` already computes `solarTransit(date, location)` which returns the zodiac sign the Sun is in. You need to:
1. Walk forward from Jan 1 of the year
2. Call `solarTransit` each day until the sign index changes
3. That's the Sankranti day
4. Apply the `dayOfMonth` offset

#### 3. New Sampradaya Implementations
**Create 8 new files** in `sampradaya/src/main/kotlin/org/panchang/sampradaya/`:
- `MarathiRules.kt`
- `TeluguRules.kt`
- `KannadaRules.kt`
- `GujaratiRules.kt`
- `NorthIndianRules.kt`
- `TamilRules.kt`
- `MalayalamRules.kt`
- `BengaliRules.kt`
- `OdiaRules.kt`

Each must implement `SampradayaRules` interface with:
```kotlin
class MarathiRules : SampradayaRules {
    override val id = "marathi"
    override val displayName = "Marathi (Maharashtra, Goa)"
    override val status = VerificationStatus.UNVERIFIED  // Until you have a reference calendar
    override val provenanceNote = "Rules derived from <STATE YOUR SOURCE>. Not yet verified against published calendar."
    
    override fun ekadashiObservances(year: Int, ctx: ObservanceContext): List<ObservanceDecision> {
        // Marathi follows Amanta, so same moon logic as ISKCON but may differ on:
        // - Viddha test timing (do they test at sunrise, arunodaya, or local noon?)
        // - Parana bounds (do they use Hari Vasara? 1/3 daylight? Or something else?)
        // - Mahadvadashi detection
        // RETURN EMPTY LIST for now with a TODO, or copy ISKCON as a starting point
        return emptyList()
    }
    
    override fun eventResolution(year: Int, ctx: ObservanceContext): YearResolution {
        // Return the festival catalog resolved to dates
        val catalog = MarathiEventCatalog.ALL  // You'll create this
        return EventResolver.resolve(year, ctx, catalog)
    }
}
```

#### 4. Event Catalogs for Each Tradition
**Create 8 new files** like `MarathiEventCatalog.kt`, `TamilEventCatalog.kt`, etc.

Example structure for **Marathi**:
```kotlin
object MarathiEventCatalog {
    val ALL: List<EventDefinition> = listOf(
        EventDefinition(
            id = "gudi_padwa",
            name = "Gudi Padwa (Marathi New Year)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = onTithi("Chaitra", Paksha.SHUKLA, 1, reckoning = MonthReckoning.AMANTA),
            sourceNote = "Chaitra Shukla Pratipada, the Marathi New Year. Source: <YOUR SOURCE>",
            confidence = RuleConfidence.UNVERIFIED,
        ),
        // Add all other Marathi festivals
    )
}
```

Example for **Tamil** (solar):
```kotlin
object TamilEventCatalog {
    val ALL: List<EventDefinition> = listOf(
        EventDefinition(
            id = "puthandu",
            name = "Puthandu (Tamil New Year)",
            group = EventGroup.MAJOR_FESTIVAL,
            rule = OnSolarMonth(solarMonthIndex = 1, dayOfMonth = 1),  // First day of Mesha
            sourceNote = "Tamil New Year, day the Sun enters Aries (Mesha Sankranti). Source: <YOUR SOURCE>",
            confidence = RuleConfidence.UNVERIFIED,
        ),
        // Add all other Tamil festivals
    )
}
```

#### 5. Registry Registration
**File**: `calc/src/main/kotlin/org/panchang/calc/CalcEngine.kt`
**Line**: ~37 (in the `init` block)

**Add**:
```kotlin
init {
    SampradayaRegistry.register(iskcon)
    SampradayaRegistry.register(MarathiRules())
    SampradayaRegistry.register(TeluguRules())
    SampradayaRegistry.register(KannadaRules())
    SampradayaRegistry.register(GujaratiRules())
    SampradayaRegistry.register(NorthIndianRules())
    SampradayaRegistry.register(TamilRules())
    SampradayaRegistry.register(MalayalamRules())
    SampradayaRegistry.register(BengaliRules())
    SampradayaRegistry.register(OdiaRules())
}
```

#### 6. Month Reckoning Enum
**File**: `core/src/main/kotlin/org/panchang/core/LunarMonth.kt`
**Check if** `MonthReckoning` enum already distinguishes AMANTA vs PURNIMANTA.

If not, verify the lunar month naming in `PanchangCalculator` respects the reckoning — Krishna paksha dates shift by one month name between the two systems.

#### 7. Fix the Reykjavik Bug (High Priority)
**File**: `sampradaya/src/main/kotlin/org/panchang/sampradaya/IskconRules.kt`
**Line**: ~442-444 (where daylight cap is computed)

**Current code**:
```kotlin
val daylight = sunTimes.daylightDays
if (daylight != null && daylight > 0.0) {
    end = sunrise + daylight / 3.0
    endReason = ParanaBoundReason.ONE_THIRD_DAYLIGHT
}
```

**Problem**: When `daylight` is null (Sun never sets), the cap is skipped but the window is still emitted with `confidence = CONFIRMED`.

**Fix**:
```kotlin
val daylight = sunTimes.daylightDays
if (daylight != null && daylight > 0.0) {
    end = sunrise + daylight / 3.0
    endReason = ParanaBoundReason.ONE_THIRD_DAYLIGHT
} else if (daylight == null) {
    // Sun never sets or never rises — cannot compute a daylight fraction.
    // This happens at high latitudes (Reykjavik, Oulu) around solstices.
    // Refuse the window rather than emit one with a missing cap.
    return ParanaOutcome(
        window = null,
        note = "No parana window is given because the Sun does not set at this site on " +
            "$paranaDate (daylight period has no end), and the tradition's ruling for " +
            "midnight-sun days is not established here.",
        confidence = RuleConfidence.INFERRED,
    )
}
```

**Also fix** the acceptance gate in `wire/src/main/kotlin/org/panchang/wire/SiteAcceptance.kt`:
Replace the fixed 66° test with a dynamic check. Add a helper:
```kotlin
private fun hasMidnightSunOrPolarNight(latitude: Double, year: Int): Boolean {
    // Approximate: any site above ~63.5° can experience at least one day
    // where sun doesn't set (summer) or doesn't rise (winter).
    // More precise: check if Sun's max declination (23.44°) + latitude > 90°
    return abs(latitude) > 66.5 - 23.44  // ~63.06°
}
```
Then in `polarCheck`:
```kotlin
if (hasMidnightSunOrPolarNight(latitude, year = 2026)) {
    return reject(
        SiteRejectionCode.POLAR_REGION,
        "Latitude $latitude experiences midnight sun or polar night, and observance " +
            "rules for such conditions are not yet established in this system."
    )
}
```

#### 8. Tests for New Sampradayas
**Create** `sampradaya/src/test/kotlin/org/panchang/sampradaya/MarathiCalendarTest.kt` (and 7 others).

Minimal test to start:
```kotlin
class MarathiCalendarTest {
    @Test
    fun `Marathi rules are registered`() {
        val rules = SampradayaRegistry["marathi"]
        assertNotNull(rules)
        assertEquals("marathi", rules.id)
        assertEquals(VerificationStatus.UNVERIFIED, rules.status)
    }
    
    @Test
    fun `Gudi Padwa 2026 resolves to a date`() {
        val ctx = ObservanceContext(
            calculator = PanchangCalculator(Vsop87Ephemeris()),
            location = GeoLocation(19.0760, 72.8777, ZoneId.of("Asia/Kolkata"))  // Mumbai
        )
        val resolution = MarathiRules().eventResolution(2026, ctx)
        val gudiPadwa = resolution.resolved.find { it.event.id == "gudi_padwa" }
        assertNotNull(gudiPadwa, "Gudi Padwa must resolve")
        // TODO: verify against a published Marathi calendar for 2026
    }
}
```

#### 9. Documentation
**File**: `docs/sampradaya-coverage.md` (create new)

Document:
- Which festivals are implemented for each tradition
- What the source was (book, website, calendar file)
- What remains unverified
- Known gaps (e.g., "Marathi parana rules are copied from ISKCON; actual tradition may differ")

## Old Issues to Fix

### Issue 1: Reykjavik Parana Bug
**Status**: CRITICAL
**Location**: `sampradaya/src/main/kotlin/org/panchang/sampradaya/IskconRules.kt:442`
**Fix**: See section 7 above.

### Issue 2: Nine Pre-Existing Test Failures
**Status**: DOCUMENTED, not blocking
**Location**: `sampradaya/` test suite
**Failures**:
1. Vrindavan 2026-06-25 vs 06-26 fasting date (in `docs/pandit-review.md` tier A)
2-4. Four Mahadvadashi label disagreements (tier A)
5-6. Two parana basis disagreements at Auckland (tier C)
7-9. Two Vyanjuli label disagreements at Moscow and Sydney (tier C)

**Action**: Leave as-is. These are documented in `docs/pandit-review.md` and are awaiting pandit rulings, not code fixes. The test failures are deliberate so the questions stay visible.

### Issue 3: Phase 4 — Acharya Day Rules
**Status**: MEDIUM PRIORITY
**Problem**: Many ISKCON festival dates use `FixedGregorian` (hardcoded per year) because their tithi rules haven't been recovered.
**Location**: `sampradaya/src/main/kotlin/org/panchang/sampradaya/IskconEventCatalog.kt`
**Action**: Search for `FixedGregorian` and convert to `OnTithi` rules where the source is known. Mark this in `provenanceNote`.

### Issue 4: No Android App Integration
**Status**: DEFERRED (not needed for sampradaya expansion)
**Location**: `:api` module has never bound a real socket
**Action**: Once all sampradayas work in `:calc` CLI, deploy `:api` and test from the Android app.

### Issue 5: GeoNames Alternate Names
**Status**: LOW PRIORITY
**Problem**: "Bombay" doesn't resolve (only "Mumbai" does)
**Location**: `gazetteer/` ingestion
**Action**: Ingest `vendor/geonames/alternatenames.txt` into the TSV so pre-rename city names work.

## The GLM 5.3 Max Prompt

Use this prompt to guide the AI through autonomous implementation:

---

**PROMPT START**

You are an expert Kotlin developer working on a Hindu panchang (calendar) calculation service. Your task is to add support for 8 new regional Hindu calendars (sampradayas) to an existing codebase, while fixing one critical high-latitude bug.

## Context

The project already supports ISKCON/Gaudiya Vaishnava observances with full conformance testing against published calendars. The architecture is:
- `:ephemeris` — VSOP87 planetary positions
- `:core` — astronomy engine (tithi, nakshatra, sunrise, solar transit)
- `:sampradaya` — observance rules (each tradition implements `SampradayaRules`)
- `:calc` — CLI that computes a year's calendar for any location
- `:api` — HTTP service (not yet used)
- `:verify` — conformance tests against golden calendars

The astronomy is tradition-neutral. Only the *rules* differ between sampradayas.

## Your Tasks

### CRITICAL: Fix the Reykjavik bug first
**File**: `sampradaya/src/main/kotlin/org/panchang/sampradaya/IskconRules.kt`
**Problem**: At Reykjavik (64.1°N) on 2026-06-26, the parana window is 834 minutes (14 hours) instead of ~200-300, because the Sun never sets that day so the "1/3 of daylight" cap is silently skipped. The window is emitted with `confidence: CONFIRMED` and no warning.
**Fix**: When `sunTimes.daylightDays` is null or <= 0, refuse the window with an explicit note and `confidence: INFERRED`, rather than emitting one with a missing cap.
**Also fix**: `wire/src/main/kotlin/org/panchang/wire/SiteAcceptance.kt` — replace the fixed 66° latitude gate with a dynamic check for sites that experience midnight sun.

### Add 8 New Sampradayas

Implement these traditions:

1. **Marathi** (Amanta, Maharashtra/Goa, New Year = Gudi Padwa on Chaitra Shukla 1)
2. **Telugu** (Amanta, AP/Telangana, New Year = Ugadi on Chaitra Shukla 1)
3. **Kannada** (Amanta, Karnataka, identical to Telugu)
4. **Gujarati** (Amanta, Gujarat, New Year = Bestu Varas on Kartika Shukla 1, day after Diwali)
5. **North Indian Hindi** (Purnimanta, UP/MP/Bihar/Rajasthan/Haryana/HP, New Year = Chaitra Navratri)
6. **Tamil** (Solar, Tamil Nadu, 60-year cycle, New Year = Puthandu when Sun enters Aries)
7. **Malayalam** (Solar, Kerala, Kollam Era epoch 825 CE, year opens in Chingam/Aug-Sep)
8. **Bengali** (Solar, West Bengal/Tripura, New Year = Pohela Boishakh ~April 14)
9. **Odia** (Solar, Odisha, New Year = Pana Sankranti mid-April)

### Steps

1. **Extend `EventRule`** in `sampradaya/src/main/kotlin/org/panchang/sampradaya/EventCatalog.kt`:
   - Add `OnSolarMonth(solarMonthIndex, dayOfMonth, transitionPoint)` for solar calendars
   - Add `In60YearCycle(cycleYear, baseRule)` for Tamil's Samvatsara system

2. **Extend `EventResolver`** in `sampradaya/src/main/kotlin/org/panchang/sampradaya/EventResolver.kt`:
   - Add handler for `OnSolarMonth`: walk days, call `calculator.solarTransit(date, location)`, detect when zodiac sign changes (that's Sankranti), apply offset
   - Add handler for `In60YearCycle`: compute which cycle year the Gregorian year maps to, then resolve the nested rule

3. **Create 9 new files** `{Tradition}Rules.kt` in `sampradaya/src/main/kotlin/org/panchang/sampradaya/`:
   - Each implements `SampradayaRules`
   - Set `status = VerificationStatus.UNVERIFIED` (no reference calendar yet)
   - `ekadashiObservances`: return empty list for now (or copy ISKCON as a starting point if the tradition follows similar moon logic)
   - `eventResolution`: call `EventResolver.resolve` with the tradition's catalog

4. **Create 9 event catalogs** `{Tradition}EventCatalog.kt`:
   - Define major festivals for each tradition using `EventDefinition`
   - Mark `confidence = RuleConfidence.UNVERIFIED` and state the source in `sourceNote`
   - For solar traditions, use the new `OnSolarMonth` rule
   - For Tamil, wrap in `In60YearCycle` where applicable

5. **Register all 9** in `calc/src/main/kotlin/org/panchang/calc/CalcEngine.kt` init block

6. **Write minimal tests** for each tradition:
   - `{Tradition}CalendarTest.kt` in `sampradaya/src/test/`
   - Assert the tradition is registered
   - Assert the New Year festival resolves to a date
   - Mark as TODO: verify against published calendar

7. **Run the gate**: `./gradlew --no-daemon :sampradaya:test :calc:test :core:test :ephemeris:test`
   - Fix any compilation errors
   - The 9 pre-existing ISKCON failures are expected (documented in `docs/pandit-review.md`)

8. **Test each tradition** via CLI:
   ```bash
   ./gradlew -q :calc:run --args="--lat 19.0760 --lon 72.8777 --tz Asia/Kolkata --sampradaya marathi --year 2026"
   ```
   Verify output includes the New Year festival on the expected date.

9. **Document** in `docs/sampradaya-coverage.md`:
   - What's implemented for each tradition
   - What the source was
   - What remains unverified

## Constraints

- **Do NOT modify** `:core` or `:ephemeris` — the astronomy is correct and tradition-neutral
- **Do NOT fabricate** festival dates or rules — if you don't have a source, mark it `UNVERIFIED` and state that plainly in `provenanceNote`
- **Do NOT widen tolerances** or edit golden files to make tests pass
- **Follow the existing code style**: kdoc comments, explicit types, no abbreviations
- **The Mayapur golden file** `verify/golden/vaisnavacalendar-mayapur-2026.json` must remain byte-identical
- When uncertain about a tradition's rule, emit the event as `UNVERIFIED` with a note stating what's unknown — an honest gap beats an invented answer

## Success Criteria

1. The Reykjavik bug is fixed (parana either refuses or has a note when daylight is null)
2. All 9 new traditions are registered and callable via `:calc`
3. Each tradition's New Year festival resolves to a plausible date
4. The gate passes (179 tests for ISKCON, plus new tests for the 9 traditions)
5. Documentation exists listing what's implemented vs what's unverified
6. No regression in ISKCON conformance (the 9 pre-existing failures are still 9, not more)

## Files You Will Modify or Create

**Modify**:
- `sampradaya/src/main/kotlin/org/panchang/sampradaya/EventCatalog.kt` (add `OnSolarMonth`, `In60YearCycle`)
- `sampradaya/src/main/kotlin/org/panchang/sampradaya/EventResolver.kt` (add handlers)
- `sampradaya/src/main/kotlin/org/panchang/sampradaya/IskconRules.kt` (fix Reykjavik)
- `wire/src/main/kotlin/org/panchang/wire/SiteAcceptance.kt` (fix polar gate)
- `calc/src/main/kotlin/org/panchang/calc/CalcEngine.kt` (register 9 new traditions)

**Create**:
- `sampradaya/src/main/kotlin/org/panchang/sampradaya/MarathiRules.kt` (and 8 others)
- `sampradaya/src/main/kotlin/org/panchang/sampradaya/MarathiEventCatalog.kt` (and 8 others)
- `sampradaya/src/test/kotlin/org/panchang/sampradaya/MarathiCalendarTest.kt` (and 8 others)
- `docs/sampradaya-coverage.md`

## Loop Until Complete

After each step:
1. Run `./gradlew --no-daemon :sampradaya:test :calc:test`
2. If compilation fails, read the error and fix it
3. If tests fail (new failures, not the 9 pre-existing ones), read the failure message and fix it
4. If a tradition's output is clearly wrong (e.g., New Year in December when it should be April), investigate and fix
5. Once all new tests pass and CLI output looks reasonable, you're done

**PROMPT END**

---

## Final Checklist

Before considering this complete:

- [ ] Reykjavik bug fixed (parana refuses or notes when daylight is null)
- [ ] Polar acceptance gate uses dynamic check, not fixed 66°
- [ ] `OnSolarMonth` rule exists and resolver handles it
- [ ] All 9 traditions registered and callable
- [ ] Each tradition's New Year resolves to a plausible date
- [ ] Tests pass (179 + new tests, 9 pre-existing failures still 9)
- [ ] Documentation written (`docs/sampradaya-coverage.md`)
- [ ] No regression in ISKCON conformance
- [ ] Mayapur golden file byte-identical
- [ ] CLI tested: `--sampradaya marathi`, `--sampradaya tamil`, etc. all work

## Notes for the Human

This is a **large** task. Expect it to take multiple iterations even with an autonomous AI. The AI should:
- Fix the Reykjavik bug first (it's critical and well-scoped)
- Add solar rule support next (needed by 4 of the 9 traditions)
- Then add traditions one by one, testing each before moving to the next

If the AI gets stuck, the most likely blockers are:
1. **No reference calendars** — the AI cannot verify what date a festival should fall on without a published calendar to check against. Solution: mark everything `UNVERIFIED` and defer validation.
2. **Ekadashi rules differ** — each tradition may have different viddha tests, parana bounds, or Mahadvadashi detection. Solution: return empty list from `ekadashiObservances` for now, document as TODO.
3. **Solar month indexing** — verify whether the code uses 0-based (Mesha=0) or 1-based (Mesha=1). Solution: check `PanchangCalculator.solarTransit` return type.

The AI must NOT invent festival rules. If a source isn't available, the tradition ships with a minimal catalog and `UNVERIFIED` status.
