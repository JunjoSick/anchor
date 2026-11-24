package dev.junjo.anchor.data

import dev.junjo.anchor.TestClocks.ROME
import dev.junjo.anchor.TestClocks.at
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TodoTest {
    private fun todo(
        id: String,
        day: String = "2026-11-14",
        createdAt: Long = 0,
        updatedAt: Long = createdAt,
        doneAt: Long? = null,
        deleted: Boolean = false,
        pinnedAt: Long? = null,
        reminderId: String? = null,
        firedAt: Long? = null,
    ) = Todo(id, "To-do $id", day, createdAt, updatedAt, doneAt, deleted, pinnedAt, reminderId, firedAt)

    private fun ms(text: String) = at(ROME, text).toInstant().toEpochMilli()

    @Test fun activeIsUndoneUndeletedInTheOrderAdded() {
        val todos = listOf(
            todo("c", createdAt = 3),
            todo("a", createdAt = 1),
            todo("done", createdAt = 0, doneAt = 5),
            todo("gone", deleted = true),
            todo("b", createdAt = 2),
        )
        assertEquals(listOf("a", "b", "c"), todos.active().map { it.id })
    }

    @Test fun pinnedTodosComeFirstMostRecentOnTop() {
        val todos = listOf(
            todo("a", createdAt = 1),
            todo("b", createdAt = 2, pinnedAt = 100),
            todo("c", createdAt = 3, pinnedAt = 200),
            todo("d", createdAt = 4),
        )
        assertEquals(listOf("c", "b", "a", "d"), todos.active().map { it.id })
    }

    @Test fun carriedOverAcrossMonthAndYearEnds() {
        val todos = listOf(todo("old", day = "2026-11-30"), todo("today", day = "2026-12-01"), todo("done", day = "2026-11-30", doneAt = 1))
        assertEquals(listOf("old"), todos.carriedOver(today = "2026-12-01").map { it.id })
        val newYear = listOf(todo("old", day = "2026-12-31"), todo("today", day = "2027-01-01"))
        assertEquals(listOf("old"), newYear.carriedOver(today = "2027-01-01").map { it.id })
        assertEquals(emptyList<Todo>(), newYear.carriedOver(today = "2026-12-31"))
    }

    @Test fun doneTodayStartsAtMidnightNewestFirst() {
        val midnight = ms("2026-11-14T00:00")
        val todos = listOf(
            todo("yesterday", doneAt = ms("2026-11-13T23:59")),
            todo("midnight", doneAt = midnight),
            todo("noon", doneAt = ms("2026-11-14T12:00")),
            todo("deleted", doneAt = ms("2026-11-14T13:00"), deleted = true),
            todo("open"),
        )
        assertEquals(listOf("noon", "midnight"), todos.doneToday(startOfDay = midnight).map { it.id })
    }

    @Test fun staleAfterThreeDaysUntouchedNeverPinnedOrRoutine() {
        val now = 10L * 24 * 60 * 60 * 1000
        val todos = listOf(
            todo("exact-3d", updatedAt = now - STALE_MS),
            todo("just-under", updatedAt = now - STALE_MS + 1),
            todo("pinned", updatedAt = now - STALE_MS * 2, pinnedAt = 1),
            todo("routine", updatedAt = now - STALE_MS * 2, reminderId = "r1", firedAt = 1),
            todo("done", updatedAt = now - STALE_MS * 2, doneAt = now),
            todo("deleted", updatedAt = now - STALE_MS * 2, deleted = true),
        )
        assertEquals(listOf("exact-3d"), todos.stale(now).map { it.id })
        // Touching updatedAt (what keep() does) resets the 3-day window.
        val kept = listOf(todo("exact-3d", updatedAt = now))
        assertEquals(emptyList<Todo>(), kept.stale(now + STALE_MS - 1))
        assertEquals(listOf("exact-3d"), kept.stale(now + STALE_MS).map { it.id })
    }

    @Test fun jsonRoundTrip() {
        val cases = listOf(
            todo("a", doneAt = 42),
            todo("b", pinnedAt = 99),
            todo("c", deleted = true),
            todo("d", reminderId = "r1", firedAt = 123_456),
        )
        for (t in cases) {
            assertEquals(t, Todo.fromJson(JSONObject(t.toJson().toString())))
        }
        // A v0.5.2 JSON entry without reminderId or firedAt loads with nulls.
        val legacy = JSONObject("""{"id":"old","text":"Call Sam","day":"2026-11-22","createdAt":10,"updatedAt":20,"deleted":false}""")
        assertEquals(Todo("old", "Call Sam", "2026-11-22", 10, 20), Todo.fromJson(legacy))
    }
}
