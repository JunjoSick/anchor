package dev.junjo.anchor.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.junjo.anchor.QuickAddActivity
import dev.junjo.anchor.R
import dev.junjo.anchor.data.Checklist
import dev.junjo.anchor.data.GoalStore
import dev.junjo.anchor.data.Note
import dev.junjo.anchor.data.NoteLine
import dev.junjo.anchor.data.NoteStore
import dev.junjo.anchor.data.visible
import kotlinx.coroutines.launch

/** All notes, newest first, opened from "All notes" on the main screen. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val allNotes by NoteStore.notes.collectAsStateWithLifecycle()
    val notes = remember(allNotes) { allNotes.visible() }
    var editingNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun offerUndo(message: String, undo: () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Notes") },
                subtitle = { Text(if (notes.isEmpty()) "Nothing kept" else if (notes.size == 1) "1 note" else "${notes.size} notes") },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { QuickAddActivity.open(ctx, QuickAddActivity.Kind.NOTE) }) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = "Note")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (notes.isEmpty()) {
                item(key = "notes-empty") {
                    Text(
                        "Thoughts, ideas, checklists — kept here without bothering you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.animateItem().padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
            }
            itemsIndexed(notes, key = { _, n -> n.id }) { i, note ->
                NoteRow(note, segmentShape(i, notes.size), Modifier.animateItem()) { editingNoteId = note.id }
            }
            item(key = "end") { Spacer(Modifier.height(8.dp)) }
        }
    }

    val editingNote = editingNoteId?.let { id -> allNotes.firstOrNull { it.id == id && !it.deleted } }
    editingNote?.let { note ->
        key(note.id) {
            NoteEditorSheet(
                note = note,
                onMakeTodo = { text ->
                    if (text.trim() != note.text) NoteStore.edit(note.id, text)
                    val first = Checklist.firstCleanLine(text)
                    val goal = first?.let { GoalStore.add(it) }
                    editingNoteId = null
                    offerUndo("Added to your list") { goal?.let { GoalStore.delete(it.id) } }
                },
                onDelete = {
                    NoteStore.delete(note.id)
                    editingNoteId = null
                    offerUndo("Note deleted") { NoteStore.restore(note.id) }
                },
                onClose = { text ->
                    if (text.trim() != note.text) NoteStore.edit(note.id, text)
                    editingNoteId = null
                },
            )
        }
    }
}

/**
 * Notes sit under the list, quieter: a plain note shows up to 4 lines; a checklist shows its first
 * line and how many items are left ("3 of 8 left").
 */
@Composable
internal fun NoteRow(note: Note, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier, onOpen: () -> Unit) {
    val summary = remember(note.text) { Checklist.summary(note.text) }
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        if (summary != null) {
            Column(Modifier.clickable(onClick = onOpen).padding(horizontal = 20.dp, vertical = 14.dp).fillMaxWidth()) {
                Text(
                    summary.headline,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    summary.countText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        } else {
            Text(
                note.text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onOpen).padding(horizontal = 20.dp, vertical = 14.dp).fillMaxWidth(),
            )
        }
    }
}

/**
 * Note editor sheet supporting both plain text notes and interactive checklists.
 * Flipping the Checklist switch converts lines in place; closing any way saves the changes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NoteEditorSheet(
    note: Note,
    onMakeTodo: (text: String) -> Unit,
    onDelete: () -> Unit,
    onClose: (text: String) -> Unit,
) {
    val view = LocalView.current
    var text by rememberSaveable { mutableStateOf(note.text) }
    var checklistMode by rememberSaveable { mutableStateOf(Checklist.isChecklist(note.text)) }
    var newItem by rememberSaveable { mutableStateOf("") }

    fun currentText(): String {
        if (!checklistMode) return text
        val lines = Checklist.parse(text)
        val withPending = if (newItem.isNotBlank()) Checklist.addItem(lines, newItem) else lines
        return Checklist.format(withPending)
    }

    ModalBottomSheet(
        onDismissRequest = { onClose(currentText()) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (checklistMode) "Checklist" else "Note",
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Checklist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = checklistMode,
                    onCheckedChange = { on ->
                        val snapshot = currentText()
                        checklistMode = on
                        text = if (on) Checklist.toChecklist(snapshot) else Checklist.toPlainText(snapshot)
                        newItem = ""
                    },
                )
            }

            if (checklistMode) {
                val lines = remember(text) { Checklist.parse(text) }
                val hasTicked = lines.any { it is NoteLine.Item && it.checked }

                lines.forEachIndexed { idx, line ->
                    when (line) {
                        is NoteLine.Plain -> {
                            BasicTextField(
                                value = line.text,
                                onValueChange = { updated ->
                                    val copy = lines.toMutableList()
                                    copy[idx] = NoteLine.Plain(updated.replace('\n', ' '))
                                    text = Checklist.formatEditing(copy)
                                },
                                textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                            )
                        }
                        is NoteLine.Item -> {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = line.checked,
                                    onCheckedChange = {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        text = Checklist.format(Checklist.toggle(lines, idx))
                                    },
                                )
                                BasicTextField(
                                    value = line.text,
                                    onValueChange = { updated ->
                                        val copy = lines.toMutableList()
                                        copy[idx] = line.copy(text = updated.replace('\n', ' '))
                                        text = Checklist.formatEditing(copy)
                                    },
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = if (line.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                        textDecoration = if (line.checked) TextDecoration.LineThrough else TextDecoration.None,
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextField(
                    value = newItem,
                    onValueChange = { newItem = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Add item") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (newItem.isNotBlank()) {
                                text = Checklist.format(Checklist.addItem(lines, newItem))
                                newItem = ""
                            }
                        },
                    ),
                    trailingIcon = {
                        if (newItem.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    text = Checklist.format(Checklist.addItem(lines, newItem))
                                    newItem = ""
                                },
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(painterResource(R.drawable.ic_add), contentDescription = "Add item")
                            }
                        }
                    },
                )

                if (hasTicked) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = { text = Checklist.format(Checklist.clearTicked(lines)) },
                            shapes = ButtonDefaults.shapes(),
                        ) { Text("Clear ticked") }
                    }
                }
            } else {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 3,
                    maxLines = 14,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default,
                    ),
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDelete, shapes = ButtonDefaults.shapes()) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                val full = currentText()
                FilledTonalButton(
                    onClick = { onMakeTodo(full) },
                    enabled = Checklist.firstCleanLine(full) != null,
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Make it a to-do") }
                Spacer(Modifier.size(8.dp))
                Button(onClick = { onClose(full) }, shapes = ButtonDefaults.shapes()) { Text("Done") }
            }
        }
    }
}
