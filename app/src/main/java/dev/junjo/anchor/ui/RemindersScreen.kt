package dev.junjo.anchor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.junjo.anchor.QuickAddActivity
import dev.junjo.anchor.R
import dev.junjo.anchor.data.ReminderStore
import dev.junjo.anchor.reminders.Reminders

/** One-offs and routines, opened from the bell on To-do. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RemindersScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val all by ReminderStore.reminders.collectAsStateWithLifecycle()
    // Re-checked when the screen comes back to the front, so past one-offs drop out and
    // "Next …" times stay current.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }

    val visible = remember(all, resumes) { ReminderStore.visible(all) }
    val onceList = remember(visible) { visible.filter { it.once }.sortedBy { it.startAt } }
    val routines = remember(visible, resumes) {
        visible.filter { !it.once }
            .sortedWith(compareBy({ !it.enabled }, { Reminders.next(it) ?: Long.MAX_VALUE }))
    }

    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = editingId?.let { id -> all.firstOrNull { it.id == id && !it.deleted } }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Reminders") },
                subtitle = { Text(if (visible.isEmpty()) "Nothing set" else "${visible.size} set") },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { QuickAddActivity.open(ctx, QuickAddActivity.Kind.REMIND) }) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = "New reminder")
            }
        },
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
            if (visible.isEmpty()) {
                item(key = "reminders-empty") {
                    Text(
                        "Once: a call in 40 minutes. Routines: meds, water, bedtime. " +
                            "When one goes off it lands on your list until you tick it off.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.animateItem().padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
            }
            if (onceList.isNotEmpty()) {
                item(key = "once-header") {
                    SectionHeader("Once", Modifier.animateItem(), topPadding = 4.dp)
                }
                reminderGroup(reminders = onceList, refresh = resumes, onEdit = { editingId = it.id; sheetOpen = true })
            }
            if (routines.isNotEmpty()) {
                item(key = "routines-header") {
                    SectionHeader("Routines", Modifier.animateItem(), topPadding = if (onceList.isEmpty()) 4.dp else 20.dp)
                }
                reminderGroup(reminders = routines, refresh = resumes, onEdit = { editingId = it.id; sheetOpen = true })
            }
            item(key = "end") { Spacer(Modifier.height(8.dp)) }
        }
    }

    if (sheetOpen) {
        // Keyed so opening a different reminder starts the sheet fresh.
        key(editing?.id) {
            ReminderSheet(initial = editing, onDismiss = { sheetOpen = false })
        }
    }
}
