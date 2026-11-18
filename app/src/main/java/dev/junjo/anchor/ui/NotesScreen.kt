package dev.junjo.anchor.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.junjo.anchor.R
import dev.junjo.anchor.data.GoalStore
import dev.junjo.anchor.data.Note
import dev.junjo.anchor.data.NoteStore
import dev.junjo.anchor.data.visible
import kotlinx.coroutines.launch

/**
 * Somewhere to put things that aren't tasks: thoughts, ideas, things to look up. Writing them
 * down gets them out of your head without crowding the goal list, and one that turns out to be
 * a task can become a goal with one tap.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotesScreen(bottomBar: @Composable () -> Unit) {
    val all by NoteStore.notes.collectAsStateWithLifecycle()
    val notes = remember(all) { all.visible() }
    var editing by remember { mutableStateOf<Note?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun offerUndo(message: String, undo: () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(message, "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) undo()
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Notes") },
                subtitle = { Text(if (notes.isEmpty()) "Not tasks, just things to keep" else "${notes.size} kept") },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "write") { WriteNote(Modifier.padding(bottom = 12.dp)) }
            if (notes.isEmpty()) {
                item(key = "empty") { NotesEmpty(Modifier.animateItem()) }
            }
            itemsIndexed(notes, key = { _, n -> n.id }) { i, note ->
                NoteRow(note, segmentShape(i, notes.size), Modifier.animateItem()) { editing = note }
            }
        }
    }

    editing?.let { note ->
        androidx.compose.runtime.key(note.id) {
            NoteSheet(
                note,
                onDismiss = { editing = null },
                onDelete = {
                    NoteStore.delete(note.id)
                    editing = null
                    offerUndo("Note deleted") { NoteStore.restore(note.id) }
                },
                onMakeGoal = { text ->
                    // A goal is one short line: the note's first line becomes the goal.
                    val goal = GoalStore.add(text.lineSequence().first { it.isNotBlank() })
                    NoteStore.delete(note.id)
                    editing = null
                    offerUndo("Moved to your goals") {
                        NoteStore.restore(note.id)
                        goal?.let { GoalStore.delete(it.id) }
                    }
                },
            )
        }
    }
}

@Composable
private fun WriteNote(modifier: Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    TextField(
        value = text,
        onValueChange = { text = it },
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Write something down…") },
        minLines = 1,
        maxLines = 8,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        // Enter is a new line here; the button saves.
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        trailingIcon = {
            if (text.isNotBlank()) {
                TextButton(onClick = { NoteStore.add(text); text = "" }, shapes = ButtonDefaults.shapes()) { Text("Save") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NotesEmpty(modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(112.dp).clip(MaterialShapes.Cookie7Sided.toShape()).background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_note),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Get it out of your head", style = MaterialTheme.typography.titleLargeEmphasized)
        Text(
            "A thought, an idea, something to look up later. It doesn't have to be a task. " +
                "Long-press the app icon for “New note” from anywhere.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun NoteRow(note: Note, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier, onOpen: () -> Unit) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.clickable(onClick = onOpen).padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(note.text, style = MaterialTheme.typography.bodyLarge, maxLines = 6, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            val now = System.currentTimeMillis()
            Text(
                if (now - note.createdAt < DateUtils.MINUTE_IN_MILLIS) "Just now"
                else DateUtils.getRelativeTimeSpanString(note.createdAt, now, DateUtils.MINUTE_IN_MILLIS).toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NoteSheet(note: Note, onDismiss: () -> Unit, onDelete: () -> Unit, onMakeGoal: (String) -> Unit) {
    var text by remember { mutableStateOf(note.text) }
    // Closing the sheet keeps the edit; there is no separate Save to forget.
    fun close() {
        if (text.trim() != note.text) NoteStore.edit(note.id, text)
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = { close() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp)) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 14,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDelete, shapes = ButtonDefaults.shapes()) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                FilledTonalButton(
                    onClick = { onMakeGoal(text) },
                    enabled = text.isNotBlank(),
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Make it a goal") }
                Spacer(Modifier.size(8.dp))
                Button(onClick = { close() }, shapes = ButtonDefaults.shapes()) { Text("Done") }
            }
        }
    }
}
