package dev.junjo.anchor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class BackupTest {
    @Test fun roundTripPreservesGoalsNotesAndReminders() {
        val snapshot = Backup.Snapshot(
            goals = listOf(
                Goal("g1", "Reply to Sam", "2026-11-22", createdAt = 10, updatedAt = 20, pinnedAt = 15),
                Goal("g2", "Done thing", "2026-11-22", createdAt = 5, updatedAt = 30, doneAt = 30),
            ),
            notes = listOf(
                Note("n1", "Line 1\nLine 2", createdAt = 1, updatedAt = 2),
            ),
            reminders = listOf(
                Reminder("r1", "Meds", 8, 30, Reminder.ALL_DAYS, enabled = true, addAsGoal = true, createdAt = 3, updatedAt = 4, alarm = true),
            ),
        )
        assertEquals(snapshot, Backup.fromJson(Backup.toJson(snapshot, exportedAt = 999)))
    }

    @Test fun mergeKeepsNewerCopyAndAddsMissing() {
        val existing = listOf(
            Goal("a", "Old local", "2026-11-22", createdAt = 1, updatedAt = 10),
            Goal("b", "Newer local", "2026-11-22", createdAt = 2, updatedAt = 50),
        )
        val incoming = listOf(
            Goal("a", "Newer backup", "2026-11-22", createdAt = 1, updatedAt = 40),
            Goal("b", "Older backup", "2026-11-22", createdAt = 2, updatedAt = 20),
            Goal("c", "Only in backup", "2026-11-22", createdAt = 3, updatedAt = 30),
        )
        val merged = Backup.mergeById(existing, incoming, Goal::id, Goal::updatedAt)
        assertEquals(listOf("Newer backup", "Newer local", "Only in backup"), merged.map { it.text })
    }

    @Test fun rejectsUnrelatedJson() {
        try {
            Backup.fromJson("""{"hello":"world"}""")
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test fun summaryMessageFormatsCleanly() {
        assertEquals("Restored 1 to-do, 2 notes, 1 reminder", Backup.Summary(1, 2, 1).message())
        assertEquals("Restored 3 to-dos", Backup.Summary(3, 0, 0).message())
        assertEquals("Backup imported (nothing active to restore)", Backup.Summary(0, 0, 0).message())
    }
}
