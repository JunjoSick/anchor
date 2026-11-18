package dev.junjo.anchor

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import dev.junjo.anchor.ui.AnchorTheme
import dev.junjo.anchor.ui.HomeScreen
import dev.junjo.anchor.ui.NotesScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AnchorTheme { AppShell() } }
    }
}

/** Two tabs: today's goals, reminders and timer; and notes. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppShell() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Back from Notes goes to Today rather than out of the app.
    BackHandler(enabled = tab != 0) { tab = 0 }
    val bar: @Composable () -> Unit = {
        ShortNavigationBar {
            ShortNavigationBarItem(
                selected = tab == 0,
                onClick = { tab = 0 },
                icon = { Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null) },
                label = { Text("Today") },
            )
            ShortNavigationBarItem(
                selected = tab == 1,
                onClick = { tab = 1 },
                icon = { Icon(painterResource(R.drawable.ic_note), contentDescription = null) },
                label = { Text("Notes") },
            )
        }
    }
    if (tab == 0) HomeScreen(bottomBar = bar) else NotesScreen(bottomBar = bar)
}
