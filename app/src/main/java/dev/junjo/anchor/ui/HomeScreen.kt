package dev.junjo.anchor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.runtime.key
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import dev.junjo.anchor.data.Note
import dev.junjo.anchor.data.NoteStore
import dev.junjo.anchor.data.visible
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon as SystemIcon
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.junjo.anchor.AppPrefs
import dev.junjo.anchor.QuickAddActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import dev.junjo.anchor.QuickAddTileService
import dev.junjo.anchor.NoteTileService
import dev.junjo.anchor.TripTileService
import dev.junjo.anchor.R
import dev.junjo.anchor.data.Backup
import dev.junjo.anchor.data.Goal
import dev.junjo.anchor.data.GoalStore
import dev.junjo.anchor.data.active
import dev.junjo.anchor.data.doneToday
import dev.junjo.anchor.data.today
import dev.junjo.anchor.notify.GoalsNotification
import dev.junjo.anchor.notify.Notifications
import dev.junjo.anchor.overlay.FloatingGoals
import dev.junjo.anchor.timer.FocusTimer
import dev.junjo.anchor.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Past this many open goals, suggest trimming: working memory holds only a handful of things. */
private const val SOFT_LIMIT = 5

private val DONE_MESSAGES = listOf("Done ✓", "Nice, that's done", "Ticked off ✓", "One less thing to hold in your head")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(onOpenReminders: () -> Unit = {}) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val goals by GoalStore.goals.collectAsStateWithLifecycle()
    val allNotes by NoteStore.notes.collectAsStateWithLifecycle()
    val timer by FocusTimer.state.collectAsStateWithLifecycle()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var editingGoalId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Re-checked each time the app comes back to the front: the day may have changed, or a
    // permission may have been granted in Settings.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        resumes++
        GoalsNotification.update(ctx)
        FloatingGoals.sync(ctx)
    }
    val today = remember(resumes) { today() }
    val date = remember(today) { LocalDate.now().format(DateTimeFormatter.ofPattern("EEE d MMM")) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        GoalsNotification.update(ctx)
        resumes++
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(ctx) && !AppPrefs.askedNotifications(ctx)) {
            AppPrefs.setAskedNotifications(ctx)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun offerUndo(message: String, undo: () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }

    fun complete(goal: Goal) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        GoalStore.setDone(goal.id, true)
        offerUndo(DONE_MESSAGES.random()) { GoalStore.setDone(goal.id, false) }
    }

    fun remove(goal: Goal) {
        GoalStore.delete(goal.id)
        offerUndo("Deleted") { GoalStore.restore(goal.id) }
    }

    // Derived lists are rebuilt only when the data behind them changes, not on every redraw.
    val active = remember(goals) { goals.active() }
    val carried = remember(active, today) { active.filter { it.day < today } }
    val current = remember(active, today) { active.filter { it.day >= today } }
    val done = remember(goals, today) { goals.doneToday() }
    val notes = remember(allNotes) { allNotes.visible() }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    // A list keeps its first visible row in place when one is added above it, which would put a
    // timer started from the focus sheet just out of sight. Bring it into view instead.
    LaunchedEffect(timer.isActive) {
        if (timer.isActive) listState.animateScrollToItem(0)
    }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Today") },
                subtitle = {
                    Text(
                        when {
                            active.isEmpty() && done.isEmpty() -> date
                            active.isEmpty() -> "$date · all clear"
                            else -> "$date · ${active.size} to do"
                        },
                    )
                },
                actions = {
                    IconButton(onClick = onOpenReminders, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_bell), contentDescription = "Reminders")
                    }
                    IconButton(onClick = { settingsOpen = true }, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = { AddMenu() },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            // Rows in a group sit 2dp apart; sections add their own space around them.
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (timer.isActive) {
                item(key = "timer") { TimerCard(timer, Modifier.animateItem().padding(bottom = 16.dp)) }
            }

            item(key = "setup") {
                SetupCard(
                    refresh = resumes,
                    modifier = Modifier.animateItem(),
                    onWidgetUnsupported = {
                        scope.launch { snackbar.showSnackbar("Long-press your home screen, tap Widgets and find Anchor") }
                    },
                )
            }

            if (carried.isNotEmpty()) {
                item(key = "carried") {
                    CarriedOverCard(
                        carried,
                        modifier = Modifier.animateItem().padding(bottom = 16.dp),
                        onKeep = { GoalStore.keepForToday(it.id) },
                        onDrop = { remove(it) },
                        onKeepAll = { GoalStore.keepAllForToday() },
                    )
                }
            }

            if (current.isEmpty() && carried.isEmpty() && notes.isEmpty()) {
                item(key = "empty") { EmptyState(Modifier.animateItem()) }
            }

            itemsIndexed(current, key = { _, g -> g.id }) { i, goal ->
                GoalRow(
                    goal,
                    shape = segmentShape(i, current.size),
                    modifier = Modifier.animateItem(),
                    onDone = { complete(goal) },
                    onFocus = { QuickAddActivity.open(ctx, QuickAddActivity.Kind.FOCUS, goal.text, goal.id) },
                    onEdit = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        editingGoalId = goal.id
                    },
                )
            }

            if (active.size > SOFT_LIMIT) {
                item(key = "limit") {
                    Text(
                        "That's ${active.size} things to keep in mind. Which 3 matter most today? The rest can go.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.animateItem().padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
            }

            if (notes.isNotEmpty()) {
                item(key = "notes-header") { SectionHeader("Notes", Modifier.animateItem()) }
                itemsIndexed(notes, key = { _, n -> n.id }) { i, note ->
                    NoteRow(note, segmentShape(i, notes.size), Modifier.animateItem()) { editingNoteId = note.id }
                }
            }

            if (done.isNotEmpty()) {
                item(key = "done-header") {
                    SectionHeader(
                        "Done today · ${done.size}",
                        Modifier.animateItem(),
                        expanded = showDone,
                        onClick = { showDone = !showDone },
                    )
                }
                if (showDone) {
                    itemsIndexed(done, key = { _, g -> g.id }) { i, goal ->
                        DoneRow(
                            goal,
                            shape = segmentShape(i, done.size),
                            modifier = Modifier.animateItem(),
                            onUndo = { GoalStore.setDone(goal.id, false) },
                        )
                    }
                }
            }

            // Room for the + button, so it never covers the last row.
            item(key = "fab-space") { Spacer(Modifier.height(72.dp)) }
        }
    }

    val editingGoal = editingGoalId?.let { id -> goals.firstOrNull { it.id == id && it.isActive } }
    editingGoal?.let { goal ->
        val canPutFirst = active.size > 1 && active.firstOrNull()?.id != goal.id
        key(goal.id) {
            EditSheet(
                initial = goal.text,
                multiLine = false,
                otherLabel = "Remind me…",
                onOther = { text ->
                    GoalStore.rename(goal.id, text)
                    editingGoalId = null
                    QuickAddActivity.open(ctx, QuickAddActivity.Kind.REMIND, text.trim())
                },
                extraLabel = if (canPutFirst) "Put first" else null,
                onExtra = if (canPutFirst) {
                    { text ->
                        if (text.trim() != goal.text) GoalStore.rename(goal.id, text)
                        GoalStore.putFirst(goal.id)
                        editingGoalId = null
                    }
                } else null,
                onDelete = { editingGoalId = null; remove(goal) },
                onClose = { text ->
                    if (text.trim() != goal.text) GoalStore.rename(goal.id, text)
                    editingGoalId = null
                },
            )
        }
    }

    val editingNote = editingNoteId?.let { id -> allNotes.firstOrNull { it.id == id && !it.deleted } }
    editingNote?.let { note ->
        key(note.id) {
            EditSheet(
                initial = note.text,
                multiLine = true,
                otherLabel = "Make it a goal",
                onOther = { text ->
                    // Save any edit, keep the note intact, and copy its first line onto today's goals.
                    if (text.trim() != note.text) NoteStore.edit(note.id, text)
                    val goal = GoalStore.add(text.lineSequence().first { it.isNotBlank() })
                    editingNoteId = null
                    offerUndo("Added to your goals") {
                        goal?.let { GoalStore.delete(it.id) }
                    }
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

    if (settingsOpen) {
        ModalBottomSheet(onDismissRequest = { settingsOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
                SettingsSection(
                    refresh = resumes,
                    onShowSetup = { resumes++; settingsOpen = false },
                    onMessage = { msg ->
                        settingsOpen = false
                        scope.launch {
                            snackbar.currentSnackbarData?.dismiss()
                            snackbar.showSnackbar(msg)
                        }
                    },
                )
            }
        }
    }
}

/**
 * The + button. One tap opens the four ways to catch something, each its own small sheet, so you
 * pick what it is once and never switch modes while typing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AddMenu() {
    val ctx = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    BackHandler(open) { open = false }
    fun pick(kind: QuickAddActivity.Kind) {
        open = false
        QuickAddActivity.open(ctx, kind)
    }
    FloatingActionButtonMenu(
        expanded = open,
        button = {
            ToggleFloatingActionButton(checked = open, onCheckedChange = { open = it }) {
                // The + turns into an × as the menu opens.
                Icon(
                    painterResource(R.drawable.ic_add),
                    contentDescription = if (open) "Close" else "Add",
                    modifier = Modifier.graphicsLayer { rotationZ = checkedProgress * 45f },
                )
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = { pick(QuickAddActivity.Kind.GOAL) },
            icon = { Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null) },
            text = { Text("Goal") },
        )
        FloatingActionButtonMenuItem(
            onClick = { pick(QuickAddActivity.Kind.NOTE) },
            icon = { Icon(painterResource(R.drawable.ic_note), contentDescription = null) },
            text = { Text("Note") },
        )
        FloatingActionButtonMenuItem(
            onClick = { pick(QuickAddActivity.Kind.REMIND) },
            icon = { Icon(painterResource(R.drawable.ic_bell), contentDescription = null) },
            text = { Text("Remind me") },
        )
        FloatingActionButtonMenuItem(
            onClick = { pick(QuickAddActivity.Kind.FOCUS) },
            icon = { Icon(painterResource(R.drawable.ic_timer), contentDescription = null) },
            text = { Text("Focus") },
        )
        FloatingActionButtonMenuItem(
            onClick = { pick(QuickAddActivity.Kind.TRIP) },
            icon = { Icon(painterResource(R.drawable.ic_trip), contentDescription = null) },
            text = { Text("In&Out") },
        )
    }
}

/** A quiet heading over a group of rows; with [onClick] it folds the group away. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier, expanded: Boolean = true, onClick: (() -> Unit)? = null) {
    Row(
        modifier
            .padding(top = 20.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary)
        if (onClick != null) {
            Icon(
                painterResource(R.drawable.ic_expand_more),
                contentDescription = if (expanded) "Hide" else "Show",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Nothing on your mind", style = MaterialTheme.typography.titleLargeEmphasized)
        Text(
            "Tap + for a goal or a note.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Tap to tick it off, ▶ to focus on it, long-press to change it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GoalRow(
    goal: Goal,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier,
    onDone: () -> Unit,
    onFocus: () -> Unit,
    onEdit: () -> Unit,
) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier
                .combinedClickable(onClick = onDone, onLongClick = onEdit, onLongClickLabel = "Change")
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_circle),
                contentDescription = "Mark done",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                goal.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(vertical = 18.dp),
            )
            IconButton(onClick = onFocus, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.ic_play), "Focus on this", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Notes sit under the goals, quieter: no circle, since there's nothing to tick off. */
@Composable
private fun NoteRow(note: Note, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier, onOpen: () -> Unit) {
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DoneRow(goal: Goal, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier, onUndo: () -> Unit) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.clickable(onClick = onUndo).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = "Done, tap to undo",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                goal.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
            )
        }
    }
}

/** Unfinished goals from earlier days ask for a decision once, instead of piling up silently. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CarriedOverCard(
    goals: List<Goal>,
    modifier: Modifier,
    onKeep: (Goal) -> Unit,
    onDrop: (Goal) -> Unit,
    onKeepAll: () -> Unit,
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Column(Modifier.padding(20.dp)) {
            Text("Still on from before?", style = MaterialTheme.typography.titleLargeEmphasized)
            Text(
                "Keep what still matters today. Dropping one is a decision, not a failure.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            goals.forEach { goal ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(goal.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onDrop(goal) }, shapes = ButtonDefaults.shapes()) { Text("Drop") }
                    TextButton(onClick = { onKeep(goal) }, shapes = ButtonDefaults.shapes()) { Text("Keep") }
                }
            }
            if (goals.size > 1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    FilledTonalButton(onClick = onKeepAll, shapes = ButtonDefaults.shapes()) { Text("Keep all") }
                }
            }
        }
    }
}

private class SetupItem(val title: String, val why: String, val button: String, val fix: () -> Unit)

/** One-time checklist for the Android settings the app needs to work well. Disappears when done. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SetupCard(refresh: Int, modifier: Modifier, onWidgetUnsupported: () -> Unit) {
    val ctx = LocalContext.current
    var hidden by remember(refresh) { mutableStateOf(AppPrefs.setupHidden(ctx)) }
    val items = remember(refresh) { setupItems(ctx, onWidgetUnsupported) }
    if (hidden || items.isEmpty()) return

    Surface(
        modifier.fillMaxWidth().padding(bottom = 16.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Setup", style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
                TextButton(onClick = { AppPrefs.setSetupHidden(ctx, true); hidden = true }, shapes = ButtonDefaults.shapes()) {
                    Text("Hide")
                }
            }
            items.forEach { item ->
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall)
                        Text(item.why, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.width(12.dp))
                    FilledTonalButton(onClick = item.fix, shapes = ButtonDefaults.shapes()) { Text(item.button) }
                }
            }
        }
    }
}

@SuppressLint("BatteryLife")
private fun setupItems(ctx: Context, onWidgetUnsupported: () -> Unit): List<SetupItem> = buildList {
    val pkg = ctx.packageName
    fun open(intent: Intent) = runCatching { ctx.startActivity(intent) }

    if (!Notifications.canPost(ctx)) add(
        SetupItem("Allow notifications", "Timers, alarms and your goals on the lock screen need them.", "Allow") {
            open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg))
        },
    )
    if (Build.VERSION.SDK_INT >= 34 && !ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) add(
        SetupItem("Allow full-screen alarms", "So “time's up” fills the screen even when the phone is locked.", "Allow") {
            open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$pkg")))
        },
    )
    if (Build.VERSION.SDK_INT >= 36 && Notifications.canPost(ctx) &&
        !NotificationManagerCompat.from(ctx).canPostPromotedNotifications()
    ) add(
        SetupItem("Show timers as Live Updates", "The countdown sits in the status bar and on top of your notifications.", "Allow") {
            open(Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg))
        },
    )
    if (!FloatingGoals.isOn(ctx)) add(
        SetupItem("Keep a goal floating on screen", "A small bubble over every app shows your top goal and the timer.", "Turn on") {
            FloatingGoals.enable(ctx)
        },
    )
    if (!Widgets.isPlaced(ctx)) add(
        SetupItem("Put your goals on the home screen", "A widget keeps them in sight, so you don't have to remember to look.", "Add") {
            if (!Widgets.requestPin(ctx)) onWidgetUnsupported()
        },
    )
    if (!ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(pkg)) add(
        SetupItem("Keep timers on time", "Lets timers and the bubble keep running when the phone tries to save battery.", "Allow") {
            open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")))
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsSection(refresh: Int, onShowSetup: () -> Unit, onMessage: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var goalsNotification by remember { mutableStateOf(AppPrefs.goalsNotification(ctx)) }
    var bubble by remember(refresh) { mutableStateOf(FloatingGoals.isOn(ctx)) }
    var bubbleNudge by remember { mutableStateOf(AppPrefs.bubbleNudge(ctx)) }

    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val json = Backup.exportJson()
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        ?: error("Could not open destination")
                }.isSuccess
            }
            onMessage(if (ok) "Backup saved" else "Couldn't save backup")
        }
    }

    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: error("Could not read file")
                    Backup.importJson(raw)
                }
            }
            onMessage(result.fold(onSuccess = { it.message() }, onFailure = { "Couldn't read that backup file" }))
        }
    }

    Column(Modifier.padding(top = 8.dp)) {
        Text(
            "Always in sight",
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        SettingRow(
            title = "Floating goal bubble",
            about = "A small pill over every app with your top goal, or the countdown while a timer runs. Tap it for the list; drag it anywhere, or push it off the side to tuck it into the edge.",
            checked = bubble,
            shape = segmentShape(0, 3),
        ) {
            bubble = it
            if (it) FloatingGoals.enable(ctx) else FloatingGoals.disable(ctx)
        }
        Spacer(Modifier.height(2.dp))
        SettingRow(
            title = "Nudge tucked bubble",
            about = "Briefly flashes the tucked edge tab red every few minutes while you still have open goals.",
            checked = bubbleNudge,
            shape = segmentShape(1, 3),
        ) {
            bubbleNudge = it
            AppPrefs.setBubbleNudge(ctx, it)
        }
        Spacer(Modifier.height(2.dp))
        SettingRow(
            title = "Goals in notifications",
            about = "Shows your list on the lock screen and lets you add a goal from the notification shade.",
            checked = goalsNotification,
            shape = segmentShape(2, 3),
        ) {
            goalsNotification = it
            AppPrefs.setGoalsNotification(ctx, it)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Backup",
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { exportBackup.launch("anchor-${today()}.json") },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f),
            ) { Text("Export") }
            FilledTonalButton(
                onClick = { importBackup.launch(arrayOf("application/json", "text/plain", "*/*")) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f),
            ) { Text("Import") }
        }
        Spacer(Modifier.height(8.dp))
        if (Build.VERSION.SDK_INT >= 33) {
            TextButton(onClick = { requestTile(ctx, QuickAddTileService::class.java, R.string.tile_label, R.drawable.ic_add) }, shapes = ButtonDefaults.shapes()) {
                Text("Add an “Add goal” button to Quick Settings")
            }
            TextButton(onClick = { requestTile(ctx, NoteTileService::class.java, R.string.note_tile_label, R.drawable.ic_note) }, shapes = ButtonDefaults.shapes()) {
                Text("Add an “Add note” button to Quick Settings")
            }
            TextButton(onClick = { requestTile(ctx, TripTileService::class.java, R.string.trip_tile_label, R.drawable.ic_trip) }, shapes = ButtonDefaults.shapes()) {
                Text("Add an “In&Out” button to Quick Settings")
            }
        }
        if (AppPrefs.setupHidden(ctx)) {
            TextButton(onClick = { AppPrefs.setSetupHidden(ctx, false); onShowSetup() }, shapes = ButtonDefaults.shapes()) {
                Text("Show setup checklist")
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    about: String,
    checked: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onChange: (Boolean) -> Unit,
) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(16.dp))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@RequiresApi(33)
private fun requestTile(ctx: Context, tile: Class<*>, label: Int, icon: Int) {
    ctx.getSystemService(StatusBarManager::class.java).requestAddTileService(
        ComponentName(ctx, tile),
        ctx.getString(label),
        SystemIcon.createWithResource(ctx, icon),
        ctx.mainExecutor,
    ) { }
}
