package dev.junjo.anchor.reminders

import dev.junjo.anchor.TestClocks.ROME
import dev.junjo.anchor.TestClocks.at
import dev.junjo.anchor.data.Reminder
import dev.junjo.anchor.data.ReminderStore
import dev.junjo.anchor.durationText
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Remind me in…": reminders that go off once. */
class RemindersOnceTest {
    private val now = at(ROME, "2026-11-18T14:00")
    private fun ms(text: String) = at(ROME, text).toInstant().toEpochMilli()

    private fun once(id: String, at: Long) = Reminder(
        id, "Call mum", 0, 0, days = 0, createdAt = 0, updatedAt = 0, startAt = at, once = true,
    )

    @Test fun goesOffOnceThenNever() {
        val r = once("a", ms("2026-11-18T14:40"))
        assertEquals(ms("2026-11-18T14:40"), Reminders.next(r, now))
        // Once it has gone off there is no next time, so nothing is rescheduled.
        assertNull(Reminders.next(r, at(ROME, "2026-11-18T14:40")))
        assertNull(Reminders.next(r, at(ROME, "2026-11-19T09:00")))
    }

    @Test fun isNeitherWeeklyNorInterval() {
        val r = once("a", ms("2026-11-18T14:40")).copy(everyMinutes = 60)
        assertTrue(r.once)
        assertFalse(r.repeatsByInterval)
        assertEquals("Once", r.daysText)
        assertEquals(ms("2026-11-18T14:40"), Reminders.next(r, now))
    }

    @Test fun listShowsUpcomingOneOffsFirstAndHidesPastOnes() {
        val nowMs = ms("2026-11-18T14:00")
        val list = listOf(
            Reminder("meds", "Meds", 8, 0, createdAt = 0, updatedAt = 0),
            once("later", ms("2026-11-18T18:00")),
            once("past", ms("2026-11-18T13:00")),
            Reminder("water", "Water", 7, 30, createdAt = 0, updatedAt = 0),
            once("soon", ms("2026-11-18T14:10")),
            once("cancelled", ms("2026-11-18T15:00")).copy(deleted = true),
        )
        assertEquals(listOf("soon", "later", "water", "meds"), ReminderStore.visible(list, nowMs).map { it.id })
    }

    @Test fun jsonRoundTrip() {
        val r = once("a", 123_456_789).copy(alarm = true)
        assertEquals(r, Reminder.fromJson(JSONObject(r.toJson().toString())))
    }

    @Test fun durations() {
        assertEquals("5 min", durationText(5))
        assertEquals("1 h", durationText(60))
        assertEquals("1 h 20 min", durationText(80))
        assertEquals("23 h 59 min", durationText(23 * 60 + 59))
    }
}
