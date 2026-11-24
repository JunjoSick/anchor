package dev.junjo.anchor.trip

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.junjo.anchor.R
import dev.junjo.anchor.formatDuration
import dev.junjo.anchor.ui.TimerDisc
import dev.junjo.anchor.ui.rememberNow
import kotlinx.coroutines.delay

/**
 * Three unmistakable pulses (~1 Hz, safely under the 3 Hz photosensitive-seizure guideline).
 * Shared by the tucked edge tab nudge, the trip pill, and the STOP card.
 */
internal suspend fun Animatable<Float, AnimationVector1D>.flashThreeTimes() {
    repeat(3) {
        animateTo(1f, tween(320))
        delay(140)
        animateTo(0f, tween(420))
        delay(180)
    }
}

/**
 * The floating pill during an active In&Out trip: countdown ring · what you came for · ✓.
 * Calm while on track, flashes 3× halfway (and after "another go"), and turns warm with a slow
 * 1-second pulse during the last 15 seconds.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TripPill(
    trip: TripState,
    flashSerial: Int,
    touch: Modifier,
    onDone: () -> Unit,
) {
    val view = LocalView.current
    val now = rememberNow(active = trip.status == TripStatus.RUNNING, alignTo = trip.endAt)
    val remaining = trip.remaining(now)
    val fraction = if (trip.totalMs > 0) remaining.toFloat() / trip.totalMs else 0f
    val last15 = trip.status == TripStatus.RUNNING && remaining in 1..TripPolicy.LAST_15_MS

    val flash = remember { Animatable(0f) }
    var seenFlash by remember { mutableIntStateOf(flashSerial) }
    LaunchedEffect(flashSerial) {
        if (flashSerial != seenFlash) {
            seenFlash = flashSerial
            flash.snapTo(0f)
            flash.flashThreeTimes()
        }
    }

    val warnPulse = remember { Animatable(0f) }
    LaunchedEffect(last15) {
        warnPulse.snapTo(0f)
        if (!last15) return@LaunchedEffect
        while (true) {
            warnPulse.animateTo(1f, tween(500))
            warnPulse.animateTo(0f, tween(500))
        }
    }

    val baseBg = if (last15) {
        lerp(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.errorContainer, warnPulse.value)
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val baseFg = if (last15) {
        lerp(MaterialTheme.colorScheme.onTertiaryContainer, MaterialTheme.colorScheme.onErrorContainer, warnPulse.value)
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = lerp(baseBg, MaterialTheme.colorScheme.error, flash.value),
        contentColor = lerp(baseFg, MaterialTheme.colorScheme.onError, flash.value),
        shadowElevation = 6.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                touch.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimerDisc(fraction, Modifier.size(24.dp), ready = last15)
                Spacer(Modifier.width(8.dp))
                Text(
                    trip.name,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp),
                )
            }
            IconButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onDone()
                },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.padding(end = 2.dp).size(38.dp),
            ) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = "Done", modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Compact menu opened by tapping the trip pill's text: Done, Later, Cancel. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TripMenu(
    trip: TripState,
    drag: Modifier,
    onDone: () -> Unit,
    onLater: () -> Unit,
    onCancel: () -> Unit,
) {
    val view = LocalView.current
    val now = rememberNow(active = trip.status == TripStatus.RUNNING, alignTo = trip.endAt)
    val remaining = trip.remaining(now)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        modifier = Modifier.width(260.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(drag.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    trip.name,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatDuration(remaining),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Button(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onDone()
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("✓ Done") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onLater, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                    Text("Later")
                }
                TextButton(onClick = onCancel, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
            }
        }
    }
}

/**
 * Full-screen STOP overlay when an In&Out trip finishes. Covers the app behind a dark scrim until
 * the user explicitly chooses what to do next.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StopCard(
    trip: TripState,
    onDone: () -> Unit,
    onAgain: () -> Unit,
    onLater: () -> Unit,
    onFocus: () -> Unit,
) {
    val view = LocalView.current
    val flash = remember { Animatable(0f) }
    LaunchedEffect(trip.gos) {
        flash.snapTo(0f)
        flash.flashThreeTimes()
    }

    val initialDelay = TripPolicy.againDelayMs(trip.gos)
    var waitMs by remember(trip.gos) { mutableLongStateOf(initialDelay) }
    LaunchedEffect(trip.gos) {
        waitMs = initialDelay
        while (waitMs > 0) {
            delay(250)
            waitMs = (waitMs - 250).coerceAtLeast(0)
        }
    }

    val suggestFocus = TripPolicy.suggestFocus(trip.gos)
    val cardBg = lerp(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.error, flash.value)
    val cardFg = lerp(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onError, flash.value)
    val big = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.LargeContainerHeight)
    val bigPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.LargeContainerHeight)
    val medium = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            // Tapping outside does nothing; user must pick an action.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = cardBg,
            contentColor = cardFg,
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "You came for:",
                    style = MaterialTheme.typography.titleMedium,
                    color = cardFg.copy(alpha = 0.78f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    trip.name,
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    textAlign = TextAlign.Center,
                )
                if (suggestFocus) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${goOrdinal(trip.gos)}. Maybe it's bigger than a quick trip?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cardFg.copy(alpha = 0.82f),
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(28.dp))

                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        onDone()
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = big,
                    contentPadding = bigPadding,
                ) {
                    Text("✓ Done", style = MaterialTheme.typography.titleLarge)
                }

                if (suggestFocus) {
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = onFocus,
                        shapes = ButtonDefaults.shapes(),
                        modifier = medium,
                    ) {
                        Text("Make it a Focus", style = MaterialTheme.typography.titleMedium)
                    }
                }

                Spacer(Modifier.height(12.dp))
                val waitSecs = ((waitMs + 999) / 1000).toInt()
                OutlinedButton(
                    onClick = onAgain,
                    enabled = waitMs == 0L,
                    shapes = ButtonDefaults.shapes(),
                    modifier = medium,
                ) {
                    Text(
                        if (waitMs > 0) "I got lost: another go (${waitSecs}s)" else "I got lost: another go",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(10.dp))
                if (suggestFocus) {
                    TextButton(onClick = onLater, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                        Text("Later")
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onLater, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                            Text("Later")
                        }
                        TextButton(onClick = onFocus, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                            Text("Make it a Focus")
                        }
                    }
                }
            }
        }
    }
}

private fun goOrdinal(gos: Int): String = when (gos) {
    3 -> "Third go"
    4 -> "Fourth go"
    else -> "Go #$gos"
}
