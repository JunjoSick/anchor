package dev.junjo.anchor.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class ReminderTest {
    private fun reminder(days: Int) = Reminder("id", "Water", 10, 15, days, createdAt = 1, updatedAt = 2)

    @Test fun isOnMatchesTheMasks() {
        for (day in DayOfWeek.entries) {
            assertTrue(reminder(Reminder.ALL_DAYS).isOn(day))
            assertEquals("$day", day.value <= 5, reminder(Reminder.WEEKDAYS).isOn(day))
            assertEquals("$day", day.value >= 6, reminder(Reminder.WEEKEND).isOn(day))
            assertFalse(reminder(0).isOn(day))
        }
    }

    @Test fun daysText() {
        assertEquals("Every day", reminder(Reminder.ALL_DAYS).daysText)
        assertEquals("Weekdays", reminder(Reminder.WEEKDAYS).daysText)
        assertEquals("Weekends", reminder(Reminder.WEEKEND).daysText)
        assertEquals("No days picked", reminder(0).daysText)
        assertEquals("Mon, Wed, Sun", reminder(0b1000101).daysText)
    }

    @Test fun jsonRoundTrip() {
        val r = Reminder("id", "Meds", 8, 5, 0b0010101, enabled = false, addAsGoal = false, createdAt = 10, updatedAt = 20, deleted = true)
        assertEquals(r, Reminder.fromJson(JSONObject(r.toJson().toString())))
    }

    @Test fun olderFileWithoutOptionalFieldsLoadsWithDefaults() {
        val json = JSONObject("""{"id":"a","text":"Bed","hour":23,"minute":0,"createdAt":1,"updatedAt":1}""")
        val r = Reminder.fromJson(json)
        assertEquals(Reminder.ALL_DAYS, r.days)
        assertTrue(r.enabled)
        assertTrue(r.addAsGoal)
        assertFalse(r.deleted)
    }
}
