package dev.junjo.anchor.widget

import dev.junjo.anchor.TestClocks
import dev.junjo.anchor.TestClocks.NEW_YORK
import dev.junjo.anchor.TestClocks.ROME
import dev.junjo.anchor.TestClocks.at
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId

class DayRolloverTest {
    private fun ms(zone: ZoneId, text: String) = at(zone, text).toInstant().toEpochMilli()

    @Test fun fiveSecondsAfterTheNextMidnight() {
        assertEquals(ms(ROME, "2026-11-15T00:00:05"), DayRollover.nextRun(at(ROME, "2026-11-14T13:00")))
        assertEquals(ms(ROME, "2026-11-15T00:00:05"), DayRollover.nextRun(at(ROME, "2026-11-14T23:59:59")))
        assertEquals(ms(ROME, "2026-11-16T00:00:05"), DayRollover.nextRun(at(ROME, "2026-11-15T00:00:00")))
        assertEquals(ms(ROME, "2027-01-01T00:00:05"), DayRollover.nextRun(at(ROME, "2026-12-31T23:59:59")))
    }

    @Test fun onClockChangeDays() {
        for (zone in listOf(ROME, NEW_YORK)) for (gap in listOf(true, false)) {
            val day = TestClocks.transition(zone, gap).dateTimeBefore.toLocalDate()
            val tomorrow = day.plusDays(1)
            // Called just before the change day, at its first moment, and at its last second.
            assertEquals("$zone", ms(zone, "${day}T00:00:05"), DayRollover.nextRun(day.minusDays(1).atTime(23, 59, 59).atZone(zone)))
            assertEquals("$zone", ms(zone, "${tomorrow}T00:00:05"), DayRollover.nextRun(day.atStartOfDay(zone)))
            assertEquals("$zone", ms(zone, "${tomorrow}T00:00:05"), DayRollover.nextRun(day.atTime(23, 59, 59).atZone(zone)))
        }
    }

    @Test fun clockChangeThatSkipsMidnightRunsAtTheFirstMomentOfTheDay() {
        // Chile moves its clocks forward at midnight, so that day starts at 01:00.
        val zone = ZoneId.of("America/Santiago")
        val t = TestClocks.transition(zone, gap = true)
        assertEquals(LocalTime.MIDNIGHT, t.dateTimeBefore.toLocalTime())
        val dayBefore = t.dateTimeBefore.toLocalDate().minusDays(1)
        assertEquals(t.instant.toEpochMilli() + 5_000, DayRollover.nextRun(dayBefore.atTime(22, 0).atZone(zone)))
    }
}
