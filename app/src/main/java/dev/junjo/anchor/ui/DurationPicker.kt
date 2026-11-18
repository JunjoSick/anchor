package dev.junjo.anchor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.junjo.anchor.R

/** "1 h 20" rather than "1 h 20 min", so any length fits in one button. */
fun shortDuration(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60}"
}

/**
 * How long, as one connected row: a few common lengths, and a last button for any other. A
 * length typed there shows in that button, so the choice is always visible in one place.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DurationRow(
    presets: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onOther: () -> Unit,
    modifier: Modifier = Modifier,
    prefix: String = "",
) {
    val custom = selected !in presets
    val count = presets.size + 1
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        @Composable
        fun Segment(index: Int, checked: Boolean, description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
            ToggleButton(
                checked = checked,
                onCheckedChange = { onClick() },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.RadioButton; contentDescription = description },
            ) { content() }
        }
        presets.forEachIndexed { i, m ->
            Segment(i, checked = selected == m, description = "$prefix${shortDuration(m)}", onClick = { onSelect(m) }) {
                Text(shortDuration(m), maxLines = 1, overflow = TextOverflow.Clip)
            }
        }
        Segment(count - 1, checked = custom, description = if (custom) "$prefix${shortDuration(selected)}, change" else "Other length", onClick = onOther) {
            if (custom) Text(shortDuration(selected), maxLines = 1, overflow = TextOverflow.Clip)
            else Icon(painterResource(R.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/** Any length of time, typed as hours and minutes. The quick picks live in [DurationRow]. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DurationDialog(
    title: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onPick: (minutes: Int) -> Unit,
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
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimeInput(state = input) } },
        confirmButton = {
            TextButton(onClick = { onPick(total) }, enabled = total > 0, shapes = ButtonDefaults.shapes()) {
                Text(if (total > 0) shortDuration(total).let { "Set $it" } else "Set")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
