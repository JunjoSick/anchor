package dev.junjo.anchor

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import dev.junjo.anchor.data.GoalStore
import dev.junjo.anchor.data.NoteStore
import dev.junjo.anchor.ui.AnchorTheme
import kotlinx.coroutines.delay

/**
 * Fast capture: a small sheet with the keyboard already open, over whatever you were doing.
 * Opened from the widget's +, the Quick Settings tile and the app icon's long-press menu.
 * A thought has to be caught in seconds, before it's gone.
 */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startAsNote = intent?.action == ACTION_NEW_NOTE
        setContent { AnchorTheme { QuickAddSheet(startAsNote, onClose = { finish() }) } }
    }

    companion object {
        const val ACTION_NEW_NOTE = "dev.junjo.anchor.NEW_NOTE"
    }
}

/** A goal is one line to tick off; a note can be anything, over several lines. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuickAddSheet(startAsNote: Boolean, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var asNote by remember { mutableStateOf(startAsNote) }
    var added by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focus.requestFocus()
        delay(150) // The window needs to have focus before the keyboard will open.
        keyboard?.show()
    }

    fun save(): Boolean {
        val ok = if (asNote) NoteStore.add(text) != null else GoalStore.add(text) != null
        if (ok) {
            text = ""
            added++
        }
        return ok
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (added == 0) (if (asNote) "New note" else "New goal") else "Saved $added ✓  Another one?",
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        modifier = Modifier.padding(start = 4.dp).weight(1f),
                    )
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = !asNote,
                            onClick = { asNote = false },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) { Text("Goal") }
                        SegmentedButton(
                            selected = asNote,
                            onClick = { asNote = true },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) { Text("Note") }
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    placeholder = { Text(if (asNote) "Write it down…" else "What do you want to get done?") },
                    singleLine = !asNote,
                    maxLines = if (asNote) 8 else 1,
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                    // A goal is saved with Enter; in a note Enter starts a new line.
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = if (asNote) ImeAction.Default else ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { save(); onClose() }),
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = { save() }, shapes = ButtonDefaults.shapes()) { Text("Save, add another") }
                    Button(onClick = { save(); onClose() }, shapes = ButtonDefaults.shapes()) { Text("Save") }
                }
            }
        }
    }
}
