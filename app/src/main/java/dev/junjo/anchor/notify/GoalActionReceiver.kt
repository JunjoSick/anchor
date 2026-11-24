package dev.junjo.anchor.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.junjo.anchor.data.TodoStore

/** Handles "Add" (typed into the notification) and "Done" from the lock-screen notification. */
class GoalActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            GoalsNotification.ACTION_ADD -> {
                GoalsNotification.typedText(intent)?.let { TodoStore.add(it) }
                // Re-post even if nothing was added, or the reply field keeps spinning.
                GoalsNotification.update(context)
            }
            GoalsNotification.ACTION_DONE ->
                intent.getStringExtra(GoalsNotification.EXTRA_ID)?.let { TodoStore.setDone(it, true) }
        }
    }
}
