package dev.junjo.anchor

import android.content.Context
import androidx.core.content.edit
import dev.junjo.anchor.notify.GoalsNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The few settings the app has. Kept deliberately small: tweaking settings is not doing tasks. */
object AppPrefs {
    private const val GOALS_NOTIFICATION = "goals_notification"
    private const val ASKED_NOTIFICATIONS = "asked_notifications"
    private const val SETUP_HIDDEN = "setup_hidden"
    private const val FOCUS_MINUTES = "focus_minutes"
    private const val READY_MINUTES = "ready_minutes"
    private const val BUBBLE_NUDGE = "bubble_nudge"

    private val _bubbleNudge = MutableStateFlow(false)
    val bubbleNudgeFlow: StateFlow<Boolean> = _bubbleNudge.asStateFlow()

    fun init(ctx: Context) {
        _bubbleNudge.value = prefs(ctx).getBoolean(BUBBLE_NUDGE, false)
    }

    /** Quick picks in the focus sheet, in minutes; "Other…" covers the rest. */
    val FOCUS_PRESETS = listOf(5, 15, 25, 45)
    val FOCUS_RANGE = 1..(23 * 60 + 59)
    val READY_CHOICES = listOf(2, 5, 10)

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun goalsNotification(ctx: Context) = prefs(ctx).getBoolean(GOALS_NOTIFICATION, true)

    fun setGoalsNotification(ctx: Context, on: Boolean) {
        prefs(ctx).edit { putBoolean(GOALS_NOTIFICATION, on) }
        GoalsNotification.update(ctx)
    }

    fun bubbleNudge(ctx: Context) = prefs(ctx).getBoolean(BUBBLE_NUDGE, false)

    fun setBubbleNudge(ctx: Context, on: Boolean) {
        prefs(ctx).edit { putBoolean(BUBBLE_NUDGE, on) }
        _bubbleNudge.value = on
    }

    fun askedNotifications(ctx: Context) = prefs(ctx).getBoolean(ASKED_NOTIFICATIONS, false)
    fun setAskedNotifications(ctx: Context) = prefs(ctx).edit { putBoolean(ASKED_NOTIFICATIONS, true) }

    /** How long a focus block lasts. 25 is the classic; people with ADHD often do better shorter. */
    fun focusMinutes(ctx: Context) = prefs(ctx).getInt(FOCUS_MINUTES, 25)
    fun setFocusMinutes(ctx: Context, minutes: Int) = prefs(ctx).edit { putInt(FOCUS_MINUTES, minutes) }

    /** The "get ready" countdown before a focus block. */
    fun readyMinutes(ctx: Context) = prefs(ctx).getInt(READY_MINUTES, 5)
    fun setReadyMinutes(ctx: Context, minutes: Int) = prefs(ctx).edit { putInt(READY_MINUTES, minutes) }

    fun setupHidden(ctx: Context) = prefs(ctx).getBoolean(SETUP_HIDDEN, false)
    fun setSetupHidden(ctx: Context, hidden: Boolean) = prefs(ctx).edit { putBoolean(SETUP_HIDDEN, hidden) }
}
