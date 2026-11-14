package dev.junjo.anchor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.junjo.anchor.clockTime
import dev.junjo.anchor.data.GoalStore
import dev.junjo.anchor.formatDuration
import dev.junjo.anchor.timer.FocusTimer
import dev.junjo.anchor.timer.FocusTimer.Status

/** 5 to get started, 25 for a classic focus block, the rest for bigger chunks. */
val TIMER_PRESETS = listOf(5, 10, 15, 25, 45, 60)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TimerCard(state: FocusTimer.State, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = if (state.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(20.dp)) {
            if (!state.isActive) {
                Text("Timer", style = MaterialTheme.typography.titleLargeEmphasized)
                Text(
                    "You'll get a nudge halfway through and 5 minutes before the end.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TIMER_PRESETS.forEach { m ->
                        FilledTonalButton(
                            onClick = { FocusTimer.start(m) },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
                            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                        ) { Text("$m min", style = MaterialTheme.typography.titleMedium) }
                    }
                }
            } else {
                ActiveTimer(state)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActiveTimer(state: FocusTimer.State) {
    val now = rememberNow(active = state.status == Status.RUNNING, alignTo = state.endAt)
    val remaining = state.remaining(now)
    val fraction = if (state.totalMs > 0) remaining.toFloat() / state.totalMs else 0f
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer

    Row(verticalAlignment = Alignment.CenterVertically) {
        TimerDisc(fraction, Modifier.size(128.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                state.label ?: "Timer",
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = onContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (state.status == Status.RINGING) "Time's up" else formatDuration(remaining),
                style = MaterialTheme.typography.displayMediumEmphasized,
                color = onContainer,
                maxLines = 1,
            )
            Text(
                when (state.status) {
                    Status.RUNNING -> "Ends at ${clockTime(state.endAt)}"
                    Status.PAUSED -> "Paused"
                    else -> "What's next?"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer.copy(alpha = 0.8f),
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state.status) {
            Status.RUNNING -> FilledTonalButton(onClick = { FocusTimer.pause() }, shapes = ButtonDefaults.shapes()) { Text("Pause") }
            Status.PAUSED -> Button(onClick = { FocusTimer.resume() }, shapes = ButtonDefaults.shapes()) { Text("Resume") }
            Status.RINGING -> {
                val goalId = state.goalId
                if (goalId != null && GoalStore.find(goalId)?.isActive == true) {
                    Button(onClick = { GoalStore.setDone(goalId, true); FocusTimer.stop() }, shapes = ButtonDefaults.shapes()) {
                        Text("Goal done ✓")
                    }
                }
                Button(onClick = { FocusTimer.stop() }, shapes = ButtonDefaults.shapes()) { Text("Done") }
            }
            Status.IDLE -> Unit
        }
        OutlinedButton(onClick = { FocusTimer.addMinutes(5) }, shapes = ButtonDefaults.shapes()) { Text("+5 min") }
        if (state.status != Status.RINGING) {
            TextButton(onClick = { FocusTimer.stop() }, shapes = ButtonDefaults.shapes()) { Text("Stop") }
        }
    }
}

/**
 * Time left as a shrinking slice, like a kitchen "time timer": how much is left reads at a glance,
 * without doing sums on digits. Cut to a cookie shape, and red for the last fifth.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TimerDisc(fraction: Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerLowest
    val fill = if (fraction in 0.001f..0.2f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(modifier.clip(MaterialShapes.Cookie12Sided.toShape()).background(track)) {
        Canvas(Modifier.fillMaxSize()) {
            // Oversized so the slice reaches into the cookie's scallops.
            val r = size.minDimension * 0.75f
            drawArc(
                color = fill,
                startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = true,
                topLeft = center.copy(x = center.x - r, y = center.y - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            )
        }
    }
}
