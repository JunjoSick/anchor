package dev.junjo.anchor

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.zone.ZoneOffsetTransition

/** Fixed zones and clock changes looked up from the zone rules, so no test reads the real clock. */
object TestClocks {
    val ROME: ZoneId = ZoneId.of("Europe/Rome")
    val NEW_YORK: ZoneId = ZoneId.of("America/New_York")

    private val FROM: Instant = Instant.parse("2026-01-01T00:00:00Z")

    /** The first clock change from 2026 on where the clocks go forward (a gap) or back (an overlap). */
    fun transition(zone: ZoneId, gap: Boolean): ZoneOffsetTransition {
        var t = zone.rules.nextTransition(FROM)
        while (t.isGap != gap) t = zone.rules.nextTransition(t.instant)
        return t
    }

    fun at(zone: ZoneId, text: String): ZonedDateTime = LocalDateTime.parse(text).atZone(zone)
}
