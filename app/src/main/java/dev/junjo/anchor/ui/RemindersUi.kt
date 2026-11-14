package dev.junjo.anchor.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.junjo.anchor.R
import dev.junjo.anchor.data.Reminder
import dev.junjo.anchor.data.ReminderStore
import java.time.LocalTime

/** The "Daily reminders" part of the main list. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LazyListScope.remindersSection(
    reminders: List<Reminder>,
    onAdd: () -> Unit,
    onEdit: (Reminder) -> Unit,
) {
    item(key = "reminders-header") {
        Row(
            Modifier.animateItem().fillMaxWidth().padding(top = 28.dp, bottom = 8.dp, start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Daily reminders",
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = onAdd, shapes = ButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add")
            }
        }
    }
    if (reminders.isEmpty()) {
        item(key = "reminders-empty") {
            Text(
                "For things that come back every day, like meds, water or winding down for bed. " +
                    "When one goes off it lands on your goal list and stays there until you tick it off.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.animateItem().padding(horizontal = 8.dp),
            )
        }
    }
    itemsIndexed(reminders, key = { _, r -> "reminder-${r.id}" }) { i, r ->
        ReminderRow(r, segmentShape(i, reminders.size), Modifier.animateItem(), onEdit = { onEdit(r) })
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReminderRow(r: Reminder, shape: androidx.compose.ui.graphics.Shape, modifier: Modifier, onEdit: () -> Unit) {
    val dim = if (r.enabled) 1f else 0.5f
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.clickable(onClick = onEdit).padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    r.timeText,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim),
                )
                Text(
                    r.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    r.daysText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim),
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = r.enabled, onCheckedChange = { ReminderStore.save(r.copy(enabled = it)) })
        }
    }
}

/** Add or edit a reminder. [initial] is null for a new one. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReminderSheet(initial: Reminder?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val defaultTime = remember { LocalTime.now().plusHours(1).withMinute(0) }

    var text by remember { mutableStateOf(initial?.text ?: "") }
    var days by remember { mutableIntStateOf(initial?.days ?: Reminder.ALL_DAYS) }
    var addAsGoal by remember { mutableStateOf(initial?.addAsGoal ?: true) }
    val time = rememberTimePickerState(
        initialHour = initial?.hour ?: defaultTime.hour,
        initialMinute = initial?.minute ?: defaultTime.minute,
        is24Hour = DateFormat.is24HourFormat(ctx),
    )
    val canSave = text.isNotBlank() && days != 0

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                if (initial == null) "New daily reminder" else "Edit reminder",
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            Spacer(Modifier.height(16.dp))
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("What to do, e.g. Take meds") },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = time) }

            Text("Repeat on", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            DayPicker(days, onChange = { days = it })
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { days = Reminder.ALL_DAYS }, shapes = ButtonDefaults.shapes()) { Text("Every day") }
                TextButton(onClick = { days = Reminder.WEEKDAYS }, shapes = ButtonDefaults.shapes()) { Text("Weekdays") }
                TextButton(onClick = { days = Reminder.WEEKEND }, shapes = ButtonDefaults.shapes()) { Text("Weekends") }
            }

            Row(
                Modifier.fillMaxWidth().clickable { addAsGoal = !addAsGoal }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Put it on my goal list", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Stays in the bubble and widget until you tick it off, so it can't get swiped away.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = addAsGoal, onCheckedChange = { addAsGoal = it })
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (initial != null) {
                    TextButton(
                        onClick = { ReminderStore.delete(initial.id); onDismiss() },
                        shapes = ButtonDefaults.shapes(),
                    ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        val saved = initial?.copy(
                            text = text,
                            hour = time.hour,
                            minute = time.minute,
                            days = days,
                            addAsGoal = addAsGoal,
                            enabled = true,
                        ) ?: Reminder.new(text, time.hour, time.minute, days, addAsGoal)
                        ReminderStore.save(saved)
                        onDismiss()
                    },
                    enabled = canSave,
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Save") }
            }
        }
    }
}

/** Seven day buttons, Monday first. A picked day turns into a filled cookie. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DayPicker(days: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, letter ->
            val bit = 1 shl i
            val on = days and bit != 0
            Box(
                Modifier
                    .size(42.dp)
                    .clip(if (on) MaterialShapes.Cookie4Sided.toShape() else MaterialShapes.Circle.toShape())
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable { onChange(days xor bit) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    letter,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
