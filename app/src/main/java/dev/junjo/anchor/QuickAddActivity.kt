package dev.junjo.anchor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.junjo.anchor.data.GoalStore
import dev.junjo.anchor.data.NoteStore
import dev.junjo.anchor.data.Reminder
import dev.junjo.anchor.data.ReminderStore
import dev.junjo.anchor.ui.AnchorTheme
import dev.junjo.anchor.ui.DurationDialog
import kotlinx.coroutines.delay

/**
 * Fast capture: a small sheet with the keyboard already open, over whatever you were doing.
 * Opened from the + menu, the widget, the Quick Settings tiles and the app icon's long-press
 * menu. A thought has to be caught in seconds, before it's gone.
 *
 * Three kinds, each with its own way in, so you never have to decide twice:
 * a goal (one line, to tick off), a note (anything, kept), and "remind me in…" (a one-off).
 */
class QuickAddActivity : ComponentActivity() {
    enum class Kind { GOAL, NOTE, REMIND }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val kind = when (intent?.action) {
            ACTION_NEW_NOTE -> Kind.NOTE
            ACTION_REMIND -> Kind.REMIND
            else -> Kind.GOAL
        }
        val text = intent?.getStringExtra(EXTRA_TEXT).orEmpty()
        setContent { AnchorTheme { QuickAddSheet(kind, text, onClose = { finish() }) } }
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
        private const val EXTRA_TEXT = "text"

        fun open(ctx: Context, kind: Kind, text: String? = null) {
            val action = when (kind) {
                Kind.GOAL -> ACTION_ADD_GOAL
                Kind.NOTE -> ACTION_NEW_NOTE
                Kind.REMIND -> ACTION_REMIND
            }
            ctx.startActivity(
                Intent(ctx, QuickAddActivity::class.java)
                    .setAction(action)
                    .apply { if (text != null) putExtra(EXTRA_TEXT, text) }
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/** "Remind me in" quick picks, in minutes. */
private val REMIND_IN = listOf(10, 30, 60, 120, 240)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun QuickAddSheet(kind: QuickAddActivity.Kind, initialText: String, onClose: () -> Unit) {
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
            QuickAddActivity.Kind.GOAL -> GoalStore.add(text)
            QuickAddActivity.Kind.NOTE -> NoteStore.add(text)
            QuickAddActivity.Kind.REMIND -> ReminderStore.save(Reminder.once(text, at(), alarm))
        }
        text = ""
        added++
        return true
    }

    if (picking) {
        DurationDialog("Remind me in", inMinutes, onDismiss = { picking = false }, onPick = { inMinutes = it; picking = false })
    }

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
            Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                Text(
                    when {
                        added > 0 -> "Saved $added ✓  Another one?"
                        note -> "Write it down"
                        remind -> "Remind me"
                        else -> "New goal"
                    },
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Spacer(Modifier.height(12.dp))
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

                Spacer(Modifier.height(8.dp))
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
        }
    }
}
