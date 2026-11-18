package dev.junjo.anchor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.junjo.anchor.durationText

/** Quick picks shared by every duration: focus, getting ready, "remind me in". */
val DURATION_PRESETS = listOf(5, 10, 15, 25, 45, 60, 90)

/**
 * Pick any length of time: tap a common one, or type hours and minutes. Used wherever the app
 * asks "how long", so it works the same everywhere.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun DurationDialog(
    title: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onPick: (minutes: Int) -> Unit,
    presets: List<Int> = DURATION_PRESETS,
) {
    // A 24-hour time input doubles as an hours-and-minutes field: 0-23 h, 0-59 min.
    val input = rememberTimePickerState(
        initialHour = (initialMinutes / 60).coerceIn(0, 23),
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )
    val total = input.hour * 60 + input.minute
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { m ->
                        FilterChip(
                            selected = total == m,
                            onClick = { onPick(m) },
                            label = { Text(durationText(m)) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Or type it: hours and minutes",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimeInput(state = input) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(total) }, enabled = total > 0, shapes = ButtonDefaults.shapes()) {
                Text("Set ${if (total > 0) durationText(total) else ""}".trim())
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Cancel") } },
    )
}
