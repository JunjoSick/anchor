package dev.junjo.anchor.data

import dev.junjo.anchor.TestClocks.ROME
import dev.junjo.anchor.TestClocks.at
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalTest {
    private fun goal(
        id: String,
        day: String = "2026-11-14",
        createdAt: Long = 0,
        updatedAt: Long = createdAt,
        doneAt: Long? = null,
        deleted: Boolean = false,
        pinnedAt: Long? = null,
        reminderId: String? = null,
        firedAt: Long? = null,
    ) = Goal(id, "Goal $id", day, createdAt, updatedAt, doneAt, deleted, pinnedAt, reminderId, firedAt)

    private fun ms(text: String) = at(ROME, text).toInstant().toEpochMilli()

    @Test fun activeIsUndoneUndeletedInTheOrderAdded() {
        val goals = listOf(
            goal("c", createdAt = 3),
            goal("a", createdAt = 1),
            goal("done", createdAt = 0, doneAt = 5),
            goal("gone", deleted = true),
            goal("b", createdAt = 2),
        )
        assertEquals(listOf("a", "b", "c"), goals.active().map { it.id })
    }

    @Test fun pinnedGoalsComeFirstMostRecentOnTop() {
        val goals = listOf(
            goal("a", createdAt = 1),
            goal("b", createdAt = 2, pinnedAt = 100),
            goal("c", createdAt = 3, pinnedAt = 200),
            goal("d", createdAt = 4),
        )
        assertEquals(listOf("c", "b", "a", "d"), goals.active().map { it.id })
    }

    @Test fun carriedOverAcrossMonthAndYearEnds() {
        val goals = listOf(goal("old", day = "2026-11-30"), goal("today", day = "2026-12-01"), goal("done", day = "2026-11-30", doneAt = 1))
        assertEquals(listOf("old"), goals.carriedOver(today = "2026-12-01").map { it.id })
        val newYear = listOf(goal("old", day = "2026-12-31"), goal("today", day = "2027-01-01"))
        assertEquals(listOf("old"), newYear.carriedOver(today = "2027-01-01").map { it.id })
        assertEquals(emptyList<Goal>(), newYear.carriedOver(today = "2026-12-31"))
    }

    @Test fun doneTodayStartsAtMidnightNewestFirst() {
        val midnight = ms("2026-11-14T00:00")
        val goals = listOf(
            goal("yesterday", doneAt = ms("2026-11-13T23:59")),
            goal("midnight", doneAt = midnight),
            goal("noon", doneAt = ms("2026-11-14T12:00")),
            goal("deleted", doneAt = ms("2026-11-14T13:00"), deleted = true),
            goal("open"),
        )
        assertEquals(listOf("noon", "midnight"), goals.doneToday(startOfDay = midnight).map { it.id })
    }

    @Test fun staleAfterThreeDaysUntouchedNeverPinnedOrRoutine() {
        val now = 10L * 24 * 60 * 60 * 1000
        val goals = listOf(
            goal("exact-3d", updatedAt = now - STALE_MS),
            goal("just-under", updatedAt = now - STALE_MS + 1),
            goal("pinned", updatedAt = now - STALE_MS * 2, pinnedAt = 1),
            goal("routine", updatedAt = now - STALE_MS * 2, reminderId = "r1", firedAt = 1),
            goal("done", updatedAt = now - STALE_MS * 2, doneAt = now),
            goal("deleted", updatedAt = now - STALE_MS * 2, deleted = true),
        )
        assertEquals(listOf("exact-3d"), goals.stale(now).map { it.id })
        // Touching updatedAt (what keep() does) resets the 3-day window.
        val kept = listOf(goal("exact-3d", updatedAt = now))
        assertEquals(emptyList<Goal>(), kept.stale(now + STALE_MS - 1))
        assertEquals(listOf("exact-3d"), kept.stale(now + STALE_MS).map { it.id })
    }

    @Test fun jsonRoundTrip() {
        val cases = listOf(
            goal("a", doneAt = 42),
            goal("b", pinnedAt = 99),
            goal("c", deleted = true),
            goal("d", reminderId = "r1", firedAt = 123_456),
        )
        for (g in cases) {
            assertEquals(g, Goal.fromJson(JSONObject(g.toJson().toString())))
        }
        // A v0.5.2 JSON entry without reminderId or firedAt loads with nulls.
        val legacy = JSONObject("""{"id":"old","text":"Call Sam","day":"2026-11-22","createdAt":10,"updatedAt":20,"deleted":false}""")
        assertEquals(Goal("old", "Call Sam", "2026-11-22", 10, 20), Goal.fromJson(legacy))
    }
}
