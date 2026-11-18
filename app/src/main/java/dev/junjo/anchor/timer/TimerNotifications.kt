package dev.junjo.anchor.timer

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.junjo.anchor.R
import dev.junjo.anchor.clockTime
import dev.junjo.anchor.formatDuration
import dev.junjo.anchor.minutesText
import dev.junjo.anchor.notify.Notifications
import dev.junjo.anchor.timer.FocusTimer.Phase
import dev.junjo.anchor.timer.FocusTimer.Status

/** The three timer notifications: the live countdown, the heads-ups, and "time's up". */
object TimerNotifications {

    /**
     * The running timer, as an Android 16 Live Update: a countdown chip in the status bar, and a
     * plain progress bar at the top of the shade and on the lock screen. Kept bare on purpose:
     * what it's for, when it ends, and two buttons. Older Android versions show the same thing
     * as an ordinary ongoing notification.
     */
    fun showRunning(ctx: Context, s: FocusTimer.State, now: Long = System.currentTimeMillis()) {
        val ready = s.phase == Phase.READY
        val running = s.status == Status.RUNNING
        val builder = NotificationCompat.Builder(ctx, Notifications.CH_TIMER)
            // Also the icon in the status bar chip, next to the countdown.
            .setSmallIcon(R.drawable.ic_anchor)
            .setContentTitle(s.label ?: "Focus")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(Notifications.openApp(ctx))
            // Android 16+: lets the system promote it to a Live Update.
            .setRequestPromotedOngoing(true)

        if (running) {
            // The system counts down by itself, so the lock screen and the status bar chip show
            // live time left without the app having to wake up every second.
            builder.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(s.endAt)
                .setShowWhen(true)
                .setContentText(if (ready) "Starts at ${clockTime(s.endAt)}" else "Until ${clockTime(s.endAt)}")
        } else {
            builder.setShowWhen(false)
                .setShortCriticalText("Paused")
                .setContentText("Paused · ${formatDuration(s.remainingMs)} left")
        }

        if (Build.VERSION.SDK_INT >= 36) {
            val b = bar(s, now)
            // The phone's own wallpaper colours, matching the card in the app: one colour while
            // getting ready, another for focus, grey while paused.
            val color = ctx.getColor(
                when {
                    !running -> android.R.color.system_neutral2_500
                    ready -> android.R.color.system_accent3_500
                    else -> android.R.color.system_accent1_500
                },
            )
            builder.setStyle(
                NotificationCompat.ProgressStyle()
                    .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(b.length).setColor(color)))
                    .setProgress(b.progress)
                    .setStyledByProgress(true),
            )
        }

        when {
            ready && running -> builder.addAction(0, "Start now", FocusTimer.broadcast(ctx, FocusTimer.ACTION_START_NOW))
            running -> builder.addAction(0, "Pause", FocusTimer.broadcast(ctx, FocusTimer.ACTION_PAUSE))
            else -> builder.addAction(0, "Resume", FocusTimer.broadcast(ctx, FocusTimer.ACTION_RESUME))
        }
        builder.addAction(0, "Stop", FocusTimer.broadcast(ctx, FocusTimer.ACTION_STOP))

        Notifications.post(ctx, Notifications.ID_TIMER, builder.build())
    }

    /** The progress bar, in seconds: how long the current part is and how much of it has gone. */
    data class Bar(val length: Int, val progress: Int)

    /** One part at a time: while getting ready the bar fills up to the start, then refills for focus. */
    internal fun bar(s: FocusTimer.State, now: Long): Bar {
        val length = (s.totalMs / 1000).toInt().coerceAtLeast(1)
        val left = (s.remaining(now) / 1000).toInt()
        return Bar(length, (length - left).coerceIn(0, length))
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
