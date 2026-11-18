package dev.junjo.anchor.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import dev.junjo.anchor.reminders.Reminders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
import java.util.concurrent.Executors

/**
 * A reminder that repeats at the same time on chosen days. Same sync-ready fields as [Goal]:
 * random id, updatedAt and soft delete.
 */
data class Reminder(
    val id: String,
    val text: String,
    val hour: Int,
    val minute: Int,
    /** Bit 0 = Monday … bit 6 = Sunday. */
    val days: Int = ALL_DAYS,
    val enabled: Boolean = true,
    /** When it goes off, also put it on today's goal list so it stays in sight until ticked off. */
    val addAsGoal: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    /** Rings and fills the screen like an alarm clock, instead of a normal notification. */
    val alarm: Boolean = false,
    /**
     * 0: repeats at [hour]:[minute] on the picked [days]. Otherwise repeats every this many
     * minutes (a whole number of days and/or hours), counted from [startAt]; [days] is unused.
     */
    val everyMinutes: Int = 0,
    /** For a repeat every few days or hours: the first time it goes off. For a one-off: when. */
    val startAt: Long = 0,
    /** Goes off once, at [startAt] ("remind me in 40 minutes"), instead of repeating. */
    val once: Boolean = false,
) {
    val repeatsByInterval: Boolean get() = !once && everyMinutes > 0

    /** A one-off that has already gone off: no longer shown in the list. */
    fun isPast(now: Long = System.currentTimeMillis()) = once && startAt <= now

    fun isOn(day: DayOfWeek) = days and (1 shl (day.value - 1)) != 0

    val timeText: String
        get() = LocalTime.of(hour, minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    val daysText: String
        get() = if (once) "Once" else if (repeatsByInterval) "${intervalText(everyMinutes)}, from $timeText" else when (days) {
            ALL_DAYS -> "Every day"
            WEEKDAYS -> "Weekdays"
            WEEKEND -> "Weekends"
            0 -> "No days picked"
            else -> DayOfWeek.entries.filter { isOn(it) }.joinToString(", ") { SHORT_NAMES[it.value - 1] }
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("hour", hour)
        put("minute", minute)
        put("days", days)
        put("enabled", enabled)
        put("addAsGoal", addAsGoal)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("deleted", deleted)
        put("alarm", alarm)
        put("everyMinutes", everyMinutes)
        put("startAt", startAt)
        put("once", once)
    }

    companion object {
        const val ALL_DAYS = 0b1111111
        const val WEEKDAYS = 0b0011111
        const val WEEKEND = 0b1100000
        val SHORT_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        fun new(text: String, hour: Int, minute: Int, days: Int, addAsGoal: Boolean): Reminder {
            val now = System.currentTimeMillis()
            return Reminder(UUID.randomUUID().toString(), text, hour, minute, days, true, addAsGoal, now, now)
        }

        /** A one-off reminder at [at]. It still lands on the goal list when it goes off. */
        fun once(text: String, at: Long, alarm: Boolean): Reminder {
            val now = System.currentTimeMillis()
            val time = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault())
            return Reminder(
                UUID.randomUUID().toString(), text.trim(), time.hour, time.minute, days = 0,
                createdAt = now, updatedAt = now, alarm = alarm, startAt = at, once = true,
            )
        }

        /**
         * Where a repeat every few days or hours starts, from the time of day picked for it.
         * Whole days: the next time that clock time comes round, so "every 2 days at 9:00" set at
         * 10:00 starts tomorrow, not in two days. With hours: today at that time, even if it has
         * passed, so "every 3 hours from 8:00" set at 10:00 next goes off at 11:00.
         */
        fun intervalStart(hour: Int, minute: Int, everyMinutes: Int, now: ZonedDateTime = ZonedDateTime.now()): Long {
            val today = now.toLocalDate().atTime(hour, minute).atZone(now.zone)
            val start = if (everyMinutes % DAY_MINUTES == 0 && !today.isAfter(now)) {
                now.toLocalDate().plusDays(1).atTime(hour, minute).atZone(now.zone)
            } else today
            return start.toInstant().toEpochMilli()
        }

        fun fromJson(o: JSONObject) = Reminder(
            id = o.getString("id"),
            text = o.getString("text"),
            hour = o.getInt("hour"),
            minute = o.getInt("minute"),
            days = o.optInt("days", ALL_DAYS),
            enabled = o.optBoolean("enabled", true),
            addAsGoal = o.optBoolean("addAsGoal", true),
            createdAt = o.getLong("createdAt"),
            updatedAt = o.getLong("updatedAt"),
            deleted = o.optBoolean("deleted", false),
            alarm = o.optBoolean("alarm", false),
            everyMinutes = o.optInt("everyMinutes", 0),
            startAt = o.optLong("startAt", 0),
            once = o.optBoolean("once", false),
        )

        const val DAY_MINUTES = 24 * 60

        /** "Every 2 days", "Every 3 hours", "Every day and 6 hours". */
        fun intervalText(minutes: Int): String {
            val d = minutes / DAY_MINUTES
            val h = (minutes % DAY_MINUTES) / 60
            val days = when (d) { 0 -> null; 1 -> "day"; else -> "$d days" }
            val hours = when (h) { 0 -> null; 1 -> "hour"; else -> "$h hours" }
            return "Every " + listOfNotNull(days, hours).joinToString(" and ")
        }
    }
}

/** All reminders, saved to a JSON file. Every change reschedules the alarms. */
object ReminderStore {
    private const val TAG = "ReminderStore"

    private lateinit var appContext: Context
    private lateinit var file: AtomicFile
    private val lock = Any()
    private val disk = Executors.newSingleThreadExecutor { Thread(it, "reminder-store") }

    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders.asStateFlow()

    fun init(context: Context) = synchronized(lock) {
        if (::file.isInitialized) return
        appContext = context.applicationContext
        file = AtomicFile(File(appContext.filesDir, "reminders.json"))
        // One-offs that went off over a day ago are finished with (a snooze is long over).
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        _reminders.value = read().filter { !(it.once && it.startAt < cutoff) }
    }

    fun find(id: String): Reminder? = _reminders.value.firstOrNull { it.id == id }

    /** Visible reminders: one-offs still to come, soonest first, then the repeating ones by time of day. */
    fun visible(list: List<Reminder> = _reminders.value, now: Long = System.currentTimeMillis()): List<Reminder> =
        list.filter { !it.deleted && !it.isPast(now) }
            .sortedWith(compareBy({ !it.once }, { if (it.once) it.startAt else 0L }, { it.hour }, { it.minute }))

    /** Adds the reminder, or replaces the one with the same id. */
    fun save(reminder: Reminder) {
        val changed = reminder.copy(text = reminder.text.trim(), updatedAt = System.currentTimeMillis())
        synchronized(lock) {
            val list = _reminders.value
            val next = if (list.any { it.id == changed.id }) list.map { if (it.id == changed.id) changed else it } else list + changed
            _reminders.value = next
            disk.execute { write(next) } // Off the main thread, in order; see GoalStore.
        }
        Reminders.schedule(appContext, changed)
    }

    fun delete(id: String) {
        find(id)?.let { save(it.copy(deleted = true)) }
    }

    private fun read(): List<Reminder> = try {
        val array = JSONArray(file.readFully().toString(Charsets.UTF_8))
        List(array.length()) { Reminder.fromJson(array.getJSONObject(it)) }
    } catch (e: FileNotFoundException) {
        emptyList()
    } catch (e: Exception) {
        Log.e(TAG, "Could not read reminders; keeping a copy and starting empty", e)
        runCatching {
            file.baseFile.copyTo(File(appContext.filesDir, "reminders-unreadable-${System.currentTimeMillis()}.json"))
        }
        emptyList()
    }

    private fun write(list: List<Reminder>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        val out = try {
            file.startWrite()
        } catch (e: IOException) {
            Log.e(TAG, "Could not save reminders", e)
            return
        }
        try {
            out.write(array.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            Log.e(TAG, "Could not save reminders", e)
        }
    }
}
