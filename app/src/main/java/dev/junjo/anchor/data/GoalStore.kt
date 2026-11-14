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
 * All goals, kept in memory and saved to a small JSON file on every change.
 *
 * A list of a few dozen goals doesn't need a database; a JSON file is simple to read, back up
 * and later sync. Every change also refreshes the widget and the goals notification, so the goals
 * on screen never fall out of step with the app.
 */
object GoalStore {
    private const val TAG = "GoalStore"
    private const val FILE_NAME = "goals.json"

    /** Finished and deleted goals are kept this long, for the weekly review planned for v0.3. */
    private const val HISTORY_MS = 90L * 24 * 60 * 60 * 1000

    private lateinit var appContext: Context
    private lateinit var file: AtomicFile
    private val lock = Any()
    private val disk = Executors.newSingleThreadExecutor { Thread(it, "goal-store") }

    private val _goals = MutableStateFlow<List<Goal>>(emptyList())
    val goals: StateFlow<List<Goal>> = _goals.asStateFlow()

    fun init(context: Context) = synchronized(lock) {
        if (::file.isInitialized) return
        appContext = context.applicationContext
        file = AtomicFile(File(appContext.filesDir, FILE_NAME))
        _goals.value = prune(read())
    }

    /** Adds a goal for today. Returns null if the text was blank. */
    fun add(text: String): Goal? {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return null
        val now = System.currentTimeMillis()
        val goal = Goal(UUID.randomUUID().toString(), clean, today(), now, now)
        update { it + goal }
        return goal
    }

    fun find(id: String): Goal? = _goals.value.firstOrNull { it.id == id }

    fun setDone(id: String, done: Boolean) =
        edit(id) { it.copy(doneAt = if (done) it.doneAt ?: System.currentTimeMillis() else null) }

    fun toggle(id: String) =
        edit(id) { it.copy(doneAt = if (it.doneAt == null) System.currentTimeMillis() else null) }

    fun delete(id: String) = edit(id) { it.copy(deleted = true) }

    fun restore(id: String) = edit(id) { it.copy(deleted = false) }

    fun keepForToday(id: String) = edit(id) { it.copy(day = today()) }

    fun keepAllForToday() {
        val day = today()
        val now = System.currentTimeMillis()
        update { list -> list.map { if (it.isActive && it.day < day) it.copy(day = day, updatedAt = now) else it } }
    }

    private fun edit(id: String, change: (Goal) -> Goal) {
        val now = System.currentTimeMillis()
        update { list -> list.map { if (it.id == id) change(it).copy(updatedAt = now) else it } }
    }

    private fun update(change: (List<Goal>) -> List<Goal>) {
        synchronized(lock) {
            val next = change(_goals.value)
            _goals.value = next
            // The screen gets the new list at once; the disk write (which waits for the storage
            // to confirm) happens off the main thread so ticking a goal never drops a frame.
            // One thread, so saves reach the disk in the order they were made.
            disk.execute { write(next) }
        }
        GoalsNotification.update(appContext)
        Widgets.refresh(appContext)
    }

    private fun prune(goals: List<Goal>): List<Goal> {
        val cutoff = System.currentTimeMillis() - HISTORY_MS
        return goals.filter { it.isActive || it.updatedAt >= cutoff }
    }

    private fun read(): List<Goal> = try {
        val array = JSONArray(file.readFully().toString(Charsets.UTF_8))
        List(array.length()) { Goal.fromJson(array.getJSONObject(it)) }
    } catch (e: FileNotFoundException) {
        emptyList()
    } catch (e: Exception) {
        // Never silently lose goals: set the unreadable file aside before starting fresh.
        Log.e(TAG, "Could not read goals; keeping a copy and starting empty", e)
        runCatching {
            file.baseFile.copyTo(File(appContext.filesDir, "goals-unreadable-${System.currentTimeMillis()}.json"))
        }
        emptyList()
    }

    private fun write(goals: List<Goal>) {
        val array = JSONArray()
        goals.forEach { array.put(it.toJson()) }
        val out = try {
            file.startWrite()
        } catch (e: IOException) {
            Log.e(TAG, "Could not save goals", e)
            return
        }
        try {
            out.write(array.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            Log.e(TAG, "Could not save goals", e)
        }
    }
}
