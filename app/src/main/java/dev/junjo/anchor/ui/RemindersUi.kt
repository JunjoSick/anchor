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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.junjo.anchor.R
import dev.junjo.anchor.clockTime
import dev.junjo.anchor.data.Reminder
import dev.junjo.anchor.data.ReminderStore
import dev.junjo.anchor.reminders.Reminders
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val ONCE_PRESETS = listOf(10, 30, 60, 120)

private enum class ScheduleMode { ONCE, DAYS, EVERY }

/** Renders one section of reminders (either Once or Routines). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LazyListScope.reminderGroup(
    reminders: List<Reminder>,
    refresh: Int = 0,
    onEdit: (Reminder) -> Unit,
) {
    itemsIndexed(reminders, key = { _, r -> "reminder-${r.id}" }) { i, r ->
        ReminderRow(r, segmentShape(i, reminders.size), refresh, Modifier.animateItem(), onEdit = { onEdit(r) })
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReminderRow(r: Reminder, shape: androidx.compose.ui.graphics.Shape, refresh: Int, modifier: Modifier, onEdit: () -> Unit) {
    val dim = if (r.enabled) 1f else 0.5f
    val nextLabel = remember(r, refresh) {
        when {
            r.once -> whenText(r.startAt)
            else -> Reminders.next(r)?.let { "Next ${whenText(it)}" } ?: r.timeText
        }
    }
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.clickable(onClick = onEdit).padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.text,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (r.alarm) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            painterResource(R.drawable.ic_bell_ring),
                            contentDescription = "Rings like an alarm",
                            tint = MaterialTheme.colorScheme.tertiary.copy(alpha = dim),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    if (r.once) nextLabel else "$nextLabel · ${r.daysText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            if (r.once) {
                TextButton(onClick = { ReminderStore.delete(r.id) }, shapes = ButtonDefaults.shapes()) { Text("Cancel") }
            } else {
                Switch(checked = r.enabled, onCheckedChange = { ReminderStore.save(r.copy(enabled = it)) })
            }
        }
    }
}

/**
 * One sheet for creating or editing any reminder: Once, On days, or Every….
 * [initial] is null for a new reminder; [initialText] optionally prefills the name.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReminderSheet(
    initial: Reminder?,
    onDismiss: () -> Unit,
    initialText: String = "",
    startOnRepeat: Boolean = false,
) {
    val ctx = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val defaultTime = remember { LocalTime.now().plusHours(1).withMinute(0) }

    val initialMode = when {
        initial == null -> if (startOnRepeat) ScheduleMode.DAYS else ScheduleMode.ONCE
        initial.once -> ScheduleMode.ONCE
        initial.repeatsByInterval -> ScheduleMode.EVERY
        else -> ScheduleMode.DAYS
    }
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var text by rememberSaveable { mutableStateOf(initial?.text ?: initialText) }
    var days by rememberSaveable {
        mutableIntStateOf(initial?.takeIf { !it.once && !it.repeatsByInterval }?.days ?: Reminder.ALL_DAYS)
    }
    var addAsGoal by rememberSaveable { mutableStateOf(initial?.addAsGoal ?: true) }
    // New one-offs ring by default; new routines default quiet; existing reminders keep their setting.
    var alarm by rememberSaveable { mutableStateOf(initial?.alarm ?: (initialMode == ScheduleMode.ONCE)) }
    var everyDays by rememberSaveable {
        mutableIntStateOf(initial?.takeIf { it.repeatsByInterval }?.let { it.everyMinutes / Reminder.DAY_MINUTES } ?: 2)
    }
    var everyHours by rememberSaveable {
        mutableIntStateOf(initial?.takeIf { it.repeatsByInterval }?.let { (it.everyMinutes % Reminder.DAY_MINUTES) / 60 } ?: 0)
    }

    // For Once mode: relative minutes (inMinutes) or explicit clock time (atHour >= 0).
    var inMinutes by rememberSaveable { mutableIntStateOf(30) }
    var atHour by rememberSaveable {
        mutableIntStateOf(
            if (initial != null && initial.once) {
                Instant.ofEpochMilli(initial.startAt).atZone(ZoneId.systemDefault()).hour
            } else -1,
        )
    }
    var atMinute by rememberSaveable {
        mutableIntStateOf(
            if (initial != null && initial.once) {
                Instant.ofEpochMilli(initial.startAt).atZone(ZoneId.systemDefault()).minute
            } else 0,
        )
    }
    var pickingOnce by rememberSaveable { mutableStateOf(false) }

    val time = rememberTimePickerState(
        initialHour = initial?.hour ?: defaultTime.hour,
        initialMinute = initial?.minute ?: defaultTime.minute,
        is24Hour = DateFormat.is24HourFormat(ctx),
    )
    val now = rememberNow(active = mode == ScheduleMode.ONCE, periodMs = 15_000L)

    fun onceTarget(base: Long = System.currentTimeMillis()): Long =
        if (atHour >= 0) Reminder.nextOccurrence(atHour, atMinute, Instant.ofEpochMilli(base).atZone(ZoneId.systemDefault()))
        else base + inMinutes * 60_000L

    val everyMinutes = if (mode == ScheduleMode.EVERY) everyDays * Reminder.DAY_MINUTES + everyHours * 60 else 0
    val canSave = text.isNotBlank() && when (mode) {
        ScheduleMode.ONCE -> true
        ScheduleMode.DAYS -> days != 0
        ScheduleMode.EVERY -> everyMinutes > 0
    }

    /** The reminder as it would be saved now. */
    fun draft(base: Long = System.currentTimeMillis()): Reminder {
        if (mode == ScheduleMode.ONCE) {
            val target = onceTarget(base)
            val zdt = Instant.ofEpochMilli(target).atZone(ZoneId.systemDefault())
            val baseReminder = initial ?: Reminder.once(text, target, alarm)
            return baseReminder.copy(
                text = text,
                hour = zdt.hour,
                minute = zdt.minute,
                days = 0,
                enabled = true,
                addAsGoal = addAsGoal,
                alarm = alarm,
                everyMinutes = 0,
                startAt = target,
                once = true,
            )
        }
        val baseReminder = initial ?: Reminder.new(text, time.hour, time.minute, days, addAsGoal)
        val sameCycle = initial != null && !initial.once && initial.everyMinutes == everyMinutes &&
            initial.hour == time.hour && initial.minute == time.minute
        return baseReminder.copy(
            text = text,
            hour = time.hour,
            minute = time.minute,
            days = if (mode == ScheduleMode.DAYS) days else 0,
            addAsGoal = addAsGoal,
            enabled = initial?.enabled ?: true,
            alarm = alarm,
            everyMinutes = everyMinutes,
            startAt = when {
                everyMinutes == 0 -> 0
                sameCycle -> initial.startAt
                else -> Reminder.intervalStart(time.hour, time.minute, everyMinutes)
            },
            once = false,
        )
    }

    if (pickingOnce) {
        RemindWhenDialog(
            initialMinutes = inMinutes,
            initialHour = atHour.takeIf { it >= 0 },
            initialMinute = atMinute,
            defaultToClock = atHour >= 0 || inMinutes in ONCE_PRESETS,
            onDismiss = { pickingOnce = false },
            onPickClock = { h, m -> atHour = h; atMinute = m; pickingOnce = false },
            onPickDuration = { m -> atHour = -1; inMinutes = m; pickingOnce = false },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                if (initial == null) "New reminder" else "Edit reminder",
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
            Spacer(Modifier.height(16.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == ScheduleMode.ONCE,
                    onClick = { mode = ScheduleMode.ONCE },
                    shape = SegmentedButtonDefaults.itemShape(0, 3),
                ) { Text("Once", maxLines = 1) }
                SegmentedButton(
                    selected = mode == ScheduleMode.DAYS,
                    onClick = { mode = ScheduleMode.DAYS },
                    shape = SegmentedButtonDefaults.itemShape(1, 3),
                ) { Text("On days", maxLines = 1) }
                SegmentedButton(
                    selected = mode == ScheduleMode.EVERY,
                    onClick = { mode = ScheduleMode.EVERY },
                    shape = SegmentedButtonDefaults.itemShape(2, 3),
                ) { Text("Every…", maxLines = 1) }
            }

            when (mode) {
                ScheduleMode.ONCE -> {
                    Spacer(Modifier.height(16.dp))
                    val pickedClockLabel = if (atHour >= 0) {
                        LocalTime.of(atHour, atMinute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                    } else null
                    DurationRow(
                        ONCE_PRESETS,
                        inMinutes,
                        onSelect = { atHour = -1; inMinutes = it },
                        onOther = { pickingOnce = true },
                        prefix = "In ",
                        customLabel = pickedClockLabel,
                        otherIcon = R.drawable.ic_schedule,
                        otherDescription = "Pick a time",
                    )
                    Text(
                        "Next: ${whenText(onceTarget(now))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp, start = 4.dp),
                    )
                }
                ScheduleMode.DAYS -> {
                    Text("At", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = time) }
                    Text("Repeat on", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                    DayPicker(days, onChange = { days = it })
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { days = Reminder.ALL_DAYS }, shapes = ButtonDefaults.shapes()) { Text("Every day") }
                        TextButton(onClick = { days = Reminder.WEEKDAYS }, shapes = ButtonDefaults.shapes()) { Text("Weekdays") }
                        TextButton(onClick = { days = Reminder.WEEKEND }, shapes = ButtonDefaults.shapes()) { Text("Weekends") }
                    }
                    if (canSave) {
                        Reminders.next(draft())?.let {
                            Text(
                                "Next: ${whenText(it)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                            )
                        }
                    }
                }
                ScheduleMode.EVERY -> {
                    Spacer(Modifier.height(16.dp))
                    Stepper("Every", everyDays, if (everyDays == 1) "day" else "days", 0..60) { everyDays = it }
                    Stepper("and", everyHours, if (everyHours == 1) "hour" else "hours", 0..23) { everyHours = it }
                    Text("Starting at", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = time) }
                    if (canSave) {
                        Reminders.next(draft())?.let {
                            Text(
                                "Next: ${whenText(it)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                            )
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().clickable { alarm = !alarm }.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Ring like an alarm", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Rings and fills the screen until you tap Done or Snooze, even on silent. For the things you can't miss.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = alarm, onCheckedChange = { alarm = it })
            }

            Row(
                Modifier.fillMaxWidth().clickable { addAsGoal = !addAsGoal }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Put it on my list", style = MaterialTheme.typography.titleSmall)
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
                        ReminderStore.save(draft())
                        onDismiss()
                    },
                    enabled = canSave,
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Save") }
            }
        }
    }
}

/** A number with - and + buttons, like "Every [-] 2 [+] days". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Stepper(before: String, value: Int, after: String, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(before, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(56.dp))
        FilledTonalIconButton(
            onClick = { onChange(value - 1) },
            enabled = value > range.first,
            shapes = IconButtonDefaults.shapes(),
        ) { Icon(painterResource(R.drawable.ic_remove), "Fewer") }
        Text(
            "$value",
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(48.dp),
        )
        FilledTonalIconButton(
            onClick = { onChange(value + 1) },
            enabled = value < range.last,
            shapes = IconButtonDefaults.shapes(),
        ) { Icon(painterResource(R.drawable.ic_add), "More") }
        Spacer(Modifier.width(12.dp))
        Text(after, style = MaterialTheme.typography.bodyLarge)
    }
}

/** "today 14:00", "tomorrow 9:00" or "Thu 20 Nov 9:00". */
fun whenText(epochMs: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val day = when (date) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
    }
    return "$day ${clockTime(epochMs)}"
}

/** Compact label for Coming up rows: "18:00" if today, otherwise "tomorrow 8:00". */
fun comingUpTimeText(epochMs: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return if (date == today) clockTime(epochMs) else "tomorrow ${clockTime(epochMs)}"
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
