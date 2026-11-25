package dev.junjo.anchor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import dev.junjo.anchor.data.Checklist
import dev.junjo.anchor.data.NoteStore
import dev.junjo.anchor.data.Reminder
import dev.junjo.anchor.data.ReminderStore
import dev.junjo.anchor.data.TodoStore
import dev.junjo.anchor.overlay.FloatingGoals
import dev.junjo.anchor.timer.FocusTimer
import dev.junjo.anchor.trip.TripPolicy
import dev.junjo.anchor.trip.Trips
import dev.junjo.anchor.ui.AnchorTheme
import dev.junjo.anchor.ui.DurationDialog
import dev.junjo.anchor.ui.DurationRow
import dev.junjo.anchor.ui.RemindWhenDialog
import dev.junjo.anchor.ui.ReminderSheet
import dev.junjo.anchor.ui.rememberNow
import dev.junjo.anchor.ui.shortDuration
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * One fast capture sheet over whatever you were doing, opened from +, the bubble, the widget,
 * Quick Settings tiles and the app icon's shortcuts.
 *
 * One morphing surface for To-do · Remind · Note · Focus: type once and switch freely without
 * losing text. In&Out (from the bubble, tile, shortcut or to-do long-press) and Focus tied to an
 * existing to-do (from ▶) open directly in their dedicated sheets without extra chrome.
 */
class QuickAddActivity : ComponentActivity() {
    enum class Kind { TODO, NOTE, REMIND, FOCUS, TRIP }

    /** What the sheet shows; [serial] counts requests, so each one starts with fresh state. */
    private class Request(val kind: Kind, val text: String?, val todoId: String?, val serial: Int)

    private var request by mutableStateOf<Request?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        show(intent, serial = savedInstanceState?.getInt(STATE_SERIAL, 0) ?: 0)
        setContent {
            AnchorTheme {
                val r = request ?: return@AnchorTheme
                // Keyed per request so each entry starts with fresh state and never mixes
                // single-line and multi-line TextField scroll state.
                key(r.serial) {
                    Sheet(onClose = { finish() }) {
                        when {
                            r.kind == Kind.TRIP -> TripSheetContent(
                                initialText = r.text.orEmpty(),
                                onClose = { finish() },
                            )
                            r.kind == Kind.FOCUS && r.todoId != null -> DedicatedFocusContent(
                                label = r.text,
                                todoId = r.todoId,
                                onClose = { finish() },
                            )
                            else -> CaptureSheetContent(
                                initialTab = when (r.kind) {
                                    Kind.REMIND -> CaptureTab.REMIND
                                    Kind.NOTE -> CaptureTab.NOTE
                                    Kind.FOCUS -> CaptureTab.FOCUS
                                    else -> CaptureTab.TODO
                                },
                                initialText = r.text.orEmpty(),
                                onClose = { finish() },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        request?.let { outState.putInt(STATE_SERIAL, it.serial) }
    }

    /** Already open (single task) and asked for another kind: swap the sheet in place. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        show(intent, serial = (request?.serial ?: 0) + 1)
    }

    private fun show(intent: Intent?, serial: Int) {
        val kind = when (intent?.action) {
            ACTION_NEW_NOTE -> Kind.NOTE
            ACTION_REMIND -> Kind.REMIND
            ACTION_FOCUS -> Kind.FOCUS
            ACTION_TRIP -> Kind.TRIP
            else -> Kind.TODO
        }
        val text = intent?.getStringExtra(EXTRA_TEXT)
        val hideIme = kind == Kind.FOCUS || (kind == Kind.TRIP && !text.isNullOrBlank())
        window.setSoftInputMode(
            (if (hideIme) WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN else WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE) or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
        )
        if (hideIme) WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
        request = Request(kind, text, intent?.getStringExtra(EXTRA_GOAL), serial)
    }

    companion object {
        const val ACTION_ADD_GOAL = "dev.junjo.anchor.ADD_GOAL"
        const val ACTION_NEW_NOTE = "dev.junjo.anchor.NEW_NOTE"
        const val ACTION_REMIND = "dev.junjo.anchor.REMIND"
        const val ACTION_FOCUS = "dev.junjo.anchor.FOCUS"
        const val ACTION_TRIP = "dev.junjo.anchor.TRIP"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_GOAL = "goal"
        private const val STATE_SERIAL = "serial"

        /** [text] fills the field, or for focus names what it's for; [todoId] ties a timer to a to-do. */
        fun open(ctx: Context, kind: Kind, text: String? = null, todoId: String? = null) {
            val action = when (kind) {
                Kind.TODO -> ACTION_ADD_GOAL
                Kind.NOTE -> ACTION_NEW_NOTE
                Kind.REMIND -> ACTION_REMIND
                Kind.FOCUS -> ACTION_FOCUS
                Kind.TRIP -> ACTION_TRIP
            }
            runCatching {
                ctx.startActivity(
                    Intent(ctx, QuickAddActivity::class.java)
                        .setAction(action)
                        .apply { if (text != null) putExtra(EXTRA_TEXT, text) }
                        .apply { if (todoId != null) putExtra(EXTRA_GOAL, todoId) }
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}

private enum class CaptureTab(val label: String, val icon: Int) {
    TODO("To-do", R.drawable.ic_circle),
    REMIND("Remind", R.drawable.ic_bell),
    NOTE("Note", R.drawable.ic_note),
    FOCUS("Focus", R.drawable.ic_play),
}

/** "Remind me in" quick picks, in minutes. */
private val REMIND_IN = listOf(10, 30, 60, 120)

/** The shared shell: a dimmed backdrop that closes on tap, and a spring-sized sheet rising from the bottom. */
@Composable
private fun Sheet(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val noRipple = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.38f))
            .clickable(interactionSource = noRipple, indication = null, onClick = onClose)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .imePadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                // Taps on the sheet itself must not fall through and close it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .animateContentSize(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                    .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 16.dp),
            ) {
                // Subtle pill handle anchoring the top of the sheet.
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 14.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                )
                content()
            }
        }
    }
}

/**
 * Expanding M3 Expressive 4-pill mode bar: the active mode smoothly widens to show its label in
 * its accent color, while the other three stay clean 48dp+ icon pills. Never wraps, never clips.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModeSwitcher(active: CaptureTab, onSelect: (CaptureTab) -> Unit) {
    val view = LocalView.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CaptureTab.entries.forEach { tab ->
            val isSelected = tab == active
            val weight by animateFloatAsState(
                targetValue = if (isSelected) 2.2f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "pill-weight",
            )
            val corner by animateDpAsState(
                targetValue = if (isSelected) 24.dp else 16.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "pill-corner",
            )
            val targetBg = when {
                !isSelected -> MaterialTheme.colorScheme.surfaceContainerHigh
                tab == CaptureTab.REMIND -> MaterialTheme.colorScheme.tertiary
                tab == CaptureTab.NOTE -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.primary
            }
            val targetFg = when {
                !isSelected -> MaterialTheme.colorScheme.onSurfaceVariant
                tab == CaptureTab.REMIND -> MaterialTheme.colorScheme.onTertiary
                tab == CaptureTab.NOTE -> MaterialTheme.colorScheme.onSecondary
                else -> MaterialTheme.colorScheme.onPrimary
            }
            val bg by animateColorAsState(targetBg, label = "pill-bg")
            val fg by animateColorAsState(targetFg, label = "pill-fg")

            Surface(
                shape = RoundedCornerShape(corner),
                color = bg,
                contentColor = fg,
                modifier = Modifier
                    .weight(weight)
                    .height(46.dp)
                    .semantics {
                        role = Role.Tab
                        selected = isSelected
                        contentDescription = tab.label
                    }
                    .clip(RoundedCornerShape(corner))
                    .clickable {
                        if (!isSelected) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onSelect(tab)
                        }
                    },
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(tab.icon),
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                    )
                    AnimatedVisibility(
                        visible = isSelected,
                        enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)),
                        exit = fadeOut(spring(stiffness = Spring.StiffnessHigh)),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(7.dp))
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.titleSmallEmphasized,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The unified morphing capture sheet:
 * - Top: expanding 4-pill switcher (To-do · Remind · Note · Focus)
 * - Middle: one living input field whose text stays intact across mode switches, plus the active
 *   mode's controls gliding smoothly underneath
 * - Bottom: consistent right-aligned primary CTA (saves & closes), with rapid-capture / mode
 *   controls cleanly on the left.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CaptureSheetContent(
    initialTab: CaptureTab,
    initialText: String,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val keyboard = LocalSoftwareKeyboardController.current

    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var text by rememberSaveable { mutableStateOf(initialText) }
    var added by rememberSaveable { mutableIntStateOf(0) }

    // Note state
    var checklist by rememberSaveable { mutableStateOf(Checklist.isChecklist(initialText)) }

    // Remind state
    var inMinutes by rememberSaveable { mutableIntStateOf(30) }
    var atHour by rememberSaveable { mutableIntStateOf(-1) }
    var atMinute by rememberSaveable { mutableIntStateOf(0) }
    var alarm by rememberSaveable { mutableStateOf(true) }
    var remindPicking by rememberSaveable { mutableStateOf(false) }
    var repeatOpen by rememberSaveable { mutableStateOf(false) }

    // Focus state
    val timer by FocusTimer.state.collectAsState()
    var focusMinutes by rememberSaveable { mutableIntStateOf(AppPrefs.focusMinutes(ctx)) }
    var readyMinutes by rememberSaveable { mutableIntStateOf(AppPrefs.readyMinutes(ctx)) }
    var focusPicking by rememberSaveable { mutableStateOf<String?>(null) }
    var startMenuOpen by rememberSaveable { mutableStateOf(false) }

    val lineFocus = remember { FocusRequester() }
    val noteFocus = remember { FocusRequester() }
    val now = rememberNow(active = tab == CaptureTab.REMIND, periodMs = 15_000L)

    // Focus/keyboard choreography: open up for typing, tuck away when switching with text ready to schedule or focus.
    LaunchedEffect(tab) {
        when (tab) {
            CaptureTab.TODO -> {
                runCatching { lineFocus.requestFocus() }
                delay(120)
                keyboard?.show()
            }
            CaptureTab.NOTE -> {
                runCatching { noteFocus.requestFocus() }
                delay(120)
                keyboard?.show()
            }
            CaptureTab.REMIND -> {
                if (text.isBlank()) {
                    runCatching { lineFocus.requestFocus() }
                    delay(120)
                    keyboard?.show()
                } else {
                    keyboard?.hide()
                }
            }
            CaptureTab.FOCUS -> keyboard?.hide()
        }
    }

    fun remindAt(base: Long = System.currentTimeMillis()): Long =
        if (atHour >= 0) Reminder.nextOccurrence(atHour, atMinute, Instant.ofEpochMilli(base).atZone(ZoneId.systemDefault()))
        else base + inMinutes * 60_000L

    fun saveTodo(closeAfter: Boolean) {
        if (text.isBlank()) return
        TodoStore.add(text)
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        if (closeAfter) {
            onClose()
        } else {
            text = ""
            added++
        }
    }

    fun saveNote(closeAfter: Boolean) {
        val toSave = if (checklist) Checklist.toChecklist(text) else text
        if (toSave.isBlank()) return
        NoteStore.add(toSave)
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        if (closeAfter) {
            onClose()
        } else {
            text = ""
            added++
        }
    }

    fun saveReminder() {
        if (text.isBlank()) return
        ReminderStore.save(Reminder.once(text, remindAt(), alarm))
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        onClose()
    }

    fun startFocus(delayMinutes: Int) {
        AppPrefs.setFocusMinutes(ctx, focusMinutes)
        val label = text.trim().takeIf { it.isNotEmpty() }
        if (delayMinutes > 0) {
            AppPrefs.setReadyMinutes(ctx, delayMinutes)
            FocusTimer.startAfter(delayMinutes, focusMinutes, label, todoId = null)
        } else {
            FocusTimer.start(focusMinutes, label, todoId = null)
        }
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        onClose()
    }

    // Dialogs & sub-sheets
    if (remindPicking) {
        RemindWhenDialog(
            initialMinutes = inMinutes,
            initialHour = atHour.takeIf { it >= 0 },
            initialMinute = atMinute,
            defaultToClock = atHour >= 0 || inMinutes in REMIND_IN,
            onDismiss = { remindPicking = false },
            onPickClock = { h, m -> atHour = h; atMinute = m; remindPicking = false },
            onPickDuration = { m -> atHour = -1; inMinutes = m; remindPicking = false },
        )
    }
    if (repeatOpen) {
        ReminderSheet(
            initial = null,
            initialText = text.trim(),
            startOnRepeat = true,
            onDismiss = { repeatOpen = false; onClose() },
        )
    }
    when (focusPicking) {
        "focus" -> DurationDialog(
            "Focus for",
            focusMinutes,
            onDismiss = { focusPicking = null },
            onPick = { focusMinutes = it.coerceIn(AppPrefs.FOCUS_RANGE); focusPicking = null },
        )
        "ready" -> DurationDialog(
            "Start in",
            readyMinutes,
            onDismiss = { focusPicking = null },
            onPick = { focusPicking = null; startFocus(it.coerceIn(AppPrefs.FOCUS_RANGE)) },
        )
    }

    // 1. Expanding 4-pill switcher
    ModeSwitcher(active = tab, onSelect = { tab = it })
    Spacer(Modifier.height(14.dp))

    // 2. Living input surface (separate keys for single-line vs multi-line avoid Compose scroll-state crashes)
    val fieldColors = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
    if (tab == CaptureTab.NOTE) {
        key("multiline-input") {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().focusRequester(noteFocus),
                placeholder = {
                    Text(if (checklist) "One item per line…" else "Write it down…")
                },
                singleLine = false,
                minLines = 3,
                maxLines = 8,
                shape = RoundedCornerShape(24.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default,
                ),
            )
        }
    } else {
        key("singleline-input") {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().focusRequester(lineFocus),
                placeholder = {
                    Text(
                        when (tab) {
                            CaptureTab.TODO -> "What needs doing?"
                            CaptureTab.REMIND -> "Remind me about…"
                            CaptureTab.FOCUS -> "Focusing on… (optional)"
                            CaptureTab.NOTE -> ""
                        },
                    )
                },
                singleLine = true,
                maxLines = 1,
                shape = RoundedCornerShape(24.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        when (tab) {
                            CaptureTab.TODO -> saveTodo(closeAfter = false)
                            CaptureTab.REMIND -> saveReminder()
                            CaptureTab.FOCUS -> keyboard?.hide()
                            CaptureTab.NOTE -> Unit
                        }
                    },
                ),
            )
        }
    }

    // 3. Mode-specific options gliding smoothly under the input
    AnimatedVisibility(
        visible = tab == CaptureTab.REMIND || tab == CaptureTab.FOCUS,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(spring(stiffness = Spring.StiffnessHigh)) + shrinkVertically(spring(stiffness = Spring.StiffnessMedium)),
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(spring(stiffness = Spring.StiffnessMedium)) togetherWith fadeOut(spring(stiffness = Spring.StiffnessHigh)) },
                label = "mode-controls",
            ) { shownTab ->
                when (shownTab) {
                    CaptureTab.REMIND -> {
                        val pickedClockLabel = if (atHour >= 0) {
                            LocalTime.of(atHour, atMinute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                        } else null
                        DurationRow(
                            REMIND_IN,
                            inMinutes,
                            onSelect = { atHour = -1; inMinutes = it },
                            onOther = { remindPicking = true },
                            prefix = "In ",
                            customLabel = pickedClockLabel,
                            otherIcon = R.drawable.ic_schedule,
                            otherDescription = "Pick a time",
                        )
                    }
                    CaptureTab.FOCUS -> {
                        Column(Modifier.fillMaxWidth()) {
                            DurationRow(
                                AppPrefs.FOCUS_PRESETS,
                                focusMinutes,
                                onSelect = { focusMinutes = it },
                                onOther = { focusPicking = "focus" },
                                prefix = "Focus for ",
                            )
                            if (timer.isActive) {
                                Text(
                                    "Replaces the timer that's running.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                                )
                            }
                        }
                    }
                    else -> Spacer(Modifier.height(0.dp))
                }
            }
        }
    }

    // 4. Consistent bottom thumb-zone action bar
    Spacer(Modifier.height(14.dp))
    val ctaColors = when (tab) {
        CaptureTab.REMIND -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
        )
        CaptureTab.NOTE -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
        )
        else -> ButtonDefaults.buttonColors()
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (tab) {
            CaptureTab.TODO -> {
                SavedCountPill(added)
                Spacer(Modifier.weight(1f))
                if (text.isNotBlank()) {
                    TextButton(
                        onClick = { saveTodo(closeAfter = false) },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("+ Another") }
                    Button(
                        onClick = { saveTodo(closeAfter = true) },
                        colors = ctaColors,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Add") }
                } else if (added > 0) {
                    Button(
                        onClick = onClose,
                        colors = ctaColors,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Done") }
                } else {
                    Button(
                        onClick = {},
                        enabled = false,
                        colors = ctaColors,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Add") }
                }
            }

            CaptureTab.REMIND -> {
                FilledTonalIconToggleButton(
                    checked = !alarm,
                    onCheckedChange = { muted ->
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        alarm = !muted
                    },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_bell_off),
                        contentDescription = if (alarm) "Silence reminder" else "Ring like an alarm",
                    )
                }
                TextButton(
                    onClick = { repeatOpen = true },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Repeat…") }
                Spacer(Modifier.weight(1f))
                val target = remindAt(now)
                val isTomorrow = Instant.ofEpochMilli(target).atZone(ZoneId.systemDefault()).toLocalDate()
                    .isAfter(LocalDate.now(ZoneId.systemDefault()))
                val whenLabel = if (isTomorrow) "tomorrow ${clockTime(target)}" else "at ${clockTime(target)}"
                Button(
                    onClick = { saveReminder() },
                    enabled = text.isNotBlank(),
                    colors = ctaColors,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(if (alarm) "Ring $whenLabel" else "Remind $whenLabel", maxLines = 1)
                }
            }

            CaptureTab.NOTE -> {
                val checkBg by animateColorAsState(
                    if (checklist) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    label = "checklist-bg",
                )
                val checkFg by animateColorAsState(
                    if (checklist) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "checklist-fg",
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = checkBg,
                    contentColor = checkFg,
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(50))
                        .semantics { role = Role.Checkbox; selected = checklist }
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            checklist = !checklist
                        },
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(if (checklist) R.drawable.ic_check_circle else R.drawable.ic_circle),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Checklist", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
                SavedCountPill(added)
                Spacer(Modifier.weight(1f))
                if (text.isNotBlank()) {
                    TextButton(
                        onClick = { saveNote(closeAfter = false) },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("+ Another") }
                    Button(
                        onClick = { saveNote(closeAfter = true) },
                        colors = ctaColors,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Save") }
                } else if (added > 0) {
                    Button(
                        onClick = onClose,
                        colors = ctaColors,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Done") }
                } else {
                    Button(
                        onClick = {},
                        enabled = false,
                        colors = ctaColors,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Save") }
                }
            }

            CaptureTab.FOCUS -> {
                Spacer(Modifier.weight(1f))
                FocusStartActions(
                    readyMinutes = readyMinutes,
                    menuOpen = startMenuOpen,
                    onMenuChange = { startMenuOpen = it },
                    onStart = { startFocus(it) },
                    onPickReady = { focusPicking = "ready" },
                )
            }
        }
    }
}

/** Gratifying compact counter pill shown while rapid-fire adding multiple to-dos or notes. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SavedCountPill(count: Int) {
    AnimatedVisibility(
        visible = count > 0,
        enter = fadeIn() + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)),
        exit = fadeOut(),
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("$count", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Split "Start in X ▾" + primary "Start" buttons shared by unified Focus mode and dedicated ▶ Focus. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FocusStartActions(
    readyMinutes: Int,
    menuOpen: Boolean,
    onMenuChange: (Boolean) -> Unit,
    onStart: (readyMinutes: Int) -> Unit,
    onPickReady: () -> Unit,
) {
    Box {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.TonalLeadingButton(onClick = { onStart(readyMinutes) }) {
                    Text("Start in ${durationText(readyMinutes)}")
                }
            },
            trailingButton = {
                SplitButtonDefaults.TonalTrailingButton(checked = menuOpen, onCheckedChange = onMenuChange) {
                    Icon(
                        painterResource(R.drawable.ic_expand_more),
                        contentDescription = "Start later",
                        modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize).rotate(if (menuOpen) 180f else 0f),
                    )
                }
            },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuChange(false) }) {
            AppPrefs.READY_CHOICES.forEach { m ->
                DropdownMenuItem(text = { Text("Start in ${durationText(m)}") }, onClick = { onMenuChange(false); onStart(m) })
            }
            DropdownMenuItem(text = { Text("Other…") }, onClick = { onMenuChange(false); onPickReady() })
        }
    }
    Button(
        onClick = { onStart(0) },
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.heightIn(min = 48.dp),
    ) { Text("Start") }
}

/** Dedicated Focus sheet opened from ▶ on a specific to-do: calm, focused, no mode switcher. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DedicatedFocusContent(label: String?, todoId: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val timer by FocusTimer.state.collectAsState()
    var focus by rememberSaveable { mutableIntStateOf(AppPrefs.focusMinutes(ctx)) }
    var ready by rememberSaveable { mutableIntStateOf(AppPrefs.readyMinutes(ctx)) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by rememberSaveable { mutableStateOf(false) }

    fun go(readyMinutes: Int) {
        AppPrefs.setFocusMinutes(ctx, focus)
        if (readyMinutes > 0) {
            AppPrefs.setReadyMinutes(ctx, readyMinutes)
            FocusTimer.startAfter(readyMinutes, focus, label, todoId)
        } else {
            FocusTimer.start(focus, label, todoId)
        }
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        onClose()
    }

    when (picking) {
        "focus" -> DurationDialog(
            "Focus for",
            focus,
            onDismiss = { picking = null },
            onPick = { focus = it.coerceIn(AppPrefs.FOCUS_RANGE); picking = null },
        )
        "ready" -> DurationDialog(
            "Start in",
            ready,
            onDismiss = { picking = null },
            onPick = { picking = null; go(it.coerceIn(AppPrefs.FOCUS_RANGE)) },
        )
    }

    Text(
        label ?: "Focus",
        style = MaterialTheme.typography.titleLargeEmphasized,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
    )
    DurationRow(AppPrefs.FOCUS_PRESETS, focus, onSelect = { focus = it }, onOther = { picking = "focus" }, prefix = "Focus for ")
    if (timer.isActive) {
        Text(
            "Replaces the timer that's running.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        )
    }
    Spacer(Modifier.height(14.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FocusStartActions(
            readyMinutes = ready,
            menuOpen = menu,
            onMenuChange = { menu = it },
            onStart = { go(it) },
            onPickReady = { picking = "ready" },
        )
    }
}

/** Dedicated In&Out sheet opened from the floating bubble, Quick Settings tile, shortcut or to-do long-press. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun TripSheetContent(initialText: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val keyboard = LocalSoftwareKeyboardController.current
    val recent by Trips.recent.collectAsState()
    val showTip = remember { !Trips.seenTip() }
    val canOverlay = FloatingGoals.canDraw(ctx)
    var text by rememberSaveable { mutableStateOf(initialText) }
    var minutes by rememberSaveable { mutableIntStateOf(Trips.lastMinutes()) }
    val focusReq = remember { FocusRequester() }
    val showInput = initialText.isBlank()

    LaunchedEffect(Unit) {
        if (showInput) {
            focusReq.requestFocus()
            delay(120)
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }

    fun start(name: String, mins: Int) {
        if (!FloatingGoals.canDraw(ctx)) {
            FloatingGoals.openOverlayPermission(ctx)
            return
        }
        if (Trips.start(name, mins)) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            onClose()
        }
    }

    Text(
        if (showInput) "In&Out" else text.trim(),
        style = MaterialTheme.typography.titleLargeEmphasized,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
    if (showTip) {
        Text(
            "Start it, then open the app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )
    }
    if (showInput) {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focusReq),
            placeholder = { Text("check Marco's stories") },
            singleLine = true,
            maxLines = 1,
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { start(text, minutes) }),
        )
    }

    if (recent.isNotEmpty() && showInput) {
        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            recent.forEach { item ->
                AssistChip(
                    onClick = { start(item.name, item.minutes) },
                    label = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    val presets = TripPolicy.PRESET_MINUTES
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        presets.forEachIndexed { i, m ->
            ToggleButton(
                checked = minutes == m,
                onCheckedChange = { minutes = m },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    presets.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.RadioButton; contentDescription = shortDuration(m) },
            ) {
                Text(shortDuration(m), maxLines = 1, overflow = TextOverflow.Clip)
            }
        }
    }

    if (!canOverlay) {
        Text(
            "Needs “Display over other apps” so the pill and STOP card can show over other apps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
    }

    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        if (!canOverlay) {
            Button(
                onClick = { FloatingGoals.openOverlayPermission(ctx) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Allow overlay") }
        } else {
            Button(
                onClick = { start(text, minutes) },
                enabled = text.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Go") }
        }
    }
}
