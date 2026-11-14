package dev.junjo.anchor.timer

import dev.junjo.anchor.timer.FocusTimer.AlarmPlan
import dev.junjo.anchor.timer.FocusTimer.MINUTE
import dev.junjo.anchor.timer.FocusTimer.State
import dev.junjo.anchor.timer.FocusTimer.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTimerTest {
    private val now = 1_800_000_000_000L

    private fun running(minutes: Int, endsIn: Long = minutes * MINUTE) =
        State(Status.RUNNING, totalMs = minutes * MINUTE, endAt = now + endsIn, label = "Write", goalId = "g")

    @Test fun remaining() {
        assertEquals(3 * MINUTE, running(5, endsIn = 3 * MINUTE).remaining(now))
        assertEquals(0, running(5, endsIn = -MINUTE).remaining(now))
        assertEquals(2 * MINUTE, State(Status.PAUSED, totalMs = 5 * MINUTE, remainingMs = 2 * MINUTE).remaining(now))
        assertEquals(0, State().remaining(now))
        assertEquals(0, running(5).copy(status = Status.RINGING).remaining(now))
    }

    @Test fun shortTimerHasOnlyTheEnd() {
        assertEquals(AlarmPlan(now + 5 * MINUTE, null, null), FocusTimer.alarmPlan(running(5), now))
        assertEquals(AlarmPlan(now + 9 * MINUTE, null, null), FocusTimer.alarmPlan(running(9), now))
    }

    @Test fun tenMinutesAddsHalfway() {
        assertEquals(AlarmPlan(now + 10 * MINUTE, now + 5 * MINUTE, null), FocusTimer.alarmPlan(running(10), now))
    }

    @Test fun fifteenMinutesAddsFiveLeft() {
        val plan = FocusTimer.alarmPlan(running(15), now)
        assertEquals(now + 15 * MINUTE, plan.end)
        assertEquals(now + 7 * MINUTE + 30_000, plan.half)
        assertEquals(now + 10 * MINUTE, plan.five)
    }

    @Test fun headsUpsInThePastOrUnderASecondAheadAreDropped() {
        // 25 minutes with 4 left: halfway and five-left have both passed.
        val late = FocusTimer.alarmPlan(running(25, endsIn = 4 * MINUTE), now)
        assertEquals(now + 4 * MINUTE, late.end)
        assertNull(late.half)
        assertNull(late.five)
        // Five-left exactly one second ahead is dropped; a millisecond more is kept.
        assertNull(FocusTimer.alarmPlan(running(20, endsIn = 5 * MINUTE + 1_000), now).five)
        assertEquals(now + 1_001, FocusTimer.alarmPlan(running(20, endsIn = 5 * MINUTE + 1_001), now).five)
    }

    @Test fun staleEndAlarm() {
        assertTrue(FocusTimer.isStaleEnd(running(5, endsIn = 2_001), now))
        assertFalse(FocusTimer.isStaleEnd(running(5, endsIn = 2_000), now))
        assertFalse(FocusTimer.isStaleEnd(running(5, endsIn = 0), now))
        assertFalse(FocusTimer.isStaleEnd(running(5, endsIn = -10_000), now))
        for (status in listOf(Status.IDLE, Status.PAUSED, Status.RINGING)) {
            assertTrue("$status", FocusTimer.isStaleEnd(running(5, endsIn = 0).copy(status = status), now))
        }
    }

    @Test fun afterRestart() {
        val stillGoing = running(25, endsIn = 10 * MINUTE)
        assertSame(stillGoing, FocusTimer.afterRestart(stillGoing, now))

        val ranOut = running(25, endsIn = -MINUTE)
        assertEquals(ranOut.copy(status = Status.RINGING), FocusTimer.afterRestart(ranOut, now))
        assertEquals(Status.RINGING, FocusTimer.afterRestart(running(25, endsIn = 0), now).status)

        val paused = State(Status.PAUSED, totalMs = 25 * MINUTE, remainingMs = 7 * MINUTE)
        assertEquals(paused, FocusTimer.afterRestart(paused, now + 60 * MINUTE))

        assertEquals(State(), FocusTimer.afterRestart(State(), now))
        val ringing = ranOut.copy(status = Status.RINGING)
        assertEquals(ringing, FocusTimer.afterRestart(ringing, now))
    }
}
