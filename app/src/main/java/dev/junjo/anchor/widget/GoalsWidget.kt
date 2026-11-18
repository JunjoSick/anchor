package dev.junjo.anchor.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import android.content.Intent
import androidx.glance.LocalContext
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import dev.junjo.anchor.MainActivity
import dev.junjo.anchor.QuickAddActivity
import dev.junjo.anchor.R
import dev.junjo.anchor.clockTime
import dev.junjo.anchor.data.Goal
import dev.junjo.anchor.data.GoalStore
import dev.junjo.anchor.data.active
import dev.junjo.anchor.data.doneToday
import dev.junjo.anchor.timer.FocusTimer

/**
 * The home-screen widget, the main way to see goals: always in sight, with no need to remember
 * to open anything. Tap a goal to tick it off (tap again to undo), + to add one.
 */
class GoalsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val goals by GoalStore.goals.collectAsState()
            val timer by FocusTimer.state.collectAsState()
            GlanceTheme { WidgetContent(goals, timer) }
        }
    }
}

class GoalsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GoalsWidget()
}

private val GoalIdKey = ActionParameters.Key<String>("goal_id")

class ToggleGoalAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        parameters[GoalIdKey]?.let { GoalStore.toggle(it) }
    }
}

/** How many ticked-off goals stay visible on the widget, as a small win and an easy undo. */
private const val DONE_SHOWN = 3

@Composable
private fun WidgetContent(goals: List<Goal>, timer: FocusTimer.State) {
    val active = goals.active()
    val done = goals.doneToday().take(DONE_SHOWN)

    Column(
        GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            // The launcher's own widget corner radius, so it sits right next to the system's widgets.
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .padding(12.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (active.isEmpty()) "Goals" else "Goals · ${active.size}",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>()),
            )
            TimerChip(timer)
            Spacer(GlanceModifier.width(8.dp))
            val ctx = LocalContext.current
            Box(
                GlanceModifier
                    .size(36.dp)
                    .cornerRadius(18.dp)
                    .background(GlanceTheme.colors.secondaryContainer)
                    .clickable(
                        actionStartActivity(
                            Intent(ctx, QuickAddActivity::class.java).setAction(QuickAddActivity.ACTION_NEW_NOTE),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    ImageProvider(R.drawable.ic_note),
                    contentDescription = "Write something down",
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                    modifier = GlanceModifier.size(20.dp),
                )
            }
            Spacer(GlanceModifier.width(6.dp))
            Box(
                GlanceModifier
                    .size(36.dp)
                    .cornerRadius(18.dp)
                    .background(GlanceTheme.colors.primary)
                    .clickable(
                        actionStartActivity(
                            Intent(ctx, QuickAddActivity::class.java).setAction(QuickAddActivity.ACTION_ADD_GOAL),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    ImageProvider(R.drawable.ic_add),
                    contentDescription = "Add goal",
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
                    modifier = GlanceModifier.size(22.dp),
                )
            }
        }
        Spacer(GlanceModifier.height(6.dp))

        if (active.isEmpty() && done.isEmpty()) {
            Text(
                "Nothing on the list. Tap + to add a goal.",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                modifier = GlanceModifier.padding(vertical = 8.dp),
            )
        } else {
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(active, itemId = { it.id.hashCode().toLong() }) { GoalLine(it, done = false) }
                items(done, itemId = { "done-${it.id}".hashCode().toLong() }) { GoalLine(it, done = true) }
            }
        }
    }
}

@Composable
private fun TimerChip(timer: FocusTimer.State) {
    val text = when (timer.status) {
        FocusTimer.Status.RUNNING ->
            if (timer.isGettingReady) "▶ focus at ${clockTime(timer.endAt)}" else "⏱ until ${clockTime(timer.endAt)}"
        FocusTimer.Status.PAUSED -> "⏸ paused"
        FocusTimer.Status.RINGING -> "⏰ time's up"
        FocusTimer.Status.IDLE -> return
    }
    Text(
        text,
        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        modifier = GlanceModifier.padding(horizontal = 4.dp).clickable(actionStartActivity<MainActivity>()),
    )
}

@Composable
private fun GoalLine(goal: Goal, done: Boolean) {
    Row(
        GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(actionRunCallback<ToggleGoalAction>(actionParametersOf(GoalIdKey to goal.id))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            ImageProvider(if (done) R.drawable.ic_check_circle else R.drawable.ic_circle),
            contentDescription = if (done) "Done, tap to undo" else "Tap to mark done",
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            modifier = GlanceModifier.size(22.dp),
        )
        Spacer(GlanceModifier.width(10.dp))
        Text(
            goal.text,
            maxLines = 2,
            style = TextStyle(
                color = if (done) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface,
                fontSize = 15.sp,
                textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
            ),
        )
    }
}
