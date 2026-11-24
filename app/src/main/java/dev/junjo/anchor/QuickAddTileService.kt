package dev.junjo.anchor

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** An "Add goal" button in the Quick Settings panel: swipe down, tap, type. Works from any app. */
open class QuickAddTileService : TileService() {
    protected open val action: String = QuickAddActivity.ACTION_ADD_GOAL

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        val intent = Intent(this, QuickAddActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            // The only way on Android 13 and older; the newer call above doesn't exist there.
            @Suppress("DEPRECATION")
            @SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}

/** An "Add note" button in Quick Settings. */
class NoteTileService : QuickAddTileService() {
    override val action: String = QuickAddActivity.ACTION_NEW_NOTE
}

/** An "In&Out" button in Quick Settings. */
class TripTileService : QuickAddTileService() {
    override val action: String = QuickAddActivity.ACTION_TRIP
}
