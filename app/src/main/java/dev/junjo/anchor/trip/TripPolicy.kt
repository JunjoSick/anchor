package dev.junjo.anchor.trip

import org.json.JSONArray
import org.json.JSONObject

/** Lifecycle of one In&Out trip. */
enum class TripStatus { IDLE, RUNNING, STOP }

/** What the overlay service should do on the current 1-second tick. */
enum class TripAction { NONE, HALFWAY, LAST_15, STOP }

/**
 * One quick errand in another app ("check Marco's stories").
 * Kept free of Android so all timing and progression rules are plain JVM unit tested.
 */
data class TripState(
    val name: String = "",
    val minutes: Int = 2,
    val startedAt: Long = 0,
    val endAt: Long = 0,
    /** 1 = first go; each "another go" adds one. */
    val gos: Int = 1,
    val halfwayDone: Boolean = false,
    val status: TripStatus = TripStatus.IDLE,
) {
    val isActive: Boolean get() = status != TripStatus.IDLE
    val totalMs: Long get() = (endAt - startedAt).coerceAtLeast(0)

    fun remaining(now: Long = System.currentTimeMillis()): Long =
        if (status == TripStatus.RUNNING) (endAt - now).coerceAtLeast(0) else 0L
}

/** A recently used trip name and its last duration, for one-tap chips. */
data class RecentTrip(val name: String, val minutes: Int)

/** Pure timing and escalation rules for In&Out trips. */
object TripPolicy {
    const val MINUTE = 60_000L
    const val LAST_15_MS = 15_000L
    const val MAX_RECENT = 5
    const val DEFAULT_MINUTES = 2
    val PRESET_MINUTES = listOf(1, 2, 5)

    /** What the service should do on this tick. */
    fun tick(s: TripState, now: Long): TripAction {
        if (s.status != TripStatus.RUNNING) return TripAction.NONE
        if (now >= s.endAt) return TripAction.STOP
        if (s.endAt - now <= LAST_15_MS) return TripAction.LAST_15
        // A 1-minute trip skips halfway: 30 s ticks only add noise.
        if (s.minutes >= 2 && !s.halfwayDone) {
            val halfwayAt = s.startedAt + (s.endAt - s.startedAt) / 2
            if (now >= halfwayAt) return TripAction.HALFWAY
        }
        return TripAction.NONE
    }

    /** Starts the same trip over with the same length and one more go on the counter. */
    fun again(s: TripState, now: Long): TripState = s.copy(
        startedAt = now,
        endAt = now + s.minutes * MINUTE,
        gos = s.gos + 1,
        halfwayDone = false,
        status = TripStatus.RUNNING,
    )

    /**
     * Wait before "another go" can be tapped. The first retry is free; a small pause goes on
     * staying from the second retry onward, never on leaving.
     */
    fun againDelayMs(gos: Int): Long = when {
        gos <= 1 -> 0L
        gos == 2 -> 3_000L
        gos == 3 -> 6_000L
        else -> 10_000L
    }

    /** From the third go, surface "Make it a Focus" next to Done. */
    fun suggestFocus(gos: Int): Boolean = gos >= 3

    /** A running trip whose end passed while the screen was off or the service restarted → STOP. */
    fun afterRestart(s: TripState, now: Long): TripState =
        if (s.status == TripStatus.RUNNING && now >= s.endAt) s.copy(status = TripStatus.STOP) else s

    /** Adds [name] (with [minutes]) to the front of the recent list, keeping at most [MAX_RECENT]. */
    fun recordRecent(existing: List<RecentTrip>, name: String, minutes: Int): List<RecentTrip> {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return existing
        val without = existing.filterNot { it.name.equals(clean, ignoreCase = true) }
        return (listOf(RecentTrip(clean, minutes)) + without).take(MAX_RECENT)
    }

    fun recentToJson(list: List<RecentTrip>): String {
        val array = JSONArray()
        list.forEach { r ->
            array.put(JSONObject().apply {
                put("name", r.name)
                put("minutes", r.minutes)
            })
        }
        return array.toString()
    }

    fun recentFromJson(raw: String?): List<RecentTrip> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val name = o.optString("name", "").trim()
                    if (name.isEmpty()) continue
                    val minutes = o.optInt("minutes", DEFAULT_MINUTES).coerceAtLeast(1)
                    add(RecentTrip(name, minutes))
                }
            }.take(MAX_RECENT)
        }.getOrDefault(emptyList())
    }
}
