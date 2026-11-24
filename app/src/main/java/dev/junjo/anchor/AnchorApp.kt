package dev.junjo.anchor

import android.app.Application
import dev.junjo.anchor.data.TodoStore
import dev.junjo.anchor.data.NoteStore
import dev.junjo.anchor.data.ReminderStore
import dev.junjo.anchor.reminders.Reminders
import dev.junjo.anchor.notify.Notifications
import dev.junjo.anchor.timer.FocusTimer
import dev.junjo.anchor.trip.Trips
import dev.junjo.anchor.widget.DayRollover
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AnchorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Everything (screens, widget, receivers, tile) runs in this one process, so loading the
        // stores here means every entry point sees the same goals and timer.
        Notifications.createChannels(this)
        AppPrefs.init(this)
        TodoStore.init(this)
        FocusTimer.init(this)
        FocusTimer.catchUp()
        Trips.init(this)
        Trips.catchUp()
        ReminderStore.init(this)
        NoteStore.init(this)
        // Cheap, and makes sure no reminder is ever left without its alarm.
        Reminders.scheduleAll(this)
        DayRollover.schedule(this)
    }

    companion object {
        /** Work that must outlive the screen or receiver that started it, like widget refreshes. */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
