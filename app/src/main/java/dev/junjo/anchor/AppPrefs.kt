package dev.junjo.anchor

import android.content.Context
import androidx.core.content.edit
import dev.junjo.anchor.notify.GoalsNotification

/** The few settings the app has. Kept deliberately small: tweaking settings is not doing tasks. */
object AppPrefs {
    private const val GOALS_NOTIFICATION = "goals_notification"
    private const val ASKED_NOTIFICATIONS = "asked_notifications"
    private const val SETUP_HIDDEN = "setup_hidden"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun goalsNotification(ctx: Context) = prefs(ctx).getBoolean(GOALS_NOTIFICATION, true)

    fun setGoalsNotification(ctx: Context, on: Boolean) {
        prefs(ctx).edit { putBoolean(GOALS_NOTIFICATION, on) }
        GoalsNotification.update(ctx)
    }

    fun askedNotifications(ctx: Context) = prefs(ctx).getBoolean(ASKED_NOTIFICATIONS, false)
    fun setAskedNotifications(ctx: Context) = prefs(ctx).edit { putBoolean(ASKED_NOTIFICATIONS, true) }

    fun setupHidden(ctx: Context) = prefs(ctx).getBoolean(SETUP_HIDDEN, false)
    fun setSetupHidden(ctx: Context, hidden: Boolean) = prefs(ctx).edit { putBoolean(SETUP_HIDDEN, hidden) }
}
