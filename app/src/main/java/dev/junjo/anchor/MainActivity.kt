package dev.junjo.anchor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.junjo.anchor.ui.AnchorTheme
import dev.junjo.anchor.ui.HomeScreen
import dev.junjo.anchor.ui.NotesScreen
import dev.junjo.anchor.ui.RemindersScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent { AnchorTheme { App() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** A "Focus" shortcut pinned before 0.4 still points here: open the focus sheet over the app. */
    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_FOCUS) QuickAddActivity.open(this, QuickAddActivity.Kind.FOCUS)
    }

    companion object {
        const val ACTION_OPEN_FOCUS = "dev.junjo.anchor.OPEN_FOCUS"
    }
}

private enum class Screen { HOME, REMINDERS, NOTES }

/** One screen, To-do, with Reminders behind the bell and All notes under Notes. */
@Composable
private fun App() {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
    when (screen) {
        Screen.REMINDERS -> RemindersScreen(onBack = { screen = Screen.HOME })
        Screen.NOTES -> NotesScreen(onBack = { screen = Screen.HOME })
        Screen.HOME -> HomeScreen(
            onOpenReminders = { screen = Screen.REMINDERS },
            onOpenNotes = { screen = Screen.NOTES },
        )
    }
}
