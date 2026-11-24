package dev.junjo.anchor.notify

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import dev.junjo.anchor.AppPrefs
import dev.junjo.anchor.R
import dev.junjo.anchor.data.TodoStore
import dev.junjo.anchor.data.active
import dev.junjo.anchor.overlay.FloatingGoalsService

/**
 * A pinned notification with the current goals. It puts them on the lock screen and in the
 * shade, where you see them dozens of times a day, and lets you type a new goal straight into it.
 */
object GoalsNotification {
    const val ACTION_ADD = "dev.junjo.anchor.ADD_GOAL"
    const val ACTION_DONE = "dev.junjo.anchor.GOAL_DONE"
    const val EXTRA_ID = "goal_id"
    private const val KEY_TEXT = "goal_text"
    private const val MAX_LINES = 6

    fun update(ctx: Context) {
        // While the floating bubble runs, this is its foreground-service notification and must stay.
        if (!AppPrefs.goalsNotification(ctx) && !FloatingGoalsService.running) {
            Notifications.cancel(ctx, Notifications.ID_GOALS)
            return
        }
        Notifications.post(ctx, Notifications.ID_GOALS, build(ctx))
    }

    fun build(ctx: Context): Notification {
        val active = TodoStore.todos.value.active()

        val builder = NotificationCompat.Builder(ctx, Notifications.CH_GOALS)
            .setSmallIcon(R.drawable.ic_check_circle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(Notifications.openApp(ctx))

        if (active.isEmpty()) {
            builder.setContentTitle("Nothing on your list")
                .setContentText("Tap “Add” to put something down")
        } else {
            builder.setContentTitle(if (active.size == 1) "1 to-do" else "${active.size} to-dos")
                .setContentText(active.joinToString("  ·  ") { it.text })
            val style = NotificationCompat.InboxStyle()
            active.take(MAX_LINES).forEach { style.addLine("○  ${it.text}") }
            if (active.size > MAX_LINES) style.setSummaryText("+${active.size - MAX_LINES} more")
            builder.setStyle(style)
        }

        val input = RemoteInput.Builder(KEY_TEXT).setLabel("Add").build()
        val add = PendingIntent.getBroadcast(
            ctx,
            100,
            Intent(ctx, GoalActionReceiver::class.java).setAction(ACTION_ADD),
            // Mutable so the system can attach the typed text; the intent is explicit, so that's safe.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        builder.addAction(
            NotificationCompat.Action.Builder(R.drawable.ic_add, "Add", add)
                .addRemoteInput(input)
                .setAllowGeneratedReplies(false)
                .build(),
        )

        active.firstOrNull()?.let { first ->
            val done = PendingIntent.getBroadcast(
                ctx,
                101,
                Intent(ctx, GoalActionReceiver::class.java).setAction(ACTION_DONE).putExtra(EXTRA_ID, first.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val name = if (first.text.length > 22) first.text.take(21) + "…" else first.text
            builder.addAction(R.drawable.ic_check_circle, "Done: $name", done)
        }

        return builder.build()
    }

    fun typedText(intent: Intent): String? =
        RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_TEXT)?.toString()
}
