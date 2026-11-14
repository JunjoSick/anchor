package dev.junjo.anchor.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import dev.junjo.anchor.R
import dev.junjo.anchor.data.GoalStore
import dev.junjo.anchor.data.Reminder
import dev.junjo.anchor.data.ReminderStore
import dev.junjo.anchor.data.active
import dev.junjo.anchor.notify.Notifications
import java.time.ZonedDateTime

/**
 * Schedules daily reminders and shows them.
 *
 * Each reminder has one system alarm for its next time. When it goes off, the next one is
 * scheduled straight away, so a missed tap never stops tomorrow's. By default it also puts the
 * task on today's goal list: a notification is easy to swipe away and forget, a goal stays in
 * the bubble and widget until it's actually done.
 */
object Reminders {
    const val ACTION_FIRE = "dev.junjo.anchor.reminder.FIRE"
    const val ACTION_SNOOZED = "dev.junjo.anchor.reminder.SNOOZED"
    const val ACTION_SNOOZE = "dev.junjo.anchor.reminder.SNOOZE"
    const val ACTION_DONE = "dev.junjo.anchor.reminder.DONE"
    private const val EXTRA_ID = "reminder_id"
    private const val EXTRA_GOAL = "goal_id"
    const val SNOOZE_MINUTES = 10

    fun scheduleAll(ctx: Context) = ReminderStore.reminders.value.forEach { schedule(ctx, it) }

    fun schedule(ctx: Context, r: Reminder) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = broadcast(ctx, ACTION_FIRE, r.id, null)
        am.cancel(pi)
        if (r.deleted || !r.enabled) {
            Notifications.cancel(ctx, notificationId(r.id))
            return
        }
        val at = next(r) ?: return
        exact(am, at, pi)
    }

    /** The next time this reminder is due, or null if no days are picked. */
    fun next(r: Reminder, now: ZonedDateTime = ZonedDateTime.now()): Long? {
        for (i in 0..7L) {
            val date = now.toLocalDate().plusDays(i)
            if (!r.isOn(date.dayOfWeek)) continue
            val time = date.atTime(r.hour, r.minute).atZone(now.zone)
            if (time.isAfter(now)) return time.toInstant().toEpochMilli()
        }
        return null
    }

    fun handle(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val goalId = intent.getStringExtra(EXTRA_GOAL)
        when (intent.action) {
            ACTION_FIRE -> fire(ctx, id, snoozed = false, goalId = null)
            ACTION_SNOOZED -> fire(ctx, id, snoozed = true, goalId = goalId)
            ACTION_SNOOZE -> {
                Notifications.cancel(ctx, notificationId(id))
                val at = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
                exact(ctx.getSystemService(AlarmManager::class.java), at, broadcast(ctx, ACTION_SNOOZED, id, goalId))
            }
            ACTION_DONE -> {
                Notifications.cancel(ctx, notificationId(id))
                goalId?.let { GoalStore.setDone(it, true) }
            }
        }
    }

    private fun fire(ctx: Context, id: String, snoozed: Boolean, goalId: String?) {
        val r = ReminderStore.find(id) ?: return
        if (r.deleted || !r.enabled) return
        var goal = goalId
        if (!snoozed) {
            schedule(ctx, r) // The next day's alarm.
            if (r.addAsGoal) {
                val existing = GoalStore.goals.value.active().firstOrNull { it.text.equals(r.text, ignoreCase = true) }
                goal = existing?.id ?: GoalStore.add(r.text)?.id
            }
        }
        // The goal may have been ticked off during a snooze; then there is nothing left to remind.
        if (snoozed && goal != null && GoalStore.find(goal)?.isActive == false) return

        val n = NotificationCompat.Builder(ctx, Notifications.CH_REMINDER)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(r.text)
            .setContentText(
                if (goal != null) "${r.timeText} · on your goal list until you tick it off"
                else "Daily reminder · ${r.timeText}",
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(Notifications.openApp(ctx))
            .addAction(R.drawable.ic_check, if (goal != null) "Done" else "OK", broadcast(ctx, ACTION_DONE, id, goal))
            .addAction(R.drawable.ic_timer, "Snooze $SNOOZE_MINUTES min", broadcast(ctx, ACTION_SNOOZE, id, goal))
            .build()
        Notifications.post(ctx, notificationId(id), n)
    }

    private fun exact(am: AlarmManager, at: Long, pi: PendingIntent) {
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    /** One PendingIntent per reminder and action; the data URI keeps them apart. */
    private fun broadcast(ctx: Context, action: String, id: String, goalId: String?): PendingIntent =
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
