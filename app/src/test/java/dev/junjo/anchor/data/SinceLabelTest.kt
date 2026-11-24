package dev.junjo.anchor.data

import dev.junjo.anchor.TestClocks
import dev.junjo.anchor.TestClocks.ROME
import dev.junjo.anchor.TestClocks.at
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.format.DateTimeFormatter

class SinceLabelTest {
    private val fmt = DateTimeFormatter.ofPattern("H:mm")

    @Test fun formatsTodayYesterdayWeekdayAndOlder() {
        // 2026-11-24 is a Tuesday.
        val now = at(ROME, "2026-11-24T14:00")
        assertEquals("8:00", sinceLabel(at(ROME, "2026-11-24T08:00").toInstant().toEpochMilli(), now, fmt))
        assertEquals("since yesterday 8:00", sinceLabel(at(ROME, "2026-11-23T08:00").toInstant().toEpochMilli(), now, fmt))
        assertEquals("since Sun 8:00", sinceLabel(at(ROME, "2026-11-22T08:00").toInstant().toEpochMilli(), now, fmt))
        assertEquals("since Wed 8:00", sinceLabel(at(ROME, "2026-11-18T08:00").toInstant().toEpochMilli(), now, fmt))
        assertEquals("since 17 Nov", sinceLabel(at(ROME, "2026-11-17T08:00").toInstant().toEpochMilli(), now, fmt))
    }

    @Test fun acrossMidnightAndBothClockChangesInRome() {
        // Just across midnight: 23:55 yesterday -> "since yesterday 23:55".
        val justAfterMidnight = at(ROME, "2026-11-24T00:05")
        val justBeforeMidnight = at(ROME, "2026-11-23T23:55").toInstant().toEpochMilli()
        assertEquals("since yesterday 23:55", sinceLabel(justBeforeMidnight, justAfterMidnight, fmt))

        // Both spring-forward (23h day) and fall-back (25h day) in Europe/Rome.
        for (gap in listOf(true, false)) {
            val changeDate = TestClocks.transition(ROME, gap).dateTimeBefore.toLocalDate()
            val fired = changeDate.minusDays(1).atTime(8, 0).atZone(ROME).toInstant().toEpochMilli()
            val afterChange = changeDate.atTime(9, 0).atZone(ROME)
            assertEquals("gap=$gap", "since yesterday 8:00", sinceLabel(fired, afterChange, fmt))
        }
    }
}
