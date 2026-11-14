package dev.junjo.anchor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * The current time for a countdown, refreshed every [periodMs].
 *
 * Built to cost as little battery as possible:
 * - It only ticks while [active] and while its screen is actually showing (lifecycle STARTED).
 *   A plain `while (true) { delay() }` keeps waking the app in the background and with the
 *   screen off, redrawing a countdown nobody can see.
 * - Ticks land exactly when the displayed value changes ([alignTo] is the countdown's end time),
 *   so once a second is enough for a seconds display; no need to poll faster to look smooth.
 */
@Composable
fun rememberNow(active: Boolean, periodMs: Long = 1000, alignTo: Long = 0): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(active, periodMs, alignTo, lifecycle) {
        now = System.currentTimeMillis()
        if (!active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val t = System.currentTimeMillis()
                now = t
                // Time until the next multiple of the period, counted from the end time.
                val untilNext = ((alignTo - t) % periodMs + periodMs) % periodMs
                delay(if (untilNext < 20) untilNext + periodMs else untilNext)
            }
        }
    }
    return now
}
