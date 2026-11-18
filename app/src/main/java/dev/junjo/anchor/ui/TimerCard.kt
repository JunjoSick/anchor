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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.junjo.anchor.AppPrefs
import dev.junjo.anchor.R
import dev.junjo.anchor.clockTime
import dev.junjo.anchor.data.GoalStore
import dev.junjo.anchor.formatDuration
import dev.junjo.anchor.minutesText
import dev.junjo.anchor.durationText
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import dev.junjo.anchor.timer.FocusTimer
import dev.junjo.anchor.timer.FocusTimer.Status

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TimerCard(state: FocusTimer.State, modifier: Modifier = Modifier) {
    val ready = state.isGettingReady
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = when {
            ready -> MaterialTheme.colorScheme.tertiaryContainer
            state.isActive -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Column(Modifier.padding(20.dp)) {
            if (state.isActive) ActiveTimer(state) else FocusSetup()
        }
    }
}

/** Pick a focus length, then start now or after a short get-ready countdown. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FocusSetup() {
    val ctx = LocalContext.current
    var focus by remember { mutableIntStateOf(AppPrefs.focusMinutes(ctx)) }
    var ready by remember { mutableIntStateOf(AppPrefs.readyMinutes(ctx)) }
    var picking by remember { mutableStateOf<String?>(null) }
    fun setFocus(minutes: Int) {
        focus = minutes.coerceIn(AppPrefs.FOCUS_RANGE)
        AppPrefs.setFocusMinutes(ctx, focus)
    }
    fun setReady(minutes: Int) {
        ready = minutes.coerceIn(AppPrefs.FOCUS_RANGE)
        AppPrefs.setReadyMinutes(ctx, ready)
    }
    // − and + move to the next multiple of 5, so 7 goes to 5 or 10.
    fun step(up: Boolean) = setFocus(
        if (up) (focus / AppPrefs.FOCUS_STEP + 1) * AppPrefs.FOCUS_STEP
        else ((focus - 1) / AppPrefs.FOCUS_STEP * AppPrefs.FOCUS_STEP).coerceAtLeast(1),
    )

    when (picking) {
        "focus" -> DurationDialog("Focus for", focus, onDismiss = { picking = null }, onPick = { setFocus(it); picking = null })
        "ready" -> DurationDialog(
            "Get ready for",
            ready,
            onDismiss = { picking = null },
            onPick = { setReady(it); picking = null },
            presets = listOf(1, 2, 3, 5, 10, 15),
        )
    }

    Text("Focus", style = MaterialTheme.typography.titleLargeEmphasized)
    Text(
        "Nudges halfway through and 5 minutes before the end. Not ready yet? Start in a few minutes and it begins by itself.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        FilledTonalIconButton(
            onClick = { step(up = false) },
            enabled = focus > AppPrefs.FOCUS_RANGE.first,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(56.dp),
        ) { Icon(painterResource(R.drawable.ic_remove), "Shorter") }
        // Tap the number for any length, typed or picked.
        Text(
            durationText(focus),
            style = if (focus < 60) MaterialTheme.typography.displaySmallEmphasized else MaterialTheme.typography.headlineLargeEmphasized,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .width(176.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClickLabel = "Pick a length") { picking = "focus" }
                .padding(vertical = 8.dp),
        )
        FilledTonalIconButton(
            onClick = { step(up = true) },
            enabled = focus < AppPrefs.FOCUS_RANGE.last,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(56.dp),
        ) { Icon(painterResource(R.drawable.ic_add), "Longer") }
    }
    Spacer(Modifier.height(16.dp))
    val tall = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight)
    val tallPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { FocusTimer.start(focus) },
            shapes = ButtonDefaults.shapes(),
            modifier = tall.weight(1f),
            contentPadding = tallPadding,
        ) { Text("Start", style = MaterialTheme.typography.titleMedium) }
        FilledTonalButton(
            onClick = { FocusTimer.startAfter(ready, focus) },
            shapes = ButtonDefaults.shapes(),
            modifier = tall.weight(1f),
            contentPadding = tallPadding,
        ) { Text("Start in ${durationText(ready)}", style = MaterialTheme.typography.titleMedium) }
    }
    Spacer(Modifier.height(8.dp))
    FlowRow(
        verticalArrangement = Arrangement.Center,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Get ready for", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppPrefs.READY_CHOICES.forEach { m ->
            FilterChip(
                selected = ready == m,
                onClick = { setReady(m) },
                label = { Text("$m min") },
            )
        }
        val custom = ready !in AppPrefs.READY_CHOICES
        FilterChip(
            selected = custom,
            onClick = { picking = "ready" },
            label = { Text(if (custom) durationText(ready) else "Other…") },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActiveTimer(state: FocusTimer.State) {
    val now = rememberNow(active = state.status == Status.RUNNING, alignTo = state.endAt)
    val remaining = state.remaining(now)
    val fraction = if (state.totalMs > 0) remaining.toFloat() / state.totalMs else 0f
    val ready = state.isGettingReady
    val onContainer = if (ready) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer

    Row(verticalAlignment = Alignment.CenterVertically) {
        TimerDisc(fraction, Modifier.size(128.dp), ready = ready)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    ready && state.label != null -> "Get ready: ${state.label}"
                    ready -> "Get ready"
                    else -> state.label ?: "Focus"
                },
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
                when {
                    state.status == Status.PAUSED -> "Paused"
                    ready -> "${minutesText(state.focusMs)} focus starts at ${clockTime(state.endAt)}"
                    state.status == Status.RUNNING -> "Ends at ${clockTime(state.endAt)}"
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
            Status.RUNNING -> if (ready) {
                Button(onClick = { FocusTimer.startNow() }, shapes = ButtonDefaults.shapes()) { Text("I'm in · start now") }
            } else FilledTonalButton(onClick = { FocusTimer.pause() }, shapes = ButtonDefaults.shapes()) { Text("Pause") }
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
