package dev.junjo.anchor.reminders

import dev.junjo.anchor.TestClocks
import dev.junjo.anchor.TestClocks.NEW_YORK
import dev.junjo.anchor.TestClocks.ROME
import dev.junjo.anchor.TestClocks.at
import dev.junjo.anchor.data.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class RemindersNextTest {
    private fun reminder(hour: Int, minute: Int, days: Int = Reminder.ALL_DAYS) =
        Reminder("id", "Meds", hour, minute, days, createdAt = 0, updatedAt = 0)

    private fun next(r: Reminder, now: ZonedDateTime): ZonedDateTime =
        Instant.ofEpochMilli(Reminders.next(r, now)!!).atZone(now.zone)

    private fun only(day: DayOfWeek) = 1 shl (day.value - 1)

    @Test fun laterTodayIsToday() {
        assertEquals(at(ROME, "2026-11-14T20:00"), next(reminder(20, 0), at(ROME, "2026-11-14T08:00")))
    }

    @Test fun alreadyPassedIsTomorrow() {
        assertEquals(at(ROME, "2026-11-15T08:00"), next(reminder(8, 0), at(ROME, "2026-11-14T09:00")))
    }

    @Test fun exactlyNowIsTheNextOneNotNow() {
        assertEquals(at(ROME, "2026-11-15T08:00"), next(reminder(8, 0), at(ROME, "2026-11-14T08:00")))
    }

    @Test fun weekdaysOnFridayEveningIsMonday() {
        // 2026-11-13 is a Friday.
        assertEquals(at(ROME, "2026-11-16T07:30"), next(reminder(7, 30, Reminder.WEEKDAYS), at(ROME, "2026-11-13T19:00")))
    }

    @Test fun singlePassedDayIsAWeekLater() {
        val r = reminder(9, 0, only(DayOfWeek.FRIDAY))
        assertEquals(at(ROME, "2026-11-20T09:00"), next(r, at(ROME, "2026-11-13T10:00")))
    }

    @Test fun noDaysPickedIsNull() {
        assertNull(Reminders.next(reminder(9, 0, days = 0), at(ROME, "2026-11-14T08:00")))
    }

    @Test fun edgesOfTheDayAcrossMonthAndYearEnds() {
        assertEquals(at(ROME, "2026-11-30T23:59"), next(reminder(23, 59), at(ROME, "2026-11-30T23:58")))
        assertEquals(at(ROME, "2026-12-01T00:00"), next(reminder(0, 0), at(ROME, "2026-11-30T23:59")))
        assertEquals(at(ROME, "2027-01-01T00:00"), next(reminder(0, 0), at(ROME, "2026-12-31T00:00")))
        assertEquals(at(ROME, "2027-01-01T23:59"), next(reminder(23, 59), at(ROME, "2026-12-31T23:59:30")))
    }

    @Test fun clocksGoForwardReminderInSkippedHourStillFiresThatDayOnce() {
        for (zone in listOf(ROME, NEW_YORK)) {
            val t = TestClocks.transition(zone, gap = true)
            val skipped = t.dateTimeBefore.plusMinutes(30) // E.g. 02:30, which doesn't exist that day.
            val r = reminder(skipped.hour, skipped.minute)
            val first = next(r, t.dateTimeBefore.minusHours(1).atZone(zone))
            assertEquals("$zone", t.dateTimeBefore.toLocalDate(), first.toLocalDate())
            // Firing it moves on to the next day, not to a second time the same day.
            val second = next(r, first)
            assertEquals("$zone", first.toLocalDate().plusDays(1), second.toLocalDate())
            assertEquals("$zone", skipped.toLocalTime(), second.toLocalTime())
        }
    }

    @Test fun clocksGoBackReminderInRepeatedHourFiresOnce() {
        for (zone in listOf(ROME, NEW_YORK)) {
            val t = TestClocks.transition(zone, gap = false)
            val repeated = t.dateTimeAfter.plusMinutes(30) // E.g. 02:30, which happens twice that day.
            val r = reminder(repeated.hour, repeated.minute)
            val first = next(r, t.dateTimeAfter.minusHours(1).atZone(zone))
            assertEquals("$zone", repeated, first.toLocalDateTime())
            // Firing it, even an hour late in the repeated hour, moves on to the next day.
            for (late in listOf(0L, 60L)) {
                val second = next(r, first.plusMinutes(late))
                assertEquals("$zone +$late min", repeated.plusDays(1), second.toLocalDateTime())
            }
        }
    }

    @Test fun sweepAcrossClockChangesAlwaysGivesTheEarliestValidTime() {
        val masks = listOf(Reminder.ALL_DAYS, Reminder.WEEKDAYS, Reminder.WEEKEND, only(DayOfWeek.SUNDAY), 0b0101010)
        for (zone in listOf(ROME, NEW_YORK)) {
            for (gap in listOf(true, false)) {
                val t = TestClocks.transition(zone, gap)
                val times = listOf(0 to 0, 2 to 30, t.dateTimeBefore.hour to t.dateTimeBefore.minute, 8 to 0, 23 to 59)
                val start = t.instant.atZone(zone).minusDays(7)
                for ((h, m) in times) for (mask in masks) {
                    val r = reminder(h, m, mask)
                    var now = start
                    while (now.isBefore(start.plusDays(14))) {
                        check(r, now, zone)
                        now = now.plusMinutes(7)
                    }
                }
            }
        }
    }

    private fun check(r: Reminder, now: ZonedDateTime, zone: ZoneId) {
        val at = next(r, now)
        val msg = "${r.hour}:${r.minute} days=${r.days} now=$now got=$at"
        assertTrue(msg, at.isAfter(now))
        assertTrue(msg, at.isBefore(now.plusDays(8)))
        assertTrue(msg, r.isOn(at.dayOfWeek))
        // It's at the reminder's time, unless a clock change skipped that time on that day.
        val wanted = at.toLocalDate().atTime(r.hour, r.minute)
        if (zone.rules.getValidOffsets(wanted).isNotEmpty()) assertEquals(msg, wanted, at.toLocalDateTime())
        // And nothing earlier was skipped: no enabled day before the result had a due time after now.
        var day = now.toLocalDate()
        while (day.isBefore(at.toLocalDate())) {
            if (r.isOn(day.dayOfWeek)) {
                val due = day.atTime(r.hour, r.minute).atZone(zone)
                assertTrue("$msg skipped $due", !due.isAfter(now))
            }
            day = day.plusDays(1)
        }
    }
}
