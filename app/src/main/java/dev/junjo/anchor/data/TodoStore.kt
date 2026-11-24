package dev.junjo.anchor.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import dev.junjo.anchor.notify.GoalsNotification
import dev.junjo.anchor.widget.Widgets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors

/**
 * All to-dos, kept in memory and saved to a small JSON file on every change.
 *
 * A list of a few dozen to-dos doesn't need a database; a JSON file is simple to read, back up
 * and later sync. Every change also refreshes the widget and the lock-screen notification, so
 * what's on screen never falls out of step with the app.
 */
object TodoStore {
    private const val TAG = "TodoStore"

    /** Kept as "goals.json" so upgrades from v0.5.x keep their saved to-dos untouched. */
    private const val FILE_NAME = "goals.json"

    /** Finished and deleted to-dos are kept this long before pruning. */
    private const val HISTORY_MS = 90L * 24 * 60 * 60 * 1000

    private lateinit var appContext: Context
    private lateinit var file: AtomicFile
    private val lock = Any()
    private val disk = Executors.newSingleThreadExecutor { Thread(it, "todo-store") }

    private val _todos = MutableStateFlow<List<Todo>>(emptyList())
    val todos: StateFlow<List<Todo>> = _todos.asStateFlow()

    fun init(context: Context) = synchronized(lock) {
        if (::file.isInitialized) return
        appContext = context.applicationContext
        file = AtomicFile(File(appContext.filesDir, FILE_NAME))
        _todos.value = prune(read())
    }

    /** Adds a to-do. Returns null if the text was blank. With [putFirst], pins it to the top. */
    fun add(text: String, putFirst: Boolean = false): Todo? {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return null
        val now = System.currentTimeMillis()
        val todo = Todo(
            UUID.randomUUID().toString(),
            clean,
            today(),
            now,
            now,
            pinnedAt = if (putFirst) now else null,
        )
        update { it + todo }
        return todo
    }

    fun find(id: String): Todo? = _todos.value.firstOrNull { it.id == id }

    /** Changes a to-do's text. Blank text leaves it as it was. */
    fun rename(id: String, text: String) {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        if (clean.isNotEmpty()) edit(id) { it.copy(text = clean, updatedAt = System.currentTimeMillis()) }
    }

    fun setDone(id: String, done: Boolean) =
        edit(id) { it.copy(doneAt = if (done) it.doneAt ?: System.currentTimeMillis() else null) }

    fun toggle(id: String) =
        edit(id) { it.copy(doneAt = if (it.doneAt == null) System.currentTimeMillis() else null) }

    fun delete(id: String) = edit(id) { it.copy(deleted = true) }

    fun restore(id: String) = edit(id) { it.copy(deleted = false) }

    /** Adds a to-do created by a routine reminder, pinned to the top with its occurrence metadata. */
    fun addFromRoutine(text: String, reminderId: String, firedAt: Long): Todo? {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return null
        val now = System.currentTimeMillis()
        val todo = Todo(
            UUID.randomUUID().toString(),
            clean,
            today(),
            now,
            now,
            pinnedAt = now,
            reminderId = reminderId,
            firedAt = firedAt,
        )
        update { it + todo }
        return todo
    }

    /** Attaches routine metadata to a reused open to-do (keeping any older [firedAt]) and puts it first. */
    fun attachRoutine(id: String, reminderId: String, firedAt: Long) = edit(id) {
        it.copy(
            pinnedAt = System.currentTimeMillis(),
            reminderId = it.reminderId ?: reminderId,
            firedAt = it.firedAt ?: firedAt,
        )
    }

    /** Resets the 3-day staleness timer on a to-do. */
    fun keep(id: String) = edit(id) { it.copy(day = today()) }

    /** Resets the 3-day staleness timer on every currently stale to-do. */
    fun keepAll(now: Long = System.currentTimeMillis()) {
        val day = today()
        val staleIds = _todos.value.stale(now).map { it.id }.toSet()
        if (staleIds.isEmpty()) return
        update { list -> list.map { if (it.id in staleIds) it.copy(day = day, updatedAt = now) else it } }
    }

    /** Moves a to-do to the top of the list (and onto the floating pill and notification). */
    fun putFirst(id: String) = edit(id) { it.copy(pinnedAt = System.currentTimeMillis()) }

    fun merge(incoming: List<Todo>) {
        if (incoming.isEmpty()) return
        update { prune(Backup.mergeById(it, incoming, Todo::id, Todo::updatedAt)) }
    }

    private fun edit(id: String, change: (Todo) -> Todo) {
        val now = System.currentTimeMillis()
        update { list -> list.map { if (it.id == id) change(it).copy(updatedAt = now) else it } }
    }

    private fun update(change: (List<Todo>) -> List<Todo>) {
        synchronized(lock) {
            val next = change(_todos.value)
            _todos.value = next
            // The screen gets the new list at once; the disk write (which waits for the storage
            // to confirm) happens off the main thread so ticking a to-do never drops a frame.
            // One thread, so saves reach the disk in the order they were made.
            disk.execute { write(next) }
        }
        GoalsNotification.update(appContext)
        Widgets.refresh(appContext)
    }

    private fun prune(todos: List<Todo>): List<Todo> {
        val cutoff = System.currentTimeMillis() - HISTORY_MS
        return todos.filter { it.isActive || it.updatedAt >= cutoff }
    }

    private fun read(): List<Todo> = try {
        val array = JSONArray(file.readFully().toString(Charsets.UTF_8))
        List(array.length()) { Todo.fromJson(array.getJSONObject(it)) }
    } catch (e: FileNotFoundException) {
        emptyList()
    } catch (e: Exception) {
        // Never silently lose to-dos: set the unreadable file aside before starting fresh.
        Log.e(TAG, "Could not read to-dos; keeping a copy and starting empty", e)
        runCatching {
            file.baseFile.copyTo(File(appContext.filesDir, "goals-unreadable-${System.currentTimeMillis()}.json"))
        }
        emptyList()
    }

    private fun write(todos: List<Todo>) {
        val array = JSONArray()
        todos.forEach { array.put(it.toJson()) }
        val out = try {
            file.startWrite()
        } catch (e: IOException) {
            Log.e(TAG, "Could not save to-dos", e)
            return
        }
        try {
            out.write(array.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            Log.e(TAG, "Could not save to-dos", e)
        }
    }
}
