package dev.junjo.anchor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.junjo.anchor.notify.GoalsNotification
import dev.junjo.anchor.overlay.FloatingGoals
import dev.junjo.anchor.reminders.Reminders
import dev.junjo.anchor.timer.FocusTimer
import dev.junjo.anchor.timer.TimerNotifications
import dev.junjo.anchor.widget.DayRollover
import dev.junjo.anchor.widget.Widgets

/**
 * Puts back what a reboot or app update clears (alarms, the pinned notification, the bubble),
 * and re-times reminders when the clock or time zone changes.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                FocusTimer.restore()
                Reminders.scheduleAll(context)
                GoalsNotification.update(context)
                Widgets.refresh(context)
                FloatingGoals.sync(context)
            }
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                Reminders.scheduleAll(context)
                DayRollover.schedule(context)
                Widgets.refresh(context)
                val timer = FocusTimer.state.value
                if (timer.status == FocusTimer.Status.RUNNING || timer.status == FocusTimer.Status.PAUSED) {
                    TimerNotifications.showRunning(context, timer)
                }
            }
            DayRollover.ACTION -> {
                Widgets.refresh(context)
                DayRollover.schedule(context)
            }
        }
    }
}
