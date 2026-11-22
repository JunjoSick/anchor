package dev.junjo.anchor.timer

import dev.junjo.anchor.timer.FocusTimer.AlarmPlan
import dev.junjo.anchor.timer.FocusTimer.MINUTE
import dev.junjo.anchor.timer.FocusTimer.Phase
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

    private fun gettingReady(readyMin: Int, focusMin: Int, endsIn: Long = readyMin * MINUTE) =
        State(Status.RUNNING, readyMin * MINUTE, now + endsIn, label = "Write", goalId = "g", phase = Phase.READY, focusMs = focusMin * MINUTE)

    @Test fun gettingReadyHasNoHeadsUps() {
        // Even a long get-ready countdown only has its end.
        assertEquals(AlarmPlan(now + 15 * MINUTE, null, null), FocusTimer.alarmPlan(gettingReady(15, 25), now))
        assertTrue(gettingReady(5, 25).isGettingReady)
        assertTrue(gettingReady(5, 25).copy(status = Status.PAUSED).isGettingReady)
        assertFalse(running(25).isGettingReady)
    }

    @Test fun focusFollowsGettingReady() {
        val focus = FocusTimer.focusAfterReady(gettingReady(5, 25), now)
        assertEquals(State(Status.RUNNING, 25 * MINUTE, now + 25 * MINUTE, 0, "Write", "g", Phase.FOCUS, 0, readyMs = 5 * MINUTE), focus)
        // And it gets the focus heads-ups.
        assertEquals(AlarmPlan(now + 25 * MINUTE, now + 12 * MINUTE + 30_000, now + 20 * MINUTE), FocusTimer.alarmPlan(focus, now))
    }

    @Test fun afterRestartWhileGettingReady() {
        val ready = gettingReady(5, 25, endsIn = 2 * MINUTE)
        assertSame(ready, FocusTimer.afterRestart(ready, now))

        // The phone was off when the get-ready countdown ended: wait ringing for the user to start.
        val missedStart = gettingReady(5, 25, endsIn = -10 * MINUTE)
        val ringing = FocusTimer.afterRestart(missedStart, now)
        assertEquals(Phase.READY, ringing.phase)
        assertEquals(Status.RINGING, ringing.status)
        assertTrue(ringing.isReadyRinging)
        assertEquals(25 * MINUTE, ringing.focusMs)

        // Paused while getting ready stays paused.
        val paused = gettingReady(5, 25).copy(status = Status.PAUSED, remainingMs = MINUTE)
        assertEquals(paused, FocusTimer.afterRestart(paused, now + 60 * MINUTE))
    }

    @Test fun progressBarShowsOnePartAtATime() {
        // Getting ready: the bar fills up to the start.
        val ready = gettingReady(5, 25, endsIn = 2 * MINUTE)
        assertEquals(TimerNotifications.Bar(300, 180), TimerNotifications.bar(ready, now))

        // Ten minutes into the focus that followed: a fresh bar for the focus block alone.
        val focus = FocusTimer.focusAfterReady(ready, now - 10 * MINUTE)
        assertEquals(TimerNotifications.Bar(1500, 600), TimerNotifications.bar(focus, now))

        // A plain timer.
        assertEquals(TimerNotifications.Bar(300, 240), TimerNotifications.bar(running(5, endsIn = MINUTE), now))

        // Paused keeps its place; past the end it stays full.
        assertEquals(1500 - 420, TimerNotifications.bar(State(Status.PAUSED, 25 * MINUTE, remainingMs = 7 * MINUTE), now).progress)
        assertEquals(TimerNotifications.Bar(300, 300), TimerNotifications.bar(running(5, endsIn = -MINUTE), now))
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
