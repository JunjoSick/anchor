package dev.junjo.anchor.reminders

import dev.junjo.anchor.data.Todo
import dev.junjo.anchor.data.active
import org.junit.Assert.assertEquals
import org.junit.Test

class OccurrenceTest {
    private val t1 = 1_800_000_000_000L
    private val t2 = t1 + 24 * 60 * 60 * 1000L

    private fun todo(
        id: String,
        text: String = "Meds",
        doneAt: Long? = null,
        deleted: Boolean = false,
        reminderId: String? = null,
        firedAt: Long? = null,
    ) = Todo(id, text, "2026-11-24", createdAt = 1, updatedAt = 1, doneAt = doneAt, deleted = deleted, reminderId = reminderId, firedAt = firedAt)

    @Test fun createsNewToDoWhenNoneIsOpen() {
        val list = listOf(todo("done", doneAt = t1, reminderId = "r1", firedAt = t1))
        assertEquals(
            Reminders.Occurrence.Create("r1", firedAt = t2),
            Reminders.resolveOccurrence(list.active(), "r1", "Meds", t2),
        )
    }

    @Test fun reusesOpenToDoFromSameReminderKeepingOldestFiredAt() {
        val list = listOf(todo("g1", text = "Renamed meds", reminderId = "r1", firedAt = t1))
        assertEquals(
            Reminders.Occurrence.Reuse("g1", attachReminderId = null, attachFiredAt = null),
            Reminders.resolveOccurrence(list.active(), "r1", "Meds", t2),
        )
    }

    @Test fun reusesHandMadeOrPreUpdateToDoWithSameTextAndAttachesReminderId() {
        val list = listOf(todo("manual", text = "meds", reminderId = null, firedAt = null))
        assertEquals(
            Reminders.Occurrence.Reuse("manual", attachReminderId = "r1", attachFiredAt = t2),
            Reminders.resolveOccurrence(list.active(), "r1", "Meds", t2),
        )
    }

    @Test fun tickedMatchingToDoDoesNotBlockCreatingANewOne() {
        val list = listOf(todo("ticked", text = "Meds", doneAt = t1))
        assertEquals(
            Reminders.Occurrence.Create("r1", firedAt = t2),
            Reminders.resolveOccurrence(list.active(), "r1", "Meds", t2),
        )
    }
}
