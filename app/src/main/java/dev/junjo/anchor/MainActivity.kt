package dev.junjo.anchor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import dev.junjo.anchor.ui.AddMenu
import dev.junjo.anchor.ui.AnchorTheme
import dev.junjo.anchor.ui.FocusScreen
import dev.junjo.anchor.ui.HomeScreen
import dev.junjo.anchor.ui.NotesScreen
import dev.junjo.anchor.ui.RemindersScreen

class MainActivity : ComponentActivity() {
    /** Set by a shortcut ("Focus") to open a particular tab; -1 when nothing was asked for. */
    private val requestedTab = mutableIntStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedTab.intValue = tabFor(intent)
        setContent { AnchorTheme { AppShell(requestedTab) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requestedTab.intValue = tabFor(intent)
    }

    private fun tabFor(intent: Intent?) = if (intent?.action == ACTION_OPEN_FOCUS) Tab.FOCUS.ordinal else -1

    companion object {
        const val ACTION_OPEN_FOCUS = "dev.junjo.anchor.OPEN_FOCUS"
    }
}

private enum class Tab(val label: String, val icon: Int) {
    TODAY("Today", R.drawable.ic_check_circle),
    FOCUS("Focus", R.drawable.ic_timer),
    REMINDERS("Reminders", R.drawable.ic_bell),
    NOTES("Notes", R.drawable.ic_note),
}

/** Four tabs with one job each, and the same + menu on all of them. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppShell(requested: MutableIntState) {
    var tab by rememberSaveable { mutableIntStateOf(Tab.TODAY.ordinal) }
    if (requested.intValue >= 0) {
        tab = requested.intValue
        requested.intValue = -1
    }
    // Back from another tab goes to Today rather than out of the app.
    BackHandler(enabled = tab != Tab.TODAY.ordinal) { tab = Tab.TODAY.ordinal }

    val bar: @Composable () -> Unit = {
        ShortNavigationBar {
            Tab.entries.forEach { t ->
                ShortNavigationBarItem(
                    selected = tab == t.ordinal,
                    onClick = { tab = t.ordinal },
                    icon = { Icon(painterResource(t.icon), contentDescription = null) },
                    label = { Text(t.label) },
                )
            }
        }
    }
    val fab: @Composable () -> Unit = { AddMenu(onFocus = { tab = Tab.FOCUS.ordinal }) }
    when (Tab.entries[tab]) {
        Tab.TODAY -> HomeScreen(bottomBar = bar, fab = fab)
        Tab.FOCUS -> FocusScreen(bottomBar = bar, fab = fab)
        Tab.REMINDERS -> RemindersScreen(bottomBar = bar, fab = fab)
        Tab.NOTES -> NotesScreen(bottomBar = bar, fab = fab)
    }
}
