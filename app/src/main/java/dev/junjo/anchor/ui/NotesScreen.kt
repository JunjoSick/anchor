package dev.junjo.anchor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.junjo.anchor.QuickAddActivity
import dev.junjo.anchor.R
import dev.junjo.anchor.data.GoalStore
import dev.junjo.anchor.data.Note
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
                        "Thoughts, ideas, things to look up — kept here without bothering you.",
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
            EditSheet(
                initial = note.text,
                multiLine = true,
                otherLabel = "Make it a to-do",
                onOther = { text ->
                    if (text.trim() != note.text) NoteStore.edit(note.id, text)
                    val goal = GoalStore.add(text.lineSequence().first { it.isNotBlank() })
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

/** Notes sit under the list, quieter: no circle, since there's nothing nagging you to tick off. */
@Composable
internal fun NoteRow(note: Note, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier, onOpen: () -> Unit) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Text(
            note.text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(onClick = onOpen).padding(horizontal = 20.dp, vertical = 14.dp).fillMaxWidth(),
        )
    }
}
