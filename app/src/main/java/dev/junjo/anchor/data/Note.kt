package dev.junjo.anchor.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Something written down that isn't a task: a thought, an idea, a thing to look up. Kept apart
 * from to-dos so the list stays short. Same sync-ready fields as [Todo].
 */
data class Note(
    val id: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("deleted", deleted)
    }

    companion object {
        fun fromJson(o: JSONObject) = Note(
            id = o.getString("id"),
            text = o.getString("text"),
            createdAt = o.getLong("createdAt"),
            updatedAt = o.getLong("updatedAt"),
            deleted = o.optBoolean("deleted", false),
        )
    }
}

/** Notes still there, newest first. */
fun List<Note>.visible(): List<Note> = filter { !it.deleted }.sortedByDescending { it.createdAt }

/** All notes, saved to a JSON file the same way as goals. */
object NoteStore {
    private const val TAG = "NoteStore"

    /** Deleted notes are kept this long before pruning, matching [TodoStore]. */
    internal const val HISTORY_MS = 90L * 24 * 60 * 60 * 1000

    private lateinit var appContext: Context
    private lateinit var file: AtomicFile
    private val lock = Any()
    private val disk = Executors.newSingleThreadExecutor { Thread(it, "note-store") }

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    fun init(context: Context) = synchronized(lock) {
        if (::file.isInitialized) return
        appContext = context.applicationContext
        file = AtomicFile(File(appContext.filesDir, "notes.json"))
        _notes.value = prune(read())
    }

    internal fun prune(notes: List<Note>, now: Long = System.currentTimeMillis()): List<Note> {
        val cutoff = now - HISTORY_MS
        return notes.filter { !it.deleted || it.updatedAt >= cutoff }
    }

    /** Returns null if the text was blank. Line breaks are kept: a note can be a list. */
    fun add(text: String): Note? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        val now = System.currentTimeMillis()
        val note = Note(UUID.randomUUID().toString(), clean, now, now)
        update { it + note }
        return note
    }

    fun find(id: String): Note? = _notes.value.firstOrNull { it.id == id }

    /** A blank edit deletes the note. */
    fun edit(id: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return delete(id)
        change(id) { it.copy(text = clean) }
    }

    fun delete(id: String) = change(id) { it.copy(deleted = true) }

    fun restore(id: String) = change(id) { it.copy(deleted = false) }

    fun merge(incoming: List<Note>) {
        if (incoming.isEmpty()) return
        update { prune(Backup.mergeById(it, incoming, Note::id, Note::updatedAt)) }
    }

    private fun change(id: String, how: (Note) -> Note) {
        val now = System.currentTimeMillis()
        update { list -> list.map { if (it.id == id) how(it).copy(updatedAt = now) else it } }
    }

    private fun update(change: (List<Note>) -> List<Note>) = synchronized(lock) {
        val next = change(_notes.value)
        _notes.value = next
        disk.execute { write(next) } // Off the main thread, in order; see TodoStore.
    }

    private fun read(): List<Note> = try {
        val array = JSONArray(file.readFully().toString(Charsets.UTF_8))
        List(array.length()) { Note.fromJson(array.getJSONObject(it)) }
    } catch (e: FileNotFoundException) {
        emptyList()
    } catch (e: Exception) {
        Log.e(TAG, "Could not read notes; keeping a copy and starting empty", e)
        runCatching { file.baseFile.copyTo(File(appContext.filesDir, "notes-unreadable-${System.currentTimeMillis()}.json")) }
        emptyList()
    }

    private fun write(list: List<Note>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        val out = try {
            file.startWrite()
        } catch (e: IOException) {
            Log.e(TAG, "Could not save notes", e)
            return
        }
        try {
            out.write(array.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            Log.e(TAG, "Could not save notes", e)
        }
    }
}
