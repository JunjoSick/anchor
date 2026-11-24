package dev.junjo.anchor.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
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
import dev.junjo.anchor.data.Reminder
import dev.junjo.anchor.data.ReminderStore

/** Repeating reminders and one-offs, opened from the bell on Today. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RemindersScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val all by ReminderStore.reminders.collectAsStateWithLifecycle()
    // Re-checked when the screen comes back to the front, so past one-offs drop out and
    // "Next …" times for intervals stay current.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val reminders = remember(all, resumes) { ReminderStore.visible(all) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = editingId?.let { id -> all.firstOrNull { it.id == id && !it.deleted } }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Reminders") },
                subtitle = { Text(if (reminders.isEmpty()) "Nothing set" else "${reminders.size} set") },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
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
                        Text("Once")
                    }
                    FilledTonalButton(
                        onClick = { editingId = null; sheetOpen = true },
                        shapes = ButtonDefaults.shapes(),
                        modifier = tall.weight(1f),
                    ) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Routine")
                    }
                }
            }
            remindersSection(reminders = reminders, refresh = resumes, onEdit = { editingId = it.id; sheetOpen = true })
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
