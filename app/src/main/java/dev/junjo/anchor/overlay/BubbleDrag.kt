package dev.junjo.anchor.overlay

import android.view.MotionEvent
import android.view.VelocityTracker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlin.math.hypot

/** What the service does with a drag on the bubble. Distances and speeds are in screen pixels. */
class BubbleGestures(
    val onStart: () -> Unit,
    /** Total movement since the finger went down. */
    val onMove: (dx: Float, dy: Float) -> Unit,
    /** Total movement, and horizontal speed in px/s at release (for flings). */
    val onEnd: (dx: Float, dy: Float, vx: Float) -> Unit,
)

private class DragState {
    var downX = 0f
    var downY = 0f
    var dragging = false
    var tracker: VelocityTracker? = null
}

/**
 * Drag-or-tap handling for the bubble.
 *
 * The bubble's window moves with the finger, so positions inside the window are useless for
 * dragging: they shift as the window does, which made the bubble jitter and fall short of the
 * edge. This reads the finger's position on the screen (raw coordinates) instead, and measures
 * everything from where the finger first went down. One handler decides tap versus drag, so a
 * slightly wobbly tap is still a tap.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.bubbleDrag(gestures: BubbleGestures, onTap: (() -> Unit)? = null): Modifier {
    val slop = LocalViewConfiguration.current.touchSlop
    val s = remember { DragState() }

    fun track(e: MotionEvent) {
        // Velocity in screen coordinates too, for the same reason as above.
        val copy = MotionEvent.obtain(e)
        copy.setLocation(e.rawX, e.rawY)
        s.tracker?.addMovement(copy)
        copy.recycle()
    }

    return this.pointerInteropFilter { e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                s.downX = e.rawX
                s.downY = e.rawY
                s.dragging = false
                s.tracker?.recycle()
                s.tracker = VelocityTracker.obtain()
                track(e)
                true
            }
            MotionEvent.ACTION_MOVE -> {
                track(e)
                val dx = e.rawX - s.downX
                val dy = e.rawY - s.downY
                if (!s.dragging && hypot(dx, dy) > slop) {
                    s.dragging = true
                    gestures.onStart()
                }
                if (s.dragging) gestures.onMove(dx, dy)
                true
            }
            MotionEvent.ACTION_UP -> {
                track(e)
                if (s.dragging) {
                    s.tracker?.computeCurrentVelocity(1000)
                    gestures.onEnd(e.rawX - s.downX, e.rawY - s.downY, s.tracker?.xVelocity ?: 0f)
                } else {
                    onTap?.invoke()
                }
                s.dragging = false
                s.tracker?.recycle()
                s.tracker = null
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (s.dragging) gestures.onEnd(e.rawX - s.downX, e.rawY - s.downY, 0f)
                s.dragging = false
                s.tracker?.recycle()
                s.tracker = null
                true
            }
            else -> false
        }
    }
}
