package dev.junjo.anchor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.junjo.anchor.data.GoalStore
import dev.junjo.anchor.data.NoteStore
import dev.junjo.anchor.data.Reminder
import dev.junjo.anchor.data.ReminderStore
import dev.junjo.anchor.timer.FocusTimer
import dev.junjo.anchor.ui.AnchorTheme
import dev.junjo.anchor.ui.DurationDialog
import kotlinx.coroutines.delay

/**
 * Every way to add something is the same small sheet over whatever you were doing, opened from
 * the + menu, the bubble, the widget, the Quick Settings tiles and the app icon's long-press
 * menu. A thought has to be caught in seconds, before it's gone.
 *
 * Four kinds, picked before the sheet opens, so you never switch modes while typing: a goal (one
 * line, to tick off), a note (anything, kept), "remind me in…" (a one-off) and focus (a timer).
 */
class QuickAddActivity : ComponentActivity() {
    enum class Kind { GOAL, NOTE, REMIND, FOCUS }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val kind = when (intent?.action) {
            ACTION_NEW_NOTE -> Kind.NOTE
            ACTION_REMIND -> Kind.REMIND
            ACTION_FOCUS -> Kind.FOCUS
            else -> Kind.GOAL
        }
        // Focus has nothing to type, so the keyboard stays down.
        if (kind == Kind.FOCUS) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val text = intent?.getStringExtra(EXTRA_TEXT)
        val goalId = intent?.getStringExtra(EXTRA_GOAL)
        setContent {
            AnchorTheme {
                Sheet(onClose = { finish() }) {
                    if (kind == Kind.FOCUS) FocusContent(label = text, goalId = goalId, onClose = { finish() })
                    else AddContent(kind, text.orEmpty(), onClose = { finish() })
                }
            }
        }
    }

    /** Already open (single task) and asked for another kind: start over with the new one. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    companion object {
        const val ACTION_ADD_GOAL = "dev.junjo.anchor.ADD_GOAL"
        const val ACTION_NEW_NOTE = "dev.junjo.anchor.NEW_NOTE"
        const val ACTION_REMIND = "dev.junjo.anchor.REMIND"
        const val ACTION_FOCUS = "dev.junjo.anchor.FOCUS"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_GOAL = "goal"

        /** [text] fills the field, or for focus names what it's for; [goalId] ties a timer to a goal. */
        fun open(ctx: Context, kind: Kind, text: String? = null, goalId: String? = null) {
            val action = when (kind) {
                Kind.GOAL -> ACTION_ADD_GOAL
                Kind.NOTE -> ACTION_NEW_NOTE
                Kind.REMIND -> ACTION_REMIND
                Kind.FOCUS -> ACTION_FOCUS
            }
            ctx.startActivity(
                Intent(ctx, QuickAddActivity::class.java)
                    .setAction(action)
                    .apply { if (text != null) putExtra(EXTRA_TEXT, text) }
                    .apply { if (goalId != null) putExtra(EXTRA_GOAL, goalId) }
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/** "Remind me in" quick picks, in minutes. */
private val REMIND_IN = listOf(10, 30, 60, 120, 240)

/** The shared shell: a dimmed backdrop that closes on tap, and a sheet rising from the bottom. */
@Composable
private fun Sheet(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val noRipple = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = noRipple, indication = null, onClick = onClose)
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
            Column(Modifier.navigationBarsPadding().padding(16.dp), content = content)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLargeEmphasized,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun AddContent(kind: QuickAddActivity.Kind, initialText: String, onClose: () -> Unit) {
    val note = kind == QuickAddActivity.Kind.NOTE
    val remind = kind == QuickAddActivity.Kind.REMIND
    var text by remember { mutableStateOf(initialText) }
    var added by remember { mutableIntStateOf(0) }
    var inMinutes by remember { mutableIntStateOf(30) }
    var alarm by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focus.requestFocus()
        delay(150) // The window needs to have focus before the keyboard will open.
        keyboard?.show()
    }

    fun at() = System.currentTimeMillis() + inMinutes * 60_000L

    fun save(): Boolean {
        if (text.isBlank()) return false
        when (kind) {
            QuickAddActivity.Kind.NOTE -> NoteStore.add(text)
            QuickAddActivity.Kind.REMIND -> ReminderStore.save(Reminder.once(text, at(), alarm))
            else -> GoalStore.add(text)
        }
        text = ""
        added++
        return true
    }

    if (picking) {
        DurationDialog("Remind me in", inMinutes, onDismiss = { picking = false }, onPick = { inMinutes = it; picking = false })
    }

    SheetTitle(
        when {
            added > 0 -> "Saved $added ✓  Another one?"
            note -> "Note"
            remind -> "Remind me"
            else -> "Goal"
        },
    )
    TextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        placeholder = {
            Text(
                when {
                    note -> "A thought, an idea, something to look up…"
                    remind -> "What about?"
                    else -> "What do you want to get done?"
                },
            )
        },
        singleLine = !note,
        maxLines = if (note) 8 else 1,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        // In a note Enter starts a new line; elsewhere it saves.
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = if (note) ImeAction.Default else ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { if (save()) onClose() }),
    )

    if (remind) {
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            REMIND_IN.forEach { m ->
                FilterChip(selected = inMinutes == m, onClick = { inMinutes = m }, label = { Text("in ${durationText(m)}") })
            }
            val custom = inMinutes !in REMIND_IN
            FilterChip(
                selected = custom,
                onClick = { picking = true },
                label = { Text(if (custom) "in ${durationText(inMinutes)}" else "Other…") },
            )
        }
        Row(
            Modifier.fillMaxWidth().clickable { alarm = !alarm }.padding(start = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Ring like an alarm", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Switch(checked = alarm, onCheckedChange = { alarm = it })
        }
    }

    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        if (!remind) {
            TextButton(onClick = { save() }, enabled = text.isNotBlank(), shapes = ButtonDefaults.shapes()) {
                Text("Save, add another")
            }
        }
        Button(onClick = { if (save()) onClose() }, enabled = text.isNotBlank(), shapes = ButtonDefaults.shapes()) {
            Text(if (remind) "Remind me at ${clockTime(at())}" else "Save")
        }
    }
}

/**
 * Focus, the same shape as the other sheets: pick a length, then start now, or start in a few
 * minutes when you need to wrap something up first.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun FocusContent(label: String?, goalId: String?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val timer by FocusTimer.state.collectAsState()
    var focus by remember { mutableIntStateOf(AppPrefs.focusMinutes(ctx)) }
    var ready by remember { mutableIntStateOf(AppPrefs.readyMinutes(ctx)) }
    var picking by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }

    fun go(readyMinutes: Int) {
        AppPrefs.setFocusMinutes(ctx, focus)
        if (readyMinutes > 0) {
            AppPrefs.setReadyMinutes(ctx, readyMinutes)
            FocusTimer.startAfter(readyMinutes, focus, label, goalId)
        } else {
            FocusTimer.start(focus, label, goalId)
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
            presets = listOf(1, 2, 3, 5, 10, 15),
        )
    }

    SheetTitle(label ?: "Focus")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppPrefs.FOCUS_PRESETS.forEach { m ->
            FilterChip(selected = focus == m, onClick = { focus = m }, label = { Text(durationText(m)) })
        }
        val custom = focus !in AppPrefs.FOCUS_PRESETS
        FilterChip(
            selected = custom,
            onClick = { picking = "focus" },
            label = { Text(if (custom) durationText(focus) else "Other…") },
        )
    }
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
        // "Start in 5 min", with the arrow for another get-ready time.
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
