package dev.junjo.anchor.notify

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationManagerCompat
import dev.junjo.anchor.MainActivity

/**
 * Notification channels. Android lets you change each one's sound and importance separately
 * in system settings, so each kind of alert gets its own channel.
 */
object Notifications {
    const val CH_GOALS = "goals"
    const val CH_TIMER = "timer"
    const val CH_CUE = "timer_cue"
    const val CH_DONE = "timer_done"
    const val CH_REMINDER = "reminders"

    const val ID_GOALS = 1
    const val ID_TIMER = 2
    const val ID_CUE = 3
    const val ID_DONE = 4

    fun createChannels(ctx: Context) {
        // Goal list and running timer: default importance so they show on the lock screen
        // (Android hides "silent" notifications there by default), but with no sound or buzz.
        fun quiet(id: String, name: String, about: String) =
            NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = about
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

        val cue = NotificationChannel(CH_CUE, "Timer heads-ups", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "A short nudge halfway through and 5 minutes before a timer ends"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val done = NotificationChannel(CH_DONE, "Timer finished", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Rings until you tap Done, like an alarm clock"
            setSound(
                alarmSound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 400, 600)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        ctx.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                quiet(CH_GOALS, "Goal list", "Your current goals, kept in the shade and on the lock screen"),
                quiet(CH_TIMER, "Running timer", "The countdown while a timer runs"),
                cue,
                done,
                NotificationChannel(CH_REMINDER, "Daily reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Reminders you set for a time of day"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            ),
        )
    }

    fun canPost(ctx: Context) = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    @SuppressLint("MissingPermission") // Checked by canPost.
    fun post(ctx: Context, id: Int, notification: Notification) {
        if (!canPost(ctx)) return
        try {
            NotificationManagerCompat.from(ctx).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; nothing to do.
        }
    }

    fun cancel(ctx: Context, id: Int) = NotificationManagerCompat.from(ctx).cancel(id)

    fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx,
        0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
