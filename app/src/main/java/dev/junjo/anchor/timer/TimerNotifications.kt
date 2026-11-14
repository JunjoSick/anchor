package dev.junjo.anchor.timer

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.junjo.anchor.R
import dev.junjo.anchor.clockTime
import dev.junjo.anchor.formatDuration
import dev.junjo.anchor.minutesText
import dev.junjo.anchor.notify.Notifications
import dev.junjo.anchor.timer.FocusTimer.Status

/** The three timer notifications: the live countdown, the heads-ups, and "time's up". */
object TimerNotifications {

    fun showRunning(ctx: Context, s: FocusTimer.State) {
        val builder = NotificationCompat.Builder(ctx, Notifications.CH_TIMER)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(s.label ?: "Timer")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(Notifications.openApp(ctx))

        if (s.status == Status.RUNNING) {
            // The system counts down by itself, so the lock screen shows live time left
            // without the app having to wake up every second.
            builder.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(s.endAt)
                .setShowWhen(true)
                .setContentText("Ends at ${clockTime(s.endAt)}")
                // Android 16+: show the countdown as a chip in the status bar.
                .setRequestPromotedOngoing(true)
                .addAction(0, "Pause", FocusTimer.broadcast(ctx, FocusTimer.ACTION_PAUSE))
        } else {
            builder.setShowWhen(false)
                .setContentText("Paused · ${formatDuration(s.remainingMs)} left")
                .addAction(0, "Resume", FocusTimer.broadcast(ctx, FocusTimer.ACTION_RESUME))
        }
        builder.addAction(0, "+5 min", FocusTimer.broadcast(ctx, FocusTimer.ACTION_ADD5))
            .addAction(0, "Stop", FocusTimer.broadcast(ctx, FocusTimer.ACTION_STOP))

        Notifications.post(ctx, Notifications.ID_TIMER, builder.build())
    }

    fun showCue(ctx: Context, s: FocusTimer.State, text: String) {
        val n = NotificationCompat.Builder(ctx, Notifications.CH_CUE)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(text)
            .setContentText(s.label ?: "Timer")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .setContentIntent(Notifications.openApp(ctx))
            .build()
        Notifications.post(ctx, Notifications.ID_CUE, n)
    }

    /**
     * "Time's up". Loud: rings and vibrates on repeat and fills the screen, until Done or +5 min.
     * Quiet: the same notification without sound, after it has rung for a while or after a reboot.
     */
    fun showDone(ctx: Context, s: FocusTimer.State, loud: Boolean) {
        val open = PendingIntent.getActivity(
            ctx,
            20,
            Intent(ctx, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(ctx, Notifications.CH_DONE)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("Time's up")
            .setContentText(s.label ?: "Your ${minutesText(s.totalMs)} timer finished")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Done", FocusTimer.broadcast(ctx, FocusTimer.ACTION_STOP))
            .addAction(0, "+5 min", FocusTimer.broadcast(ctx, FocusTimer.ACTION_ADD5))
        if (loud) builder.setFullScreenIntent(open, true)
        else builder.setSilent(true).setOnlyAlertOnce(true)

        val n = builder.build()
        if (loud) n.flags = n.flags or Notification.FLAG_INSISTENT

        Notifications.cancel(ctx, Notifications.ID_TIMER)
        Notifications.cancel(ctx, Notifications.ID_CUE)
        Notifications.post(ctx, Notifications.ID_DONE, n)
    }

    fun clearAlerts(ctx: Context) {
        Notifications.cancel(ctx, Notifications.ID_CUE)
        Notifications.cancel(ctx, Notifications.ID_DONE)
    }

    fun clearAll(ctx: Context) {
        clearAlerts(ctx)
        Notifications.cancel(ctx, Notifications.ID_TIMER)
    }
}
