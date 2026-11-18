package dev.junjo.anchor.reminders

import dev.junjo.anchor.TestClocks
import dev.junjo.anchor.TestClocks.NEW_YORK
import dev.junjo.anchor.TestClocks.ROME
import dev.junjo.anchor.TestClocks.at
import dev.junjo.anchor.data.Reminder
import dev.junjo.anchor.data.Reminder.Companion.DAY_MINUTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/** Reminders that repeat every few days or hours instead of on set weekdays. */
class RemindersIntervalTest {
    private fun every(days: Int, hours: Int, hour: Int, minute: Int, startAt: ZonedDateTime) =
        Reminder("id", "Meds", hour, minute, days = 0, createdAt = 0, updatedAt = 0,
            everyMinutes = days * DAY_MINUTES + hours * 60, startAt = startAt.toInstant().toEpochMilli())

    private fun next(r: Reminder, now: ZonedDateTime): ZonedDateTime =
        Instant.ofEpochMilli(Reminders.next(r, now)!!).atZone(now.zone)

    private fun ms(t: ZonedDateTime) = t.toInstant().toEpochMilli()

    @Test fun everyTwoDaysKeepsItsRhythm() {
        val r = every(2, 0, 9, 0, at(ROME, "2026-11-14T09:00"))
        assertEquals(at(ROME, "2026-11-14T09:00"), next(r, at(ROME, "2026-11-14T08:00")))
        assertEquals(at(ROME, "2026-11-16T09:00"), next(r, at(ROME, "2026-11-14T09:00")))
        assertEquals(at(ROME, "2026-11-16T09:00"), next(r, at(ROME, "2026-11-15T12:00")))
        assertEquals(at(ROME, "2026-11-18T09:00"), next(r, at(ROME, "2026-11-16T09:30")))
        // Across a month end, and months later.
        assertEquals(at(ROME, "2026-12-02T09:00"), next(r, at(ROME, "2026-11-30T10:00")))
        assertEquals(at(ROME, "2027-03-02T09:00"), next(r, at(ROME, "2027-03-01T09:00")))
    }

    @Test fun startInTheFutureWaitsForIt() {
        val r = every(3, 0, 20, 0, at(ROME, "2026-11-20T20:00"))
        assertEquals(at(ROME, "2026-11-20T20:00"), next(r, at(ROME, "2026-11-14T08:00")))
        val h = every(0, 6, 7, 0, at(ROME, "2026-11-20T07:00"))
        assertEquals(at(ROME, "2026-11-20T07:00"), next(h, at(ROME, "2026-11-14T08:00")))
    }

    @Test fun everyFewHoursCountsFromTheStart() {
        val r = every(0, 3, 8, 0, at(ROME, "2026-11-14T08:00"))
        assertEquals(at(ROME, "2026-11-14T11:00"), next(r, at(ROME, "2026-11-14T10:00")))
        assertEquals(at(ROME, "2026-11-14T14:00"), next(r, at(ROME, "2026-11-14T11:00")))
        // Overnight it carries on: 23:00, 02:00, 05:00 …
        assertEquals(at(ROME, "2026-11-15T02:00"), next(r, at(ROME, "2026-11-14T23:30")))
    }

    @Test fun dayAndHoursMixed() {
        // Every 1 day and 12 hours = every 36 hours.
        val r = every(1, 12, 8, 0, at(ROME, "2026-11-14T08:00"))
        assertEquals(at(ROME, "2026-11-15T20:00"), next(r, at(ROME, "2026-11-14T08:00")))
        assertEquals(at(ROME, "2026-11-17T08:00"), next(r, at(ROME, "2026-11-15T20:00")))
    }

    @Test fun wholeDaysStayAtTheSameClockTimeAcrossClockChanges() {
        for (zone in listOf(ROME, NEW_YORK)) for (gap in listOf(true, false)) {
            val change = TestClocks.transition(zone, gap).dateTimeBefore.toLocalDate()
            val r = every(1, 0, 9, 0, change.minusDays(3).atTime(9, 0).atZone(zone))
            var now = change.minusDays(2).atTime(10, 0).atZone(zone)
            repeat(5) {
                val got = next(r, now)
                assertEquals("$zone $now", 9, got.hour)
                assertEquals("$zone $now", 0, got.minute)
                now = got
            }
        }
    }

    @Test fun hoursAreRealTimeAcrossClockChanges() {
        // Medicine every 8 hours: always exactly 8 real hours apart, even when the clocks move.
        for (zone in listOf(ROME, NEW_YORK)) for (gap in listOf(true, false)) {
            val change = TestClocks.transition(zone, gap).instant.atZone(zone)
            val r = every(0, 8, 22, 0, change.minusDays(1))
            var now = change.minusDays(1)
            repeat(9) {
                val got = next(r, now)
                assertTrue("$zone $now", got.isAfter(now))
                assertEquals("$zone $now", 0, Duration.between(change.minusDays(1).toInstant(), got.toInstant()).toMinutes() % (8 * 60))
                now = got
            }
        }
    }

    @Test fun sweepNeverSkipsOrRepeats() {
        val start = at(ROME, "2026-03-20T07:30")
        for ((d, h) in listOf(1 to 0, 2 to 0, 7 to 0, 0 to 1, 0 to 5, 1 to 6)) {
            val r = every(d, h, 7, 30, start)
            var now = start.minusHours(1)
            var last = now
            while (now.isBefore(start.plusDays(21))) {
                val got = next(r, now)
                assertTrue("$d/$h $now", got.isAfter(now))
                assertTrue("$d/$h $now", Duration.between(now, got) <= Duration.ofMinutes(r.everyMinutes.toLong()).plusHours(1))
                assertTrue("$d/$h $now", !got.isBefore(last))
                last = got
                now = now.plusMinutes(41)
            }
        }
    }

    @Test fun intervalStart() {
        val tenAm = at(ROME, "2026-11-14T10:00")
        // Whole days: the next time 9:00 comes round, which is tomorrow.
        assertEquals(ms(at(ROME, "2026-11-15T09:00")), Reminder.intervalStart(9, 0, 2 * DAY_MINUTES, tenAm))
        assertEquals(ms(at(ROME, "2026-11-14T11:00")), Reminder.intervalStart(11, 0, 2 * DAY_MINUTES, tenAm))
        // With hours: today at 8:00 even though it has passed, so the next one is 11:00.
        val start = Reminder.intervalStart(8, 0, 3 * 60, tenAm)
        assertEquals(ms(at(ROME, "2026-11-14T08:00")), start)
        val r = every(0, 3, 8, 0, at(ROME, "2026-11-14T08:00"))
        assertEquals(at(ROME, "2026-11-14T11:00"), next(r, tenAm))
    }

    @Test fun intervalText() {
        assertEquals("Every day", Reminder.intervalText(DAY_MINUTES))
        assertEquals("Every 2 days", Reminder.intervalText(2 * DAY_MINUTES))
        assertEquals("Every hour", Reminder.intervalText(60))
        assertEquals("Every 8 hours", Reminder.intervalText(8 * 60))
        assertEquals("Every 3 days and 6 hours", Reminder.intervalText(3 * DAY_MINUTES + 6 * 60))
    }
}
