package dev.junjo.anchor.ui

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon as SystemIcon
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import dev.junjo.anchor.AppPrefs
import dev.junjo.anchor.NoteTileService
import dev.junjo.anchor.QuickAddTileService
import dev.junjo.anchor.R
import dev.junjo.anchor.TripTileService
import dev.junjo.anchor.data.Backup
import dev.junjo.anchor.data.today
import dev.junjo.anchor.notify.Notifications
import dev.junjo.anchor.overlay.FloatingGoals
import dev.junjo.anchor.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class SetupItem(val title: String, val why: String, val button: String, val fix: () -> Unit)

/** One-time checklist for the Android settings the app needs to work well. Disappears when done. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SetupCard(refresh: Int, modifier: Modifier, onWidgetUnsupported: () -> Unit) {
    val ctx = LocalContext.current
    var hidden by remember(refresh) { mutableStateOf(AppPrefs.setupHidden(ctx)) }
    val items = remember(refresh) { setupItems(ctx, onWidgetUnsupported) }
    if (hidden || items.isEmpty()) return

    Surface(
        modifier.fillMaxWidth().padding(bottom = 16.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Setup", style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
                TextButton(onClick = { AppPrefs.setSetupHidden(ctx, true); hidden = true }, shapes = ButtonDefaults.shapes()) {
                    Text("Hide")
                }
            }
            items.forEach { item ->
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall)
                        Text(item.why, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.width(12.dp))
                    FilledTonalButton(onClick = item.fix, shapes = ButtonDefaults.shapes()) { Text(item.button) }
                }
            }
        }
    }
}

@SuppressLint("BatteryLife")
private fun setupItems(ctx: Context, onWidgetUnsupported: () -> Unit): List<SetupItem> = buildList {
    val pkg = ctx.packageName
    fun open(intent: Intent) = runCatching { ctx.startActivity(intent) }

    if (!Notifications.canPost(ctx)) add(
        SetupItem("Allow notifications", "Timers, alarms and your list on the lock screen need them.", "Allow") {
            open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg))
        },
    )
    if (Build.VERSION.SDK_INT >= 34 && !ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) add(
        SetupItem("Allow full-screen alarms", "So “time's up” fills the screen even when the phone is locked.", "Allow") {
            open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$pkg")))
        },
    )
    if (Build.VERSION.SDK_INT >= 36 && Notifications.canPost(ctx) &&
        !NotificationManagerCompat.from(ctx).canPostPromotedNotifications()
    ) add(
        SetupItem("Show timers as Live Updates", "The countdown sits in the status bar and on top of your notifications.", "Allow") {
            open(Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg))
        },
    )
    if (!FloatingGoals.isOn(ctx)) add(
        SetupItem("Keep your list floating on screen", "A small bubble over every app shows your top to-do and the timer.", "Turn on") {
            FloatingGoals.enable(ctx)
        },
    )
    if (!Widgets.isPlaced(ctx)) add(
        SetupItem("Put your list on the home screen", "A widget keeps them in sight, so you don't have to remember to look.", "Add") {
            if (!Widgets.requestPin(ctx)) onWidgetUnsupported()
        },
    )
    if (!ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(pkg)) add(
        SetupItem("Keep timers on time", "Lets timers and the bubble keep running when the phone tries to save battery.", "Allow") {
            open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")))
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsSection(refresh: Int, onShowSetup: () -> Unit, onMessage: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var goalsNotification by remember { mutableStateOf(AppPrefs.goalsNotification(ctx)) }
    var bubble by remember(refresh) { mutableStateOf(FloatingGoals.isOn(ctx)) }
    var bubbleNudge by remember { mutableStateOf(AppPrefs.bubbleNudge(ctx)) }

    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val json = Backup.exportJson()
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        ?: error("Could not open destination")
                }.isSuccess
            }
            onMessage(if (ok) "Backup saved" else "Couldn't save backup")
        }
    }

    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: error("Could not read file")
                    Backup.importJson(raw)
                }
            }
            onMessage(result.fold(onSuccess = { it.message() }, onFailure = { "Couldn't read that backup file" }))
        }
    }

    Column(Modifier.padding(top = 8.dp)) {
        Text(
            "Always in sight",
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        SettingRow(
            title = "Floating bubble",
            about = "A small pill over every app with your top to-do, or the countdown while a timer runs. Tap it for your list; drag it anywhere, or push it off the side to tuck it into the edge.",
            checked = bubble,
            shape = segmentShape(0, 3),
        ) {
            bubble = it
            if (it) FloatingGoals.enable(ctx) else FloatingGoals.disable(ctx)
        }
        Spacer(Modifier.height(2.dp))
        SettingRow(
            title = "Nudge tucked bubble",
            about = "Briefly flashes the tucked edge tab red every few minutes while you still have open to-dos.",
            checked = bubbleNudge,
            shape = segmentShape(1, 3),
        ) {
            bubbleNudge = it
            AppPrefs.setBubbleNudge(ctx, it)
        }
        Spacer(Modifier.height(2.dp))
        SettingRow(
            title = "Your list in notifications",
            about = "Shows your list on the lock screen and lets you add a to-do from the notification shade.",
            checked = goalsNotification,
            shape = segmentShape(2, 3),
        ) {
            goalsNotification = it
            AppPrefs.setGoalsNotification(ctx, it)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Backup",
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { exportBackup.launch("anchor-${today()}.json") },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f),
            ) { Text("Export") }
            FilledTonalButton(
                onClick = { importBackup.launch(arrayOf("application/json", "text/plain", "*/*")) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f),
            ) { Text("Import") }
        }
        Spacer(Modifier.height(8.dp))
        if (Build.VERSION.SDK_INT >= 33) {
            TextButton(onClick = { requestTile(ctx, QuickAddTileService::class.java, R.string.tile_label, R.drawable.ic_add) }, shapes = ButtonDefaults.shapes()) {
                Text("Add an “Add” button to Quick Settings")
            }
            TextButton(onClick = { requestTile(ctx, NoteTileService::class.java, R.string.note_tile_label, R.drawable.ic_note) }, shapes = ButtonDefaults.shapes()) {
                Text("Add a “Note” button to Quick Settings")
            }
            TextButton(onClick = { requestTile(ctx, TripTileService::class.java, R.string.trip_tile_label, R.drawable.ic_trip) }, shapes = ButtonDefaults.shapes()) {
                Text("Add an “In&Out” button to Quick Settings")
            }
        }
        if (AppPrefs.setupHidden(ctx)) {
            TextButton(onClick = { AppPrefs.setSetupHidden(ctx, false); onShowSetup() }, shapes = ButtonDefaults.shapes()) {
                Text("Show setup checklist")
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    about: String,
    checked: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onChange: (Boolean) -> Unit,
) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(16.dp))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@RequiresApi(33)
private fun requestTile(ctx: Context, tile: Class<*>, label: Int, icon: Int) {
    ctx.getSystemService(StatusBarManager::class.java).requestAddTileService(
        ComponentName(ctx, tile),
        ctx.getString(label),
        SystemIcon.createWithResource(ctx, icon),
        ctx.mainExecutor,
    ) { }
}
