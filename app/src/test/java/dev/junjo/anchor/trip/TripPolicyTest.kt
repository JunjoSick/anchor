package dev.junjo.anchor.trip

import dev.junjo.anchor.trip.TripPolicy.MINUTE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPolicyTest {
    private val t0 = 1_800_000_000_000L

    private fun running(minutes: Int, startedAt: Long = t0, gos: Int = 1, halfwayDone: Boolean = false) =
        TripState(
            name = "check Marco's stories",
            minutes = minutes,
            startedAt = startedAt,
            endAt = startedAt + minutes * MINUTE,
            gos = gos,
            halfwayDone = halfwayDone,
            status = TripStatus.RUNNING,
        )

    @Test fun twoMinuteTripHitsHalfwayOnceThenLast15ThenStop() {
        val s = running(minutes = 2)
        assertEquals(TripAction.NONE, TripPolicy.tick(s, t0 + 59_000))
        assertEquals(TripAction.HALFWAY, TripPolicy.tick(s, t0 + 60_000))
        val afterHalf = s.copy(halfwayDone = true)
        assertEquals(TripAction.NONE, TripPolicy.tick(afterHalf, t0 + 61_000))
        assertEquals(TripAction.NONE, TripPolicy.tick(afterHalf, t0 + 104_000))
        assertEquals(TripAction.LAST_15, TripPolicy.tick(afterHalf, t0 + 105_000))
        assertEquals(TripAction.LAST_15, TripPolicy.tick(afterHalf, t0 + 119_000))
        assertEquals(TripAction.STOP, TripPolicy.tick(afterHalf, t0 + 120_000))
    }

    @Test fun oneMinuteTripSkipsHalfway() {
        val s = running(minutes = 1)
        assertEquals(TripAction.NONE, TripPolicy.tick(s, t0 + 30_000))
        assertEquals(TripAction.NONE, TripPolicy.tick(s, t0 + 44_000))
        assertEquals(TripAction.LAST_15, TripPolicy.tick(s, t0 + 45_000))
        assertEquals(TripAction.STOP, TripPolicy.tick(s, t0 + 60_000))
    }

    @Test fun againKeepsMinutesIncrementsGosAndResetsHalfway() {
        val stopped = running(minutes = 2, gos = 1, halfwayDone = true).copy(status = TripStatus.STOP)
        val next = TripPolicy.again(stopped, t0 + 200_000)
        assertEquals("check Marco's stories", next.name)
        assertEquals(2, next.minutes)
        assertEquals(2, next.gos)
        assertEquals(t0 + 200_000, next.startedAt)
        assertEquals(t0 + 200_000 + 2 * MINUTE, next.endAt)
        assertFalse(next.halfwayDone)
        assertEquals(TripStatus.RUNNING, next.status)
    }

    @Test fun againDelayEscalatesAndCapsAtTenSeconds() {
        assertEquals(0L, TripPolicy.againDelayMs(1))
        assertEquals(3_000L, TripPolicy.againDelayMs(2))
        assertEquals(6_000L, TripPolicy.againDelayMs(3))
        assertEquals(10_000L, TripPolicy.againDelayMs(4))
        assertEquals(10_000L, TripPolicy.againDelayMs(5))
    }

    @Test fun suggestFocusFromThirdGo() {
        assertFalse(TripPolicy.suggestFocus(1))
        assertFalse(TripPolicy.suggestFocus(2))
        assertTrue(TripPolicy.suggestFocus(3))
        assertTrue(TripPolicy.suggestFocus(4))
    }

    @Test fun afterRestartTransitionsExpiredRunningTripToStop() {
        val s = running(minutes = 2)
        assertEquals(s, TripPolicy.afterRestart(s, t0 + 119_000))
        assertEquals(s.copy(status = TripStatus.STOP), TripPolicy.afterRestart(s, t0 + 120_000))
        assertEquals(s.copy(status = TripStatus.STOP), TripPolicy.afterRestart(s, t0 + 500_000))

        val idle = TripState()
        assertEquals(idle, TripPolicy.afterRestart(idle, t0 + 500_000))
        val stopped = s.copy(status = TripStatus.STOP)
        assertEquals(stopped, TripPolicy.afterRestart(stopped, t0 + 500_000))
    }
}
