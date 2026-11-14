package dev.junjo.anchor.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/**
 * The floating goal bubble's on/off switch. It needs Android's "Display over other apps"
 * permission; when that's missing, turning it on opens the setting, and the bubble appears
 * when you come back to the app.
 */
object FloatingGoals {
    private const val ON = "bubble_on"
    private const val X = "bubble_x"
    private const val Y = "bubble_y"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun wanted(ctx: Context) = prefs(ctx).getBoolean(ON, false)

    fun canDraw(ctx: Context) = Settings.canDrawOverlays(ctx)

    /** True when the bubble is switched on and allowed, i.e. actually on screen. */
    fun isOn(ctx: Context) = wanted(ctx) && canDraw(ctx)

    fun enable(ctx: Context) {
        prefs(ctx).edit { putBoolean(ON, true) }
        if (canDraw(ctx)) {
            sync(ctx)
        } else {
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    fun disable(ctx: Context) {
        prefs(ctx).edit { putBoolean(ON, false) }
        sync(ctx)
    }

    /** Starts or stops the bubble to match the switch and the permission. */
    fun sync(ctx: Context) {
        val intent = Intent(ctx, FloatingGoalsService::class.java)
        if (isOn(ctx)) {
            if (!FloatingGoalsService.running) runCatching { ContextCompat.startForegroundService(ctx, intent) }
        } else if (FloatingGoalsService.running) {
            ctx.stopService(intent)
        }
    }

    fun position(ctx: Context): Pair<Int, Int> = prefs(ctx).getInt(X, 0) to prefs(ctx).getInt(Y, 320)

    fun dock(ctx: Context): Dock =
        runCatching { Dock.valueOf(prefs(ctx).getString(DOCK, null) ?: "NONE") }.getOrDefault(Dock.NONE)

    /** Whether x is measured from the right screen edge rather than the left. */
    fun anchoredRight(ctx: Context) = prefs(ctx).getBoolean(RIGHT, false)

    fun savePosition(ctx: Context, x: Int, y: Int, dock: Dock, anchoredRight: Boolean) =
        prefs(ctx).edit { putInt(X, x); putInt(Y, y); putString(DOCK, dock.name); putBoolean(RIGHT, anchoredRight) }

    private const val DOCK = "bubble_dock"
    private const val RIGHT = "bubble_right"
}

/** Where the bubble is tucked: not at all, or into the left or right screen edge. */
enum class Dock { NONE, LEFT, RIGHT }
