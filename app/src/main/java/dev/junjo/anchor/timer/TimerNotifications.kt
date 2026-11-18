package dev.junjo.anchor.timer

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
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
     * The running timer, as an Android 16 Live Update: a countdown chip in the status bar, and
     * a progress bar at the top of the shade and on the lock screen. Older Android versions show
     * the same countdown as an ordinary ongoing notification.
     */
    fun showRunning(ctx: Context, s: FocusTimer.State, now: Long = System.currentTimeMillis()) {
        val ready = s.phase == Phase.READY
        val running = s.status == Status.RUNNING
        val title = when {
            ready && s.label != null -> "Get ready: ${s.label}"
            ready -> "Get ready"
            else -> s.label ?: "Focus"
        }
        val builder = NotificationCompat.Builder(ctx, Notifications.CH_TIMER)
            // Also the icon in the status bar chip, next to the countdown.
            .setSmallIcon(R.drawable.ic_anchor)
            .setContentTitle(title)
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
                .setContentText(
                    if (ready) "${minutesText(s.focusMs)} of focus starts at ${clockTime(s.endAt)}"
                    else if (s.label != null) "Focus · ends at ${clockTime(s.endAt)}" else "Ends at ${clockTime(s.endAt)}",
                )
        } else {
            builder.setShowWhen(false)
                .setShortCriticalText("Paused")
                .setContentText("Paused · ${formatDuration(s.remainingMs)} left")
        }

        if (Build.VERSION.SDK_INT >= 36) builder.setStyle(progress(ctx, s, now))

        when {
            ready && running -> builder.addAction(0, "Start now", FocusTimer.broadcast(ctx, FocusTimer.ACTION_START_NOW))
            running -> builder.addAction(0, "Pause", FocusTimer.broadcast(ctx, FocusTimer.ACTION_PAUSE))
            else -> builder.addAction(0, "Resume", FocusTimer.broadcast(ctx, FocusTimer.ACTION_RESUME))
        }
        builder.addAction(0, "+5 min", FocusTimer.broadcast(ctx, FocusTimer.ACTION_ADD5))
            .addAction(0, "Stop", FocusTimer.broadcast(ctx, FocusTimer.ACTION_STOP))

        Notifications.post(ctx, Notifications.ID_TIMER, builder.build())
    }

    enum class Part { READY, FOCUS }

    /** The whole session as one bar, in seconds: segments in order, how far along, and marks. */
    data class Bar(val segments: List<Pair<Part, Int>>, val progress: Int, val marks: List<Int>)

    /**
     * Getting ready and the focus block that follows it share one bar, so the whole run is
     * visible at once: you see the start coming, then the block itself. Marks show where the
     * halfway and 5-minutes-left nudges will fall.
     */
    internal fun bar(s: FocusTimer.State, now: Long): Bar {
        fun sec(ms: Long) = (ms / 1000).toInt()
        val gettingReady = s.phase == Phase.READY
        val ready = sec(if (gettingReady) s.totalMs else s.readyMs)
        val focus = sec(if (gettingReady) s.focusMs else s.totalMs)
        val segments = listOfNotNull(
            (Part.READY to ready).takeIf { ready > 0 },
            (Part.FOCUS to focus).takeIf { focus > 0 },
        ).ifEmpty { listOf(Part.FOCUS to 1) }
        val left = sec(s.remaining(now))
        val done = if (gettingReady) ready - left else ready + focus - left
        val marks = buildList {
            if (focus >= 10 * 60) add(ready + focus / 2)
            if (focus >= 15 * 60) add(ready + focus - 5 * 60)
        }
        return Bar(segments, done.coerceIn(0, segments.sumOf { it.second }), marks)
    }

    @RequiresApi(36)
    private fun progress(ctx: Context, s: FocusTimer.State, now: Long): NotificationCompat.ProgressStyle {
        val b = bar(s, now)
        val paused = s.status == Status.PAUSED
        // The phone's own wallpaper colours; grey while paused, so a paused timer looks stopped.
        fun color(part: Part) = ctx.getColor(
            when {
                paused -> android.R.color.system_neutral2_400
                part == Part.READY -> android.R.color.system_accent3_400
                else -> android.R.color.system_accent1_400
            },
        )
        return NotificationCompat.ProgressStyle()
            .setProgressSegments(b.segments.map { (part, length) -> NotificationCompat.ProgressStyle.Segment(length).setColor(color(part)) })
            .setProgressPoints(b.marks.map { NotificationCompat.ProgressStyle.Point(it).setColor(ctx.getColor(android.R.color.system_accent1_600)) })
            .setProgress(b.progress)
            .setStyledByProgress(true)
            // The anchor sails along the bar from ▶ to ✓.
            .setProgressTrackerIcon(IconCompat.createWithResource(ctx, R.drawable.ic_bar_anchor))
            .setProgressStartIcon(IconCompat.createWithResource(ctx, R.drawable.ic_bar_start))
            .setProgressEndIcon(IconCompat.createWithResource(ctx, R.drawable.ic_bar_end))
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
