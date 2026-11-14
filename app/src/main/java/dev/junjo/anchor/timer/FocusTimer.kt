package dev.junjo.anchor.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.junjo.anchor.notify.Notifications
import dev.junjo.anchor.widget.Widgets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One countdown timer at a time.
 *
 * Nothing ticks in the background. The timer is just an end time saved to disk, plus system
 * alarms for the end and the heads-ups. That survives the app being closed, the screen being off
 * and battery saving, and the countdown on screen is worked out from the end time when drawn.
 */
object FocusTimer {
    enum class Status { IDLE, RUNNING, PAUSED, RINGING }

    data class State(
        val status: Status = Status.IDLE,
        val totalMs: Long = 0,
        /** Wall-clock end time while running. */
        val endAt: Long = 0,
        /** Time left while paused. */
        val remainingMs: Long = 0,
        val label: String? = null,
        val goalId: String? = null,
    ) {
        val isActive: Boolean get() = status != Status.IDLE

        fun remaining(now: Long = System.currentTimeMillis()): Long = when (status) {
            Status.RUNNING -> (endAt - now).coerceAtLeast(0)
            Status.PAUSED -> remainingMs
            else -> 0
        }
    }

    const val ACTION_END = "dev.junjo.anchor.timer.END"
    const val ACTION_HALF = "dev.junjo.anchor.timer.HALF"
    const val ACTION_FIVE = "dev.junjo.anchor.timer.FIVE"
    const val ACTION_SILENCE = "dev.junjo.anchor.timer.SILENCE"
    const val ACTION_PAUSE = "dev.junjo.anchor.timer.PAUSE"
    const val ACTION_RESUME = "dev.junjo.anchor.timer.RESUME"
    const val ACTION_ADD5 = "dev.junjo.anchor.timer.ADD5"
    const val ACTION_STOP = "dev.junjo.anchor.timer.STOP"

    const val MINUTE = 60_000L

    /** Heads-ups only where they help: halfway from 10 minutes, "5 minutes left" from 15. */
    private const val HALFWAY_FROM = 10 * MINUTE
    private const val FIVE_LEFT_FROM = 15 * MINUTE

    /** Like a clock app, stop ringing after a while if nobody is there to hear it. */
    private const val RING_FOR = 5 * MINUTE

    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("timer", Context.MODE_PRIVATE)
        _state.value = load()
    }

    fun start(minutes: Int, label: String? = null, goalId: String? = null) {
        val total = minutes * MINUTE
        TimerNotifications.clearAlerts(appContext)
        set(State(Status.RUNNING, total, now() + total, 0, label, goalId))
        scheduleAlarms()
        TimerNotifications.showRunning(appContext, _state.value)
    }

    fun pause() {
        val s = _state.value
        if (s.status != Status.RUNNING) return
        cancelAlarms()
        set(s.copy(status = Status.PAUSED, remainingMs = s.remaining()))
        TimerNotifications.showRunning(appContext, _state.value)
    }

    fun resume() {
        val s = _state.value
        if (s.status != Status.PAUSED) return
        set(s.copy(status = Status.RUNNING, endAt = now() + s.remainingMs, remainingMs = 0))
        scheduleAlarms()
        TimerNotifications.showRunning(appContext, _state.value)
    }

    /** Adds time; when the timer has finished, starts a new one with the same label (a snooze). */
    fun addMinutes(minutes: Int) {
        val s = _state.value
        val extra = minutes * MINUTE
        when (s.status) {
            Status.RUNNING -> {
                set(s.copy(totalMs = s.totalMs + extra, endAt = s.endAt + extra))
                scheduleAlarms()
                TimerNotifications.showRunning(appContext, _state.value)
            }
            Status.PAUSED -> {
                set(s.copy(totalMs = s.totalMs + extra, remainingMs = s.remainingMs + extra))
                TimerNotifications.showRunning(appContext, _state.value)
            }
            Status.RINGING, Status.IDLE -> start(minutes, s.label, s.goalId)
        }
    }

    fun stop() {
        cancelAlarms()
        set(State())
        TimerNotifications.clearAll(appContext)
    }

    /** Called by [TimerReceiver] when one of the scheduled alarms goes off. */
    fun onAlarm(action: String) {
        val s = _state.value
        when (action) {
            ACTION_END -> {
                if (isStaleEnd(s, now())) return
                set(s.copy(status = Status.RINGING))
                TimerNotifications.showDone(appContext, _state.value, loud = true)
                at(ACTION_SILENCE, now() + RING_FOR)
            }
            ACTION_HALF -> if (s.status == Status.RUNNING) {
                TimerNotifications.showCue(appContext, s, "Halfway · ${minutesLeft(s)} min left")
            }
            ACTION_FIVE -> if (s.status == Status.RUNNING) {
                TimerNotifications.showCue(appContext, s, "5 minutes left")
            }
            ACTION_SILENCE -> if (s.status == Status.RINGING) {
                TimerNotifications.showDone(appContext, s, loud = false)
            }
        }
    }

    /** After a reboot or app update the system has dropped the alarms; put them back. */
    fun restore() {
        val before = _state.value
        val s = afterRestart(before, now())
        if (s != before) set(s)
        when (s.status) {
            Status.RUNNING -> {
                scheduleAlarms()
                TimerNotifications.showRunning(appContext, s)
            }
            Status.PAUSED -> TimerNotifications.showRunning(appContext, s)
            Status.RINGING -> TimerNotifications.showDone(appContext, s, loud = false)
            Status.IDLE -> Unit
        }
    }

    // The timing decisions, kept free of Android so they can be unit tested.

    /** When each alarm should go off; null for a heads-up that isn't wanted or is too close to schedule. */
    data class AlarmPlan(val end: Long, val half: Long?, val five: Long?)

    internal fun alarmPlan(s: State, now: Long): AlarmPlan {
        fun ahead(time: Long) = time.takeIf { it > now + 1_000 }
        return AlarmPlan(
            end = s.endAt,
            half = if (s.totalMs >= HALFWAY_FROM) ahead(s.endAt - s.totalMs / 2) else null,
            five = if (s.totalMs >= FIVE_LEFT_FROM) ahead(s.endAt - 5 * MINUTE) else null,
        )
    }

    /** An end alarm to ignore, e.g. one that fired just as time was added. */
    internal fun isStaleEnd(s: State, now: Long): Boolean = s.status != Status.RUNNING || s.endAt - now > 2_000

    /** The state to carry on from after a reboot: a timer that ran out meanwhile is ringing. */
    internal fun afterRestart(s: State, now: Long): State =
        if (s.status == Status.RUNNING && s.endAt <= now) s.copy(status = Status.RINGING) else s

    /** A PendingIntent that sends [action] to [TimerReceiver]; used by alarms and notification buttons. */
    fun broadcast(ctx: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        ctx,
        action.hashCode(),
        Intent(ctx, TimerReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun scheduleAlarms() {
        cancelAlarms()
        val plan = alarmPlan(_state.value, now())
        val am = alarmManager()
        val end = broadcast(appContext, ACTION_END)
        if (am.canScheduleExactAlarms()) {
            // An "alarm clock" alarm is the one kind Android always delivers on time, even in
            // deep battery saving. It also shows the end time in the status bar.
            am.setAlarmClock(AlarmManager.AlarmClockInfo(plan.end, Notifications.openApp(appContext)), end)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.end, end)
        }
        plan.half?.let { at(ACTION_HALF, it) }
        plan.five?.let { at(ACTION_FIVE, it) }
    }

    private fun at(action: String, time: Long) {
        if (time <= now() + 1_000) return
        val am = alarmManager()
        val pi = broadcast(appContext, action)
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
    }

    private fun cancelAlarms() {
        val am = alarmManager()
        listOf(ACTION_END, ACTION_HALF, ACTION_FIVE, ACTION_SILENCE).forEach { am.cancel(broadcast(appContext, it)) }
    }

    private fun minutesLeft(s: State) = (s.remaining() + 30_000) / MINUTE

    private fun alarmManager() = appContext.getSystemService(AlarmManager::class.java)

    private fun now() = System.currentTimeMillis()

    private fun set(s: State) {
        _state.value = s
        prefs.edit {
            putString("status", s.status.name)
            putLong("total", s.totalMs)
            putLong("end", s.endAt)
            putLong("remaining", s.remainingMs)
            putString("label", s.label)
            putString("goal", s.goalId)
        }
        Widgets.refresh(appContext)
    }

    private fun load() = State(
        status = runCatching { Status.valueOf(prefs.getString("status", null) ?: "IDLE") }.getOrDefault(Status.IDLE),
        totalMs = prefs.getLong("total", 0),
        endAt = prefs.getLong("end", 0),
        remainingMs = prefs.getLong("remaining", 0),
        label = prefs.getString("label", null),
        goalId = prefs.getString("goal", null),
    )
}
