package dev.junjo.anchor.data

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * One to-do item.
 *
 * The random id, updatedAt and soft delete are there so a later version can sync between phones
 * by merging on id and keeping the newest change, without changing how items are stored.
 */
data class Todo(
    val id: String,
    val text: String,
    /** Kept for compatibility with older versions; staleness now uses [updatedAt]. */
    val day: String,
    val createdAt: Long,
    val updatedAt: Long,
    val doneAt: Long? = null,
    val deleted: Boolean = false,
    /** When the user pinned this to-do to the top; most recently pinned comes first. */
    val pinnedAt: Long? = null,
    /** The routine reminder that created (or matched) this to-do, if any. */
    val reminderId: String? = null,
    /** When the oldest still-open occurrence of [reminderId] went off. */
    val firedAt: Long? = null,
) {
    val isActive: Boolean get() = !deleted && doneAt == null

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("day", day)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        if (doneAt != null) put("doneAt", doneAt)
        put("deleted", deleted)
        if (pinnedAt != null) put("pinnedAt", pinnedAt)
        if (reminderId != null) put("reminderId", reminderId)
        if (firedAt != null) put("firedAt", firedAt)
    }

    companion object {
        fun fromJson(o: JSONObject) = Todo(
            id = o.getString("id"),
            text = o.getString("text"),
            day = o.getString("day"),
            createdAt = o.getLong("createdAt"),
            updatedAt = o.getLong("updatedAt"),
            doneAt = if (o.has("doneAt")) o.getLong("doneAt") else null,
            deleted = o.optBoolean("deleted", false),
            pinnedAt = if (o.has("pinnedAt")) o.getLong("pinnedAt") else null,
            reminderId = if (o.has("reminderId")) o.getString("reminderId") else null,
            firedAt = if (o.has("firedAt")) o.getLong("firedAt") else null,
        )
    }
}

/** Untouched ordinary to-dos ask "Still on?" after three days. */
const val STALE_MS = 3L * 24 * 60 * 60 * 1000

fun today(): String = LocalDate.now().toString()

fun startOfToday(): Long =
    LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

// The views of the list that the screen, widget and notification all share.

/** To-dos still to do: any put first (most recent on top), then the rest in the order added. */
fun List<Todo>.active(): List<Todo> =
    filter { it.isActive }.sortedWith(compareByDescending<Todo> { it.pinnedAt ?: Long.MIN_VALUE }.thenBy { it.createdAt })

/** To-dos untouched for at least 3 days. Never pinned to-dos and never routine to-dos. */
fun List<Todo>.stale(now: Long = System.currentTimeMillis()): List<Todo> =
    active().filter { it.pinnedAt == null && it.reminderId == null && now - it.updatedAt >= STALE_MS }

/** Older day-based helper retained for unit tests and compatibility. */
fun List<Todo>.carriedOver(today: String = today()): List<Todo> = active().filter { it.day < today }

/** To-dos ticked off since midnight, newest first. */
fun List<Todo>.doneToday(startOfDay: Long = startOfToday()): List<Todo> =
    filter { !it.deleted && (it.doneAt ?: 0L) >= startOfDay }.sortedByDescending { it.doneAt }

/**
 * Label for a routine to-do's oldest still-open occurrence:
 * today → "8:00", yesterday → "since yesterday 8:00", 2–6 days ago → "since Mon 8:00",
 * older → "since 17 Nov".
 */
fun sinceLabel(
    firedAt: Long,
    now: ZonedDateTime = ZonedDateTime.now(),
    timeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT),
): String {
    val fired = Instant.ofEpochMilli(firedAt).atZone(now.zone)
    val days = ChronoUnit.DAYS.between(fired.toLocalDate(), now.toLocalDate())
    val time = fired.toLocalTime().format(timeFormat)
    return when {
        days <= 0L -> time
        days == 1L -> "since yesterday $time"
        days in 2L..6L -> "since ${fired.toLocalDate().format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))} $time"
        else -> "since ${fired.toLocalDate().format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}"
    }
}
