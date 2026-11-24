package dev.junjo.anchor.ui

import android.Manifest
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.junjo.anchor.AppPrefs
import dev.junjo.anchor.QuickAddActivity
import dev.junjo.anchor.R
import dev.junjo.anchor.clockTime
import dev.junjo.anchor.data.Goal
import dev.junjo.anchor.data.GoalStore
import dev.junjo.anchor.data.NoteStore
import dev.junjo.anchor.data.Reminder
import dev.junjo.anchor.data.ReminderStore
import dev.junjo.anchor.data.active
import dev.junjo.anchor.data.doneToday
import dev.junjo.anchor.data.sinceLabel
import dev.junjo.anchor.data.stale
import dev.junjo.anchor.data.today
import dev.junjo.anchor.data.visible
import dev.junjo.anchor.notify.GoalsNotification
import dev.junjo.anchor.notify.Notifications
import dev.junjo.anchor.overlay.FloatingGoals
import dev.junjo.anchor.timer.FocusTimer
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Past this many open to-dos, suggest trimming: working memory holds only a handful of things. */
private const val SOFT_LIMIT = 5

/** How many notes show on the main screen before "All notes". */
private const val HOME_NOTES_LIMIT = 3

private val DONE_MESSAGES = listOf("Done ✓", "Nice, that's done", "Ticked off ✓", "One less thing to hold in your head")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(onOpenReminders: () -> Unit = {}, onOpenNotes: () -> Unit = {}) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val goals by GoalStore.goals.collectAsStateWithLifecycle()
    val allNotes by NoteStore.notes.collectAsStateWithLifecycle()
    val allReminders by ReminderStore.reminders.collectAsStateWithLifecycle()
    val timer by FocusTimer.state.collectAsStateWithLifecycle()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var editingGoalId by rememberSaveable { mutableStateOf<String?>(null) }
    var remindGoalId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingReminderId by rememberSaveable { mutableStateOf<String?>(null) }
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
    val stale = remember(active, resumes) { active.stale() }
    val staleIds = remember(stale) { stale.map { it.id }.toSet() }
    val current = remember(active, staleIds) { active.filterNot { it.id in staleIds } }
    val comingUp = remember(allReminders, resumes) { ReminderStore.comingUp(allReminders) }
    val done = remember(goals, today) { goals.doneToday() }
    val notes = remember(allNotes) { allNotes.visible() }
    val shownNotes = remember(notes) { notes.take(HOME_NOTES_LIMIT) }

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
                title = { Text("To-do") },
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

            if (stale.isNotEmpty()) {
                item(key = "stale") {
                    StillOnCard(
                        stale,
                        modifier = Modifier.animateItem().padding(bottom = 16.dp),
                        onKeep = { GoalStore.keep(it.id) },
                        onDrop = { remove(it) },
                        onKeepAll = { GoalStore.keepAll() },
                    )
                }
            }

            if (current.isEmpty() && stale.isEmpty() && comingUp.isEmpty() && notes.isEmpty()) {
                item(key = "empty") { EmptyState(Modifier.animateItem()) }
            }

            itemsIndexed(current, key = { _, g -> g.id }) { i, goal ->
                GoalRow(
                    goal,
                    refresh = resumes,
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
                        "That's ${active.size} things to keep in mind. Which 3 matter most? The rest can go.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.animateItem().padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
            }

            if (comingUp.isNotEmpty()) {
                item(key = "coming-up-header") { SectionHeader("Coming up", Modifier.animateItem()) }
                itemsIndexed(comingUp, key = { _, r -> "coming-${r.id}" }) { i, r ->
                    ComingUpRow(r, segmentShape(i, comingUp.size), Modifier.animateItem()) {
                        editingReminderId = r.id
                    }
                }
            }

            if (notes.isNotEmpty()) {
                item(key = "notes-header") {
                    Row(
                        Modifier.fillMaxWidth().animateItem(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionHeader("Notes", Modifier.weight(1f))
                        if (notes.size > HOME_NOTES_LIMIT) {
                            TextButton(
                                onClick = onOpenNotes,
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.padding(top = 14.dp),
                            ) { Text("All notes (${notes.size})") }
                        }
                    }
                }
                itemsIndexed(shownNotes, key = { _, n -> n.id }) { i, note ->
                    NoteRow(note, segmentShape(i, shownNotes.size), Modifier.animateItem()) { editingNoteId = note.id }
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
        val extras = buildList {
            if (canPutFirst) {
                add(
                    SheetAction("Put first") { text ->
                        if (text.trim() != goal.text) GoalStore.rename(goal.id, text)
                        GoalStore.putFirst(goal.id)
                        editingGoalId = null
                    },
                )
            }
            add(
                SheetAction("In&Out") { text ->
                    if (text.trim() != goal.text) GoalStore.rename(goal.id, text)
                    editingGoalId = null
                    QuickAddActivity.open(ctx, QuickAddActivity.Kind.TRIP, text.trim())
                },
            )
        }
        key(goal.id) {
            EditSheet(
                initial = goal.text,
                multiLine = false,
                otherLabel = "Remind me…",
                onOther = { text ->
                    if (text.trim() != goal.text) GoalStore.rename(goal.id, text)
                    editingGoalId = null
                    remindGoalId = goal.id
                },
                extraActions = extras,
                onDelete = { editingGoalId = null; remove(goal) },
                onClose = { text ->
                    if (text.trim() != goal.text) GoalStore.rename(goal.id, text)
                    editingGoalId = null
                },
            )
        }
    }

    val remindGoal = remindGoalId?.let { id -> goals.firstOrNull { it.id == id && it.isActive } }
    remindGoal?.let { goal ->
        key(goal.id) {
            HideAndRemindSheet(
                goal = goal,
                onDismiss = { remindGoalId = null },
                onSaved = { reminder, keepOnList ->
                    remindGoalId = null
                    if (keepOnList) {
                        offerUndo("Reminder set for ${whenText(reminder.startAt)}") {
                            ReminderStore.delete(reminder.id)
                        }
                    } else {
                        GoalStore.delete(goal.id)
                        val timeText = comingUpTimeText(reminder.startAt)
                        val backLabel = if (timeText.startsWith("tomorrow")) "Back $timeText" else "Back at $timeText"
                        offerUndo(backLabel) {
                            GoalStore.restore(goal.id)
                            ReminderStore.delete(reminder.id)
                        }
                    }
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
                otherLabel = "Make it a to-do",
                onOther = { text ->
                    // Save any edit, keep the note intact, and copy its first line onto the list.
                    if (text.trim() != note.text) NoteStore.edit(note.id, text)
                    val goal = GoalStore.add(text.lineSequence().first { it.isNotBlank() })
                    editingNoteId = null
                    offerUndo("Added to your list") {
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

    val editingReminder = editingReminderId?.let { id -> allReminders.firstOrNull { it.id == id && !it.deleted } }
    editingReminder?.let { reminder ->
        key(reminder.id) {
            ReminderSheet(initial = reminder, onDismiss = { editingReminderId = null })
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
 * The + button. One tap opens the ways to catch something, each its own small sheet (folded into
 * one add sheet in phase 4).
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
            text = { Text("To-do") },
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
internal fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    topPadding: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .padding(top = topPadding, bottom = 6.dp)
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
        Text("Nothing on your list", style = MaterialTheme.typography.titleLargeEmphasized)
        Text(
            "Tap + to add something.",
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
    refresh: Int,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier,
    onDone: () -> Unit,
    onFocus: () -> Unit,
    onEdit: () -> Unit,
) {
    val routineLabel = remember(goal.reminderId, goal.firedAt, refresh) {
        if (goal.reminderId != null && goal.firedAt != null) sinceLabel(goal.firedAt) else null
    }
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
            Column(Modifier.weight(1f).padding(vertical = if (routineLabel != null) 12.dp else 18.dp)) {
                Text(goal.text, style = MaterialTheme.typography.bodyLarge)
                if (routineLabel != null) {
                    val overdue = routineLabel.startsWith("since ")
                    Text(
                        "🔁 $routineLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onFocus, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.ic_play), "Focus on this", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** One-off reminders due in the next 24 hours: quieter than active to-dos, tap to edit. */
@Composable
private fun ComingUpRow(reminder: Reminder, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier, onOpen: () -> Unit) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.clickable(onClick = onOpen).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(if (reminder.alarm) R.drawable.ic_bell_ring else R.drawable.ic_bell),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                reminder.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                comingUpTimeText(reminder.startAt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

/** Untouched to-dos (3+ days) asking for a keep-or-drop decision. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StillOnCard(
    goals: List<Goal>,
    modifier: Modifier,
    onKeep: (Goal) -> Unit,
    onDrop: (Goal) -> Unit,
    onKeepAll: () -> Unit,
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Column(Modifier.padding(20.dp)) {
            Text("Still on?", style = MaterialTheme.typography.titleLargeEmphasized)
            Text(
                "Keep what still matters. Dropping one is a decision, not a failure.",
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

private val HIDE_REMIND_PRESETS = listOf(10, 30, 60, 120)

/**
 * "Remind me…" from a to-do: picks when it should come back and hides the to-do until then
 * (moving it down into Coming up) unless "Keep it on my list too" is switched on.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HideAndRemindSheet(
    goal: Goal,
    onDismiss: () -> Unit,
    onSaved: (reminder: Reminder, keepOnList: Boolean) -> Unit,
) {
    var inMinutes by rememberSaveable { mutableIntStateOf(30) }
    var atHour by rememberSaveable { mutableIntStateOf(-1) }
    var atMinute by rememberSaveable { mutableIntStateOf(0) }
    var alarm by rememberSaveable { mutableStateOf(true) }
    var keepOnList by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val now = rememberNow(active = true, periodMs = 15_000L)

    fun targetAt(base: Long = System.currentTimeMillis()): Long =
        if (atHour >= 0) Reminder.nextOccurrence(atHour, atMinute, Instant.ofEpochMilli(base).atZone(ZoneId.systemDefault()))
        else base + inMinutes * 60_000L

    if (picking) {
        RemindWhenDialog(
            initialMinutes = inMinutes,
            initialHour = atHour.takeIf { it >= 0 },
            initialMinute = atMinute,
            defaultToClock = atHour >= 0 || inMinutes in HIDE_REMIND_PRESETS,
            onDismiss = { picking = false },
            onPickClock = { h, m -> atHour = h; atMinute = m; picking = false },
            onPickDuration = { m -> atHour = -1; inMinutes = m; picking = false },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                goal.text,
                style = MaterialTheme.typography.titleLargeEmphasized,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))
            val pickedClockLabel = if (atHour >= 0) {
                LocalTime.of(atHour, atMinute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
            } else null
            DurationRow(
                HIDE_REMIND_PRESETS,
                inMinutes,
                onSelect = { atHour = -1; inMinutes = it },
                onOther = { picking = true },
                prefix = "In ",
                customLabel = pickedClockLabel,
                otherIcon = R.drawable.ic_schedule,
                otherDescription = "Pick a time",
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clickable { keepOnList = !keepOnList }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Keep it on my list too", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Off: hides it until then (it waits under Coming up).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = keepOnList, onCheckedChange = { keepOnList = it })
            }
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
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        val reminder = Reminder.once(goal.text, targetAt(), alarm)
                        ReminderStore.save(reminder)
                        onSaved(reminder, keepOnList)
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    val target = targetAt(now)
                    val isTomorrow = Instant.ofEpochMilli(target).atZone(ZoneId.systemDefault()).toLocalDate()
                        .isAfter(LocalDate.now(ZoneId.systemDefault()))
                    val whenLabel = if (isTomorrow) "tomorrow ${clockTime(target)}" else "at ${clockTime(target)}"
                    Text(if (alarm) "Ring $whenLabel" else "Remind $whenLabel")
                }
            }
        }
    }
}
