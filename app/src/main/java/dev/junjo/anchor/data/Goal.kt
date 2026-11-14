package dev.junjo.anchor.data

import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/**
 * One short-term goal.
 *
 * The random id, updatedAt and soft delete are there so a later version can sync goals between
 * phones by merging on id and keeping the newest change, without changing how goals are stored.
 */
data class Goal(
    val id: String,
    val text: String,
    /** The day this goal is planned for, as an ISO date (2026-11-13). */
    val day: String,
    val createdAt: Long,
    val updatedAt: Long,
    val doneAt: Long? = null,
    val deleted: Boolean = false,
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
    }

    companion object {
        fun fromJson(o: JSONObject) = Goal(
            id = o.getString("id"),
            text = o.getString("text"),
            day = o.getString("day"),
            createdAt = o.getLong("createdAt"),
            updatedAt = o.getLong("updatedAt"),
            doneAt = if (o.has("doneAt")) o.getLong("doneAt") else null,
            deleted = o.optBoolean("deleted", false),
        )
    }
}

fun today(): String = LocalDate.now().toString()

fun startOfToday(): Long =
    LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

// The views of the list that the screen, widget and notification all share.

/** Goals still to do, in the order they were added. */
fun List<Goal>.active(): List<Goal> = filter { it.isActive }.sortedBy { it.createdAt }

/** Goals still to do that were planned for an earlier day and need a keep-or-drop decision. */
fun List<Goal>.carriedOver(today: String = today()): List<Goal> = active().filter { it.day < today }

/** Goals ticked off since midnight, newest first. */
fun List<Goal>.doneToday(startOfDay: Long = startOfToday()): List<Goal> =
    filter { !it.deleted && (it.doneAt ?: 0L) >= startOfDay }.sortedByDescending { it.doneAt }
