package dev.junjo.anchor.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.PowerManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.lerp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.junjo.anchor.AppPrefs
import dev.junjo.anchor.ui.rememberNow
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowManager
import android.view.Choreographer
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.junjo.anchor.MainActivity
import dev.junjo.anchor.QuickAddActivity
import dev.junjo.anchor.R
import dev.junjo.anchor.data.Todo
import dev.junjo.anchor.data.TodoStore
import dev.junjo.anchor.data.active
import dev.junjo.anchor.formatDuration
import dev.junjo.anchor.notify.GoalsNotification
import dev.junjo.anchor.notify.Notifications
import dev.junjo.anchor.timer.FocusTimer
import dev.junjo.anchor.timer.FocusTimer.Status
import dev.junjo.anchor.trip.StopCard
import dev.junjo.anchor.trip.TripAction
import dev.junjo.anchor.trip.TripMenu
import dev.junjo.anchor.trip.TripPill
import dev.junjo.anchor.trip.TripPolicy
import dev.junjo.anchor.trip.TripStatus
import dev.junjo.anchor.trip.Trips
import dev.junjo.anchor.trip.flashThreeTimes
import dev.junjo.anchor.ui.AnchorTheme
import dev.junjo.anchor.ui.TimerDisc
import dev.junjo.anchor.ui.segmentShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Draws the floating goal bubble over every app.
 *
 * Collapsed, it is a small pill with the top goal, or the countdown while a timer runs. Tap it
 * for the full list; tap outside to fold it back; drag it anywhere. Drag it mostly off the left
 * or right edge (like a YouTube picture-in-picture) and it tucks away into a slim tab that stays
 * on the edge; tap the tab to bring it back.
 *
 * It runs as a foreground service (Android requires one for a window that stays up), using the
 * goals notification as its notification.
 */
class FloatingGoalsService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var view: ComposeView? = null
    private var stopView: FrameLayout? = null

    private var expanded by mutableStateOf(false)
    private var dock by mutableStateOf(Dock.NONE)
    private var maxListHeight by mutableStateOf(480.dp)
    private var dragging = false
    private var animator: ValueAnimator? = null

    /**
     * Which screen edge the window's x is measured from. The bubble is anchored to its nearer
     * edge, so when it changes size (pill ↔ list ↔ tab) it grows away from that edge instead of
     * off the screen and then being shoved back.
     */
    private var anchorRight = false

    /**
     * Set while the content swaps between pill and tab. The window is kept invisible until the
     * new content has its size, then placed just off the edge and slid in, so the swap is one
     * smooth motion instead of a pop.
     */
    private var revealPending = false

    /** True while a window move is queued for the next frame. */
    private var applyPending = false

    /** Screen turned on or off: start or stop the bubble's drawing work to match. */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = syncLifecycleWithScreen()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        savedState.performAttach()
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WindowManager::class.java)
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        observeTrips()
    }

    /** Watches active In&Out trips: auto-untucks on start, ticks once a second while screen is on, and shows the STOP card. */
    private fun observeTrips() {
        lifecycleScope.launch {
            var wasTripActive = false
            Trips.state.collect { trip ->
                if (trip.isActive && !wasTripActive) {
                    showList(false)
                    if (dock != Dock.NONE) {
                        animator?.cancel()
                        revealPending = false
                        view?.alpha = 1f
                        dock = Dock.NONE
                        params.x = 0
                        applyNow()
                    }
                }
                wasTripActive = trip.isActive
                if (trip.status == TripStatus.STOP) {
                    showList(false)
                    showStopCard()
                } else {
                    hideStopCard()
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                Trips.catchUp()
                Trips.state.collectLatest { trip ->
                    if (trip.status != TripStatus.RUNNING) return@collectLatest
                    while (true) {
                        val now = System.currentTimeMillis()
                        when (TripPolicy.tick(trip, now)) {
                            TripAction.HALFWAY -> {
                                Trips.markHalfway()
                                return@collectLatest
                            }
                            TripAction.STOP -> {
                                Trips.reachStop(playCue = true)
                                return@collectLatest
                            }
                            TripAction.LAST_15, TripAction.NONE -> {
                                val untilNext = ((trip.endAt - now) % 1000L + 1000L) % 1000L
                                delay(if (untilNext < 20) untilNext + 1000L else untilNext)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun goHome() {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Ends the trip and takes the user to the home screen (started while our overlay is still visible). */
    private fun finishTripDone() {
        showList(false)
        goHome()
        hideStopCard()
        Trips.done()
    }

    private fun showStopCard() {
        if (stopView != null || !FloatingGoals.canDraw(this)) return
        val stopParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Focusable so Back comes to this window and does nothing until the user picks an action.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val container = BackBlockingContainer(this).apply {
            // Compose's WindowRecomposer resolves lifecycle & saved-state owners from the window's
            // root view (this container), not from a child ComposeView.
            setViewTreeLifecycleOwner(this@FloatingGoalsService)
            setViewTreeSavedStateRegistryOwner(this@FloatingGoalsService)
            val compose = ComposeView(this@FloatingGoalsService).apply {
                setContent {
                    AnchorTheme {
                        val trip by Trips.state.collectAsState()
                        if (trip.status == TripStatus.STOP) {
                            StopCard(
                                trip = trip,
                                onDone = ::finishTripDone,
                                onAgain = { Trips.again() },
                                onLater = {
                                    hideStopCard()
                                    Trips.later()
                                },
                                onFocus = {
                                    // Launch QuickAdd while the overlay window is still visible on Android 14+.
                                    Trips.toFocus()
                                    hideStopCard()
                                },
                            )
                        }
                    }
                }
            }
            addView(compose, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        runCatching {
            windowManager.addView(container, stopParams)
            stopView = container
        }
    }

    private fun hideStopCard() {
        stopView?.let { runCatching { windowManager.removeView(it) } }
        stopView = null
    }

    /**
     * With the screen off nobody can see the bubble, so its lifecycle drops to CREATED. That
     * pauses Compose's redrawing and every countdown clock (see rememberNow) until the screen
     * comes back, instead of ticking away in the dark.
     */
    private fun syncLifecycleWithScreen() {
        if (view == null || lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) return
        val screenOn = getSystemService(PowerManager::class.java).isInteractive
        lifecycleRegistry.currentState = if (screenOn) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
    }

    /**
     * Opens or folds the goal list. Only while it is open does the window ask to be told about
     * touches elsewhere on screen (to fold itself). Asking all the time would wake this app for
     * every single touch in every other app.
     */
    private fun showList(open: Boolean) {
        if (expanded == open) return
        expanded = open
        if (!::params.isInitialized) return
        val watch = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        params.flags = if (open) params.flags or watch else params.flags and watch.inv()
        applyNow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            Notifications.ID_GOALS,
            GoalsNotification.build(this),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (!FloatingGoals.shouldRun(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (view == null) addBubble()
        Trips.catchUp()
        if (Trips.state.value.status == TripStatus.STOP) showStopCard()
        syncLifecycleWithScreen()
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        animator?.cancel()
        hideStopCard()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        running = false
        // The notification was the service's; put it back as a normal one (or remove it).
        GoalsNotification.update(this)
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateMaxListHeight()
        if (view == null) return
        animator?.cancel()
        unfreezeSize()
        val w = width()
        val h = height()
        params.y = clampY(params.y, h)
        params.x = if (dock != Dock.NONE) 0 else params.x.coerceIn(0, (screenWidth() - w).coerceAtLeast(0))
        applyNow()
        save()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addBubble() {
        updateMaxListHeight()
        val (x, y) = FloatingGoals.position(this)
        dock = if (Trips.isActive) Dock.NONE else FloatingGoals.dock(this)
        anchorRight = when (dock) {
            Dock.RIGHT -> true
            Dock.LEFT -> false
            Dock.NONE -> FloatingGoals.anchoredRight(this)
        }
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not focusable, so typing and Back still go to the app underneath. No limits, so the
            // pill can be dragged partly off screen to tuck it away. (FLAG_WATCH_OUTSIDE_TOUCH is
            // added only while the list is open; see showList.)
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or (if (anchorRight) Gravity.RIGHT else Gravity.LEFT)
            this.x = if (dock != Dock.NONE) 0 else x.coerceAtLeast(0)
            this.y = clampY(y, dp(76f).toInt())
        }

        val compose = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingGoalsService)
            setViewTreeSavedStateRegistryOwner(this@FloatingGoalsService)
            // A tap anywhere else on screen folds the open list back into the pill.
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) showList(false)
                false
            }
            // Whenever the content changes size (pill, list, tab), keep it where it belongs.
            addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, _, oldRight, _ ->
                // Touches on the bubble are ours, not the system's edge-swipe Back gesture.
                v.systemGestureExclusionRects = listOf(android.graphics.Rect(0, 0, right - left, bottom - top))
                val w = right - left
                val h = bottom - top
                if (revealPending && w != oldRight - oldLeft) {
                    reveal(w)
                    return@addOnLayoutChangeListener
                }
                if (dragging || revealPending || animator?.isRunning == true) return@addOnLayoutChangeListener
                // Keep it fully on screen (and clear of system bars) after a size or orientation change.
                val targetX = if (dock != Dock.NONE) 0 else params.x.coerceIn(0, (screenWidth() - w).coerceAtLeast(0))
                val targetY = clampY(params.y, h)
                if (targetX != params.x || targetY != params.y) {
                    params.x = targetX
                    params.y = targetY
                    applyNow()
                    save()
                }
            }
            val root = this
            setContent {
                AnchorTheme {
                    // The window is sized to its content, but Compose doesn't tell the window when
                    // the content changes size (pill ↔ list ↔ edge tab); this asks for a new layout.
                    Box(Modifier.onSizeChanged { size ->
                        if (size.width != root.width || size.height != root.height) root.post { root.requestLayout() }
                    }) {
                    Bubble(
                        expanded = expanded,
                        dock = dock,
                        maxListHeight = maxListHeight,
                        onExpand = ::showList,
                        gestures = BubbleGestures(::dragStart, ::dragMove, ::dragEnd),
                        onUntuck = ::untuck,
                        onTuck = { tuck(if (leftX() + width() / 2 > screenWidth() / 2) Dock.RIGHT else Dock.LEFT) },
                        onFocus = { todo ->
                            showList(false)
                            QuickAddActivity.open(this@FloatingGoalsService, QuickAddActivity.Kind.FOCUS, todo.text, todo.id)
                        },
                        onAdd = { kind ->
                            showList(false)
                            QuickAddActivity.open(this@FloatingGoalsService, kind)
                        },
                        onOpenApp = {
                            showList(false)
                            open(Intent(this@FloatingGoalsService, MainActivity::class.java))
                        },
                        onTripDone = ::finishTripDone,
                        onTripLater = {
                            showList(false)
                            Trips.later()
                        },
                        onTripCancel = {
                            showList(false)
                            Trips.cancel()
                        },
                    )
                    }
                }
            }
        }
        windowManager.addView(compose, params)
        view = compose
    }

    private fun screenWidth() = windowManager.currentWindowMetrics.bounds.width()
    private fun screenHeight() = windowManager.currentWindowMetrics.bounds.height()

    private fun safeInsets() = windowManager.currentWindowMetrics.windowInsets
        .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())

    /** Keeps the bubble clear of the status bar and bottom gesture bar, especially in landscape. */
    private fun clampY(y: Int, h: Int): Int {
        val insets = safeInsets()
        val minY = insets.top
        val maxY = (screenHeight() - insets.bottom - h).coerceAtLeast(minY)
        return y.coerceIn(minY, maxY)
    }

    private fun updateMaxListHeight() {
        val insets = safeInsets()
        val usablePx = (screenHeight() - insets.top - insets.bottom).coerceAtLeast(0)
        // Stay clear of system bars in landscape, and cap height in portrait so the bubble scrolls
        // smoothly without swallowing the whole screen.
        maxListHeight = ((usablePx / resources.displayMetrics.density) - 24f).coerceIn(180f, 520f).dp
    }

    private var startLeft = 0
    private var startY = 0

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private var dragWidth = 0
    private var dragHeight = 0

    /**
     * The bubble's size. During a drag it is the size when the finger went down: the window can
     * change size while part of it is past a screen edge, and a right-anchored bubble's position
     * depends on its width, so a live width made size and position chase each other every frame.
     * That was the stutter on the right side only.
     */
    private fun width() = if (dragWidth > 0) dragWidth else view?.width ?: 0
    private fun height() = if (dragHeight > 0) dragHeight else view?.height ?: 0

    /** Back to the live size once the bubble has settled after a drag. */
    private fun unfreezeSize() {
        dragWidth = 0
        dragHeight = 0
    }

    /** The window's left edge on screen, whichever edge it is anchored to. */
    private fun leftX(): Int = if (anchorRight) screenWidth() - params.x - width() else params.x

    private fun setLeftX(left: Int) {
        params.x = if (anchorRight) screenWidth() - left - width() else left
    }

    /** Re-anchors to the other edge without moving anything on screen. */
    private fun setAnchor(right: Boolean) {
        if (right == anchorRight) return
        val left = leftX()
        anchorRight = right
        params.gravity = Gravity.TOP or (if (right) Gravity.RIGHT else Gravity.LEFT)
        setLeftX(left)
        applyNow()
    }

    private fun applyNow() {
        val v = view ?: return
        if (::params.isInitialized && v.isAttachedToWindow) windowManager.updateViewLayout(v, params)
    }

    /** Moves the window at most once per screen refresh, however many touch events arrive. */
    private fun applyNextFrame() {
        if (applyPending) return
        applyPending = true
        Choreographer.getInstance().postFrameCallback {
            applyPending = false
            applyNow()
        }
    }

    private fun dragStart() {
        animator?.cancel()
        dragWidth = view?.width ?: 0
        dragHeight = view?.height ?: 0
        dragging = true
        startLeft = leftX()
        startY = params.y
    }

    /** Positions come from where the drag started plus the finger's total movement: no drift. */
    private fun dragMove(dx: Float, dy: Float) {
        val v = view ?: return
        val w = width()
        params.y = clampY(startY + dy.toInt(), height())
        if (dock == Dock.NONE) {
            if (Trips.isActive) {
                // During an In&Out trip the pill cannot be tucked away: keep it on screen.
                setLeftX((startLeft + dx.toInt()).coerceIn(0, (screenWidth() - w).coerceAtLeast(0)))
            } else {
                // Up to 70% may go past an edge while dragging; letting go there tucks it away.
                setLeftX((startLeft + dx.toInt()).coerceIn(-(w * 0.7f).toInt(), screenWidth() - (w * 0.3f).toInt()))
            }
        }
        // A tucked tab only slides up and down its edge; pulling it inward brings the pill out (on release).
        applyNextFrame()
    }

    /**
     * After a drag. Tucked: pulled inward far or fast enough brings the pill back, else it stays.
     * Not tucked: pushed past an edge, or flicked toward the near edge, tucks it away; otherwise
     * it eases back fully on screen.
     */
    private fun dragEnd(dx: Float, @Suppress("UNUSED_PARAMETER") dy: Float, vx: Float) {
        dragging = false
        val v = view ?: return
        val fling = dp(700f)

        if (dock != Dock.NONE) {
            val inward = if (dock == Dock.LEFT) dx else -dx
            val inwardSpeed = if (dock == Dock.LEFT) vx else -vx
            if (inward > dp(40f) || inwardSpeed > fling) {
                untuck()
            } else {
                unfreezeSize()
                save()
            }
            return
        }

        val w = width()
        val sw = screenWidth()
        val left = leftX()
        val centre = left + w / 2
        when {
            !Trips.isActive && left < -w * 0.25f -> tuck(Dock.LEFT)
            !Trips.isActive && left + w > sw + w * 0.25f -> tuck(Dock.RIGHT)
            !Trips.isActive && vx < -fling && centre < sw * 0.6f -> tuck(Dock.LEFT)
            !Trips.isActive && vx > fling && centre > sw * 0.4f -> tuck(Dock.RIGHT)
            else -> {
                // Anchor to the nearer edge, then ease fully back on screen.
                setAnchor(centre > sw / 2)
                animateX(params.x.coerceIn(0, (sw - w).coerceAtLeast(0))) { save() }
            }
        }
    }

    /** Slides the pill off the edge, swaps it for the tab while hidden, slides the tab in. */
    private fun tuck(side: Dock) {
        val v = view ?: return
        showList(false)
        v.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
        setAnchor(side == Dock.RIGHT)
        animateX(-width(), slideIn = false) { swapWhileHidden(side) }
    }

    /** The reverse: the tab slides out, the pill takes its place off screen and slides in. */
    private fun untuck() {
        val v = view ?: return
        v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        animateX(-width(), slideIn = false) { swapWhileHidden(Dock.NONE) }
    }

    private fun swapWhileHidden(newDock: Dock) {
        val v = view ?: return
        v.alpha = 0f
        revealPending = true
        dock = newDock
        // If the new content happens to be the same width, no layout change will call reveal().
        v.postDelayed({ if (revealPending) reveal(width()) }, 250)
    }

    /** Places the freshly laid-out content just past the edge and slides it in. */
    private fun reveal(width: Int) {
        val v = view ?: return
        revealPending = false
        params.x = -width
        applyNow()
        animateX(0, slideIn = true) { save() }
        // Show it once the window is really off screen, i.e. on the animation's first frame.
        Choreographer.getInstance().postFrameCallback { v.alpha = 1f }
    }

    private fun animateX(to: Int, slideIn: Boolean = true, then: () -> Unit) {
        animator?.cancel()
        animator = ValueAnimator.ofInt(params.x, to).apply {
            duration = if (slideIn) 220 else 160
            interpolator = if (slideIn) DecelerateInterpolator(1.5f) else AccelerateInterpolator(1.5f)
            addUpdateListener {
                params.x = it.animatedValue as Int
                applyNow()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    cancelled = true
                }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (animator === animation) animator = null
                    if (!cancelled) {
                        unfreezeSize()
                        then()
                    }
                }
            })
            start()
        }
    }

    private fun save() = FloatingGoals.savePosition(this, params.x, params.y, dock, anchorRight)

    private fun open(intent: Intent) {
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    companion object {
        @Volatile
        var running = false
            private set
    }
}

/** Overlay root for the STOP card that swallows both Back key events and Android 13+ predictive back. */
private class BackBlockingContainer(context: Context) : FrameLayout(context) {
    private val backCallback = if (Build.VERSION.SDK_INT >= 33) {
        OnBackInvokedCallback { /* Intentionally ignored until user picks an action. */ }
    } else null

    init {
        isFocusableInTouchMode = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestFocus()
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                backCallback,
            )
        }
    }

    override fun onDetachedFromWindow() {
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(backCallback)
        }
        super.onDetachedFromWindow()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return true
        return super.dispatchKeyEvent(event)
    }
}

/** How often the tucked edge tab pulses red when the nudge setting is on (active screen-on time). */
private const val TUCK_NUDGE_INTERVAL_MS = 3 * 60_000L

@Composable
private fun Bubble(
    expanded: Boolean,
    dock: Dock,
    maxListHeight: Dp,
    onExpand: (Boolean) -> Unit,
    gestures: BubbleGestures,
    onUntuck: () -> Unit,
    onTuck: () -> Unit,
    onFocus: (Todo) -> Unit,
    onAdd: (QuickAddActivity.Kind) -> Unit,
    onOpenApp: () -> Unit,
    onTripDone: () -> Unit,
    onTripLater: () -> Unit,
    onTripCancel: () -> Unit,
) {
    val todos by TodoStore.todos.collectAsState()
    val timer by FocusTimer.state.collectAsState()
    val trip by Trips.state.collectAsState()
    val tripFlash by Trips.flashSerial.collectAsState()
    val nudge by AppPrefs.bubbleNudgeFlow.collectAsState()
    val active = todos.active()

    // Each piece below keeps its own clock (rememberNow), so a tick redraws only the piece that
    // shows the time, and each ticks only as often as what it shows can change.
    when {
        trip.isActive -> Box(Modifier.padding(8.dp)) {
            if (expanded) {
                TripMenu(trip, Modifier.bubbleDrag(gestures), onTripDone, onTripLater, onTripCancel)
            } else {
                TripPill(
                    trip = trip,
                    flashSerial = tripFlash,
                    touch = Modifier.bubbleDrag(gestures, onTap = { onExpand(true) }),
                    onDone = onTripDone,
                )
            }
        }
        dock != Dock.NONE -> EdgeTab(dock, active, timer, nudge, Modifier.bubbleDrag(gestures, onTap = onUntuck))
        // Padding leaves room for the shadow inside the overlay window.
        expanded -> Box(Modifier.padding(8.dp)) {
            ExpandedList(active, timer, maxListHeight, Modifier.bubbleDrag(gestures), onFocus, onAdd, onOpenApp, onTuck)
        }
        else -> Box(Modifier.padding(8.dp)) {
            Pill(
                active,
                timer,
                Modifier.bubbleDrag(gestures, onTap = { onExpand(true) }),
            )
        }
    }
}

/** The tucked-away bubble: a slim tab on the screen edge, still showing the count or the timer. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EdgeTab(side: Dock, active: List<Todo>, timer: FocusTimer.State, nudge: Boolean, touch: Modifier) {
    // The tab only shows a small disc, which changes visibly far less often than once a second.
    val now = rememberNow(
        active = timer.status == Status.RUNNING,
        periodMs = (timer.totalMs / 120).coerceIn(1_000, 30_000),
        alignTo = timer.endAt,
    )
    // Optional periodic red pulse while tucked with open goals and no timer running.
    // Pauses automatically when the screen turns off (lifecycle drops to CREATED).
    val shouldNudge = nudge && active.isNotEmpty() && !timer.isActive
    val pulse = remember { Animatable(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(shouldNudge, lifecycle) {
        pulse.snapTo(0f)
        if (!shouldNudge) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(TUCK_NUDGE_INTERVAL_MS)
                pulse.flashThreeTimes()
            }
        }
    }

    val ringing = timer.status == Status.RINGING
    val shape = if (side == Dock.LEFT) RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp)
    else RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
    val baseBg = if (ringing) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
    val baseFg = if (ringing) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        shape = shape,
        color = lerp(baseBg, MaterialTheme.colorScheme.error, pulse.value),
        contentColor = lerp(baseFg, MaterialTheme.colorScheme.onError, pulse.value),
        border = BorderStroke(1.dp, lerp(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.error, pulse.value)),
        modifier = touch.width(28.dp).height(76.dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                timer.isActive -> {
                    val fraction = if (timer.totalMs > 0) timer.remaining(now).toFloat() / timer.totalMs else 0f
                    TimerDisc(fraction, Modifier.size(18.dp), ready = timer.phase == FocusTimer.Phase.READY)
                }
                active.isNotEmpty() -> Text("${active.size}", style = MaterialTheme.typography.titleSmallEmphasized)
            }
            Icon(
                painterResource(R.drawable.ic_chevron),
                contentDescription = "Bring the bubble back",
                modifier = Modifier.size(20.dp).rotate(if (side == Dock.LEFT) 0f else 180f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Pill(active: List<Todo>, timer: FocusTimer.State, touch: Modifier) {
    val now = rememberNow(active = timer.status == Status.RUNNING, alignTo = timer.endAt)
    val top = active.firstOrNull()
    val warm = timer.status == Status.RINGING || timer.phase == FocusTimer.Phase.READY
    Surface(
        shape = RoundedCornerShape(50),
        color = if (warm) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (warm) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 6.dp,
        modifier = touch,
    ) {
        Row(
            Modifier.padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                timer.isActive -> {
                    val fraction = if (timer.totalMs > 0) timer.remaining(now).toFloat() / timer.totalMs else 0f
                    TimerDisc(fraction, Modifier.size(24.dp), ready = timer.phase == FocusTimer.Phase.READY)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            timer.isReadyRinging -> "Time to start"
                            timer.status == Status.RINGING -> "Time's up"
                            timer.status == Status.PAUSED -> "⏸ " + formatDuration(timer.remaining(now))
                            else -> formatDuration(timer.remaining(now))
                        },
                        style = MaterialTheme.typography.titleSmallEmphasized,
                    )
                    val label = timer.label ?: top?.text
                    if (label != null) {
                        Text(
                            "  ·  $label",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 160.dp),
                        )
                    }
                }
                top != null -> {
                    Box(
                        Modifier.size(24.dp).clip(MaterialShapes.Cookie9Sided.toShape())
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (top.reminderId != null) "🔁 ${top.text}" else top.text,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp),
                    )
                    if (active.size > 1) {
                        Text(
                            "  +${active.size - 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                else -> {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpandedList(
    active: List<Todo>,
    timer: FocusTimer.State,
    maxHeight: Dp,
    drag: Modifier,
    onFocus: (Todo) -> Unit,
    onAdd: (QuickAddActivity.Kind) -> Unit,
    onOpenApp: () -> Unit,
    onTuck: () -> Unit,
) {
    val view = LocalView.current
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        modifier = Modifier.width(300.dp).heightIn(max = maxHeight),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // The title is the drag handle, so the list and buttons stay easy to tap.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (active.isEmpty()) "To-do" else "To-do · ${active.size}",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = drag.weight(1f).padding(start = 8.dp, top = 12.dp, bottom = 12.dp),
                )
                IconButton(onClick = onOpenApp, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        painterResource(R.drawable.ic_open_in_new),
                        contentDescription = "Open Anchor",
                        modifier = Modifier.size(20.dp),
                    )
                }
                TextButton(onClick = onTuck, shapes = ButtonDefaults.shapes()) { Text("Tuck away") }
            }

            // Middle content scrolls when the viewport is short (e.g. landscape), keeping the
            // drag handle above and the Goal / Note / Open actions below always reachable.
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            if (timer.isActive) {
                val readyPhase = timer.phase == FocusTimer.Phase.READY
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (readyPhase) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (readyPhase) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val now = rememberNow(active = timer.status == Status.RUNNING, alignTo = timer.endAt)
                            val fraction = if (timer.totalMs > 0) timer.remaining(now).toFloat() / timer.totalMs else 0f
                            TimerDisc(fraction, Modifier.size(28.dp), ready = readyPhase)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                when {
                                    timer.isReadyRinging -> "Time to start!"
                                    timer.status == Status.RINGING -> "Time's up"
                                    else -> formatDuration(timer.remaining(now))
                                },
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                modifier = Modifier.weight(1f),
                            )
                            val compact = PaddingValues(horizontal = 8.dp)
                            when {
                                timer.isReadyRinging -> Unit
                                timer.status == Status.RINGING -> {
                                    TextButton(onClick = { FocusTimer.addMinutes(5) }, contentPadding = compact, shapes = ButtonDefaults.shapes()) { Text("+5") }
                                    TextButton(onClick = { FocusTimer.stop() }, contentPadding = compact, shapes = ButtonDefaults.shapes()) { Text("Done") }
                                }
                                timer.isGettingReady -> Unit
                                timer.status == Status.PAUSED -> {
                                    TextButton(onClick = { FocusTimer.addMinutes(5) }, contentPadding = compact, shapes = ButtonDefaults.shapes()) { Text("+5") }
                                    IconButton(onClick = { FocusTimer.resume() }, shapes = IconButtonDefaults.shapes()) {
                                        Icon(painterResource(R.drawable.ic_play), contentDescription = "Resume")
                                    }
                                }
                                else -> {
                                    TextButton(onClick = { FocusTimer.addMinutes(5) }, contentPadding = compact, shapes = ButtonDefaults.shapes()) { Text("+5") }
                                    IconButton(onClick = { FocusTimer.pause() }, shapes = IconButtonDefaults.shapes()) {
                                        Icon(painterResource(R.drawable.ic_pause), contentDescription = "Pause")
                                    }
                                }
                            }
                            if (timer.status != Status.RINGING || timer.isReadyRinging) {
                                IconButton(onClick = { FocusTimer.stop() }, shapes = IconButtonDefaults.shapes()) {
                                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Stop")
                                }
                            }
                        }
                        if (timer.isGettingReady || timer.isReadyRinging) {
                            Row(
                                Modifier.fillMaxWidth().padding(end = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                            ) {
                                TextButton(onClick = { FocusTimer.addMinutes(5) }, shapes = ButtonDefaults.shapes()) { Text("+5 min") }
                                TextButton(onClick = { FocusTimer.startNow() }, shapes = ButtonDefaults.shapes()) {
                                    Text(if (timer.isReadyRinging) "Start" else "Start now")
                                }
                            }
                        }
                    }
                }
            }

            if (active.isEmpty()) {
                Text(
                    "Nothing on the list.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                active.forEachIndexed { i, todo ->
                    Surface(
                        shape = segmentShape(i, active.size),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    TodoStore.setDone(todo.id, true)
                                }
                                .padding(start = 12.dp, end = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_circle),
                                contentDescription = "Mark done",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                todo.text,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                            )
                            IconButton(onClick = { onFocus(todo) }, shapes = IconButtonDefaults.shapes()) {
                                Icon(
                                    painterResource(R.drawable.ic_play),
                                    contentDescription = "Focus on this",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
            }

            Spacer(Modifier.height(8.dp))
            val actionPad = PaddingValues(horizontal = 8.dp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                    onClick = { onAdd(QuickAddActivity.Kind.TODO) },
                    contentPadding = actionPad,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add", maxLines = 1)
                }
                FilledTonalButton(
                    onClick = { onAdd(QuickAddActivity.Kind.NOTE) },
                    contentPadding = actionPad,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(painterResource(R.drawable.ic_note), contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Note", maxLines = 1)
                }
                FilledTonalButton(
                    onClick = { onAdd(QuickAddActivity.Kind.TRIP) },
                    contentPadding = actionPad,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(painterResource(R.drawable.ic_trip), contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("In&Out", maxLines = 1)
                }
            }
        }
    }
}
