package dev.junjo.anchor.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives the timer's system alarms and its notification buttons. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val action = intent.action) {
            FocusTimer.ACTION_END,
            FocusTimer.ACTION_HALF,
            FocusTimer.ACTION_FIVE,
            FocusTimer.ACTION_SILENCE,
            FocusTimer.ACTION_TICK -> FocusTimer.onAlarm(action)
            FocusTimer.ACTION_START_NOW -> FocusTimer.startNow()
            FocusTimer.ACTION_PAUSE -> FocusTimer.pause()
            FocusTimer.ACTION_RESUME -> FocusTimer.resume()
            FocusTimer.ACTION_ADD5 -> FocusTimer.addMinutes(5)
            FocusTimer.ACTION_STOP -> FocusTimer.stop()
        }
    }
}
