package dev.junjo.anchor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.junjo.anchor.R
import dev.junjo.anchor.clockTime
import dev.junjo.anchor.data.GoalStore
import dev.junjo.anchor.formatDuration
import dev.junjo.anchor.minutesText
import dev.junjo.anchor.timer.FocusTimer
import dev.junjo.anchor.timer.FocusTimer.Status

/**
 * The running timer, kept small: the disc, the time, and the one or two things you might do.
 * Starting one is the focus sheet's job, so there are no settings here.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TimerCard(state: FocusTimer.State, modifier: Modifier = Modifier) {
    val now = rememberNow(active = state.status == Status.RUNNING, alignTo = state.endAt)
    val remaining = state.remaining(now)
    val fraction = if (state.totalMs > 0) remaining.toFloat() / state.totalMs else 0f
    val readyPhase = state.phase == FocusTimer.Phase.READY
    val ready = state.isGettingReady
    val ringing = state.status == Status.RINGING
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = if (readyPhase) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (readyPhase) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimerDisc(fraction, Modifier.size(48.dp), ready = readyPhase)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            state.isReadyRinging -> "Time to start!"
                            ringing -> "Time's up"
                            ready -> "Starts in ${formatDuration(remaining)}"
                            else -> formatDuration(remaining)
                        },
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        maxLines = 1,
                    )
                    Text(
                        when {
                            state.isReadyRinging -> listOfNotNull(state.label, "${minutesText(state.focusMs)} focus").joinToString(" · ")
                            ringing -> state.label ?: "${minutesText(state.totalMs)} done"
                            ready -> listOfNotNull(state.label, "${minutesText(state.focusMs)} focus").joinToString(" · ")
                            state.status == Status.PAUSED -> listOfNotNull("Paused", state.label).joinToString(" · ")
                            else -> listOfNotNull(state.label, "until ${clockTime(state.endAt)}").joinToString(" · ")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                when {
                    ringing || ready -> Unit
                    state.status == Status.PAUSED -> {
                        TextButton(onClick = { FocusTimer.addMinutes(5) }, shapes = ButtonDefaults.shapes()) { Text("+5") }
                        IconButton(onClick = { FocusTimer.resume() }, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(R.drawable.ic_play), contentDescription = "Resume")
                        }
                    }
                    else -> {
                        TextButton(onClick = { FocusTimer.addMinutes(5) }, shapes = ButtonDefaults.shapes()) { Text("+5") }
                        IconButton(onClick = { FocusTimer.pause() }, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(R.drawable.ic_pause), contentDescription = "Pause")
                        }
                    }
                }
                if (!ringing) {
                    IconButton(onClick = { FocusTimer.stop() }, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Stop")
                    }
                }
            }
            if (ready) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    OutlinedButton(onClick = { FocusTimer.addMinutes(5) }, shapes = ButtonDefaults.shapes()) { Text("+5 min") }
                    Button(onClick = { FocusTimer.startNow() }, shapes = ButtonDefaults.shapes()) { Text("Start now") }
                }
            } else if (state.isReadyRinging) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = { FocusTimer.stop() }, shapes = ButtonDefaults.shapes()) { Text("Cancel") }
                    OutlinedButton(onClick = { FocusTimer.addMinutes(5) }, shapes = ButtonDefaults.shapes()) { Text("+5 min") }
                    Button(onClick = { FocusTimer.startNow() }, shapes = ButtonDefaults.shapes()) { Text("Start") }
                }
            } else if (ringing) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    val goalId = state.goalId
                    if (goalId != null && GoalStore.find(goalId)?.isActive == true) {
                        TextButton(onClick = { GoalStore.setDone(goalId, true); FocusTimer.stop() }, shapes = ButtonDefaults.shapes()) {
                            Text("Goal done ✓")
                        }
                    }
                    OutlinedButton(onClick = { FocusTimer.addMinutes(5) }, shapes = ButtonDefaults.shapes()) { Text("+5 min") }
                    Button(onClick = { FocusTimer.stop() }, shapes = ButtonDefaults.shapes()) { Text("Done") }
                }
            }
        }
    }
}

/**
 * Time left as a shrinking slice, like a kitchen "time timer": how much is left reads at a glance,
 * without doing sums on digits. Cut to a cookie shape, and red for the last fifth.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TimerDisc(fraction: Float, modifier: Modifier = Modifier, ready: Boolean = false) {
    val track = MaterialTheme.colorScheme.surfaceContainerLowest
    val fill = when {
        ready -> MaterialTheme.colorScheme.tertiary
        fraction in 0.001f..0.2f -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
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
