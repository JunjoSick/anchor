package dev.junjo.anchor.reminders

import dev.junjo.anchor.data.Reminder
import dev.junjo.anchor.data.ReminderStore
import org.junit.Assert.assertEquals
import org.junit.Test

class ComingUpTest {
    private val now = 1_800_000_000_000L
    private val hour = 60 * 60 * 1000L
    private val day = 24 * hour

    private fun once(id: String, inMs: Long, enabled: Boolean = true, deleted: Boolean = false) = Reminder(
        id = id,
        text = "Call $id",
        hour = 10,
        minute = 0,
        days = 0,
        enabled = enabled,
        createdAt = 0,
        updatedAt = 0,
        deleted = deleted,
        startAt = now + inMs,
        once = true,
    )

    @Test fun includesOneOffsWithin24HoursSoonestFirstCappedAtThree() {
        val list = listOf(
            once("exact-24h", day),
            once("past-24h", day + 1),
            once("already-fired", 0),
            once("in-past", -hour),
            once("disabled", hour, enabled = false),
            once("deleted", hour, deleted = true),
            Reminder("routine", "Meds", 8, 0, createdAt = 0, updatedAt = 0, startAt = now + hour, once = false),
            once("third", 5 * hour),
            once("first", hour),
            once("second", 2 * hour),
        )
        assertEquals(listOf("first", "second", "third"), ReminderStore.comingUp(list, now).map { it.id })
    }

    @Test fun exact24hIsIncludedAnd24hPlusOneMsIsExcluded() {
        val list = listOf(once("at-24h", day), once("past-24h", day + 1))
        assertEquals(listOf("at-24h"), ReminderStore.comingUp(list, now).map { it.id })
    }
}
