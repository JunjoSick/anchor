package dev.junjo.anchor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
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
 * Type first, then choose what to do with it: Add (to-do), Focus, In&Out, Remind me… or Note.
 * Opened from ▶, a tile, a shortcut or the Reminders page, it starts directly in that mode.
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
                // Keyed per request so switching kinds never restores single-line scroll state into
                // a multi-line TextField (which crashes Compose).
                key(r.serial) {
                    var switchedToNote by rememberSaveable { mutableStateOf(false) }
                    var noteInitial by rememberSaveable { mutableStateOf(r.text.orEmpty()) }
                    val showNote = r.kind == Kind.NOTE || switchedToNote

                    Sheet(onClose = { finish() }) {
                        if (showNote) {
                            key("note") {
                                NoteContent(initialText = noteInitial, onClose = { finish() })
                            }
                        } else {
                            key("add") {
                                AddSheetContent(
                                    initialMode = when (r.kind) {
                                        Kind.FOCUS -> AddMode.FOCUS
                                        Kind.TRIP -> AddMode.TRIP
                                        Kind.REMIND -> AddMode.REMIND
                                        else -> AddMode.PICK
                                    },
                                    initialText = r.text.orEmpty(),
                                    todoId = r.todoId,
                                    onSwitchToNote = { typed ->
                                        noteInitial = typed
                                        switchedToNote = true
                                    },
                                    onClose = { finish() },
                                )
                            }
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
        // Focus has nothing to type, so the keyboard stays down; the others open with it up.
        window.setSoftInputMode(
            (if (kind == Kind.FOCUS) WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN else WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE) or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
        )
        if (kind == Kind.FOCUS) WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
        request = Request(kind, intent?.getStringExtra(EXTRA_TEXT), intent?.getStringExtra(EXTRA_GOAL), serial)
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

private enum class AddMode { PICK, FOCUS, TRIP, REMIND }

/** "Remind me in" quick picks, in minutes. */
private val REMIND_IN = listOf(10, 30, 60, 120)

/** The shared shell: a dimmed backdrop that closes on tap, and a sheet rising from the bottom. */
@Composable
private fun Sheet(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val noRipple = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = noRipple, indication = null, onClick = onClose)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .imePadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                // Taps on the sheet itself must not fall through and close it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .padding(16.dp),
                content = content,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SheetTitle(text: String, onBack: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
            }
        }
        Text(
            text,
            style = MaterialTheme.typography.titleLargeEmphasized,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = if (onBack != null) 0.dp else 4.dp),
        )
    }
}

/**
 * Type first, then choose: Add (to-do), Focus, In&Out, Remind me… or Note.
 * When opened directly in a verb mode (from ▶, a tile, a shortcut, or Reminders), shows that
 * verb straight away and lets Back return to the chooser or close.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun AddSheetContent(
    initialMode: AddMode,
    initialText: String,
    todoId: String?,
    onSwitchToNote: (String) -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var text by rememberSaveable { mutableStateOf(initialText) }
    var added by rememberSaveable { mutableIntStateOf(0) }
    val focusReq = remember { FocusRequester() }

    val canGoBack = mode != AddMode.PICK && initialMode == AddMode.PICK
    BackHandler(enabled = canGoBack) { mode = AddMode.PICK }

    // Keyboard stays up when typing a name; drops down when choosing Focus or after typing and picking a verb.
    val needsKeyboard = when (mode) {
        AddMode.PICK -> true
        AddMode.FOCUS -> false
        AddMode.TRIP, AddMode.REMIND -> text.isBlank() && initialMode == mode
    }
    LaunchedEffect(mode) {
        if (needsKeyboard) {
            focusReq.requestFocus()
            delay(150)
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }

    fun addTodo(): Boolean {
        if (text.isBlank()) return false
        TodoStore.add(text)
        text = ""
        added++
        return true
    }

    when (mode) {
        AddMode.PICK -> {
            SheetTitle(if (added > 0) "Saved $added ✓  Another one?" else "What's on your mind?")
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focusReq),
                placeholder = { Text("Reply to Sam, call the bank, check stories…") },
                singleLine = true,
                maxLines = 1,
                shape = RoundedCornerShape(28.dp),
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
                // Keyboard Done adds the to-do and keeps the sheet open for another.
                keyboardActions = KeyboardActions(onDone = { addTodo() }),
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { addTodo() },
                    enabled = text.isNotBlank(),
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.heightIn(min = 44.dp),
                ) { Text("Add") }
                VerbChip(R.drawable.ic_play, "Focus") {
                    keyboard?.hide()
                    mode = AddMode.FOCUS
                }
                VerbChip(R.drawable.ic_trip, "In&Out") {
                    if (text.isNotBlank()) keyboard?.hide()
                    mode = AddMode.TRIP
                }
                VerbChip(R.drawable.ic_bell, "Remind me…") {
                    if (text.isNotBlank()) keyboard?.hide()
                    mode = AddMode.REMIND
                }
                VerbChip(R.drawable.ic_note, "Note") {
                    onSwitchToNote(text)
                }
            }
            if (added > 0 && text.isBlank()) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose, shapes = ButtonDefaults.shapes()) { Text("Done") }
                }
            }
        }

        AddMode.FOCUS -> {
            FocusVerbContent(
                label = text.trim().takeIf { it.isNotEmpty() },
                todoId = todoId,
                onBack = if (canGoBack) { { mode = AddMode.PICK } } else null,
                onClose = onClose,
            )
        }

        AddMode.TRIP -> {
            TripVerbContent(
                text = text,
                onTextChange = { text = it },
                showInput = initialMode == AddMode.TRIP || text.isBlank(),
                focusRequester = focusReq,
                onBack = if (canGoBack) { { mode = AddMode.PICK } } else null,
                onClose = onClose,
            )
        }

        AddMode.REMIND -> {
            RemindVerbContent(
                text = text,
                onTextChange = { text = it },
                showInput = initialMode == AddMode.REMIND || text.isBlank(),
                focusRequester = focusReq,
                onBack = if (canGoBack) { { mode = AddMode.PICK } } else null,
                onClose = onClose,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VerbChip(icon: Int, label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = Modifier.heightIn(min = 44.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1)
    }
}

/** Direct multi-line note sheet (also reached by tapping Note from the add sheet). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NoteContent(initialText: String, onClose: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    var checklist by rememberSaveable { mutableStateOf(Checklist.isChecklist(initialText)) }
    var added by rememberSaveable { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focus.requestFocus()
        delay(150)
        keyboard?.show()
    }

    fun save(): Boolean {
        val toSave = if (checklist) Checklist.toChecklist(text) else text
        if (toSave.isBlank()) return false
        NoteStore.add(toSave)
        text = ""
        added++
        return true
    }

    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (added > 0) "Saved $added ✓  Another one?" else if (checklist) "Checklist" else "Note",
            style = MaterialTheme.typography.titleLargeEmphasized,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        Text(
            "Checklist",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Switch(checked = checklist, onCheckedChange = { checklist = it })
    }
    TextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        placeholder = {
            Text(if (checklist) "One item per line…" else "A thought, an idea, something to look up…")
        },
        singleLine = false,
        maxLines = 8,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Default,
        ),
    )
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { save() }, enabled = text.isNotBlank(), shapes = ButtonDefaults.shapes()) {
            Text("Save, add another")
        }
        Button(onClick = { if (save()) onClose() }, enabled = text.isNotBlank(), shapes = ButtonDefaults.shapes(), modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Save")
        }
    }
}

/** Remind me mode inside the add sheet: quick durations, clock time, Repeat…, and mute bell. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RemindVerbContent(
    text: String,
    onTextChange: (String) -> Unit,
    showInput: Boolean,
    focusRequester: FocusRequester,
    onBack: (() -> Unit)?,
    onClose: () -> Unit,
) {
    var inMinutes by rememberSaveable { mutableIntStateOf(30) }
    var atHour by rememberSaveable { mutableIntStateOf(-1) }
    var atMinute by rememberSaveable { mutableIntStateOf(0) }
    var alarm by rememberSaveable { mutableStateOf(true) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var repeatOpen by rememberSaveable { mutableStateOf(false) }
    val now = rememberNow(active = true, periodMs = 15_000L)

    fun at(base: Long = System.currentTimeMillis()): Long =
        if (atHour >= 0) Reminder.nextOccurrence(atHour, atMinute, Instant.ofEpochMilli(base).atZone(ZoneId.systemDefault()))
        else base + inMinutes * 60_000L

    fun save(): Boolean {
        if (text.isBlank()) return false
        ReminderStore.save(Reminder.once(text, at(), alarm))
        onClose()
        return true
    }

    if (picking) {
        RemindWhenDialog(
            initialMinutes = inMinutes,
            initialHour = atHour.takeIf { it >= 0 },
            initialMinute = atMinute,
            defaultToClock = atHour >= 0 || inMinutes in REMIND_IN,
            onDismiss = { picking = false },
            onPickClock = { h, m -> atHour = h; atMinute = m; picking = false },
            onPickDuration = { m -> atHour = -1; inMinutes = m; picking = false },
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

    SheetTitle(if (showInput) "Remind me" else text.trim(), onBack = onBack)
    if (showInput) {
        TextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            placeholder = { Text("What about?") },
            singleLine = true,
            maxLines = 1,
            shape = RoundedCornerShape(28.dp),
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
            keyboardActions = KeyboardActions(onDone = { save() }),
        )
        Spacer(Modifier.height(12.dp))
    }

    val pickedClockLabel = if (atHour >= 0) {
        LocalTime.of(atHour, atMinute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    } else null
    DurationRow(
        REMIND_IN,
        inMinutes,
        onSelect = { atHour = -1; inMinutes = it },
        onOther = { picking = true },
        prefix = "In ",
        customLabel = pickedClockLabel,
        otherIcon = R.drawable.ic_schedule,
        otherDescription = "Pick a time",
    )

    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconToggleButton(
            checked = !alarm,
            onCheckedChange = { muted -> alarm = !muted },
            shapes = IconButtonDefaults.toggleableShapes(),
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_bell_off),
                contentDescription = if (alarm) "Silence reminder" else "Ring like an alarm",
            )
        }
        TextButton(onClick = { repeatOpen = true }, shapes = ButtonDefaults.shapes()) {
            Text("Repeat…")
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = { save() }, enabled = text.isNotBlank(), shapes = ButtonDefaults.shapes(), modifier = Modifier.heightIn(min = 48.dp)) {
            val target = at(now)
            val isTomorrow = Instant.ofEpochMilli(target).atZone(ZoneId.systemDefault()).toLocalDate()
                .isAfter(LocalDate.now(ZoneId.systemDefault()))
            val whenLabel = if (isTomorrow) "tomorrow ${clockTime(target)}" else "at ${clockTime(target)}"
            Text(if (alarm) "Ring $whenLabel" else "Remind $whenLabel")
        }
    }
}

/** Focus mode inside the add sheet: pick a length, then start now or after a get-ready countdown. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FocusVerbContent(label: String?, todoId: String?, onBack: (() -> Unit)?, onClose: () -> Unit) {
    val ctx = LocalContext.current
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
        onClose()
    }

    when (picking) {
        "focus" -> DurationDialog("Focus for", focus, onDismiss = { picking = null }, onPick = { focus = it.coerceIn(AppPrefs.FOCUS_RANGE); picking = null })
        "ready" -> DurationDialog(
            "Start in",
            ready,
            onDismiss = { picking = null },
            onPick = { picking = null; go(it.coerceIn(AppPrefs.FOCUS_RANGE)) },
        )
    }

    SheetTitle(label ?: "Focus", onBack = onBack)
    DurationRow(AppPrefs.FOCUS_PRESETS, focus, onSelect = { focus = it }, onOther = { picking = "focus" }, prefix = "Focus for ")
    if (timer.isActive) {
        Text(
            "Replaces the timer that's running.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )
    }

    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        Box {
            SplitButtonLayout(
                leadingButton = {
                    SplitButtonDefaults.TonalLeadingButton(onClick = { go(ready) }) { Text("Start in ${durationText(ready)}") }
                },
                trailingButton = {
                    SplitButtonDefaults.TonalTrailingButton(checked = menu, onCheckedChange = { menu = it }) {
                        Icon(
                            painterResource(R.drawable.ic_expand_more),
                            contentDescription = "Start later",
                            modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize).rotate(if (menu) 180f else 0f),
                        )
                    }
                },
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                AppPrefs.READY_CHOICES.forEach { m ->
                    DropdownMenuItem(text = { Text("Start in ${durationText(m)}") }, onClick = { menu = false; go(m) })
                }
                DropdownMenuItem(text = { Text("Other…") }, onClick = { menu = false; picking = "ready" })
            }
        }
        Button(onClick = { go(0) }, shapes = ButtonDefaults.shapes()) { Text("Start") }
    }
}

/** In&Out mode inside the add sheet: recent chips, 1 / 2 / 5 min, Go. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun TripVerbContent(
    text: String,
    onTextChange: (String) -> Unit,
    showInput: Boolean,
    focusRequester: FocusRequester,
    onBack: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val recent by Trips.recent.collectAsState()
    val showTip = remember { !Trips.seenTip() }
    val canOverlay = FloatingGoals.canDraw(ctx)
    var minutes by rememberSaveable { mutableIntStateOf(Trips.lastMinutes()) }

    fun start(name: String, mins: Int) {
        if (!FloatingGoals.canDraw(ctx)) {
            FloatingGoals.openOverlayPermission(ctx)
            return
        }
        if (Trips.start(name, mins)) onClose()
    }

    SheetTitle(if (showInput) "In&Out" else text.trim(), onBack = onBack)
    if (showTip) {
        Text(
            "Start it, then open the app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
    }
    if (showInput) {
        TextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            placeholder = { Text("check Marco's stories") },
            singleLine = true,
            maxLines = 1,
            shape = RoundedCornerShape(28.dp),
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

    Spacer(Modifier.height(12.dp))
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
