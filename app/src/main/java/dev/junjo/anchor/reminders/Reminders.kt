package dev.junjo.anchor.reminders

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import dev.junjo.anchor.R
import dev.junjo.anchor.data.GoalStore
import dev.junjo.anchor.data.Reminder
import dev.junjo.anchor.data.ReminderStore
import dev.junjo.anchor.data.active
import dev.junjo.anchor.notify.Notifications
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Schedules reminders and shows them.
 *
 * Each reminder has one system alarm for its next time. When it goes off, the next one is
 * scheduled straight away, so a missed tap never stops the next one. By default it also puts the
 * task on today's goal list: a notification is easy to swipe away and forget, a goal stays in
 * the bubble and widget until it's actually done.
 *
 * A reminder is either a normal notification, or an alarm that rings and fills the screen
 * until it's answered, for the things that can't be missed.
 */
object Reminders {
    const val ACTION_FIRE = "dev.junjo.anchor.reminder.FIRE"
    const val ACTION_SNOOZED = "dev.junjo.anchor.reminder.SNOOZED"
    const val ACTION_SNOOZE = "dev.junjo.anchor.reminder.SNOOZE"
    const val ACTION_DONE = "dev.junjo.anchor.reminder.DONE"
    const val ACTION_SILENCE = "dev.junjo.anchor.reminder.SILENCE"
    const val EXTRA_ID = "reminder_id"
    const val EXTRA_GOAL = "goal_id"
    const val SNOOZE_MINUTES = 10

    /** Like a clock app, an alarm stops ringing after a while if nobody is there to hear it. */
    private const val RING_FOR_MS = 5 * 60_000L

    /** Alarm reminders ringing right now, so their full-screen page can close when answered elsewhere. */
    private val _ringing = MutableStateFlow<Set<String>>(emptySet())
    val ringing: StateFlow<Set<String>> = _ringing.asStateFlow()

    fun scheduleAll(ctx: Context) {
        ReminderStore.reminders.value.forEach { schedule(ctx, it) }
        restoreSnoozes(ctx)
    }

    fun schedule(ctx: Context, r: Reminder) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = broadcast(ctx, ACTION_FIRE, r.id, null)
        am.cancel(pi)
        if (r.deleted || !r.enabled) {
            answered(ctx, r.id)
            return
        }
        val at = next(r) ?: return
        scheduleAlarm(ctx, am, r.alarm, at, pi)
    }

    /** The next time this reminder is due, or null if it never is (no days picked). */
    fun next(r: Reminder, now: ZonedDateTime = ZonedDateTime.now()): Long? = when {
        r.once -> r.startAt.takeIf { it > now.toInstant().toEpochMilli() }
        r.repeatsByInterval -> nextByInterval(r, now)
        else -> nextByDays(r, now)
    }

    private fun nextByDays(r: Reminder, now: ZonedDateTime): Long? {
        for (i in 0..7L) {
            val date = now.toLocalDate().plusDays(i)
            if (!r.isOn(date.dayOfWeek)) continue
            val time = date.atTime(r.hour, r.minute).atZone(now.zone)
            if (time.isAfter(now)) return time.toInstant().toEpochMilli()
        }
        return null
    }

    /**
     * Every few whole days: counted in calendar days, so it stays at the same clock time across
     * clock changes. Anything with hours in it: counted in real time, like a medicine every 8
     * hours, which must not move when the clocks do.
     */
    private fun nextByInterval(r: Reminder, now: ZonedDateTime): Long {
        val nowMs = now.toInstant().toEpochMilli()
        if (r.everyMinutes % Reminder.DAY_MINUTES == 0) {
            val days = (r.everyMinutes / Reminder.DAY_MINUTES).toLong()
            val start = Instant.ofEpochMilli(r.startAt).atZone(now.zone).toLocalDate()
            var k = Math.floorDiv(ChronoUnit.DAYS.between(start, now.toLocalDate()), days).coerceAtLeast(0)
            while (true) {
                val time = start.plusDays(k * days).atTime(r.hour, r.minute).atZone(now.zone)
                if (time.isAfter(now)) return time.toInstant().toEpochMilli()
                k++
            }
        }
        val step = r.everyMinutes * 60_000L
        if (r.startAt > nowMs) return r.startAt
        return r.startAt + ((nowMs - r.startAt) / step + 1) * step
    }

    fun handle(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val goalId = intent.getStringExtra(EXTRA_GOAL)
        when (intent.action) {
            ACTION_FIRE -> fire(ctx, id, snoozed = false, goalId = null)
            ACTION_SNOOZED -> fire(ctx, id, snoozed = true, goalId = goalId)
            ACTION_SNOOZE -> {
                answered(ctx, id)
                val r = ReminderStore.find(id)
                if (r == null || r.deleted || !r.enabled) return
                val at = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
                saveSnooze(ctx, id, at, goalId)
                scheduleAlarm(ctx, ctx.getSystemService(AlarmManager::class.java), r.alarm, at, broadcast(ctx, ACTION_SNOOZED, id, goalId))
            }
            ACTION_DONE -> {
                answered(ctx, id)
                goalId?.let { GoalStore.setDone(it, true) }
            }
            ACTION_SILENCE -> {
                val r = ReminderStore.find(id) ?: return
                if (isShowing(ctx, id)) post(ctx, r, goalId, loud = false)
            }
        }
    }

    /** Whether this reminder's notification is still up, i.e. it hasn't been answered yet. */
    fun isShowing(ctx: Context, id: String): Boolean =
        ctx.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == notificationId(id) }

    /** For the full-screen page: it was opened for a reminder that is still waiting for an answer. */
    fun markRinging(id: String) = _ringing.update { it + id }

    private fun answered(ctx: Context, id: String) {
        Notifications.cancel(ctx, notificationId(id))
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(broadcast(ctx, ACTION_SILENCE, id, null))
        am.cancel(broadcast(ctx, ACTION_SNOOZED, id, null))
        clearSnooze(ctx, id)
        _ringing.update { it - id }
    }

    private fun fire(ctx: Context, id: String, snoozed: Boolean, goalId: String?) {
        if (snoozed) clearSnooze(ctx, id)
        val r = ReminderStore.find(id) ?: return
        if (r.deleted || !r.enabled) return
        var goal = goalId
        if (!snoozed) {
            schedule(ctx, r) // The next one.
            if (r.addAsGoal) {
                val existing = GoalStore.goals.value.active().firstOrNull { it.text.equals(r.text, ignoreCase = true) }
                // Push reminder goals to the top so they surface on the bubble pill and notification immediately.
                goal = if (existing != null) {
                    GoalStore.putFirst(existing.id)
                    existing.id
                } else {
                    GoalStore.add(r.text, putFirst = true)?.id
                }
            }
        }
        // The goal may have been ticked off during a snooze; then there is nothing left to remind.
        if (snoozed && goal != null && GoalStore.find(goal)?.isActive == false) return

        post(ctx, r, goal, loud = r.alarm)
        if (r.alarm) {
            _ringing.update { it + id }
            exact(
                ctx.getSystemService(AlarmManager::class.java),
                System.currentTimeMillis() + RING_FOR_MS,
                broadcast(ctx, ACTION_SILENCE, id, goal),
            )
        }
    }

    /** [loud]: rings on repeat and fills the screen. Otherwise a normal (or, once silenced, quiet) notification. */
    private fun post(ctx: Context, r: Reminder, goal: String?, loud: Boolean) {
        val builder = NotificationCompat.Builder(ctx, if (r.alarm) Notifications.CH_REMINDER_ALARM else Notifications.CH_REMINDER)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(r.text)
            .setContentText(if (goal != null) "On your goal list until you tick it off" else r.daysText)
            .setCategory(if (r.alarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (r.alarm) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_check, if (goal != null) "Done" else "OK", broadcast(ctx, ACTION_DONE, r.id, goal))
            .addAction(R.drawable.ic_timer, "Snooze $SNOOZE_MINUTES min", broadcast(ctx, ACTION_SNOOZE, r.id, goal))

        if (r.alarm) {
            val page = PendingIntent.getActivity(
                ctx,
                0,
                Intent(ctx, ReminderAlarmActivity::class.java)
                    .setData(Uri.parse("anchor://reminder/${r.id}/ring"))
                    .putExtra(EXTRA_ID, r.id)
                    .apply { if (goal != null) putExtra(EXTRA_GOAL, goal) }
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            // An alarm stays until it's answered with Done or Snooze.
            builder.setOngoing(true).setContentIntent(page)
            if (loud) builder.setFullScreenIntent(page, true) else builder.setSilent(true).setOnlyAlertOnce(true)
        } else {
            builder.setAutoCancel(true).setContentIntent(Notifications.openApp(ctx))
        }

        val n = builder.build()
        if (loud) n.flags = n.flags or Notification.FLAG_INSISTENT
        Notifications.post(ctx, notificationId(r.id), n)
    }

    private fun scheduleAlarm(ctx: Context, am: AlarmManager, alarm: Boolean, at: Long, pi: PendingIntent) {
        if (alarm && am.canScheduleExactAlarms()) {
            // The kind of alarm a clock app uses: always on time, and shown as the next alarm.
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, Notifications.openApp(ctx)), pi)
        } else {
            exact(am, at, pi)
        }
    }

    private fun snoozePrefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("reminder_snoozes", Context.MODE_PRIVATE)

    private fun saveSnooze(ctx: Context, id: String, at: Long, goalId: String?) {
        snoozePrefs(ctx).edit { putString(id, "$at|${goalId.orEmpty()}") }
    }

    private fun clearSnooze(ctx: Context, id: String) {
        snoozePrefs(ctx).edit { remove(id) }
    }

    /** Re-schedules any pending snoozes after a reboot or app update, dropping expired ones. */
    private fun restoreSnoozes(ctx: Context) {
        val prefs = snoozePrefs(ctx)
        val entries = prefs.all
        if (entries.isEmpty()) return
        val now = System.currentTimeMillis()
        val am = ctx.getSystemService(AlarmManager::class.java)
        val drop = mutableListOf<String>()
        entries.forEach { (id, raw) ->
            val parts = (raw as? String)?.split("|", limit = 2)
            val at = parts?.getOrNull(0)?.toLongOrNull()
            val goalId = parts?.getOrNull(1)?.takeIf { it.isNotEmpty() }
            val r = ReminderStore.find(id)
            if (at == null || at <= now || r == null || r.deleted || !r.enabled) {
                drop += id
            } else {
                scheduleAlarm(ctx, am, r.alarm, at, broadcast(ctx, ACTION_SNOOZED, id, goalId))
            }
        }
        if (drop.isNotEmpty()) prefs.edit { drop.forEach { remove(it) } }
    }

    private fun exact(am: AlarmManager, at: Long, pi: PendingIntent) {
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    /** One PendingIntent per reminder and action; the data URI keeps them apart. */
    fun broadcast(ctx: Context, action: String, id: String, goalId: String?): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            0,
            Intent(ctx, ReminderReceiver::class.java)
                .setAction(action)
                .setData(Uri.parse("anchor://reminder/$id/${action.substringAfterLast('.')}"))
                .putExtra(EXTRA_ID, id)
                .apply { if (goalId != null) putExtra(EXTRA_GOAL, goalId) },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun notificationId(id: String) = 10_000 + (id.hashCode() and 0xFFFF)
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Reminders.handle(context, intent)
}
