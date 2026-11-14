# Plan: unit tests for the time maths

**Goal:** catch the bugs that fail silently, such as a reminder that skips a day or a timer that
never rings after a reboot. No new features, and no change to how the app behaves.

**Done means:** `./gradlew testDebugUnitTest` passes, `./gradlew assembleRelease` still builds,
and the release APK has no new runtime dependencies.

## Ground rules

- Plain JVM unit tests in `app/src/test/java/dev/junjo/anchor/`. No Robolectric, no emulator.
- Refactors are limited to the seams listed below. Each one must leave behaviour identical.
- If a test exposes a real bug, keep the failing test, report it, and fix it in its own commit.
  Do not bend the test to match the code.
- Out of scope: UI, the overlay bubble, the widget, notifications, CI, a release, installing on
  the phone.

## Setup

- Add to `gradle/libs.versions.toml` and `app/build.gradle.kts`:
  `testImplementation` for `junit:junit:4.13.2` and `org.json:json` (the Android stub of
  `org.json` throws "not mocked" in unit tests, so the JSON tests need the real one).
- Build with `JAVA_HOME` pointing at Android Studio's `jbr`. AGP 9.3.1 uses built-in Kotlin, so
  do not add the Kotlin Gradle plugin.
- Every test passes an explicit `ZonedDateTime` or epoch value. No test may read the real clock
  or the machine's time zone. Use `Europe/Rome` and `America/New_York` as fixed zones, and look
  the clock-change dates up from `ZoneRules` rather than hard-coding them.

## Seams to add (small, behaviour-preserving)

| Where | Change |
|---|---|
| `reminders/Reminders.kt` | None. `next(r, now)` already takes the time. |
| `widget/Widgets.kt` `DayRollover` | Extract `nextRun(now: ZonedDateTime): Long`; `schedule` calls it. |
| `data/Goal.kt` | `doneToday(startOfDay: Long = startOfToday())`. `carriedOver(today)` already takes the day. |
| `timer/FocusTimer.kt` | Extract three pure functions; the object keeps calling them: `alarmPlan(state, now)` returning the end, halfway and five-left times (null when not scheduled), `isStaleEnd(state, now)`, and `afterRestart(state, now)` returning the state to restore to. |

## Tests

**`RemindersNextTest`** (`Reminders.next`)
- Later today gives today. Already passed gives the next enabled day. Exactly now gives the next one, not now.
- Weekdays-only on a Friday evening gives Monday. A single enabled day that has passed gives 7 days later.
- No days picked gives null.
- 23:59 and 00:00, across a month end and a year end.
- Clocks go forward: a reminder set inside the skipped hour still fires that day, once.
- Clocks go back: a reminder inside the repeated hour fires once, and the one after it is the next day.
- Sweep: for every 7 minutes across two weeks that include a clock change, the result is after
  `now`, at most 8 days ahead, and on an enabled day.

**`ReminderTest`**: `isOn` for each day against `ALL_DAYS`, `WEEKDAYS`, `WEEKEND`; `daysText`;
JSON round trip; JSON with the optional fields missing (an older file) loads with the defaults.

**`GoalTest`**: `active` order; `carriedOver` across a month end and a year end; `doneToday`
excludes a goal done at 23:59 yesterday, includes one done at 00:00, excludes deleted, newest
first; JSON round trip with and without `doneAt`.

**`DayRolloverTest`**: `nextRun` is 5 s after the next local midnight, including on both
clock-change days and when called at 23:59:59 and at 00:00:00.

**`FocusTimerTest`**
- `State.remaining` when running, paused, idle, and past the end (never negative).
- `alarmPlan`: 5 min has only the end; 10 min adds halfway; 15 min adds five-left; anything
  less than a second ahead is dropped (the case of adding time late in a timer).
- `isStaleEnd`: an end alarm more than 2 s early is ignored; on time is accepted; not running is ignored.
- `afterRestart`: running with time left stays running with the same end; running past the end
  becomes ringing; paused keeps its remaining time; idle stays idle.

## Known gap, to record and not fix here

The timer's end is a wall-clock time. If the user changes the phone's clock mid-timer, the
timer shifts with it. Write this down in the report; a fix is a separate decision.

## Report back

List the tests added, any bug found (with the failing test's name), and the seams changed.
Commit as `JunjoSick <78446819+JunjoSick@users.noreply.github.com>`. Do not push.
