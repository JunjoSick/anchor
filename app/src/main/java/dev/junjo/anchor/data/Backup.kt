package dev.junjo.anchor.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Plain JSON backup of all to-dos, notes and reminders, for switching phones or reinstalling.
 *
 * Importing merges by id and keeps the newest [updatedAt], so importing onto a fresh install
 * restores everything and importing onto an existing one never wipes newer changes or duplicates
 * items. The JSON top-level key stays "goals" (version 1) for compatibility with v0.5 backups.
 */
object Backup {
    private const val VERSION = 1

    data class Snapshot(
        val todos: List<Todo>,
        val notes: List<Note>,
        val reminders: List<Reminder>,
    )

    data class Summary(val todos: Int, val notes: Int, val reminders: Int) {
        fun message(): String {
            val parts = buildList {
                if (todos > 0) add(if (todos == 1) "1 to-do" else "$todos to-dos")
                if (notes > 0) add(if (notes == 1) "1 note" else "$notes notes")
                if (reminders > 0) add(if (reminders == 1) "1 reminder" else "$reminders reminders")
            }
            return if (parts.isEmpty()) "Backup imported (nothing active to restore)"
            else "Restored ${parts.joinToString(", ")}"
        }
    }

    fun toJson(snapshot: Snapshot, exportedAt: Long = System.currentTimeMillis()): String = JSONObject().apply {
        put("version", VERSION)
        put("exportedAt", exportedAt)
        put("goals", JSONArray().apply { snapshot.todos.forEach { put(it.toJson()) } })
        put("notes", JSONArray().apply { snapshot.notes.forEach { put(it.toJson()) } })
        put("reminders", JSONArray().apply { snapshot.reminders.forEach { put(it.toJson()) } })
    }.toString(2)

    fun fromJson(raw: String): Snapshot {
        val root = JSONObject(raw)
        require(root.has("goals") || root.has("notes") || root.has("reminders")) {
            "Not an Anchor backup file"
        }
        val todos = root.optJSONArray("goals")?.let { a ->
            List(a.length()) { Todo.fromJson(a.getJSONObject(it)) }
        } ?: emptyList()
        val notes = root.optJSONArray("notes")?.let { a ->
            List(a.length()) { Note.fromJson(a.getJSONObject(it)) }
        } ?: emptyList()
        val reminders = root.optJSONArray("reminders")?.let { a ->
            List(a.length()) { Reminder.fromJson(a.getJSONObject(it)) }
        } ?: emptyList()
        return Snapshot(todos, notes, reminders)
    }

    /** Merges two sync-ready lists by id, keeping whichever copy has the newer [updatedAt]. */
    fun <T> mergeById(
        existing: List<T>,
        incoming: List<T>,
        id: (T) -> String,
        updatedAt: (T) -> Long,
    ): List<T> {
        val merged = LinkedHashMap<String, T>(existing.size + incoming.size)
        existing.forEach { merged[id(it)] = it }
        incoming.forEach { item ->
            val key = id(item)
            val current = merged[key]
            if (current == null || updatedAt(item) > updatedAt(current)) {
                merged[key] = item
            }
        }
        return merged.values.toList()
    }

    fun exportJson(): String = toJson(
        Snapshot(
            todos = TodoStore.todos.value,
            notes = NoteStore.notes.value,
            reminders = ReminderStore.reminders.value,
        ),
    )

    fun importJson(raw: String, now: Long = System.currentTimeMillis()): Summary {
        val snapshot = fromJson(raw)
        TodoStore.merge(snapshot.todos)
        NoteStore.merge(snapshot.notes)
        ReminderStore.merge(snapshot.reminders)
        return Summary(
            todos = snapshot.todos.active().size,
            notes = snapshot.notes.visible().size,
            reminders = ReminderStore.visible(snapshot.reminders, now).size,
        )
    }
}
