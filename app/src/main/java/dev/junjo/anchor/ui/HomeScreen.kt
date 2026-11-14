package dev.junjo.anchor.ui

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.junjo.anchor.AppPrefs
import dev.junjo.anchor.QuickAddTileService
import dev.junjo.anchor.R
import dev.junjo.anchor.data.Goal
import dev.junjo.anchor.data.GoalStore
import dev.junjo.anchor.data.Reminder
import dev.junjo.anchor.data.ReminderStore
import dev.junjo.anchor.data.active
import dev.junjo.anchor.data.doneToday
import dev.junjo.anchor.data.today
import dev.junjo.anchor.notify.GoalsNotification
import dev.junjo.anchor.notify.Notifications
import dev.junjo.anchor.overlay.FloatingGoals
import dev.junjo.anchor.timer.FocusTimer
import dev.junjo.anchor.widget.Widgets
import kotlinx.coroutines.launch

/** Past this many open goals, suggest trimming: working memory holds only a handful of things. */
private const val SOFT_LIMIT = 5

private val DONE_MESSAGES = listOf("Done ✓", "Nice, that's done", "Ticked off ✓", "One less thing to hold in your head")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val view = LocalView.current
    val goals by GoalStore.goals.collectAsStateWithLifecycle()
    val timer by FocusTimer.state.collectAsStateWithLifecycle()
    val reminders by ReminderStore.reminders.collectAsStateWithLifecycle()
    var sheetOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Reminder?>(null) }
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

    fun startFor(goal: Goal, minutes: Int) {
        FocusTimer.start(minutes, goal.text, goal.id)
        scope.launch { snackbar.showSnackbar("Timer started: $minutes min") }
    }

    // Derived lists are rebuilt only when the data behind them changes, not on every redraw.
    val active = remember(goals) { goals.active() }
    val carried = remember(active, today) { active.filter { it.day < today } }
    val current = remember(active, today) { active.filter { it.day >= today } }
    val done = remember(goals, today) { goals.doneToday() }
    val visibleReminders = remember(reminders) {
        reminders.filter { !it.deleted }.sortedWith(compareBy({ it.hour }, { it.minute }))
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Anchor") },
                subtitle = {
                    Text(
                        when {
                            active.isEmpty() && done.isEmpty() -> "Nothing on the list"
                            active.isEmpty() -> "All clear · ${done.size} done today"
                            done.isEmpty() -> "${active.size} to do"
                            else -> "${active.size} to do · ${done.size} done"
                        },
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
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
            // Rows in a group sit 2dp apart; sections add their own space around them.
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // A running timer goes on top; otherwise goals come first and the presets wait below.
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

            item(key = "add") {
                AddGoalField(onAdd = { GoalStore.add(it) }, modifier = Modifier.padding(bottom = 12.dp))
            }

            if (current.isEmpty() && carried.isEmpty()) {
                item(key = "empty") { EmptyState(Modifier.animateItem()) }
            }

            itemsIndexed(current, key = { _, g -> g.id }) { i, goal ->
                GoalRow(
                    goal,
                    shape = segmentShape(i, current.size),
                    modifier = Modifier.animateItem(),
                    onDone = { complete(goal) },
                    onStart = { startFor(goal, it) },
                    onDelete = { remove(goal) },
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

            if (done.isNotEmpty()) {
                item(key = "done-header") {
                    Text(
                        "Done today · ${done.size}",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.animateItem().padding(top = 24.dp, bottom = 8.dp, start = 8.dp),
                    )
                }
                // Same keys as the to-do rows, so a ticked goal visibly slides down into this list.
                itemsIndexed(done, key = { _, g -> g.id }) { i, goal ->
                    DoneRow(
                        goal,
                        shape = segmentShape(i, done.size),
                        modifier = Modifier.animateItem(),
                        onUndo = { GoalStore.setDone(goal.id, false) },
                    )
                }
            }

            remindersSection(
                reminders = visibleReminders,
                onAdd = { editing = null; sheetOpen = true },
                onEdit = { editing = it; sheetOpen = true },
            )

            if (!timer.isActive) {
                item(key = "timer") { TimerCard(timer, Modifier.animateItem().padding(top = 24.dp)) }
            }

            item(key = "settings") { SettingsSection(refresh = resumes, onShowSetup = { resumes++ }) }
        }
    }

    if (sheetOpen) {
        // Keyed so opening a different reminder starts the sheet fresh.
        androidx.compose.runtime.key(editing?.id) {
            ReminderSheet(initial = editing, onDismiss = { sheetOpen = false })
        }
    }
}

@Composable
private fun AddGoalField(onAdd: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    fun submit() {
        if (text.isNotBlank()) {
            onAdd(text)
            text = ""
        }
    }
    TextField(
        value = text,
        onValueChange = { text = it },
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Add a goal…") },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        // Enter adds the goal and keeps the keyboard open, so a brain dump is just type, enter, type.
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        trailingIcon = {
            if (text.isNotBlank()) {
                IconButton(onClick = { submit() }) { Icon(painterResource(R.drawable.ic_add), "Add") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(112.dp)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Nothing on the list", style = MaterialTheme.typography.titleLargeEmphasized)
        Text(
            "Add the next thing you want to get done. Small and specific works best.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GoalRow(
    goal: Goal,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier,
    onDone: () -> Unit,
    onStart: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.clickable(onClick = onDone).padding(start = 16.dp, end = 4.dp),
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
            Box {
                IconButton(onClick = { menu = true }, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_play), "Start a timer for this goal", tint = MaterialTheme.colorScheme.primary)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Just start · 5 min") }, onClick = { menu = false; onStart(5) })
                    DropdownMenuItem(text = { Text("Focus · 15 min") }, onClick = { menu = false; onStart(15) })
                    DropdownMenuItem(text = { Text("Focus · 25 min") }, onClick = { menu = false; onStart(25) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                }
            }
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
private fun SettingsSection(refresh: Int, onShowSetup: () -> Unit) {
    val ctx = LocalContext.current
    var goalsNotification by remember { mutableStateOf(AppPrefs.goalsNotification(ctx)) }
    var bubble by remember(refresh) { mutableStateOf(FloatingGoals.isOn(ctx)) }
    Column(Modifier.padding(top = 32.dp)) {
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
            shape = segmentShape(0, 2),
        ) {
            bubble = it
            if (it) FloatingGoals.enable(ctx) else FloatingGoals.disable(ctx)
        }
        Spacer(Modifier.height(2.dp))
        SettingRow(
            title = "Goals in notifications",
            about = "Shows your list on the lock screen and lets you add a goal from the notification shade.",
            checked = goalsNotification,
            shape = segmentShape(1, 2),
        ) {
            goalsNotification = it
            AppPrefs.setGoalsNotification(ctx, it)
        }
        Spacer(Modifier.height(8.dp))
        if (Build.VERSION.SDK_INT >= 33) {
            TextButton(onClick = { requestTile(ctx) }, shapes = ButtonDefaults.shapes()) {
                Text("Add an “Add goal” button to Quick Settings")
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
private fun requestTile(ctx: Context) {
    ctx.getSystemService(StatusBarManager::class.java).requestAddTileService(
        ComponentName(ctx, QuickAddTileService::class.java),
        ctx.getString(R.string.tile_label),
        SystemIcon.createWithResource(ctx, R.drawable.ic_add),
        ctx.mainExecutor,
    ) { }
}
