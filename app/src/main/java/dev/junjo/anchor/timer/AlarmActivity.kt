package dev.junjo.anchor.timer

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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.junjo.anchor.data.GoalStore
import dev.junjo.anchor.minutesText
import dev.junjo.anchor.ui.AnchorTheme

/** Full-screen "time's up", shown over the lock screen like a clock alarm. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnchorTheme {
                val state by FocusTimer.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.status) {
                    if (state.status != FocusTimer.Status.RINGING) finish()
                }
                AlarmScreen(state)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AlarmScreen(state: FocusTimer.State) {
    val big = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.LargeContainerHeight)
    val bigPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.LargeContainerHeight)
    val bigText = MaterialTheme.typography.titleLarge
    val ready = state.phase == FocusTimer.Phase.READY

    Surface(
        Modifier.fillMaxSize(),
        color = if (ready) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            Modifier.safeDrawingPadding().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The morphing shape keeps moving, which pulls the eye back if attention drifts.
            LoadingIndicator(
                Modifier.size(160.dp),
                color = if (ready) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(24.dp))
            Text(if (ready) "Time to start!" else "Time's up", style = MaterialTheme.typography.displayLargeEmphasized)
            Spacer(Modifier.height(8.dp))
            Text(
                if (ready) listOfNotNull(state.label ?: "Focus", minutesText(state.focusMs)).joinToString(" · ")
                else state.label ?: "Your ${minutesText(state.totalMs)} timer finished",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(48.dp))

            if (ready) {
                Button(
                    onClick = { FocusTimer.startNow() },
                    shapes = ButtonDefaults.shapes(),
                    modifier = big,
                    contentPadding = bigPadding,
                ) { Text("Start focus", style = bigText) }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { FocusTimer.addMinutes(5) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = big,
                    contentPadding = bigPadding,
                ) { Text("5 more minutes", style = bigText) }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { FocusTimer.stop() }, shapes = ButtonDefaults.shapes()) {
                    Text("Cancel", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                val goalId = state.goalId
                if (goalId != null && GoalStore.find(goalId)?.isActive == true) {
                    Button(
                        onClick = { GoalStore.setDone(goalId, true); FocusTimer.stop() },
                        shapes = ButtonDefaults.shapes(),
                        modifier = big,
                        contentPadding = bigPadding,
                    ) { Text("Done ✓", style = bigText) }
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = { FocusTimer.stop() },
                        shapes = ButtonDefaults.shapes(),
                        modifier = big,
                        contentPadding = bigPadding,
                    ) { Text("Stop for now", style = bigText) }
                } else {
                    Button(
                        onClick = { FocusTimer.stop() },
                        shapes = ButtonDefaults.shapes(),
                        modifier = big,
                        contentPadding = bigPadding,
                    ) { Text("Done", style = bigText) }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { FocusTimer.addMinutes(5) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = big,
                    contentPadding = bigPadding,
                ) { Text("5 more minutes", style = bigText) }
            }
        }
    }
}
