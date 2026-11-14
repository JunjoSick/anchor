package dev.junjo.anchor.data

import dev.junjo.anchor.TestClocks.ROME
import dev.junjo.anchor.TestClocks.at
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalTest {
    private fun goal(id: String, day: String = "2026-11-14", createdAt: Long = 0, doneAt: Long? = null, deleted: Boolean = false) =
        Goal(id, "Goal $id", day, createdAt, createdAt, doneAt, deleted)

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

    @Test fun jsonRoundTrip() {
        for (g in listOf(goal("a", doneAt = 42), goal("b"), goal("c", deleted = true))) {
            assertEquals(g, Goal.fromJson(JSONObject(g.toJson().toString())))
        }
    }
}
