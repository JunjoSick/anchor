package dev.junjo.anchor.reminders

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.junjo.anchor.data.ReminderStore
import dev.junjo.anchor.ui.AnchorTheme

/** Full-screen page for a reminder set to ring like an alarm, shown over the lock screen. */
class ReminderAlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(Reminders.EXTRA_ID)
        val reminder = id?.let { ReminderStore.find(it) }
        // Opened late (e.g. from an old notification) for a reminder that's already been answered.
        if (reminder == null || !Reminders.isShowing(this, reminder.id)) {
            finish()
            return
        }
        Reminders.markRinging(reminder.id)
        val goal = intent.getStringExtra(Reminders.EXTRA_GOAL)

        fun send(action: String) {
            sendBroadcast(
                Intent(this, ReminderReceiver::class.java)
                    .setAction(action)
                    .putExtra(Reminders.EXTRA_ID, reminder.id)
                    .apply { if (goal != null) putExtra(Reminders.EXTRA_GOAL, goal) },
            )
            finish()
        }

        enableEdgeToEdge()
        setContent {
            AnchorTheme {
                val ringing by Reminders.ringing.collectAsStateWithLifecycle()
                // Answered from the notification instead: nothing left to show.
                LaunchedEffect(ringing) { if (reminder.id !in ringing) finish() }
                ReminderAlarmScreen(
                    text = reminder.text,
                    about = if (goal != null) "It's on your goal list too" else reminder.daysText,
                    doneLabel = if (goal != null) "Done ✓" else "OK",
                    onDone = { send(Reminders.ACTION_DONE) },
                    onSnooze = { send(Reminders.ACTION_SNOOZE) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReminderAlarmScreen(text: String, about: String, doneLabel: String, onDone: () -> Unit, onSnooze: () -> Unit) {
    val big = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.LargeContainerHeight)
    val bigPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.LargeContainerHeight)
    val bigText = MaterialTheme.typography.titleLarge

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Column(
            Modifier.safeDrawingPadding().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LoadingIndicator(Modifier.size(160.dp), color = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.height(24.dp))
            Text(text, style = MaterialTheme.typography.displaySmallEmphasized, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(about, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(48.dp))
            Button(onClick = onDone, shapes = ButtonDefaults.shapes(), modifier = big, contentPadding = bigPadding) {
                Text(doneLabel, style = bigText)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onSnooze, shapes = ButtonDefaults.shapes(), modifier = big, contentPadding = bigPadding) {
                Text("Snooze ${Reminders.SNOOZE_MINUTES} min", style = bigText)
            }
        }
    }
}
