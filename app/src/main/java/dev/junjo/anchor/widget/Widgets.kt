package dev.junjo.anchor.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.updateAll
import dev.junjo.anchor.AnchorApp
import dev.junjo.anchor.BootReceiver
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

object Widgets {
    /** Redraws every goals widget on the home screen. Does nothing if there isn't one. */
    fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        // Starting a Glance update is not free; skip it when no widget has been placed.
        if (!isPlaced(app)) return
        AnchorApp.scope.launch {
            runCatching { GoalsWidget().updateAll(app) }
                .onFailure { Log.w("Widgets", "Widget refresh failed", it) }
        }
    }

    fun isPlaced(ctx: Context): Boolean =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(component(ctx)).isNotEmpty()

    /** Asks the launcher to add the widget for you. Returns false if the launcher can't. */
    fun requestPin(ctx: Context): Boolean {
        val manager = AppWidgetManager.getInstance(ctx)
        if (!manager.isRequestPinAppWidgetSupported) return false
        return manager.requestPinAppWidget(component(ctx), null, null)
    }

    private fun component(ctx: Context) = ComponentName(ctx, GoalsWidgetReceiver::class.java)
}

/**
 * One alarm just after midnight, to reset the widget's "done today". It replaces the widget's
 * built-in periodic update, which can't be slower than every 30 minutes (48 wake-ups a day for
 * something that changes once). It is a non-waking alarm: if the phone is asleep at midnight it
 * simply runs when the phone next wakes, which is before anyone can look at the widget anyway.
 */
object DayRollover {
    const val ACTION = "dev.junjo.anchor.DAY_ROLLOVER"

    fun schedule(ctx: Context) {
        val midnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = PendingIntent.getBroadcast(
            ctx,
            0,
            Intent(ctx, BootReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        ctx.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, midnight + 5_000, intent)
    }
}
