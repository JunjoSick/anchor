package dev.junjo.anchor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.junjo.anchor.QuickAddActivity
import dev.junjo.anchor.R
import dev.junjo.anchor.data.Reminder
import dev.junjo.anchor.data.ReminderStore
import dev.junjo.anchor.timer.FocusTimer

/**
 * The + button on every tab. One tap opens the four ways to catch something, each with its own
 * sheet, so you pick what it is once and never switch modes while typing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddMenu(onFocus: () -> Unit) {
    val ctx = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    BackHandler(open) { open = false }
    fun pick(action: () -> Unit) {
        open = false
        action()
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
            onClick = { pick { QuickAddActivity.open(ctx, QuickAddActivity.Kind.GOAL) } },
            icon = { Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null) },
            text = { Text("Goal") },
        )
        FloatingActionButtonMenuItem(
            onClick = { pick { QuickAddActivity.open(ctx, QuickAddActivity.Kind.NOTE) } },
            icon = { Icon(painterResource(R.drawable.ic_note), contentDescription = null) },
            text = { Text("Write something down") },
        )
        FloatingActionButtonMenuItem(
            onClick = { pick { QuickAddActivity.open(ctx, QuickAddActivity.Kind.REMIND) } },
            icon = { Icon(painterResource(R.drawable.ic_bell), contentDescription = null) },
            text = { Text("Remind me in…") },
        )
        FloatingActionButtonMenuItem(
            onClick = { pick(onFocus) },
            icon = { Icon(painterResource(R.drawable.ic_timer), contentDescription = null) },
            text = { Text("Focus") },
        )
    }
}

/** The timer on its own page: pick a length, start now or after getting ready. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FocusScreen(bottomBar: @Composable () -> Unit, fab: @Composable () -> Unit) {
    val timer by FocusTimer.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Focus") },
                subtitle = { Text(if (timer.isActive) "Running" else "One thing, for a set time") },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = fab,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
        ) {
            item(key = "timer") { TimerCard(timer, Modifier.animateItem()) }
        }
    }
}

/** Repeating reminders and one-offs. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RemindersScreen(bottomBar: @Composable () -> Unit, fab: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val all by ReminderStore.reminders.collectAsStateWithLifecycle()
    // Re-sorted on each change; a one-off that has gone off drops out when the list next changes
    // or the tab is opened again.
    val reminders = remember(all) { ReminderStore.visible(all) }
    var sheetOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Reminder?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Reminders") },
                subtitle = { Text(if (reminders.isEmpty()) "Nothing set" else "${reminders.size} set") },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = fab,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "buttons") {
                Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val tall = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight)
                    FilledTonalButton(
                        onClick = { QuickAddActivity.open(ctx, QuickAddActivity.Kind.REMIND) },
                        shapes = ButtonDefaults.shapes(),
                        modifier = tall.weight(1f),
                    ) {
                        Icon(painterResource(R.drawable.ic_bell), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Remind me in…")
                    }
                    FilledTonalButton(
                        onClick = { editing = null; sheetOpen = true },
                        shapes = ButtonDefaults.shapes(),
                        modifier = tall.weight(1f),
                    ) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Repeating")
                    }
                }
            }
            remindersSection(reminders = reminders, onEdit = { editing = it; sheetOpen = true })
            item(key = "end") { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (sheetOpen) {
        // Keyed so opening a different reminder starts the sheet fresh.
        androidx.compose.runtime.key(editing?.id) {
            ReminderSheet(initial = editing, onDismiss = { sheetOpen = false })
        }
    }
}
